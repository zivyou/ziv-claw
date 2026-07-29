package com.zivyou.zivclaw.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;

@Data
public class WriteToolArg {
    @JsonProperty(required = true)
    @JsonPropertyDescription("相对于当前工作目录的路径")
    String path;

    @JsonProperty(required = false)
    @JsonPropertyDescription("要写入文件的内容")
    String content;
}
