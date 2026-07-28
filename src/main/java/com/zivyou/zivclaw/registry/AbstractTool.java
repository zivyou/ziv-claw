package com.zivyou.zivclaw.registry;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * {@link Tool} 的推荐基类。通过 {@link Class#getGenericSuperclass()} 反射当前子类的父类
 * 泛型实参,自动实现 {@link #argsType()},作者不用手写。工具的 name 与 description 通过
 * 类上的 {@link AgentTool} 注解声明,由 {@link DefaultRegistry} 读取。
 *
 * <p><b>最小示例:</b></p>
 * <pre>{@code
 * @AgentTool(name = "read_file", description = "读取本地文件的内容")
 * public class ReadFileTool extends AbstractTool<ReadFileTool.Args> {
 *     public static class Args {
 *         public String path;
 *         public Integer limit;
 *     }
 *
 *     @Override
 *     public ToolResult invoke(Context ctx, Args args) {
 *         // 业务逻辑,args 已经是反序列化好的强类型 POJO
 *         return ToolResult.ok(...);
 *     }
 * }
 * }</pre>
 *
 * <p><b>约束:</b>子类必须"直接继承"本基类且必须是命名类(不能是匿名类 / lambda),
 * 因为 {@code getGenericSuperclass()} 只对直接父类可见的泛型实参有效。
 * 遇到匿名类等无法拿到泛型实参的情况,{@link #argsType()} 会抛
 * {@link IllegalStateException},并在错误信息里指引作者直接实现 {@link Tool} 接口。</p>
 *
 * @param <A> 工具参数 POJO 类型
 */
public abstract class AbstractTool<A> implements Tool<A> {

    private final Class<A> argsType;

    @SuppressWarnings("unchecked")
    protected AbstractTool() {
        Type superType = getClass().getGenericSuperclass();
        if (!(superType instanceof ParameterizedType parameterized)) {
            throw new IllegalStateException(
                    "无法从 " + getClass().getName() + " 的父类反射出泛型实参 A。"
                            + " AbstractTool 依赖 getGenericSuperclass() 拿到泛型实参,"
                            + " 因此子类必须是命名的具体类,不能是匿名类 / lambda 等。"
                            + " 如果确实需要动态构造,请直接实现 Tool<A> 接口并显式返回 argsType()。");
        }
        Type[] typeArgs = parameterized.getActualTypeArguments();
        if (typeArgs.length == 0 || !(typeArgs[0] instanceof Class<?> raw)) {
            throw new IllegalStateException(
                    "AbstractTool 期望第一个泛型实参 A 是一个具体 Class,但在 "
                            + getClass().getName() + " 上拿到的是 "
                            + (typeArgs.length == 0 ? "<空>" : typeArgs[0].getTypeName())
                            + "。请直接实现 Tool<A> 接口并显式返回 argsType()。");
        }
        this.argsType = (Class<A>) raw;
    }

    @Override
    public Class<A> argsType() {
        return argsType;
    }
}
