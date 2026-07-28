package com.zivyou.zivclaw.registry;

import com.zivyou.zivclaw.context.Context;

/**
 * ziv-claw Agent 工具的顶层抽象。
 *
 * <p>一个 {@code Tool<A>} 表示"一个可以被 LLM 调用的工具",其参数由类型形参 {@code A} 描述
 * (一个纯 POJO)。工具的 <b>name</b> 与 <b>description</b> 通过类上的 {@link AgentTool}
 * 注解声明,不再作为接口方法暴露,避免与注解值双写漂移。{@link DefaultRegistry} 会:</p>
 * <ol>
 *   <li>从 {@link AgentTool} 注解读出 {@code name} / {@code description};</li>
 *   <li>通过 {@link #argsType()} 拿到 {@code A} 的 Class,用 Jackson 把 tool_call 里的 JSON
 *       反序列化成 {@code A} 实例,并自动生成 JSON Schema 填到
 *       {@link ToolDefinition#getInputSchema()};</li>
 *   <li>调用 {@link #invoke(Context, Object)} 执行工具逻辑。</li>
 * </ol>
 *
 * <p>大多数情况下应继承 {@link AbstractTool} 而不是直接实现本接口 —— 基类会自动实现
 * {@link #argsType()},作者只需实现 {@link #invoke} 并在类上加 {@link AgentTool} 注解。</p>
 *
 * @param <A> 参数 POJO 类型
 */
public interface Tool<A> {

    /**
     * 参数 POJO 的运行时 Class。返回值供 Jackson 反序列化与 JSON Schema 生成器使用。
     * 由于 Java 泛型有类型擦除,无法从 {@code Tool<?>} 直接反射出 {@code A},所以必须显式返回。
     */
    Class<A> argsType();

    /**
     * 执行工具逻辑。
     *
     * @param context Agent 运行上下文
     * @param args    由 Registry 从 tool_call JSON 反序列化得到的强类型参数对象
     * @return 工具执行结果,交回给 LLM 作为 role=tool 的消息内容
     */
    ToolResult invoke(Context context, A args);
}
