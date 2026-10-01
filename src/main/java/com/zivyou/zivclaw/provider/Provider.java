package com.zivyou.zivclaw.provider;

import com.zivyou.zivclaw.context.AgentContext;
import com.zivyou.zivclaw.message.Message;
import com.zivyou.zivclaw.registry.ToolDefinition;

import java.util.List;

public interface Provider extends AutoCloseable {
    Message generate(AgentContext agentContext, List<Message> messages, List<ToolDefinition> toolDefinitions);
}
