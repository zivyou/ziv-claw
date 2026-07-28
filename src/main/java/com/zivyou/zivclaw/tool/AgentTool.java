package com.zivyou.zivclaw.tool;

import java.lang.annotation.*;

/**
 * 标记一个类是 ziv-claw 的 Agent 工具。被本注解标记的类会在启动时被
 * {@link DefaultRegistry} 通过 classpath 扫描自动发现,并作为单例注入到工具注册表中,
 * LLM 后续可以按 {@link #name()} 调用它。注解上的 {@code name} / {@code description}
 * 是工具身份的<b>唯一事实源</b> —— {@link Tool} 接口不再暴露对应方法,避免双写漂移。
 *
 * <p><b>约束:</b></p>
 * <ul>
 *   <li>必须实现 {@link Tool} 接口(推荐继承 {@link AbstractTool} 基类以省掉样板)。</li>
 *   <li>必须提供 public 无参构造函数(容器用反射 {@code newInstance()})。</li>
 *   <li>{@link #name()} 在整个应用内必须唯一,重名会导致启动失败(fail-fast)。</li>
 * </ul>
 *
 * <p><b>为什么不在这里声明 inputSchema?</b>
 * 工具参数的 JSON Schema 由 {@link DefaultRegistry} 根据 {@code Tool.argsType()} 返回的
 * 参数 POJO 自动生成 —— 作者只需要维护 POJO 字段,LLM 看到的 schema 会自动同步,
 * 从根本上避免"改了 POJO 忘了改 schema"的双写漂移。</p>
 *
 * <p><b>示例:</b></p>
 * <pre>{@code
 * @AgentTool(name = "read_file", description = "读取本地文件的内容")
 * public class ReadFileTool extends AbstractTool<ReadFileTool.Args> {
 *     public static class Args { public String path; }
 *     @Override
 *     public ToolResult invoke(Context ctx, Args args) { ... }
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target(ElementType.TYPE)
public @interface AgentTool {
    /** 工具唯一名称,LLM 通过此名称调用,应用内必须唯一。 */
    String name();

    /** 一句话工具描述,直接暴露给 LLM 让它决定何时调用本工具。 */
    String description();
}
