package com.zivyou.zivclaw.config;

import lombok.Data;

@Data
public class ArkProviderConfig {
    String baseUrl;
    String apiKey;
    Long timeout;
    Long connectTimeout;
    Integer retryTimes;
}
