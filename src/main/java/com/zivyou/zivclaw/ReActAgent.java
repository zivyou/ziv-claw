package com.zivyou.zivclaw;

import com.zivyou.zivclaw.context.Context;
import com.zivyou.zivclaw.message.Message;
import com.zivyou.zivclaw.message.Role;
import com.zivyou.zivclaw.provider.Provider;
import com.zivyou.zivclaw.registry.Registry;
import com.zivyou.zivclaw.reporter.Reporter;
import com.zivyou.zivclaw.util.JsonUtil;
import com.zivyou.zivclaw.util.NamedThreadFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.concurrent.*;

@Slf4j
@RequiredArgsConstructor
public class ReActAgent {
    private final Provider provider;
    private final Registry registry;
    private final Reporter reporter;
    private final ExecutorService executorService =
            new ThreadPoolExecutor(10, 20,
                    60, TimeUnit.SECONDS,
                    new LinkedBlockingDeque<>(1000),
                    new NamedThreadFactory("tool-exec")
            );

    public void start(Context context, String userPrompt) {
        log.info("[Agent] agent启动, pwd: {}", context.getWorkDir());
        List<Message> messages = new CopyOnWriteArrayList<>();
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
            log.info("[Agent] thinking...");
            var tools = registry.getAvailableTools();
            var response = provider.generate(context, messages, tools);
            if (response == null) {
                log.error("模型调用失败!");
                return;
            }
            messages.add(response);
            reporter.report(response.getContent());

            if (CollectionUtils.isEmpty(response.getToolCalls())) {
                log.info("[Agent] 任务完成,退出循环.");
                break;
            }
            reporter.report("模型调用工具: " + JsonUtil.stringify(response.getToolCalls()));
            var futures = response.getToolCalls().stream().map(toolCall -> CompletableFuture.runAsync(() -> {
                log.info(" -> 工具调用: {}, 参数: {}", toolCall.getFunction().getName(), toolCall.getFunction().getArguments());
                var result = registry.execute(context, toolCall);
                reporter.report(String.format("工具调用结果: %s", JsonUtil.stringify(result)));
                if (result == null) {
                    log.error(" -> 工具: {} 执行失败!", toolCall.getFunction().getName());
                } else {
                    log.info(" -> 工具: {} 执行成功: {}", toolCall.getFunction().getName(), result);
                    var message = Message.builder().role(Role.TOOL).name(toolCall.getFunction().getName()).toolCallId(result.getToolCallId()).content(result.getOutput()).build();
                    messages.add(message);
                }
            }, executorService));
            var tasks = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
            tasks.thenAccept(v->{
                log.info("工具调用完成!");
            }).exceptionally(e -> {
                log.error("工具调用失败！ {}", e.getMessage());
                return null;
            });
            tasks.join();
            turn++;
        }
    }
}
