# Tasks: add-tool-registry

## 1. 依赖

- [x] 1.1 在 `pom.xml` 新增 `jackson-databind`(2.17.2)、`jsonschema-generator`(4.35.0)、`jsonschema-module-jackson`(4.35.0)三个依赖。
- [x] 1.2 `mvn -q -DskipTests package` 通过。

## 2. 注解与接口

- [x] 2.1 修改 `AgentTool`:`@Target(TYPE)`,新增 `String name()`、`String description()` 字段(无默认值)。
- [x] 2.2 新增 `Tool<A>` 接口(`tool/Tool.java`):`name / description / argsType / invoke`。
- [x] 2.3 新增 `AbstractTool<A>` 抽象基类(`tool/AbstractTool.java`):通过 `getGenericSuperclass()` 反射父类泛型实参实现 `argsType()`;拿不到时抛 `IllegalStateException` 并给出可操作错误信息(提示作者直接实现 `Tool<A>`)。
- [x] 2.4 改造 `ToolDefinition`:改成不可变值对象,构造函数接受 `name / description / inputSchema`。

## 3. DefaultRegistry 重写

- [x] 3.1 私有构造 + `getInstance()` 静态方法(单例)。
- [x] 3.2 构造函数中:
  - Reflections 扫 `com.zivyou.zivclaw` 下 `@AgentTool` 类。
  - 校验每个类实现 `Tool`;不实现 → `IllegalStateException`。
  - `getDeclaredConstructor().newInstance()`;失败 → 包装原因抛出。
  - name 冲突 → `IllegalStateException` 并列出两个冲突类。
  - 生成 `ToolDefinition` 时,`inputSchema` 通过 victools 的 `SchemaGenerator` 从 `argsType()` 生成 JSON 字符串。
  - 结果冻结到不可变 `Map` / `List`。
  - **schema 生成器初始化 & 调用处必须写一段中文块注释,说明:(a) 为什么 schema 不进注解、不由 Tool 手写(避免 POJO 与 schema 双写漂移);(b) 生成器读的是 argsType() 返回的 Class,作者只维护 POJO 字段;(c) 用的是哪个 JSON Schema draft,以及 module-jackson 让它认得 Jackson 注解。**
- [x] 3.3 实现 `getAvailableTools()`:返回缓存的不可变 `List`。
- [x] 3.4 实现 `execute(Context, ToolCall)`:
  - 按 `call.function.name` 查 tool;缺失 → `ToolResult.error(...)`。
  - 用一个私有泛型方法 `<A> A parseArgs(Tool<A> t, String json)` 反序列化,规避通配符编译问题。
  - 捕 tool 内异常并包成错误结果,附上 `toolCallId`。
- [x] 3.5 扫到 0 个 tool 时打 warn 日志,但不抛异常。

## 4. 集成 & 清扫

- [x] 4.1 检查 `ReActAgent` 如何拿到 `Registry`:如需从外部注入切换到 `DefaultRegistry.getInstance()`,做最小改动。
- [x] 4.2 如果 `ToolResult` 缺 `error(String message, String toolCallId)` 工厂方法,补一个(纯加法,不破坏原有 API)。
- [x] 4.3 删除旧的 `loadAgentTools()` / `loadAgentTool(String)` 骨架。

## 5. 验证

- [x] 5.1 写一个测试用 `@AgentTool` 类(放在 `src/test` 下的一个测试子包,或用一个存根业务包),继承 `AbstractTool<某 POJO>`,断言:
  - `DefaultRegistry.getInstance().getAvailableTools()` 能找到它。
  - `ToolDefinition.inputSchema` 是合法 JSON 且包含 POJO 的字段名。
  - `execute()` 能正确反序列化并调用 `invoke`。
- [x] 5.2 写一个失败路径测试:两个工具同名 → 构造 Registry 抛异常。
- [x] 5.3 `mvn -q test` 全绿。

## 6. 文档

- [x] 6.1 在 `AbstractTool` 类头写一段中文 Javadoc,示例代码演示"最小工具"的写法(继承 + `@AgentTool` + POJO)。
- [x] 6.2 在 `AgentTool` 注解上加中文 Javadoc,写明 `name` / `description` 的语义,以及"schema 由 Registry 自动生成,不需要在这里写"。
