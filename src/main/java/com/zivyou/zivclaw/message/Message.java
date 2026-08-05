package com.zivyou.zivclaw.message;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.util.List;

@Builder
@Accessors
@Getter
@ToString
@Setter
public class Message {
    Role role;
    String content;
    String name;
    List<ToolCall> toolCalls;
    String toolCallId;
    String refusal;
    /** 由 ContextCompactor 设置：该消息的原文已被外存到磁盘，content/arguments 中带有 BACKUP_MARKER 前缀。 */
    @Setter(AccessLevel.NONE)
    boolean compacted;

    public void markCompacted() {
        this.compacted = true;
    }
}
