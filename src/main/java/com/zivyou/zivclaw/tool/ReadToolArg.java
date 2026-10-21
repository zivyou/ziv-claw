package com.zivyou.zivclaw.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;

/**
 * Read 工具的入参 POJO。字段即 JSON Schema —— 由 {@code DefaultRegistry} 通过
 * victools/jsonschema-generator + JacksonModule 自动派生给 LLM。
 *
 * <p>字段描述用 {@link JsonPropertyDescription},必填标记用
 * {@link JsonProperty#required()};DO NOT 把 tool_call_id、required 名单、
 * 字段描述字典这些"元信息"当作字段写进来 —— 它们会被当成额外入参输出到
 * schema 里污染 LLM 的可选参数集合。tool_call_id 是协议层字段,由 registry
 * 单点注入到 {@code ToolResult},工具入参永远不该看到它。</p>
 */
@Data
public class ReadToolArg {
    @JsonProperty(required = true)
    @JsonPropertyDescription("相对于当前工作目录的相对路径")
    String path;

    @JsonPropertyDescription("从文件第几行开始读取,可省略;省略时从文件开头读取,默认从第 1 行(offset=0)开始")
    Integer offset;

    @JsonPropertyDescription("只读取连续多少行,可省略;省略时读到文件末尾")
    Integer limit;
}
