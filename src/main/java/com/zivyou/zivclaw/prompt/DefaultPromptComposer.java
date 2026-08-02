package com.zivyou.zivclaw.prompt;

import com.zivyou.zivclaw.message.Message;
import com.zivyou.zivclaw.message.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Paths;

@Slf4j
@RequiredArgsConstructor
public class DefaultPromptComposer implements PromptComposer {
    private final String workDir;
    private final StringBuilder sb = new StringBuilder(
"""
你是ziv-claw,一个基于harness工程搭建的智能助手。

# 核心纪律
1. 你必须使用中文作答；
"""
    );

    private void loadAgentsMD() {
        var basePath = Paths.get(workDir);
        var relativePath = Paths.get("./AGENTS.md");
        var filePath = basePath.resolve(relativePath);
        if (!Files.exists(filePath)) return;
        try {
            var content = Files.readString(filePath);
            if (content.isBlank()) {
                sb.append("\n # 项目专属指南(来自AGENTS.md) \n");
                sb.append("以下是当前工作区特有的架构规范与注意事项，你的行为必须绝对符合以下要求：\n");
                sb.append("```markdown");
                sb.append(content);
                sb.append("\n```\n");
            }
        } catch (IOException e) {
            log.error("loadAgentsMD failed.", e);
            throw new RuntimeException(e);
        }
    }

    private void loadSkills() {
        var basePath = Paths.get(workDir);
        var relativePath = Paths.get("./.agents/skills");
        var dirPath = basePath.resolve(relativePath);
        if (Files.exists(dirPath, LinkOption.NOFOLLOW_LINKS)) {
            sb.append("\n # 当前项目可用的SKILLS列表 \n");
            sb.append("以下是你拥有的标准化外挂技能，请在符合 description 描述的场景下严格遵循其正文指令：\n\n");
            // TODO： 完成SKILL机制；
        }
    }

    @Override
    public Message compose() {
        loadAgentsMD();
        loadSkills();
        return Message.builder()
                .role(Role.SYSTEM)
                .content(sb.toString())
                .build();
    }
}
