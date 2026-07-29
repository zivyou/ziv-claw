package com.zivyou.zivclaw.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;

@Data
public class BashToolArg {
    @JsonProperty(required = true)
    @JsonPropertyDescription("要执行的bash命令")
    String cmd;
}
