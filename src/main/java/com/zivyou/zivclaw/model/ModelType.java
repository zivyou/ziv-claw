package com.zivyou.zivclaw.model;

/**
 * 模型类型。一个 Provider 可同时提供多种类型的模型，注册与查找时据此区分。
 */
public enum ModelType {
    CHAT,
    EMBEDDING,
    RERANK
}
