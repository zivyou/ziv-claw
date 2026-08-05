package com.zivyou.zivclaw.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfig;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jackson.JacksonOption;
import com.zivyou.zivclaw.context.AgentContext;
import com.zivyou.zivclaw.message.ToolCall;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ziv-claw 的手搓 IoC / 工具注册表。
 *
 * <p>启动时通过 {@link Reflections} 扫描 {@link #BASE_PACKAGE} 下所有标注了 {@link AgentTool}
 * 的类,对每个类各 {@code new} 一份实例(单例),按 {@link AgentTool#name()} 建索引,并根据
 * {@link Tool#argsType()} 自动生成 JSON Schema 得到 {@link ToolDefinition}。</p>
 *
 * <p>全局单例通过 {@link #getInstance()} 获取;测试可通过包私有的
 * {@link #DefaultRegistry(Collection)} 构造函数直接注入工具类列表,绕开 classpath 扫描。</p>
 */
@Slf4j
public class DefaultRegistry implements Registry {

    /** 全局扫描根包,固定值。若未来需要扩展再改为可配置。 */
    private static final String BASE_PACKAGE = "com.zivyou.zivclaw";

    /** JVM 生命周期单例;懒加载,首次调用 {@link #getInstance()} 时才启动扫描。 */
    private static volatile DefaultRegistry INSTANCE;

    private final Map<String, Tool<?>> toolsByName;
    private final List<ToolDefinition> definitions;
    private final ObjectMapper objectMapper;

    /**
     * 生产路径:扫描 classpath 上所有 {@code @AgentTool} 类完成注册。
     */
    public DefaultRegistry() {
        this(discoverToolClasses());
    }

    /**
     * 测试友好路径:直接注入工具类列表,不做 classpath 扫描。包私有,仅供测试。
     */
    DefaultRegistry(Collection<Class<?>> toolClasses) {
        this.objectMapper = new ObjectMapper();
        // ─────────────────────────────────────────────────────────────────────────────
        // 【关于 inputSchema 自动生成】
        //
        // ziv-claw 的工具参数 schema 有意不由作者手写,也不塞进 @AgentTool 注解,而是在
        // 这里由 victools/jsonschema-generator 从 Tool.argsType() 返回的参数 POJO 自动派生。
        // 这样做的原因:
        //   1. 唯一事实源。POJO 的字段就是 LLM 看到的 schema,增删字段 / 改类型 / 改必填,
        //      LLM 立刻看到最新版,永远不会出现"改了 POJO 忘了改 schema"的双写漂移。
        //   2. 注解不适合承载多行 schema。JSON Schema 动辄几十行且需要换行、层级,
        //      写在 @AgentTool 里既丑又不可读。
        //   3. 让工具作者只关心一件事:定义参数 POJO(和 invoke 逻辑)。schema、
        //      反序列化、name/description 提取全部由容器接管。
        //
        // 使用的组件:
        //   · SchemaVersion.DRAFT_2020_12  ── 输出符合 JSON Schema 2020-12 草案,
        //     LLM 供应商(OpenAI / 火山 Ark 等)对该草案的兼容度较好。
        //   · OptionPreset.PLAIN_JSON      ── 生成对 LLM 友好的"纯 JSON Schema"(不带
        //     Java 类型元信息)。
        //   · JacksonModule                ── 让生成器识别 @JsonProperty / @JsonPropertyDescription
        //     等 Jackson 注解,作者可以借此给字段加描述、改字段名。
        //   · JacksonOption.RESPECT_JSONPROPERTY_REQUIRED  ── 让 @JsonProperty(required=true)
        //     真的落到 schema 的 "required": [...] 数组里(默认关闭)。工具作者只需
        //     在字段上写 @JsonProperty(required = true),不再需要手写 required 数组。
        // ─────────────────────────────────────────────────────────────────────────────
        SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(
                SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON);
        configBuilder.with(new JacksonModule(JacksonOption.RESPECT_JSONPROPERTY_REQUIRED));
        SchemaGeneratorConfig schemaConfig = configBuilder.build();
        SchemaGenerator schemaGenerator = new SchemaGenerator(schemaConfig);

        Map<String, Tool<?>> byName = new LinkedHashMap<>();
        Map<String, Class<?>> ownerByName = new LinkedHashMap<>();
        List<ToolDefinition> defs = new ArrayList<>();

        for (Class<?> clazz : toolClasses) {
            if (!clazz.isAnnotationPresent(AgentTool.class)) {
                throw new IllegalStateException(
                        clazz.getName() + " 出现在工具注册列表中但缺少 @AgentTool 注解");
            }
            if (!Tool.class.isAssignableFrom(clazz)) {
                throw new IllegalStateException(
                        clazz.getName() + " 标注了 @AgentTool 但未实现 Tool 接口");
            }
            Tool<?> tool = instantiate(clazz);
            AgentTool meta = clazz.getAnnotation(AgentTool.class);
            String name = meta.name();
            if (name == null || name.isBlank()) {
                throw new IllegalStateException(
                        clazz.getName() + " 的 @AgentTool.name() 为空,必须提供非空唯一名称");
            }
            Class<?> previousOwner = ownerByName.get(name);
            if (previousOwner != null) {
                throw new IllegalStateException(String.format(
                        "工具名称冲突: \"%s\" 被两个类同时使用: %s 与 %s",
                        name, previousOwner.getName(), clazz.getName()));
            }
            // schema 生成的调用点 —— 参见上方"关于 inputSchema 自动生成"注释块。
            String inputSchema = schemaGenerator.generateSchema(tool.argsType()).toString();
            byName.put(name, tool);
            ownerByName.put(name, clazz);
            defs.add(new ToolDefinition(name, meta.description(), inputSchema));
        }

        if (byName.isEmpty()) {
            log.warn("[Registry] 未在 {} 下发现任何 @AgentTool 工具类,注册表为空", BASE_PACKAGE);
        } else {
            log.info("[Registry] 发现 {} 个工具: {}", byName.size(), byName.keySet());
        }

        this.toolsByName = Map.copyOf(byName);
        this.definitions = List.copyOf(defs);
    }

    /** 通过 classpath 扫描发现所有 {@code @AgentTool} 类。 */
    private static Collection<Class<?>> discoverToolClasses() {
        Reflections reflections = new Reflections(BASE_PACKAGE);
        return reflections.getTypesAnnotatedWith(AgentTool.class);
    }

    private static Tool<?> instantiate(Class<?> clazz) {
        try {
            Object instance = clazz.getDeclaredConstructor().newInstance();
            return (Tool<?>) instance;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(
                    clazz.getName() + " 缺少 public 无参构造函数,无法被容器实例化", e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException(
                    clazz.getName() + " 的构造函数抛出异常", e.getCause());
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IllegalStateException(
                    "反射实例化 " + clazz.getName() + " 失败", e);
        }
    }

    /** 获取全局单例,首次调用时触发一次 classpath 扫描。 */
    public static DefaultRegistry getInstance() {
        DefaultRegistry local = INSTANCE;
        if (local == null) {
            synchronized (DefaultRegistry.class) {
                local = INSTANCE;
                if (local == null) {
                    local = new DefaultRegistry();
                    INSTANCE = local;
                }
            }
        }
        return local;
    }

    @Override
    public List<ToolDefinition> getAvailableTools() {
        return definitions;
    }

    @Override
    public ToolResult execute(AgentContext agentContext, ToolCall toolCall) {
        String toolCallId = toolCall.getId();
        String name = toolCall.getFunction() == null ? null : toolCall.getFunction().getName();
        if (name == null) {
            return ToolResult.error(toolCallId, "tool_call 缺少 function.name");
        }
        Tool<?> tool = toolsByName.get(name);
        if (tool == null) {
            return ToolResult.error(toolCallId, "未知的工具名称: " + name);
        }
        try {
            return invokeTyped(tool, name, agentContext, toolCall.getFunction().getArguments(), toolCallId);
        } catch (Exception e) {
            log.error("[Registry] 工具 {} 执行失败", name, e);
            return ToolResult.error(toolCallId,
                    "工具 " + name + " 执行失败: " + e.getClass().getSimpleName()
                            + ": " + e.getMessage());
        }
    }

    /**
     * 私有泛型辅助方法。把 {@code Tool<?>} 上的通配符捕获为具体类型 {@code A},
     * 让 {@link ObjectMapper#readValue} 与 {@link Tool#invoke} 在编译期类型对齐。
     * {@code name} 由调用方从 tool_call / 注册表拿到,只用于错误信息 —— 工具本身
     * 不再暴露 {@code name()} 方法(见 {@link AgentTool})。
     */
    private <A> ToolResult invokeTyped(Tool<A> tool, String name, AgentContext agentContext,
                                       String rawJsonArgs, String toolCallId) throws Exception {
        A args;
        if (rawJsonArgs == null || rawJsonArgs.isBlank()) {
            args = objectMapper.readValue("{}", tool.argsType());
        } else {
            args = objectMapper.readValue(rawJsonArgs, tool.argsType());
        }
        ToolResult result = tool.invoke(agentContext, args);
        if (result == null) {
            return ToolResult.error(toolCallId, "工具 " + name + " 返回了 null");
        }
        // 协议 id 单点注入:工具自身不应关心 tool_call_id(它是 LLM 协议层字段,
        // 用于把 tool 消息和发起它的 tool_call 配对),这里强制盖上,避免任何一个
        // 工具漏塞 id 导致下一轮请求被 provider 判为 MissingParameter。
        return result.withToolCallId(toolCallId);
    }
}
