package com.zivyou.zivclaw.model;

/**
 * 所有可插拔模型的统一抽象。具体能力由子接口（如 {@link ChatModel}）声明。
 */
public interface Model {

    /** 模型类型，如 CHAT / EMBEDDING / RERANK。 */
    ModelType getType();

    /** 模型唯一名，对应底层 Provider 侧的 model id。 */
    String getName();

    /** 人类可读的模型描述。 */
    String getDesc();
}
