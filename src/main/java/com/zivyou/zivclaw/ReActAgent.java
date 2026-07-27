package com.zivyou.zivclaw;

import com.zivyou.zivclaw.context.Context;
import com.zivyou.zivclaw.message.Message;
import com.zivyou.zivclaw.message.Role;
import com.zivyou.zivclaw.provider.Provider;
import com.zivyou.zivclaw.tool.Registry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class ReActAgent {
    private final Provider provider;
    private final Registry registry;
    private final String workDir;
    private final Boolean enableThinking;

    public void start(Context context, String userPrompt) {
        log.info("[Agent] agent启动, pwd: {}", workDir);
        List<Message> messages = new ArrayList<>();
        messages.add(
                Message.builder()
                        .role(Role.SYSTEM)
                        .content("你是ziv-claw,一个专业的coding助手,你可以自主调用提供给你的tools")
                        .build()
        );
        messages.add(
                Message.builder().role(Role.USER).content(userPrompt).build()
        );
        int turn = 0;
        while (true) {

            log.debug("无工具挂载,强制思考...");
            if (enableThinking) {
                log.info("[Agent] thinking...");
                var response = provider.generate(context, messages, List.of());
                if (response == null) {
                    log.error("模型调用失败!");
                    return;
                }
                messages.add(response);
                log.debug("模型思考过程: {}", response);
            }

            var tools = registry.getAvailableTools();
            log.debug("挂载工具,进行推理...");
            var response = provider.generate(context, messages, tools);
            if (CollectionUtils.isEmpty(response.getToolCalls())) {
                log.info("[Agent] 任务完成,退出循环.");
                break;
            }
            log.debug("模型调用工具: {}", response.getToolCalls());
            response.getToolCalls().forEach(toolCall -> {
                log.info(" -> 工具调用: {}, 参数: {}", toolCall.getFunction().getName(), toolCall.getFunction().getArguments());
                var result = registry.execute(context, toolCall);
                if (result == null) {
                    log.error(" -> 工具: {} 执行失败!", toolCall.getFunction().getName());
                } else {
                    log.info(" -> 工具: {} 执行成功: {}", toolCall.getFunction().getName(), result);
                    var message = Message.builder().role(Role.TOOL).toolCallId(result.getToolCallId()).content(result.getOutput()).build();
                    messages.add(message);
                }
            });
            turn++;
        }
    }
}
