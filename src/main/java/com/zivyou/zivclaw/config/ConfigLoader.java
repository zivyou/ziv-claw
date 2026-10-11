package com.zivyou.zivclaw.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public final class ConfigLoader {
    private final static ObjectMapper objectMapper = new ObjectMapper()
            .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static <T> T load(String key, Class<T> clazz) {
        Map<String, Object> merged = new HashMap<>();
        read("application.yaml").ifPresent(m -> deepMerge(merged, m));
        read("application.yml").ifPresent(m -> deepMerge(merged, m));

        for (String profile : profiles().split(",")) {
            if (!profile.isBlank()) {
                read("application-" + profile.trim() + ".yaml").ifPresent(m -> deepMerge(merged, m));
            }
        }
        resolvePlaceHolder(merged);

        Object node = resolve(merged, key);
        if (node == null) {
            return null;
        }

        return objectMapper.convertValue(node, clazz);
    }

    /**
     * 按 "." 分隔的路径逐层下钻，例如 "app.provider.ark"。
     * 任一层缺失或类型不是 Map 时返回 null。
     */
    private static Object resolve(Map<String, Object> root, String key) {
        Object node = root;
        for (String segment : key.split("\\.")) {
            if (!(node instanceof Map<?, ?> map)) {
                return null;
            }
            node = map.get(segment);
            if (node == null) {
                return null;
            }
        }
        return node;
    }

    private static String profiles() {
        String env = System.getenv("APP_PROFILE");
        String prop = System.getProperty("app.profile");
        if (env == null) {
            env = "";
        }
        if (prop == null) {
            prop = "";
        }
        return env + "," + prop;
    }

    /**
     * 解析配置中的 ${VAR} / ${VAR:default} 占位符，避免敏感信息入库。
     * 查找顺序: system property > 环境变量 > 默认值; 都找不到时保留原样并打 warning。
     */
    static void resolvePlaceHolder(Map<String, Object> root) {
        resolveEntry(root);
    }

    private static final Pattern PH = Pattern.compile("\\$\\{([^}:]+)(?::([^}]*))?}");

    static String substitute(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        Matcher matcher = PH.matcher(value);
        if (!matcher.find()) {
            return value;
        }
        StringBuilder sb = new StringBuilder();
        matcher.reset();
        while (matcher.find()) {
            String name = matcher.group(1).trim();
            String replacement = System.getProperty(name);
            if (replacement == null) {
                replacement = System.getenv(name);
            }
            if (replacement == null) {
                replacement = matcher.group(2);
            }
            if (replacement == null) {
                log.warn("unresolved placeholder: {}", name);
                replacement = matcher.group(0);
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void resolveEntry(Object node) {
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof String s) {
                    ((Map.Entry<String, Object>) entry).setValue(substitute(s));
                } else {
                    resolveEntry(value);
                }
            }
        } else if (node instanceof List<?>) {
            List<Object> list = (List<Object>) node;
            for (int i = 0; i < list.size(); i++) {
                Object value = list.get(i);
                if (value instanceof String s) {
                    list.set(i, substitute(s));
                } else {
                    resolveEntry(value);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void deepMerge(Map<String, Object> target, Map<String, Object> source) {
        source.forEach((key, value) -> {
            Object existing = target.get(key);
            if (value instanceof Map<?, ?> valueMap && existing instanceof Map<?, ?> existingMap) {
                deepMerge((Map<String, Object>) existingMap, (Map<String, Object>) valueMap);
            } else {
                target.put(key, value);
            }
        });
    }

    private static Optional<Map<String, Object>> read(String path) {
        try (InputStream is = ConfigLoader.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                return Optional.empty();
            }
            Map<String, Object> map = new Yaml().load(is);
            return Optional.ofNullable(map);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new UncheckedIOException(e);
        }
    }
}
