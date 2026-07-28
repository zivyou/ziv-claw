package com.zivyou.zivclaw.registry;

import lombok.Getter;

/**
 * 工具的对外描述,交给 LLM 让它决定是否 / 如何调用该工具。不可变值对象。
 * 由 {@link DefaultRegistry} 在扫描阶段生成,{@code inputSchema} 由 Registry 自动派生自
 * {@link Tool#argsType()},作者无需手写。
 */
@Getter
public final class ToolDefinition {
    private final String name;
    private final String description;
    private final String inputSchema;

    public ToolDefinition(String name, String description, String inputSchema) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
    }
}
