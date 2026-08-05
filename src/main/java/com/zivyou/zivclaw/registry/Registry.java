package com.zivyou.zivclaw.registry;

import com.zivyou.zivclaw.context.AgentContext;
import com.zivyou.zivclaw.message.ToolCall;

import java.util.List;

public interface Registry {
    List<ToolDefinition> getAvailableTools();
    ToolResult execute(AgentContext agentContext, ToolCall toolCall);
}
