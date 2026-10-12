package com.zivyou.zivclaw.provider;

import com.zivyou.zivclaw.config.ConfigLoader;
import com.zivyou.zivclaw.config.ProviderInstanceConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.function.Function;

/**
 * 按配置创建 Provider。新增一个 OpenAI 兼容厂商只需在 yaml 的
 * app.provider.instances 下加一条 type: openai-compatible 的配置；
 * 协议不兼容的厂商才需要新增 Provider 实现并注册到这里。
 */
@Slf4j
public class DefaultProviderFactory {
    private static final Map<String, Function<ProviderInstanceConfig, Provider>> REGISTRY = Map.of(
            "openai-compatible", OpenAICompatibleProvider::new,
            "ark", ArkProvider::new
    );

    public static Provider getProvider() {
        String active = activeInstance();
        ProviderInstanceConfig config = ConfigLoader.load("app.provider.instances." + active, ProviderInstanceConfig.class);
        if (config == null) {
            throw new IllegalStateException("missing config: app.provider.instances." + active);
        }
        var constructor = REGISTRY.get(config.getType());
        if (constructor == null) {
            throw new IllegalStateException(
                    "unknown provider type: " + config.getType() + ", available: " + REGISTRY.keySet());
        }
        log.info("using provider instance: {} (type: {}, baseUrl: {})", active, config.getType(), config.getBaseUrl());
        return constructor.apply(config);
    }

    /** APP_PROVIDER 环境变量 > app.provider.active 配置 */
    private static String activeInstance() {
        String active = System.getenv("APP_PROVIDER");
        if (active == null || active.isBlank()) {
            active = ConfigLoader.load("app.provider.active", String.class);
        }
        if (active == null || active.isBlank()) {
            throw new IllegalStateException("no active provider: set APP_PROVIDER env or app.provider.active in yaml");
        }
        return active.trim();
    }
}
