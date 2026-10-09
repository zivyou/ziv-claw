package com.zivyou.zivclaw;

import com.zivyou.zivclaw.context.AgentContext;
import com.zivyou.zivclaw.context.ContextCompactor;
import com.zivyou.zivclaw.message.Message;
import com.zivyou.zivclaw.message.Role;
import com.zivyou.zivclaw.prompt.DefaultPromptComposer;
import com.zivyou.zivclaw.provider.DefaultProviderFactory;
import com.zivyou.zivclaw.provider.Provider;
import com.zivyou.zivclaw.registry.DefaultRegistry;
import com.zivyou.zivclaw.registry.Registry;
import com.zivyou.zivclaw.reporter.ConsoleReporter;
import com.zivyou.zivclaw.reporter.Reporter;
import com.zivyou.zivclaw.session.DefaultSessionManager;
import com.zivyou.zivclaw.session.SessionManager;
import com.zivyou.zivclaw.util.JsonUtil;
import com.zivyou.zivclaw.util.NamedThreadFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@RequiredArgsConstructor
public class ReActAgent {
    private final Provider provider = DefaultProviderFactory.getProvider();
    private final Registry registry = new DefaultRegistry();
    private final Reporter reporter = new ConsoleReporter();
    private final SessionManager sessionManager = new DefaultSessionManager();
    private final ContextCompactor contextCompactor = new ContextCompactor();

    private final ExecutorService executorService =
            new ThreadPoolExecutor(10, 20,
                    60, TimeUnit.SECONDS,
                    new LinkedBlockingDeque<>(1000),
                    new NamedThreadFactory("tool-exec")
            );

    public void start(AgentContext agentContext, String userPrompt) {
        log.info("[Agent] agent启动, pwd: {}", agentContext.getWorkDir());
        List<Message> history = new CopyOnWriteArrayList<>();
        var systemPromptComposer = new DefaultPromptComposer(agentContext.getWorkDir());
        history.add(
                systemPromptComposer.compose()
        );
        var session = sessionManager.getOrCreateSession(agentContext.getSessionId(), agentContext.getWorkDir());
        if (session == null) {
            log.error("[SessionManager]: getOrCreateSession failed: {}", JsonUtil.stringify(agentContext));
            return;
        }
        agentContext.setSessionId(session.getId());
        history.addAll(session.getWorkingMemory());
        history.add(
                Message.builder().role(Role.USER).content(userPrompt).build()
        );

        while (true) {
            log.info("[Agent] thinking...");
            var tools = registry.getAvailableTools();
            List<Message> messages = contextCompactor.compact(agentContext, history);
            var response = provider.generate(agentContext, messages, tools);
            if (response == null) {
                log.error("模型调用失败!");
                return;
            }
            messages.add(response);
            session.appendSession(response);
            reporter.report(response.getContent());

            if (CollectionUtils.isEmpty(response.getToolCalls())) {
                log.info("[Agent] 任务完成,退出循环.");
                break;
            }
            reporter.report("模型调用工具: " + JsonUtil.stringify(response.getToolCalls()));
            var futures = response.getToolCalls().stream().map(toolCall -> CompletableFuture.runAsync(() -> {
                log.info(" -> 工具调用: {}, 参数: {}", toolCall.getFunction().getName(), toolCall.getFunction().getArguments());
                var result = registry.execute(agentContext, toolCall);
                reporter.report(String.format("工具调用结果: %s", JsonUtil.stringify(result)));
                if (result == null) {
                    log.error(" -> 工具: {} 执行失败!", toolCall.getFunction().getName());
                } else {
                    log.info(" -> 工具: {} 执行成功: {}", toolCall.getFunction().getName(), result);
                    var message = Message.builder().role(Role.TOOL).name(toolCall.getFunction().getName()).toolCallId(result.getToolCallId()).content(result.getOutput()).build();
                    messages.add(message);
                    session.appendSession(message);
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
            history = messages;
            sessionManager.save(agentContext.getSessionId());
        }
    }

    private final AtomicBoolean shutdownCalled = new AtomicBoolean(false);

    public void shutdown() {
        if (!shutdownCalled.compareAndSet(false, true)) {
            return;
        }
        try {
            provider.close();
        } catch (Exception e) {
            log.warn("provider close失败", e);
        }
        executorService.shutdown();
    }
}
