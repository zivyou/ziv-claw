# Design: add-tool-registry

## Context

`ReActAgent` 通过 `Registry` 拿到 tool schema 交给 LLM,再把 LLM 返回的 `ToolCall` 交给 `Registry.execute()` 实际执行。目前 `DefaultRegistry` 是空壳,整条链路跑不起来。项目坚持"不用 Spring",但仍需要一个装配层:发现工具类、单例化、按名称派发。

## Goals

1. 工具作者体验尽量轻:新增一个工具 = 写一个 `@AgentTool` 类 + 一个参数 POJO,零额外注册。
2. `inputSchema` 不需要人肉维护;字段改了,LLM 看到的 schema 也自动改。
3. 失败尽量早:命名冲突、构造失败、类型不匹配,全部启动期就报出来。
4. 依赖尽量少;引入的库都必须替代掉大量样板代码,不做无谓抽象。

## Non-goals

- 通用 DI 容器(不做 `@Inject`、依赖图、`@Scope` 之类)。
- 热加载 / 动态注册。
- 方法级工具(`@AgentTool` on method)—— 明确不支持,统一在类上。

## Decisions

### Decision 1: 注解目标 —— `@Target(TYPE)`

**选择**: 类级注解 + 一个类对应一个工具。

**Alternatives considered**:
- 方法级(现状):一个类可以宿含多个 tool。样板少,但反射调用参数、schema 派生都变复杂,且和"单例对象"的心智不匹配。

**理由**: 一类一工具,天然无状态,单例化即安全;`argsType()` 通过泛型参数拿到,统一走 Jackson 反序列化,链路简单。

### Decision 2: 用 Reflections 库扫描

**选择**: 依赖 `org.reflections:reflections`(pom 里已存在)。

**Alternatives considered**:
- 手搓 `ClassLoader.getResources` + JAR/File 双分支:细节多(包名/路径互转、`IOException`、Java 模块化边界、嵌套 JAR),对本项目无价值。

**理由**: 库已在依赖里,两行搞定,避免维护扫描代码。

### Decision 3: 参数走强类型 POJO(选项 3)

**选择**: `Tool<A>` 泛型 + `Class<A> argsType()`;Registry 用 Jackson 把 `ToolCall.function.arguments` 反序列化成 `A`。

**Alternatives considered**:
- 裸 JSON 字符串:每个工具重复解析代码。
- `Map<String, Object>`:类型检查全在运行期,IDE 无提示。

**理由**: 工具作者拿到的是强类型 POJO,IDE 支持完整;所有 JSON 边界统一收敛到 Registry。

### Decision 4: 提供 `AbstractTool<A>` 基类,让 `argsType()` 免写

**选择**: 抽象基类通过 `getGenericSuperclass()` 反射自身父类的泛型实参,自动实现 `argsType()`。

**Alternatives considered**:
- 让作者显式实现 `argsType() { return XxxArgs.class; }`:每个工具多写 3 行,忘记同步类型会出问题。
- 只保留接口,不给基类:同上。

**理由**: 业界通行手法(Jackson `TypeReference<T>` 同源);95% 场景下作者只需要继承基类,零样板。

**风险**: 匿名类 / lambda 使用 `AbstractTool` 会拿不到泛型信息。缓解:保留 `Tool<A>` 接口作为逃生口,遇到就让作者显式实现 `argsType()`;基类实现里如果拿不到 `ParameterizedType` 立即抛清晰错误信息,而不是 `ClassCastException`。

### Decision 5: `inputSchema` 由 victools/jsonschema-generator 自动生成

**选择**: 引入 `com.github.victools:jsonschema-generator`,在 Registry 构造时对每个 `argsType()` 生成 JSON Schema。

**Alternatives considered**:
- 手写 schema 字符串放注解里:注解值不能过长且没有换行支持,几乎不可读。
- 手写 schema 放 classpath 资源文件:改 POJO 必须记得改 JSON,人肉双写。
- 用 jackson-module-jsonSchema:已弃用。

**理由**: POJO 是唯一事实源;字段增删/改类型,LLM 看到的 schema 立刻跟着变,永远不会漂移。

**Code convention (硬性要求)**: schema 生成的调用位置(`DefaultRegistry` 里),**必须写一段中文注释**说明:
1. schema 为什么不写在 `@AgentTool` 注解里、也不让 Tool 手写 —— 避免和 POJO 双写漂移。
2. 生成器读的是 `tool.argsType()` 返回的 Class,所以工具作者只需要维护 POJO 字段即可。
3. victools 生成器的作用范围(标准 JSON Schema draft、Jackson module 兼容)与不足(不覆盖复杂多态)。

后续任何维护者读这段代码时都不用去翻 design.md 才能理解。

### Decision 6: Registry 单例 + fail-fast

- 单例:静态字段 `INSTANCE`,通过 `DefaultRegistry.getInstance()` 拿。工具集在整个 JVM 生命周期不变。
- 命名冲突 → `IllegalStateException`,列出冲突的两个类。
- `@AgentTool` 类没实现 `Tool` → `IllegalStateException`。
- 缺无参构造 / 构造抛异常 → 直接向上抛,包装原因。

**理由**: Tool 注册是编译期意图,不该在运行期"静悄悄地少一个"。

### Decision 7: 扫描根包硬编码 `com.zivyou.zivclaw`

**Alternatives considered**: 通过配置文件 / 构造参数传入。

**理由**: 项目就一个根包,加参数只是过度设计。以后如果发现要扩展,再抽 `basePackages` 参数,那时改动很小。

## 依赖增量

```xml
<dependency>
  <groupId>com.fasterxml.jackson.core</groupId>
  <artifactId>jackson-databind</artifactId>
  <version>2.17.2</version>   <!-- 稳定版即可 -->
</dependency>
<dependency>
  <groupId>com.github.victools</groupId>
  <artifactId>jsonschema-generator</artifactId>
  <version>4.35.0</version>
</dependency>
<!-- 可选: victools 的 jackson module,让生成器认 @JsonProperty 等注解 -->
<dependency>
  <groupId>com.github.victools</groupId>
  <artifactId>jsonschema-module-jackson</artifactId>
  <version>4.35.0</version>
</dependency>
```

## 数据流一图

```
构造期 (启动时一次):
  Reflections("com.zivyou.zivclaw")
    → getTypesAnnotatedWith(AgentTool.class)
    → for each class:
         assert implements Tool
         Tool<?> t = class.getDeclaredConstructor().newInstance()
         Class<?> A = t.argsType()
         String schema = schemaGenerator.generateSchema(A).toString()
         def = new ToolDefinition(t.name(), t.description(), schema)
         if (byName.putIfAbsent(t.name(), t) != null) throw ...
         defs.add(def)

执行期 (每次 tool_call):
  Tool<A> t = byName.get(call.function.name)     // A 是通配符
  A args = objectMapper.readValue(call.function.arguments, t.argsType())
  try { return t.invoke(ctx, args) }
  catch (Exception e) { return ToolResult.error(e.getMessage(), toolCallId) }
```

(注:`Tool<A>` 在 Map 中以 `Tool<?>` 存储,`argsType()` 返回 `Class<A>`。为了让 `objectMapper.readValue` 通过编译,可以把它包装成一个 `<A> A read(Tool<A> t, String json)` 的私有泛型辅助方法,把通配符捕获成具体类型。)

## Open Questions

- `AbstractTool` 找不到 `ParameterizedType` 时(比如匿名类)要不要给 fallback?当前决定:直接抛 `IllegalStateException`,让作者去实现 `Tool<A>` 接口而不是继承基类。
- `ToolResult` 目前结构未在本 change 中动。如果它没有 `error(String, String toolCallId)` 工厂方法,需要在实现阶段小补一个。
