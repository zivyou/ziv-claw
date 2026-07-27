package com.zivyou.zivclaw.tool;

import com.zivyou.zivclaw.context.Context;
import com.zivyou.zivclaw.message.ToolCall;

import java.util.List;

public class DefaultRegistry implements Registry{
    @Override
    public List<ToolDefinition> getAvailableTools() {
        return List.of();
    }

    @Override
    public ToolResult execute(Context context, ToolCall toolCall) {
        return null;
    }
}
