package com.zivyou.zivclaw.tool;

import com.zivyou.zivclaw.context.Context;
import com.zivyou.zivclaw.message.ToolCall;

import java.util.List;

public interface Registry {
    List<ToolDefinition> getAvailableTools();
    ToolResult execute(Context context, ToolCall toolCall);
}
