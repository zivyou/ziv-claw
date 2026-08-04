package com.zivyou.zivclaw.util;

import com.fasterxml.jackson.core.JsonProcessingException;
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

    public static <T> T parse(String jsonStr, Class<T> tClass) {
        try {
            return objectMapper.readValue(jsonStr, tClass);
        } catch (JsonProcessingException e) {
            log.error("JsonProcessingException: ", e);
            return null;
        }
    }
}
