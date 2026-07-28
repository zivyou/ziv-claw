package com.zivyou.zivclaw.registry;

import lombok.Getter;

/**
 * 工具执行结果,回传给 LLM 作为 role=tool 消息内容。不可变值对象。
 *
 * <p>{@code toolCallId} 属于协议层字段(把 tool 消息和发起它的 tool_call 配对),
 * 工具自身不应关心 —— 由 {@link DefaultRegistry} 在调用完成后统一 {@link #withToolCallId}
 * 注入。工具作者只使用 {@link #ok(String)} / {@link #error(String)} 两个不带 id 的工厂。</p>
 */
@Getter
public final class ToolResult {
    private final String toolCallId;
    private final String output;
    private final boolean isError;

    private ToolResult(String toolCallId, String output, boolean isError) {
        this.toolCallId = toolCallId;
        this.output = output;
        this.isError = isError;
    }

    /** 成功结果(工具作者使用):toolCallId 由 registry 统一注入。 */
    public static ToolResult ok(String output) {
        return new ToolResult(null, output, false);
    }

    /** 失败结果(工具作者使用):message 会作为 output 回传给 LLM,LLM 可据此决定重试或换路。 */
    public static ToolResult error(String message) {
        return new ToolResult(null, message, true);
    }

    /** 协议层使用:registry 在工具异常/返回 null 时构造带 id 的结果。 */
    public static ToolResult ok(String toolCallId, String output) {
        return new ToolResult(toolCallId, output, false);
    }

    /** 协议层使用:registry 在工具异常/返回 null 时构造带 id 的结果。 */
    public static ToolResult error(String toolCallId, String message) {
        return new ToolResult(toolCallId, message, true);
    }

    /** 返回一个 toolCallId 被替换后的新实例。用于 registry 单点注入协议 id。 */
    public ToolResult withToolCallId(String toolCallId) {
        if (java.util.Objects.equals(this.toolCallId, toolCallId)) {
            return this;
        }
        return new ToolResult(toolCallId, this.output, this.isError);
    }
}
