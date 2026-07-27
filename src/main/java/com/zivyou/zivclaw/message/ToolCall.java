package com.zivyou.zivclaw.message;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ToolCall {
    String id;
    String type;
    Function function;
}
