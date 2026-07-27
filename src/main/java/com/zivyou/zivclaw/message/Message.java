package com.zivyou.zivclaw.message;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.util.List;

@Builder
@Accessors
@Getter
@ToString
public class Message {
    Role role;
    String content;
    String name;
    List<ToolCall> toolCalls;
    String toolCallId;
    String refusal;
}
