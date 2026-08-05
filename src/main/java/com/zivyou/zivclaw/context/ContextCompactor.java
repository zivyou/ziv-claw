package com.zivyou.zivclaw.context;

import com.zivyou.zivclaw.message.Function;
import com.zivyou.zivclaw.message.Message;
import com.zivyou.zivclaw.message.Role;
import com.zivyou.zivclaw.message.ToolCall;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 渐进式上下文压缩器。
 *
 * 压缩策略（两档，逐级加码）：
 *   1. 按长度从大到小逐条压缩非 system 消息；如果只压最长的一条就能降到 target 水位，
 *      循环会立刻退出，不会过度压缩。ASSISTANT 的 tool_calls 参数也会被压缩。
 *   2. 仍超长则按"轮"（assistant + 它触发的 tool 结果）丢弃早期历史，只保留最近 N 轮 + system。
 *
 * 被压缩的原文写入 workDir 下的 .ziv-claw/compacted/&lt;sessionId&gt;/，并在消息体里留下
 * 可被 Read 工具读取的相对路径，配合 system prompt 让模型可自助取回原文。
 */
@Slf4j
public class ContextCompactor {

    /** 截断标记，%s 为相对于 workDir 的路径，模型可通过 Read 工具读取。 */
    public static final String BACKUP_MARKER = "...[原文已外存至: %s，请用 Read 工具读取该路径以获取完整内容]...";
    public static final String BACKUP_DIR_NAME = ".ziv-claw/compacted";

    private static final int DEFAULT_CONTEXT_WINDOW_CHARS = 1_000_000;
    private static final int DEFAULT_TRIGGER_PERCENT = 70;
    private static final int DEFAULT_TARGET_PERCENT = 40;
    private static final int DEFAULT_KEEP_LATEST_ROUNDS = 6;
    private static final int DEFAULT_PER_MESSAGE_THRESHOLD = 1000;

    private final int contextWindowChars;
    private final int triggerPercent;
    private final int targetPercent;
    private final int keepLatestRounds;
    private final int perMessageThreshold;

    public ContextCompactor() {
        this(DEFAULT_CONTEXT_WINDOW_CHARS, DEFAULT_TRIGGER_PERCENT, DEFAULT_TARGET_PERCENT,
                DEFAULT_KEEP_LATEST_ROUNDS, DEFAULT_PER_MESSAGE_THRESHOLD);
    }

    public ContextCompactor(int contextWindowChars, int triggerPercent, int targetPercent,
                            int keepLatestRounds, int perMessageThreshold) {
        if (targetPercent >= triggerPercent) {
            throw new IllegalArgumentException("targetPercent must be < triggerPercent to provide hysteresis");
        }
        this.contextWindowChars = contextWindowChars;
        this.triggerPercent = triggerPercent;
        this.targetPercent = targetPercent;
        this.keepLatestRounds = keepLatestRounds;
        this.perMessageThreshold = perMessageThreshold;
    }

    /**
     * @return 压缩后的消息列表；就地压缩时返回入参本身，丢弃历史时返回一个新列表（调用方应用返回值替换原引用）。
     */
    public List<Message> compact(AgentContext agentContext, List<Message> messages) {
        int total = totalLength(messages);
        int trigger = contextWindowChars * triggerPercent / 100;
        int target = contextWindowChars * targetPercent / 100;

        if (total < trigger) {
            return messages;
        }
        log.info("auto compact triggered: length={}, messages={}, trigger={}, target={}",
                total, messages.size(), trigger, target);

        // Phase 1: 从最长的非 system 消息开始，逐条压缩直到降到 target 水位。
        // 注意：即使只压一条就能降到 target，也会只压那一条；否则会继续逐条压，直到压完或达标。
        List<Integer> order = nonSystemIndicesByLengthDesc(messages);
        for (int idx : order) {
            if (totalLength(messages) <= target) break;
            compactMessage(agentContext, messages.get(idx));
        }
        if (totalLength(messages) <= target) {
            return messages;
        }

        // Phase 2: 按轮丢弃早期历史。
        log.warn("still over target after compaction (length={}), trimming to latest {} rounds",
                totalLength(messages), keepLatestRounds);
        return trimToLatestRounds(messages, keepLatestRounds);
    }

    // ---- 消息级压缩 -----------------------------------------------------------------------------

    private void compactMessage(AgentContext ctx, Message m) {
        if (m == null || m.isCompacted() || m.getRole() == Role.SYSTEM) {
            return;
        }
        boolean changed = false;

        if ((m.getRole() == Role.ASSISTANT || m.getRole() == Role.USER || m.getRole() == Role.TOOL)
                && m.getContent() != null
                && m.getContent().length() > perMessageThreshold) {
            backupContent(ctx, m);
            changed = true;
        }

        if (m.getRole() == Role.ASSISTANT && m.getToolCalls() != null) {
            for (ToolCall tc : m.getToolCalls()) {
                Function f = tc == null ? null : tc.getFunction();
                if (f != null && f.getArguments() != null
                        && f.getArguments().length() > perMessageThreshold) {
                    backupArguments(ctx, f);
                    changed = true;
                }
            }
        }

        if (changed) {
            m.markCompacted();
        }
    }

    private void backupContent(AgentContext ctx, Message m) {
        Path rel = writeBackup(ctx, m.getContent());
        if (rel != null) {
            m.setContent(String.format(BACKUP_MARKER, rel));
        } else {
            m.setContent("...[消息太长，已被截断且原文落盘失败]...");
        }
    }

    private void backupArguments(AgentContext ctx, Function f) {
        Path rel = writeBackup(ctx, f.getArguments());
        if (rel != null) {
            f.setArguments(String.format(BACKUP_MARKER, rel));
        } else {
            f.setArguments("...[参数太长，已被截断且原文落盘失败]...");
        }
    }

    /**
     * 把原文写到 workDir 下的固定目录，使 Read 工具能按相对路径读回。
     *
     * @return 相对于 workDir 的路径；失败返回 null。
     */
    private Path writeBackup(AgentContext ctx, String content) {
        String sessionId = ctx.getSessionId() != null ? ctx.getSessionId() : "default";
        String workDir = ctx.getWorkDir() != null ? ctx.getWorkDir() : ".";
        Path baseDir = Paths.get(workDir, BACKUP_DIR_NAME, sessionId);
        String fileName = UUID.randomUUID() + ".txt";
        Path abs = baseDir.resolve(fileName);
        try {
            Files.createDirectories(baseDir);
            Files.writeString(abs, content);
            // marker 中给出相对 workDir 的路径，方便模型直接传给 Read。
            return Paths.get(BACKUP_DIR_NAME, sessionId, fileName);
        } catch (IOException e) {
            String preview = content == null ? "" : content.substring(0, Math.min(200, content.length()));
            log.error("backup content failed, length={}, preview={}", content == null ? 0 : content.length(), preview, e);
            return null;
        }
    }

    // ---- 按轮裁剪 -------------------------------------------------------------------------------

    static List<Message> trimToLatestRounds(List<Message> messages, int keepRounds) {
        List<Message> systems = new ArrayList<>();
        List<List<Message>> turns = new ArrayList<>();

        int i = 0;
        while (i < messages.size()) {
            Message m = messages.get(i);
            if (m.getRole() == Role.SYSTEM) {
                systems.add(m);
                i++;
                continue;
            }
            List<Message> turn = new ArrayList<>();
            turn.add(m);
            i++;
            // 把这条 assistant tool_call 对应的所有 tool 结果绑在同一轮里，避免协议失配。
            if (m.getRole() == Role.ASSISTANT && m.getToolCalls() != null && !m.getToolCalls().isEmpty()) {
                while (i < messages.size() && messages.get(i).getRole() == Role.TOOL) {
                    turn.add(messages.get(i));
                    i++;
                }
            }
            turns.add(turn);
        }

        int from = Math.max(0, turns.size() - keepRounds);
        List<Message> result = new ArrayList<>(systems);
        for (int t = from; t < turns.size(); t++) {
            result.addAll(turns.get(t));
        }
        return result;
    }

    // ---- 长度估算 -------------------------------------------------------------------------------

    /** 字符级长度估算。注意：这是近似值，未做真正的 tokenize；可在构造时按模型调 contextWindowChars。 */
    public static int length(Message m) {
        if (m == null) return 0;
        int r = m.getContent() == null ? 0 : m.getContent().length();
        if (m.getToolCalls() != null) {
            for (ToolCall tc : m.getToolCalls()) {
                if (tc == null) continue;
                Function f = tc.getFunction();
                if (f == null) continue;
                r += f.getName() == null ? 0 : f.getName().length();
                r += f.getArguments() == null ? 0 : f.getArguments().length();
            }
        }
        return r;
    }

    private static int totalLength(List<Message> messages) {
        return messages.stream().mapToInt(ContextCompactor::length).sum();
    }

    private static List<Integer> nonSystemIndicesByLengthDesc(List<Message> messages) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).getRole() != Role.SYSTEM) {
                indices.add(i);
            }
        }
        indices.sort(Comparator.comparingInt((Integer idx) -> length(messages.get(idx))).reversed());
        return indices;
    }
}
