package com.zivyou.zivclaw;

import com.zivyou.zivclaw.context.AgentContext;
import com.zivyou.zivclaw.context.ContextCompactor;
import com.zivyou.zivclaw.message.Message;
import com.zivyou.zivclaw.message.Role;
import com.zivyou.zivclaw.message.ToolCall;
import com.zivyou.zivclaw.prompt.DefaultPromptComposer;
import com.zivyou.zivclaw.provider.DefaultProviderFactory;
import com.zivyou.zivclaw.provider.Provider;
import com.zivyou.zivclaw.registry.DefaultRegistry;
import com.zivyou.zivclaw.registry.Registry;
import com.zivyou.zivclaw.registry.ToolResult;
import com.zivyou.zivclaw.reporter.ConsolePresenter;
import com.zivyou.zivclaw.reporter.EventType;
import com.zivyou.zivclaw.reporter.Reporter;
import com.zivyou.zivclaw.reporter.ToolResultEvent;
import com.zivyou.zivclaw.session.DefaultSessionManager;
import com.zivyou.zivclaw.session.SessionManager;
import com.zivyou.zivclaw.util.JsonUtil;
import com.zivyou.zivclaw.util.NamedThreadFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@RequiredArgsConstructor
public class ReActAgent {
    private final Provider provider = DefaultProviderFactory.getProvider();
    private final Registry registry = new DefaultRegistry();
    private final Reporter reporter = new ConsolePresenter();
    private final SessionManager sessionManager = new DefaultSessionManager();
    private final ContextCompactor contextCompactor = new ContextCompactor();
    private static final int DUPLICATED_TOOL_CALL_LIMIT = 3;

    private final ExecutorService executorService =
            new ThreadPoolExecutor(10, 20,
                    60, TimeUnit.SECONDS,
                    new LinkedBlockingDeque<>(1000),
                    new NamedThreadFactory("tool-exec")
            );
    private final static String LOOP_WARNING_TEMPLATE = """
            [SYSTEM REMINDER 警告] 你似乎陷入了死循环。你刚刚连续 %d 次使用相同的参数调用了 '%s' 工具，并且都失败了。请立即停止这种无效的重试！你的注意力被当前的报错过度吸引了。
            你需要：
            1. 停止猜测参数。跳出当前的局部思维。
            2. 彻底改变你的策略。
            3. 如果你确实无法通过系统工具解决当前问题，请直接结束任务并向用户说明你需要什么人工帮助，而不是继续盲目消耗 API 资源尝试。
            """;

    public void start(AgentContext agentContext, String userPrompt) {
        log.debug("[Agent] agent启动, pwd: {}", agentContext.getWorkDir());
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
        Map<String, Integer> toolCallCounts = new ConcurrentHashMap<>();
        while (true) {
            log.debug("[Agent] thinking...");
            var tools = registry.getAvailableTools();
            List<Message> messages = contextCompactor.compact(agentContext, history);
            var response = provider.generate(agentContext, messages, tools);
            if (response == null) {
                log.error("模型调用失败!");
                return;
            }
            messages.add(response);
            session.appendSession(response);
            reporter.report(EventType.ASSISTANT_TEXT, response.getContent());

            if (CollectionUtils.isEmpty(response.getToolCalls())) {
                log.debug("[Agent] 任务完成,退出循环.");
                break;
            }
            reporter.report(EventType.TOOL_CALL, response.getToolCalls());
            var futures = response.getToolCalls().stream().map(toolCall -> CompletableFuture.runAsync(() -> {
                log.debug(" -> 工具调用: {}, 参数: {}", toolCall.getFunction().getName(), toolCall.getFunction().getArguments());
                String toolName = toolCall.getFunction().getName();
                ToolResult result;
                try {
                    result = registry.execute(agentContext, toolCall);
                } catch (Exception e) {
                    // 抛异常也归类为失败并回填,避免消息序列缺一条 tool 结果
                    log.error(" -> 工具: {} 执行抛异常!", toolName, e);
                    result = ToolResult.error(toolCall.getId(),
                            "工具 " + toolName + " 执行抛异常: "
                                    + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
                reporter.report(EventType.TOOL_RESULT,
                        new ToolResultEvent(toolName, result));
                if (result != null && !result.isError()) {
                    log.debug(" -> 工具: {} 执行成功: {}", toolName, result.getOutput());
                    var message = Message.builder().role(Role.TOOL).name(toolName).toolCallId(result.getToolCallId()).content(result.getOutput()).build();
                    messages.add(message);
                    session.appendSession(message);
                    return;
                }
                // ── 失败:统一回填一条 TOOL 结果,让 LLM 明确看到失败原因,同时保证消息序列 schema 合法。
                String output = result == null
                        ? "工具 " + toolName + " 返回了 null"
                        : result.getOutput();
                int failCount = toolCallCounts.merge(hash(toolCall), 1, Integer::sum);
                if (failCount == DUPLICATED_TOOL_CALL_LIMIT + 1) {
                    // 超过阈值后仅提示一次,避免上下文被循环注入的警告撑爆
                    log.error("-> 工具调用{}陷入失败死循环!", toolName);
                    output = String.format(LOOP_WARNING_TEMPLATE, failCount, toolName);
                }
                log.error(" -> 工具: {} 执行失败: {}", toolName, output);
                var message = Message.builder().role(Role.TOOL).name(toolName).toolCallId(toolCall.getId()).content(output).build();
                messages.add(message);
                session.appendSession(message);
            }, executorService));
            var tasks = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
            tasks.thenAccept(v->{
                log.debug("工具调用完成!");
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

    private String hash(ToolCall toolCall) {
        var text = toolCall.getFunction().getName() + "|" + toolCall.getFunction().getArguments();
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

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
