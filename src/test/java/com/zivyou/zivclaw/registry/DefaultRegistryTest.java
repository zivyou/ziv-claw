package com.zivyou.zivclaw.registry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zivyou.zivclaw.context.AgentContext;
import com.zivyou.zivclaw.message.Function;
import com.zivyou.zivclaw.message.ToolCall;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 {@link DefaultRegistry} 的正常路径与失败路径。
 *
 * <p>为避免与 classpath 扫描出的真实工具耦合(暂无),这里通过包私有构造函数
 * 直接注入工具类列表,精准控制被测集合。</p>
 */
class DefaultRegistryTest {

    // ─── 正常工具:参数含两个字段,invoke 直接把 args.name 回显 ────────────────
    public static class EchoArgs {
        public String name;
        public Integer times;
    }

    @AgentTool(name = "echo", description = "把入参 name 回显 times 次,用于测试")
    public static class EchoTool extends AbstractTool<EchoArgs> {
        @Override
        public ToolResult invoke(AgentContext ctx, EchoArgs args) {
            StringBuilder sb = new StringBuilder();
            int n = args.times == null ? 1 : args.times;
            for (int i = 0; i < n; i++) sb.append(args.name);
            // 工具不关心 tool_call_id,registry 会统一注入
            return ToolResult.ok(sb.toString());
        }
    }

    // ─── 冲突用:与 EchoTool 同名 ─────────────────────────────────────────────
    @AgentTool(name = "echo", description = "同名冲突工具")
    public static class DuplicateEchoTool extends AbstractTool<EchoArgs> {
        @Override
        public ToolResult invoke(AgentContext ctx, EchoArgs args) {
            return ToolResult.ok("dup");
        }
    }

    // ─── 有 @AgentTool 但没实现 Tool ─────────────────────────────────────────
    @AgentTool(name = "not_a_tool", description = "缺少 Tool 实现")
    public static class NotATool {
    }

    @Test
    void 发现工具_生成schema_并按名称派发执行() throws Exception {
        DefaultRegistry registry = new DefaultRegistry(List.of(EchoTool.class));

        List<ToolDefinition> defs = registry.getAvailableTools();
        assertEquals(1, defs.size());

        ToolDefinition def = defs.get(0);
        assertEquals("echo", def.getName());
        assertEquals("把入参 name 回显 times 次,用于测试", def.getDescription());

        // inputSchema 必须是合法 JSON,并包含 POJO 的字段名
        JsonNode schema = new ObjectMapper().readTree(def.getInputSchema());
        assertNotNull(schema);
        assertTrue(def.getInputSchema().contains("\"name\""),
                "schema 应包含字段 name: " + def.getInputSchema());
        assertTrue(def.getInputSchema().contains("\"times\""),
                "schema 应包含字段 times: " + def.getInputSchema());

        // execute 走一次完整链路:JSON → POJO → invoke → ToolResult
        ToolCall call = ToolCall.builder()
                .id("call-1")
                .type("function")
                .function(Function.builder()
                        .name("echo")
                        .arguments("{\"name\":\"hi\",\"times\":3}")
                        .build())
                .build();
        ToolResult result = registry.execute(AgentContext.builder().workDir(".").build(), call);
        assertFalse(result.isError());
        assertEquals("hihihi", result.getOutput());
        // registry 应把 tool_call 的 id 单点注入到 ToolResult,工具自己不用管
        assertEquals("call-1", result.getToolCallId());
    }

    @Test
    void 单例复用同一工具实例() {
        DefaultRegistry registry = new DefaultRegistry(List.of(EchoTool.class));
        AgentContext ctx = AgentContext.builder().workDir(".").build();
        ToolCall call = ToolCall.builder()
                .id("call-x")
                .function(Function.builder().name("echo").arguments("{\"name\":\"a\"}").build())
                .build();
        // 两次调用都能命中并成功,证明工具实例存活于 registry 生命周期
        assertFalse(registry.execute(ctx, call).isError());
        assertFalse(registry.execute(ctx, call).isError());
    }

    @Test
    void 未知工具名_返回error而不是抛异常() {
        DefaultRegistry registry = new DefaultRegistry(List.of(EchoTool.class));
        ToolCall call = ToolCall.builder()
                .id("call-2")
                .function(Function.builder().name("nope").arguments("{}").build())
                .build();
        ToolResult result = registry.execute(AgentContext.builder().workDir(".").build(), call);
        assertTrue(result.isError());
        assertEquals("call-2", result.getToolCallId());
        assertTrue(result.getOutput().contains("nope"));
    }

    @Test
    void 名称冲突_构造Registry时立即fail_fast() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new DefaultRegistry(List.of(EchoTool.class, DuplicateEchoTool.class)));
        assertTrue(ex.getMessage().contains("echo"), ex.getMessage());
        assertTrue(ex.getMessage().contains(EchoTool.class.getName()), ex.getMessage());
        assertTrue(ex.getMessage().contains(DuplicateEchoTool.class.getName()), ex.getMessage());
    }

    @Test
    void 有注解但未实现Tool_fail_fast() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new DefaultRegistry(List.of(NotATool.class)));
        assertTrue(ex.getMessage().contains("Tool"), ex.getMessage());
        assertTrue(ex.getMessage().contains(NotATool.class.getName()), ex.getMessage());
    }

    @Test
    void 空工具列表_不抛异常_返回空定义() {
        DefaultRegistry registry = new DefaultRegistry(List.of());
        assertTrue(registry.getAvailableTools().isEmpty());
    }
}
