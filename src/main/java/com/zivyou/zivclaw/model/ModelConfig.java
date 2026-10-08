package com.zivyou.zivclaw.model;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 模型注册/创建所需的配置。由装配层（配置文件、环境变量或代码）构造，
 * 交给对应厂商的 {@code ModelProviderFactory} 生成具体 {@link Model}。
 */
@Data
@Builder
public class ModelConfig {

    /** 厂商标识，如 "ark"，用于路由到对应的 ProviderFactory。 */
    private String provider;

    /** 模型类型。 */
    private ModelType type;

    /** 模型名 / 底层 model id。 */
    private String name;

    /** 人类可读描述。 */
    private String desc;

    /** 服务地址，可选；缺省由厂商实现自行决定。 */
    private String baseUrl;

    /** 访问密钥，可选；缺省由厂商实现读取环境变量等。 */
    private String apiKey;

    /** 其它厂商相关的扩展配置（超时、模型参数等）。 */
    @Builder.Default
    private Map<String, Object> config = Map.of();

    /** 注册表里的唯一键：同厂商同类型同名即视为同一个模型。 */
    public String key() {
        return provider + ":" + type + ":" + name;
    }
}
