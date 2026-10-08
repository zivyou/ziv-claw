package com.zivyou.zivclaw.model;

import com.zivyou.zivclaw.model.spi.ModelProviderFactory;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * 基于 JDK {@link ServiceLoader} 的工厂发现器。
 * 负责扫描 classpath 下所有 {@link ModelProviderFactory} 实现并交给注册表使用。
 */
@Slf4j
public final class ModelRegistryLoader {

    private ModelRegistryLoader() {
    }

    /** 加载 classpath 中所有厂商工厂。 */
    public static List<ModelProviderFactory> loadFactories() {
        List<ModelProviderFactory> factories = new ArrayList<>();
        for (ModelProviderFactory factory : ServiceLoader.load(ModelProviderFactory.class)) {
            factories.add(factory);
            log.info("[ModelRegistry] 发现模型工厂: {}", factory.provider());
        }
        if (factories.isEmpty()) {
            log.warn("[ModelRegistry] 未发现任何 ModelProviderFactory，请检查 META-INF/services 配置");
        }
        return factories;
    }
}
