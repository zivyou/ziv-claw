package com.zivyou.zivclaw.model;

import java.util.List;
import java.util.Optional;

/**
 * 模型注册表。负责模型实例的注册、查找与生命周期管理。
 * 对 Agent 而言它是获取可用模型的统一入口。
 */
public interface ModelRegistry {

    /** 按配置创建并注册一个模型；若 key 已存在则直接返回已有实例。 */
    Model register(ModelConfig config);

    /** 直接注册一个已构造的模型实例。 */
    Model register(Model model);

    /** 按厂商 + 类型 + 名称精确查找。 */
    Optional<Model> get(String provider, ModelType type, String name);

    /** 按类型列出全部模型。 */
    List<Model> list(ModelType type);

    /**
     * 获取默认对话模型，供未显式选择模型的调用方使用。
     */
    ChatModel getDefaultChatModel();

    /** 释放全部已注册模型占用的资源。 */
    void closeAll();
}
