package com.zivyou.zivclaw.model;

import com.zivyou.zivclaw.model.spi.ModelProviderFactory;
import com.zivyou.zivclaw.provider.ArkModelProviderFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultModelRegistryTest {

    @Test
    void spi_discoversArkFactory() {
        List<ModelProviderFactory> factories = ModelRegistryLoader.loadFactories();
        assertEquals(1, factories.size());
        assertInstanceOf(ArkModelProviderFactory.class, factories.get(0));
        assertEquals("ark", factories.get(0).provider());
    }

    @Test
    void register_createsModelViaFactoryAndIsFindable() {
        ModelRegistry registry = new DefaultModelRegistry();
        ModelConfig config = ModelConfig.builder()
                .provider("ark")
                .type(ModelType.CHAT)
                .name("ark-code-latest")
                .desc("test model")
                .build();

        Model model = registry.register(config);

        assertInstanceOf(ChatModel.class, model);
        assertEquals(ModelType.CHAT, model.getType());
        assertEquals("ark-code-latest", model.getName());
        assertEquals("test model", model.getDesc());

        assertTrue(registry.get("ark", ModelType.CHAT, "ark-code-latest").isPresent());
        assertEquals(1, registry.list(ModelType.CHAT).size());
        assertInstanceOf(ChatModel.class, registry.getDefaultChatModel());

        registry.closeAll();
    }

    @Test
    void register_sameConfigIsIdempotent() {
        ModelRegistry registry = new DefaultModelRegistry();
        ModelConfig config = ModelConfig.builder()
                .provider("ark").type(ModelType.CHAT).name("ark-code-latest").build();

        Model first = registry.register(config);
        Model second = registry.register(config);

        assertTrue(first == second, "相同 key 应返回同一实例");
        registry.closeAll();
    }

    @Test
    void register_unknownProviderThrows() {
        ModelRegistry registry = new DefaultModelRegistry();
        ModelConfig config = ModelConfig.builder()
                .provider("nope").type(ModelType.CHAT).name("x").build();

        assertThrows(IllegalArgumentException.class, () -> registry.register(config));
        registry.closeAll();
    }
}
