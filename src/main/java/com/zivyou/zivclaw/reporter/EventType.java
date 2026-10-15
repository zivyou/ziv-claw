package com.zivyou.zivclaw.reporter;

/**
 * 交互链路中的事件类型。Reporter 据此对不同角色/环节做差异化呈现,
 * 与具体渲染(颜色、排版)解耦。
 */
public enum EventType {
    /** 用户输入,在交互式模式下经由 TUI 发射。 */
    USER_INPUT,
    /** 助手正文回复。 */
    ASSISTANT_TEXT,
    /** 模型发起工具调用,payload 为 {@code List<ToolCall>}。 */
    TOOL_CALL,
    /** 工具执行结果,payload 为 {@link ToolResultEvent}。 */
    TOOL_RESULT,
    /** 系统提示/分隔等辅助信息。 */
    SYSTEM
}