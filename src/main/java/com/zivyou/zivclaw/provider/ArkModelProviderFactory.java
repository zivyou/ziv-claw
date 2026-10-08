package com.zivyou.zivclaw.provider;

import com.zivyou.zivclaw.model.Model;
import com.zivyou.zivclaw.model.ModelConfig;
import com.zivyou.zivclaw.model.ModelType;
import com.zivyou.zivclaw.model.spi.ModelProviderFactory;

/**
 * Ark 厂商工厂。被 {@code META-INF/services} 中的 SPI 配置声明，
 * 由 {@code ModelRegistryLoader} 自动发现。
 */
public class ArkModelProviderFactory implements ModelProviderFactory {

    @Override
    public String provider() {
        return "ark";
    }

    @Override
    public Model create(ModelConfig config) {
        if (config.getType() != null && config.getType() != ModelType.CHAT) {
            throw new IllegalArgumentException(
                    "ArkModelProviderFactory 目前仅支持 CHAT 类型，收到: " + config.getType());
        }
        return new ArkProvider(config);
    }
}
