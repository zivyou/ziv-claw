package com.zivyou.zivclaw.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;

@Data
public class EditToolArg {
    @JsonProperty(required = true)
    @JsonPropertyDescription("待修改的文件相对于当前工作目录的路径")
    String path;

    @JsonProperty(required = true)
    @JsonPropertyDescription("文件中需要被替换的旧的内容")
    String oldContent;

    @JsonProperty(required = true)
    @JsonPropertyDescription("文件中需要被替换的新的内容")
    String newContent;
}
