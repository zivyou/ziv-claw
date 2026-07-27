package com.zivyou.zivclaw.provider;

import com.zivyou.zivclaw.context.Context;
import com.zivyou.zivclaw.message.Message;
import com.zivyou.zivclaw.tool.ToolDefinition;

import java.util.List;

public interface Provider {
    Message generate(Context context, List<Message> messages, List<ToolDefinition> toolDefinitions);
}
