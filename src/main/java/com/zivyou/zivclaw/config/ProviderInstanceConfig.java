package com.zivyou.zivclaw.config;

import lombok.Data;

import java.util.Map;

/**
 * 一个具名 LLM provider 实例的配置，对应 yaml 中 app.provider.instances.&lt;name&gt;。
 * type 决定由哪个 Provider 实现来消费这份配置（见 DefaultProviderFactory 的注册表），
 * 不同 type 只读取自己关心的字段，其余字段忽略。
 */
@Data
public class ProviderInstanceConfig {
    /** provider 类型，如 openai-compatible / ark */
    String type;
    String model;
    String baseUrl;
    String apiKey;
    /** 仅 openai-compatible: responses 或 chatCompletions，缺省走 responses */
    String wireApi;
    /** 仅 openai-compatible: 需要透传的额外请求头，如 OpenRouter 的 HTTP-Referer */
    Map<String, String> headers;
    /** 以下仅 ark 使用 */
    Long timeout;
    Long connectTimeout;
    Integer retryTimes;
}
