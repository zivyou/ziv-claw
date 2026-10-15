package com.zivyou.zivclaw.reporter;

/**
 * 交互事件的输出通道。agent 与宿主只负责发射类型化事件({@link EventType} +
 * 结构化 payload),如何呈现(颜色、排版、是否写日志)交由实现决定。
 */
public interface Reporter {
    void report(EventType type, Object payload);
}