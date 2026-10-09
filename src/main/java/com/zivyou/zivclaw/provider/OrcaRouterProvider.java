package com.zivyou.zivclaw.provider;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonValue;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionFunctionTool;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam;
import com.openai.models.chat.completions.ChatCompletionTool;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.FunctionTool;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.zivyou.zivclaw.config.ConfigLoader;
import com.zivyou.zivclaw.config.OrcaRouterProviderConfig;
import com.zivyou.zivclaw.context.AgentContext;
import com.zivyou.zivclaw.message.Function;
import com.zivyou.zivclaw.message.Message;
import com.zivyou.zivclaw.message.Role;
import com.zivyou.zivclaw.message.ToolCall;
import com.zivyou.zivclaw.registry.ToolDefinition;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class OrcaRouterProvider implements Provider {
    private final OrcaRouterProviderConfig config = ConfigLoader.load("app.provider.orcaRouter", OrcaRouterProviderConfig.class);
    private final OpenAIClient client;
    private final static ObjectMapper objectMapper =
            new ObjectMapper().setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);

    public OrcaRouterProvider() {
        if (config == null) {
            throw new IllegalStateException("missing config: app.provider.orcaRouter");
        }
        this.client = OpenAIOkHttpClient.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(config.getApiKey())
                .build();
    }

    @Override
    public Message generate(AgentContext agentContext, List<Message> messages, List<ToolDefinition> toolDefinitions) {
        String wireApi = config.getWireApi();
        if (wireApi != null && (wireApi.equalsIgnoreCase("chat") || wireApi.equalsIgnoreCase("chatCompletions"))) {
            return generateByChatCompletions(messages, toolDefinitions);
        }
        return generateByResponses(messages, toolDefinitions);
    }

    // ---------- Responses API ----------

    private Message generateByResponses(List<Message> messages, List<ToolDefinition> toolDefinitions) {
        var request = ResponseCreateParams.builder().model(config.getModel());
        List<ResponseInputItem> input = new ArrayList<>();
        for (Message message : messages) {
            input.addAll(toResponseInputItems(message));
        }
        request.inputOfResponse(input);
        if (toolDefinitions != null) {
            for (ToolDefinition toolDefinition : toolDefinitions) {
                request.addTool(toFunctionTool(toolDefinition));
            }
        }

        try {
            var response = client.responses().create(request.build());
            return convert(response.output());
        } catch (Exception e) {
            try {
                log.error("orcaRouter responses invoke fail! request: {}", objectMapper.writeValueAsString(request.build()), e);
            } catch (JsonProcessingException ex) {
                throw new RuntimeException(ex);
            }
            throw new RuntimeException("orcaRouter responses invoke failed!");
        }
    }

    private static List<ResponseInputItem> toResponseInputItems(Message message) {
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        List<ResponseInputItem> items = new ArrayList<>();
        switch (message.getRole()) {
            case SYSTEM -> items.add(easyInputMessage(EasyInputMessage.Role.SYSTEM, message.getContent()));
            case USER -> items.add(easyInputMessage(EasyInputMessage.Role.USER, message.getContent()));
            case ASSISTANT -> {
                if (message.getContent() != null && !message.getContent().isEmpty()) {
                    items.add(easyInputMessage(EasyInputMessage.Role.ASSISTANT, message.getContent()));
                }
                if (message.getToolCalls() != null) {
                    for (ToolCall toolCall : message.getToolCalls()) {
                        items.add(ResponseInputItem.ofFunctionCall(toResponseFunctionToolCall(toolCall)));
                    }
                }
            }
            case TOOL -> items.add(ResponseInputItem.ofFunctionCallOutput(
                    ResponseInputItem.FunctionCallOutput.builder()
                            .callId(message.getToolCallId())
                            .output(message.getContent() == null ? "" : message.getContent())
                            .build()));
        }
        return items;
    }

    private static ResponseInputItem easyInputMessage(EasyInputMessage.Role role, String content) {
        return ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder()
                .role(role)
                .content(content == null ? "" : content)
                .build());
    }

    private static ResponseFunctionToolCall toResponseFunctionToolCall(ToolCall toolCall) {
        if (toolCall == null || toolCall.getFunction() == null) {
            throw new IllegalArgumentException("tool call and its function must not be null");
        }
        return ResponseFunctionToolCall.builder()
                .callId(toolCall.getId())
                .name(toolCall.getFunction().getName())
                .arguments(toolCall.getFunction().getArguments())
                .build();
    }

    private static FunctionTool toFunctionTool(ToolDefinition toolDefinition) {
        return FunctionTool.builder()
                .name(toolDefinition.getName())
                .description(toolDefinition.getDescription())
                .parameters(FunctionTool.Parameters.builder()
                        .additionalProperties(parseSchema(toolDefinition.getInputSchema()))
                        .build())
                .strict(false)
                .build();
    }

    private static Message convert(List<ResponseOutputItem> output) {
        StringBuilder content = new StringBuilder();
        String refusal = null;
        List<ToolCall> toolCalls = null;

        for (ResponseOutputItem item : output) {
            if (item.isMessage()) {
                ResponseOutputMessage outputMessage = item.asMessage();
                for (ResponseOutputMessage.Content part : outputMessage.content()) {
                    if (part.outputText().isPresent()) {
                        content.append(part.outputText().get().text());
                    } else if (part.refusal().isPresent()) {
                        refusal = part.refusal().get().refusal();
                    }
                }
            } else if (item.isFunctionCall()) {
                ResponseFunctionToolCall functionCall = item.asFunctionCall();
                if (toolCalls == null) {
                    toolCalls = new ArrayList<>();
                }
                toolCalls.add(ToolCall.builder()
                        .id(functionCall.callId())
                        .type("function")
                        .function(Function.builder()
                                .name(functionCall.name())
                                .arguments(functionCall.arguments())
                                .build())
                        .build());
            }
        }

        return Message.builder()
                .role(Role.ASSISTANT)
                .content(content.length() == 0 ? null : content.toString())
                .refusal(refusal)
                .toolCalls(toolCalls)
                .build();
    }

    // ---------- Chat Completions API ----------

    private Message generateByChatCompletions(List<Message> messages, List<ToolDefinition> toolDefinitions) {
        var request = ChatCompletionCreateParams.builder().model(config.getModel());
        for (Message message : messages) {
            request.addMessage(toChatMessageParam(message));
        }
        if (toolDefinitions != null) {
            for (ToolDefinition toolDefinition : toolDefinitions) {
                request.addTool(toChatCompletionTool(toolDefinition));
            }
        }

        try {
            var response = client.chat().completions().create(request.build());
            return convert(response.choices().get(0).message());
        } catch (Exception e) {
            try {
                log.error("orcaRouter chatCompletions invoke fail! request: {}", objectMapper.writeValueAsString(request.build()), e);
            } catch (JsonProcessingException ex) {
                throw new RuntimeException(ex);
            }
            throw new RuntimeException("orcaRouter chatCompletions invoke failed!");
        }
    }

    private static ChatCompletionMessageParam toChatMessageParam(Message message) {
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        return switch (message.getRole()) {
            case SYSTEM -> {
                var builder = ChatCompletionSystemMessageParam.builder()
                        .content(message.getContent() == null ? "" : message.getContent());
                if (message.getName() != null) {
                    builder.name(message.getName());
                }
                yield ChatCompletionMessageParam.ofSystem(builder.build());
            }
            case USER -> {
                var builder = ChatCompletionUserMessageParam.builder()
                        .content(message.getContent() == null ? "" : message.getContent());
                if (message.getName() != null) {
                    builder.name(message.getName());
                }
                yield ChatCompletionMessageParam.ofUser(builder.build());
            }
            case ASSISTANT -> {
                var builder = ChatCompletionAssistantMessageParam.builder();
                if (message.getContent() != null) {
                    builder.content(message.getContent());
                }
                if (message.getToolCalls() != null) {
                    for (ToolCall toolCall : message.getToolCalls()) {
                        builder.addToolCall(toChatMessageToolCall(toolCall));
                    }
                }
                if (message.getName() != null) {
                    builder.name(message.getName());
                }
                yield ChatCompletionMessageParam.ofAssistant(builder.build());
            }
            // tool message 不带 name 字段，强行序列化过去会被网关以 400 拒掉。
            case TOOL -> ChatCompletionMessageParam.ofTool(ChatCompletionToolMessageParam.builder()
                    .toolCallId(message.getToolCallId())
                    .content(message.getContent() == null ? "" : message.getContent())
                    .build());
        };
    }

    private static ChatCompletionMessageToolCall toChatMessageToolCall(ToolCall toolCall) {
        if (toolCall == null || toolCall.getFunction() == null) {
            throw new IllegalArgumentException("tool call and its function must not be null");
        }
        return ChatCompletionMessageToolCall.ofFunction(ChatCompletionMessageFunctionToolCall.builder()
                .id(toolCall.getId())
                .function(ChatCompletionMessageFunctionToolCall.Function.builder()
                        .name(toolCall.getFunction().getName())
                        .arguments(toolCall.getFunction().getArguments())
                        .build())
                .build());
    }

    private static ChatCompletionTool toChatCompletionTool(ToolDefinition toolDefinition) {
        return ChatCompletionTool.ofFunction(ChatCompletionFunctionTool.builder()
                .function(FunctionDefinition.builder()
                        .name(toolDefinition.getName())
                        .description(toolDefinition.getDescription())
                        .parameters(FunctionParameters.builder()
                                .additionalProperties(parseSchema(toolDefinition.getInputSchema()))
                                .build())
                        .build())
                .build());
    }

    private static Message convert(ChatCompletionMessage chatMessage) {
        List<ToolCall> toolCalls = null;
        if (chatMessage.toolCalls().isPresent()) {
            toolCalls = new ArrayList<>();
            for (ChatCompletionMessageToolCall toolCall : chatMessage.toolCalls().get()) {
                if (toolCall.isFunction()) {
                    ChatCompletionMessageFunctionToolCall functionToolCall = toolCall.asFunction();
                    toolCalls.add(ToolCall.builder()
                            .id(functionToolCall.id())
                            .type("function")
                            .function(Function.builder()
                                    .name(functionToolCall.function().name())
                                    .arguments(functionToolCall.function().arguments())
                                    .build())
                            .build());
                }
            }
        }
        return Message.builder()
                .role(Role.ASSISTANT)
                .content(chatMessage.content().orElse(null))
                .refusal(chatMessage.refusal().orElse(null))
                .toolCalls(toolCalls)
                .build();
    }

    // ---------- common ----------

    private static Map<String, JsonValue> parseSchema(String inputSchema) {
        try {
            Map<String, Object> raw = objectMapper.readValue(inputSchema, new TypeReference<>() {
            });
            Map<String, JsonValue> result = new LinkedHashMap<>(raw.size());
            raw.forEach((key, value) -> result.put(key, JsonValue.from(value)));
            return result;
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        client.close();
    }
}
