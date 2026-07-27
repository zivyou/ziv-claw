package com.zivyou.zivclaw.provider;

import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.model.completion.chat.ChatFunctionCall;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import com.volcengine.ark.runtime.model.completion.chat.ChatToolCall;
import com.volcengine.ark.runtime.service.ArkService;
import com.zivyou.zivclaw.context.Context;
import com.zivyou.zivclaw.message.Function;
import com.zivyou.zivclaw.message.Message;
import com.zivyou.zivclaw.message.Role;
import com.zivyou.zivclaw.message.ToolCall;
import com.zivyou.zivclaw.tool.ToolDefinition;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class ArkProvider implements Provider, AutoCloseable {
    private final String baseUrl = "https://ark.cn-beijing.volces.com/api/v3";
    private final String apiKey = System.getenv("ARK_AGENT_KEY");
    private final ArkService arkService;

    public ArkProvider() {
        this.arkService = ArkService.builder().baseUrl(baseUrl)
                .apiKey(apiKey)
                .timeout(Duration.ofSeconds(1800))
                .connectTimeout(Duration.ofSeconds(20))
                .retryTimes(2)
                .build();
    }

    @Override
    public Message generate(Context context, List<Message> messages, List<ToolDefinition> toolDefinitions) {
        ChatCompletionRequest request = ChatCompletionRequest.builder().build();
        var response = arkService.createChatCompletion(request).getChoices().get(0).getMessage();
        return convert(response);
    }

    static Message convert(ChatMessage chatMessage) {
        if (chatMessage == null) {
            throw new IllegalArgumentException("chatMessage must not be null");
        }
        if (chatMessage.getFunctionCall() != null) {
            throw new IllegalArgumentException("legacy function_call is not supported");
        }
        Role role = toLocalRole(chatMessage.getRole());
        String content = toLocalContent(chatMessage.getContent());

        List<ToolCall> localToolCalls = null;
        List<ChatToolCall> srcToolCalls = chatMessage.getToolCalls();
        if (srcToolCalls != null) {
            localToolCalls = new ArrayList<>(srcToolCalls.size());
            for (ChatToolCall tc : srcToolCalls) {
                localToolCalls.add(toLocalToolCall(tc));
            }
        }

        return Message.builder()
                .role(role)
                .content(content)
                .name(chatMessage.getName())
                .toolCalls(localToolCalls)
                .toolCallId(chatMessage.getToolCallId())
                .build();
    }

    static ChatMessage convert(Message message) {
        return null;
    }

    private static Role toLocalRole(ChatMessageRole role) {
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }
        return switch (role) {
            case SYSTEM -> Role.SYSTEM;
            case USER -> Role.USER;
            case ASSISTANT -> Role.ASSISTANT;
            case TOOL -> Role.TOOL;
            case FUNCTION -> throw new IllegalArgumentException("legacy FUNCTION role is not supported");
        };
    }

    private static String toLocalContent(Object content) {
        if (content == null) {
            return null;
        }
        if (content instanceof String s) {
            return s;
        }
        throw new IllegalArgumentException(
                "non-string content is not supported (got " + content.getClass().getName() + ")");
    }

    private static ToolCall toLocalToolCall(ChatToolCall arkToolCall) {
        if (arkToolCall == null) {
            throw new IllegalArgumentException("tool call must not be null");
        }
        ChatFunctionCall arkFunction = arkToolCall.getFunction();
        if (arkFunction == null) {
            throw new IllegalArgumentException("tool call function must not be null");
        }
        return ToolCall.builder()
                .id(arkToolCall.getId())
                .type(arkToolCall.getType())
                .function(Function.builder()
                        .name(arkFunction.getName())
                        .arguments(arkFunction.getArguments())
                        .build())
                .build();
    }

    @Override
    public void close() throws Exception {
        if (arkService != null) {
            arkService.shutdownExecutor();
        }
    }
}
