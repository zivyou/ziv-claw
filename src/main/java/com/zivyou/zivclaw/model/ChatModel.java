package com.zivyou.zivclaw.model;

import com.zivyou.zivclaw.context.AgentContext;
import com.zivyou.zivclaw.message.Message;
import com.zivyou.zivclaw.registry.ToolDefinition;

import java.util.List;

/**
 * 对话模型能力。Agent 只依赖该接口，不感知具体厂商（Ark / OpenAI / ...）。
 */
public interface ChatModel extends Model, AutoCloseable {

    /**
     * 基于上下文与历史消息生成下一条消息（可能携带 toolCalls）。
     */
    Message generate(AgentContext agentContext,
                     List<Message> messages,
                     List<ToolDefinition> toolDefinitions);

    @Override
    default ModelType getType() {
        return ModelType.CHAT;
    }

    @Override
    default void close() throws Exception {
        // 默认无资源需要释放
    }
}
