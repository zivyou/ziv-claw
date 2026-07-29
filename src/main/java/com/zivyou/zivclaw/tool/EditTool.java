package com.zivyou.zivclaw.tool;

import com.zivyou.zivclaw.context.Context;
import com.zivyou.zivclaw.registry.AbstractTool;
import com.zivyou.zivclaw.registry.AgentTool;
import com.zivyou.zivclaw.registry.ToolResult;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@AgentTool(name = "Edit", description = "文件编辑工具，核心逻辑是对指定文本块执行替换操作")
public class EditTool extends AbstractTool<EditToolArg> {
    @Override
    public ToolResult invoke(Context context, EditToolArg args) {
        Path basePath = Paths.get(context.getWorkDir()).toAbsolutePath().normalize();
        Path filePath = basePath.resolve(args.getPath()).normalize();

        // 防止路径逃逸出工作目录
        if (!filePath.startsWith(basePath)) {
            return ToolResult.error("path escapes work dir: " + args.getPath());
        }

        String content;
        try {
            content = Files.readString(filePath, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            log.error("file not exists: {}", filePath);
            return ToolResult.error("file not exists: " + filePath);
        } catch (IOException e) {
            log.error("read file failed: {}", e.getMessage());
            return ToolResult.error(String.format("read file %s failed: %s", filePath, e.getMessage()));
        }

        String oldContent = args.getOldContent();
        String newContent = args.getNewContent();

        if (oldContent.equals(newContent)) {
            return ToolResult.ok("no changes: oldContent == newContent");
        }

        // 正确统计出现次数：每次匹配后向后跳 oldContent.length()
        int count = 0;
        int firstIndex = -1;
        int step = Math.max(oldContent.length(), 1);
        for (int i = 0; (i = content.indexOf(oldContent, i)) != -1; i += step) {
            if (count == 0) firstIndex = i;
            if (++count > 1) break;
        }

        if (count == 0) {
            return ToolResult.error("old content 在原文中未找到，无法正确执行 Edit");
        }
        if (count > 1) {
            return ToolResult.error("old content 在原文中存在不止一处，Edit 操作存在歧义，请提供更多上下文");
        }

        // 用字面量拼接，而不是 replaceAll（后者会把 oldContent 当作正则）
        String newFileContent =
                content.substring(0, firstIndex)
                        + newContent
                        + content.substring(firstIndex + oldContent.length());

        try {
            Files.writeString(filePath, newFileContent, StandardCharsets.UTF_8);
            return ToolResult.ok("edit file succeeded");
        } catch (IOException e) {
            log.error("edit file failed: {}", e.getMessage());
            return ToolResult.error(String.format("edit file failed: %s", e.getMessage()));
        }
    }
}
