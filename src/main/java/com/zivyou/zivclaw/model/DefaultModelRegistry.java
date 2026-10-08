package com.zivyou.zivclaw.model;

import com.zivyou.zivclaw.model.spi.ModelProviderFactory;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link ModelRegistry} 默认实现：内存态、线程安全。
 * 通过 SPI 发现的工厂列表按 {@link ModelConfig#getProvider()} 路由创建模型。
 */
@Slf4j
public class DefaultModelRegistry implements ModelRegistry {

    private final Map<String, Model> models = new ConcurrentHashMap<>();
    private final Map<String, ModelProviderFactory> factories = new ConcurrentHashMap<>();

    public DefaultModelRegistry() {
        this(ModelRegistryLoader.loadFactories());
    }

    public DefaultModelRegistry(List<ModelProviderFactory> factoryList) {
        for (ModelProviderFactory factory : factoryList) {
            factories.put(factory.provider().toLowerCase(), factory);
        }
    }

    @Override
    public Model register(ModelConfig config) {
        String key = keyOf(config.getType(), config.getName());
        return models.computeIfAbsent(key, k -> {
            ModelProviderFactory factory = factories.get(config.getProvider().toLowerCase());
            if (factory == null) {
                throw new IllegalArgumentException(
                        "未找到 provider=" + config.getProvider() + " 对应的 ModelProviderFactory");
            }
            Model model = factory.create(config);
            log.info("[ModelRegistry] 注册模型: {} (provider={})", key, config.getProvider());
            return model;
        });
    }

    @Override
    public Model register(Model model) {
        String key = keyOf(model);
        models.put(key, model);
        log.info("[ModelRegistry] 注册模型实例: {}", key);
        return model;
    }

    @Override
    public Optional<Model> get(String provider, ModelType type, String name) {
        // 索引用 type:name；provider 参数保留给调用方表达意图 / 未来做多厂商同名隔离
        return Optional.ofNullable(models.get(keyOf(type, name)));
    }

    @Override
    public List<Model> list(ModelType type) {
        List<Model> result = new ArrayList<>();
        for (Model model : models.values()) {
            if (model.getType() == type) {
                result.add(model);
            }
        }
        return result;
    }

    @Override
    public ChatModel getDefaultChatModel() {
        List<Model> chats = list(ModelType.CHAT);
        if (chats.isEmpty()) {
            throw new IllegalStateException("注册表中没有可用的 ChatModel，请先注册");
        }
        return (ChatModel) chats.get(0);
    }

    @Override
    public void closeAll() {
        for (Model model : models.values()) {
            if (model instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception e) {
                    log.warn("[ModelRegistry] 关闭模型失败: {}", keyOf(model), e);
                }
            }
        }
        models.clear();
    }

    private static String keyOf(Model model) {
        return keyOf(model.getType(), model.getName());
    }

    private static String keyOf(ModelType type, String name) {
        return type + ":" + name;
    }
}
