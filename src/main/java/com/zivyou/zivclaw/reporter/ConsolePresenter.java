package com.zivyou.zivclaw.reporter;

import com.zivyou.zivclaw.message.Function;
import com.zivyou.zivclaw.message.ToolCall;
import com.zivyou.zivclaw.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.PrintStream;
import java.util.List;

/**
 * 把交互事件渲染到控制台的 Presenter:
 * 按角色着色,工具参数/结果 JSON 美化折叠成多行,正文走 System.out 与日志前缀解耦。
 *
 * <p>工具是并行执行的,同一 Presenter 实例可能被多个线程同时调用
 * (ReActAgent 的工具回调),因此整体渲染在实例锁上串行化,避免多行块交错。</p>
 */
@Slf4j
public class ConsolePresenter implements Reporter {
    private static final String RESET   = "\u001B[0m";
    private static final String BOLD    = "\u001B[1m";
    private static final String DIM     = "\u001B[2m";
    private static final String RED     = "\u001B[31m";
    private static final String GREEN   = "\u001B[32m";
    private static final String YELLOW  = "\u001B[33m";
    private static final String MAGENTA = "\u001B[35m";
    private static final String CYAN    = "\u001B[36m";

    private final PrintStream out = System.out;
    private final boolean color;

    public ConsolePresenter() {
        this.color = detectColor();
    }

    private static boolean detectColor() {
        if (System.getenv("NO_COLOR") != null) {
            return false;
        }
        if ("dumb".equals(System.getenv("TERM"))) {
            return false;
        }
        // 标准输出重定向到管道/文件时 System.console() 为 null,此时不输出 ANSI 转义。
        return System.console() != null;
    }

    @Override
    public synchronized void report(EventType type, Object payload) {
        switch (type) {
            case USER_INPUT    -> renderUser((String) payload);
            case ASSISTANT_TEXT -> renderAssistant((String) payload);
            case TOOL_CALL     -> renderToolCall((List<?>) payload);
            case TOOL_RESULT   -> renderToolResult((ToolResultEvent) payload);
            case SYSTEM        -> renderSystem((String) payload);
        }
    }

    private void renderUser(String content) {
        divider();
        label(YELLOW, "USER  输入");
        body(content, YELLOW);
        end();
    }

    private void renderAssistant(String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        divider();
        label(CYAN, "ASSISTANT");
        body(content, CYAN);
        end();
    }

    @SuppressWarnings("unchecked")
    private void renderToolCall(List<?> calls) {
        for (Object item : calls) {
            if (!(item instanceof ToolCall call)) {
                continue;
            }
            Function fn = call.getFunction();
            divider();
            label(MAGENTA, "TOOL  ▶ 调用 " + fn.getName());
            body(JsonUtil.prettyPrintJson(fn.getArguments()), MAGENTA);
            end();
        }
    }

    private void renderToolResult(ToolResultEvent event) {
        divider();
        String status = event.isError() ? "失败" : "成功";
        label(event.isError() ? RED : GREEN, "TOOL  ◀ " + event.toolName() + " (" + status + ")");
        body(String.valueOf(event.output()), event.isError() ? RED : GREEN);
        end();
    }

    private void renderSystem(String content) {
        out.println(dim(content));
    }

    private void divider() {
        out.println(dim("－".repeat(48)));
    }

    private void label(String fg, String text) {
        out.println(paint(fg, BOLD, text));
    }

    private void body(String text, String fg) {
        if (text == null || text.isBlank()) {
            return;
        }
        out.println(paint(fg, text));
    }

    private void end() {
        out.println();
    }

    private String paint(String fg, String text) {
        return color ? fg + text + RESET : text;
    }

    private String paint(String fg, String style, String text) {
        return color ? fg + style + text + RESET : text;
    }

    private String dim(String text) {
        return color ? DIM + text + RESET : text;
    }
}