package com.zivyou.zivclaw.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConfigLoaderTest {

    private static final String PROP = "zivclaw.test.placeholder";

    @AfterEach
    void tearDown() {
        System.clearProperty(PROP);
    }

    @Test
    void resolvesFromSystemProperty() {
        System.setProperty(PROP, "secret");
        assertEquals("secret", ConfigLoader.substitute("${" + PROP + "}"));
    }

    @Test
    void systemPropertyTakesPrecedenceOverDefault() {
        System.setProperty(PROP, "secret");
        assertEquals("secret", ConfigLoader.substitute("${" + PROP + ":fallback}"));
    }

    @Test
    void resolvesFromEnvironment() {
        // PATH 在所有平台都存在
        assertEquals(System.getenv("PATH"), ConfigLoader.substitute("${PATH}"));
    }

    @Test
    void fallsBackToDefault() {
        assertEquals("fallback", ConfigLoader.substitute("${zivclaw.test.missing:fallback}"));
    }

    @Test
    void keepsLiteralWhenUnresolvable() {
        assertEquals("${zivclaw.test.missing}", ConfigLoader.substitute("${zivclaw.test.missing}"));
    }

    @Test
    void substitutesMultiplePlaceholdersInOneString() {
        System.setProperty(PROP, "abc");
        assertEquals("key=abc,path=" + System.getenv("PATH"),
                ConfigLoader.substitute("key=${" + PROP + "},path=${PATH}"));
    }

    @Test
    void leavesPlainStringUntouched() {
        assertEquals("no placeholder here", ConfigLoader.substitute("no placeholder here"));
        assertNull(ConfigLoader.substitute(null));
    }

    @Test
    void resolvesNestedMapAndList() {
        System.setProperty(PROP, "secret");
        Map<String, Object> root = new HashMap<>();
        Map<String, Object> provider = new HashMap<>();
        provider.put("apiKey", "${" + PROP + "}");
        provider.put("baseUrl", "https://example.com");
        provider.put("timeout", 30);
        List<Object> endpoints = new ArrayList<>();
        endpoints.add("${" + PROP + ":fallback}");
        Map<String, Object> nested = new HashMap<>();
        nested.put("nested", "${" + PROP + "}");
        endpoints.add(nested);
        root.put("provider", provider);
        root.put("endpoints", endpoints);

        ConfigLoader.resolvePlaceHolder(root);

        assertEquals("secret", provider.get("apiKey"));
        assertEquals("https://example.com", provider.get("baseUrl"));
        assertEquals(30, provider.get("timeout"));
        assertEquals("secret", endpoints.get(0));
        assertEquals("secret", nested.get("nested"));
    }
}
