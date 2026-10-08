package com.zivyou.zivclaw.model.spi;

import com.zivyou.zivclaw.model.Model;
import com.zivyou.zivclaw.model.ModelConfig;

/**
 * 厂商模型工厂 SPI。每个厂商（Ark、OpenAI、……）提供一个实现，
 * 通过 {@code META-INF/services} 注册，由 {@code ModelRegistryLoader} 发现。
 */
public interface ModelProviderFactory {

    /** 厂商标识，需与 {@link ModelConfig#getProvider()} 一致，如 "ark"。 */
    String provider();

    /** 该工厂是否能处理给定配置（默认按 provider 匹配）。 */
    default boolean supports(ModelConfig config) {
        return provider().equalsIgnoreCase(config.getProvider());
    }

    /** 依据配置创建一个具体模型实例。 */
    Model create(ModelConfig config);
}
