package com.zivyou.zivclaw.tool;

import lombok.Getter;

/**
 * 工具执行结果,回传给 LLM 作为 role=tool 消息内容。不可变值对象。
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

    /** 成功结果。 */
    public static ToolResult ok(String toolCallId, String output) {
        return new ToolResult(toolCallId, output, false);
    }

    /** 失败结果:message 会作为 output 回传给 LLM,LLM 可据此决定重试或换路。 */
    public static ToolResult error(String toolCallId, String message) {
        return new ToolResult(toolCallId, message, true);
    }
}
