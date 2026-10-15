package com.zivyou.zivclaw.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JsonUtil {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    public static String stringify(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.error("JsonProcessingException: {}", e.getMessage());
            return "[JsonProcessingException]";
        }
    }

    /** 把任意对象美化折叠成多行 JSON(缩进打印),用于控制台可读展示。 */
    public static String prettyPrint(Object object) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.error("JsonProcessingException: {}", e.getMessage());
            return "[JsonProcessingException]";
        }
    }

    /** 把一段 JSON 字符串重排为多行缩进形式;非法 JSON 时原样返回。 */
    public static String prettyPrintJson(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (JsonProcessingException e) {
            return json;
        }
    }

    public static <T> T parse(String jsonStr, Class<T> tClass) {
        try {
            return objectMapper.readValue(jsonStr, tClass);
        } catch (JsonProcessingException e) {
            log.error("JsonProcessingException: ", e);
            return null;
        }
    }
}
