package com.zivyou.zivclaw.tool;

import com.zivyou.zivclaw.context.AgentContext;
import com.zivyou.zivclaw.registry.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditToolTest {

    @TempDir
    Path workDir;

    private EditTool tool;
    private AgentContext agentContext;

    @BeforeEach
    void setUp() {
        tool = new EditTool();
        agentContext = AgentContext.builder().workDir(workDir.toString()).build();
    }

    private Path writeFile(String relative, String content) throws IOException {
        Path file = workDir.resolve(relative);
        Files.createDirectories(file.getParent() == null ? workDir : file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    private EditToolArg args(String path, String oldContent, String newContent) {
        EditToolArg a = new EditToolArg();
        a.setPath(path);
        a.setOldContent(oldContent);
        a.setNewContent(newContent);
        return a;
    }

    // Bug 3 回归：oldContent 里含正则元字符，之前 replaceAll 会当正则解析而炸/失配
    @Test
    void edit_replacesLiteralOldContent_evenWhenItLooksLikeRegex() throws IOException {
        Path file = writeFile("a.txt", "price is $9.99 (final).\n");

        ToolResult result = tool.invoke(agentContext, args("a.txt", "$9.99 (final)", "$10.00"));

        assertFalse(result.isError(), () -> "unexpected error: " + result.getOutput());
        assertEquals("price is $10.00.\n", read(file));
    }

    // Bug 1 回归：多行 oldContent 需要能匹配到并且保留其余换行
    @Test
    void edit_supportsMultiLineOldContent_andPreservesNewlines() throws IOException {
        String original = "line1\nline2\nline3\nline4\n";
        Path file = writeFile("multi.txt", original);

        ToolResult result = tool.invoke(agentContext, args("multi.txt", "line2\nline3", "LINE_2_3"));

        assertFalse(result.isError(), () -> "unexpected error: " + result.getOutput());
        assertEquals("line1\nLINE_2_3\nline4\n", read(file));
    }

    // Bug 2 回归：文件中出现多次时应返回歧义错误、原文不被改动，且不会陷入死循环
    @Test
    void edit_returnsError_whenOldContentAppearsMoreThanOnce() throws IOException {
        String original = "foo\nfoo\nbar\n";
        Path file = writeFile("dup.txt", original);

        ToolResult result = tool.invoke(agentContext, args("dup.txt", "foo", "baz"));

        assertTrue(result.isError());
        assertTrue(result.getOutput().contains("不止一处"), result.getOutput());
        assertEquals(original, read(file), "文件不应被修改");
    }

    @Test
    void edit_returnsError_whenOldContentNotFound() throws IOException {
        writeFile("nf.txt", "hello world\n");

        ToolResult result = tool.invoke(agentContext, args("nf.txt", "missing", "x"));

        assertTrue(result.isError());
        assertTrue(result.getOutput().contains("未找到"), result.getOutput());
    }

    @Test
    void edit_returnsError_whenFileNotExists() {
        ToolResult result = tool.invoke(agentContext, args("nope.txt", "a", "b"));

        assertTrue(result.isError());
        assertTrue(result.getOutput().contains("file not exists"), result.getOutput());
    }

    @Test
    void edit_rejectsPathEscapingWorkDir_viaRelative() throws IOException {
        // 在 workDir 之外放一个文件，看能否被 ../ 逃出去改到
        Path outside = workDir.getParent().resolve("outside-" + workDir.getFileName() + ".txt");
        Files.writeString(outside, "should-not-change", StandardCharsets.UTF_8);
        try {
            String rel = "../" + outside.getFileName().toString();
            ToolResult result = tool.invoke(agentContext, args(rel, "should-not-change", "hacked"));

            assertTrue(result.isError());
            assertTrue(result.getOutput().contains("escapes work dir"), result.getOutput());
            assertEquals("should-not-change", Files.readString(outside, StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void edit_rejectsAbsolutePathOutsideWorkDir() throws IOException {
        Path outside = Files.createTempFile("edit-tool-abs", ".txt");
        Files.writeString(outside, "keep", StandardCharsets.UTF_8);
        try {
            ToolResult result = tool.invoke(agentContext,
                    args(outside.toAbsolutePath().toString(), "keep", "hacked"));

            assertTrue(result.isError());
            assertTrue(result.getOutput().contains("escapes work dir"), result.getOutput());
            assertEquals("keep", Files.readString(outside, StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void edit_isNoop_whenOldEqualsNew() throws IOException {
        Path file = writeFile("same.txt", "content\n");

        ToolResult result = tool.invoke(agentContext, args("same.txt", "content", "content"));

        assertFalse(result.isError());
        assertTrue(result.getOutput().contains("no changes"), result.getOutput());
        assertEquals("content\n", read(file));
    }

    @Test
    void edit_replaceKeepsDollarAndBackslashInNewContentLiteral() throws IOException {
        // 之前 replaceAll 会把 $1、\\ 当替换语法处理；改成字面拼接后应完整保留
        Path file = writeFile("lit.txt", "TOKEN\n");

        ToolResult result = tool.invoke(agentContext, args("lit.txt", "TOKEN", "$1 and \\n literal"));

        assertFalse(result.isError(), () -> "unexpected error: " + result.getOutput());
        assertEquals("$1 and \\n literal\n", read(file));
    }
}
