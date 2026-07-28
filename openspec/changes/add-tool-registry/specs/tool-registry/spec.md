# Spec Delta: tool-registry (ADDED)

## ADDED Requirements

### Requirement: Tool Discovery via Class Annotation

系统 SHALL 通过扫描 classpath 上标注了 `@AgentTool`(`@Target(TYPE)`)的类来发现工具,不需要任何显式注册代码。

#### Scenario: 扫描发现工具类

- **WHEN** `DefaultRegistry` 初始化
- **AND** 类路径下存在若干标注了 `@AgentTool` 且实现 `Tool<?>` 的类
- **THEN** 每个此类的一个实例 SHALL 被创建,并以 `Tool.name()` 为键放入注册表
- **AND** `getAvailableTools()` 的返回列表中,SHALL 包含每个工具的 `ToolDefinition`

#### Scenario: 类标注了 @AgentTool 但未实现 Tool

- **WHEN** 存在类 `X` 标注了 `@AgentTool` 但没有实现 `Tool<?>`
- **THEN** `DefaultRegistry` 初始化 SHALL 抛 `IllegalStateException`,错误信息包含类名 `X`

#### Scenario: 工具类缺少无参构造函数

- **WHEN** 某个 `@AgentTool` 类不存在可调用的无参构造函数
- **THEN** `DefaultRegistry` 初始化 SHALL 抛异常,原因链保留原始 `NoSuchMethodException` 或反射异常

### Requirement: 工具单例

系统 SHALL 保证每个被发现的工具类在 JVM 生命周期内只被实例化一次,并被后续所有 `execute()` 调用复用。

#### Scenario: 多次 execute 复用同一实例

- **WHEN** 同一工具被 `execute()` 调用 N 次
- **THEN** 该工具类的 `invoke` 方法所在实例 SHALL 是同一个对象(`==` 相等)

### Requirement: 工具名唯一性

系统 SHALL 拒绝在同一注册表中存在两个 `name()` 相同的工具。

#### Scenario: 名称冲突

- **WHEN** 两个不同的 `@AgentTool` 类返回相同的 `name()`
- **THEN** `DefaultRegistry` 初始化 SHALL 抛 `IllegalStateException`,错误信息包含冲突的名称和两个类的全限定名

### Requirement: InputSchema 自动派生

系统 SHALL 从工具的参数 POJO 类型(`Tool.argsType()` 返回值)自动生成 JSON Schema,并作为 `ToolDefinition.inputSchema` 暴露给上游。工具作者 SHALL NOT 手写 schema。

#### Scenario: 新增字段后 schema 自动更新

- **WHEN** 工具作者向参数 POJO 增加一个字段 `foo`
- **AND** 无其他改动
- **AND** 重新启动应用
- **THEN** 对应工具的 `ToolDefinition.inputSchema` SHALL 包含字段 `foo`

#### Scenario: schema 生成源码注释

- **WHEN** 维护者查看 `DefaultRegistry` 中生成 `inputSchema` 的代码块
- **THEN** 该处 SHALL 存在一段中文注释,说明:(a) 为什么不将 schema 写在 `@AgentTool` 注解或 Tool 接口方法中;(b) 生成器的输入是 `argsType()` 返回的 Class,作者只需要维护 POJO;(c) 所使用的 JSON Schema draft 与 module-jackson 的作用

### Requirement: 工具执行按名称派发

`Registry.execute(Context, ToolCall)` SHALL 按 `ToolCall.function.name` 定位工具,并把 `ToolCall.function.arguments`(JSON 字符串)反序列化为该工具 `argsType()` 声明的类型,再调用 `tool.invoke(ctx, args)`。

#### Scenario: 正常执行

- **GIVEN** 注册表中存在名为 `read_file` 的工具,其 `argsType()` 为 `ReadFileArgs.class`
- **WHEN** `execute(ctx, call)` 被调用,`call.function.name == "read_file"`,`call.function.arguments == '{"path":"/tmp/x"}'`
- **THEN** 系统 SHALL 用 Jackson 把 arguments 反序列化为 `ReadFileArgs { path = "/tmp/x" }`
- **AND** 调用 `tool.invoke(ctx, args)`,返回其 `ToolResult`

#### Scenario: 未知工具名

- **WHEN** `call.function.name` 在注册表中不存在
- **THEN** 系统 SHALL 返回一个失败态的 `ToolResult`,而不是抛异常;`toolCallId` 与请求一致

#### Scenario: 工具执行抛异常

- **WHEN** `tool.invoke` 抛出任意异常
- **THEN** 系统 SHALL 捕获异常并返回失败态 `ToolResult`,其中包含异常摘要与原始 `toolCallId`

### Requirement: 空注册表允许启动

即使类路径上不存在任何 `@AgentTool` 类,`DefaultRegistry` SHALL 正常初始化,不 SHALL 抛异常;`getAvailableTools()` SHALL 返回空列表。

#### Scenario: 类路径下无任何工具

- **WHEN** 类路径上不存在任何标注了 `@AgentTool` 的类
- **THEN** `DefaultRegistry.getInstance()` SHALL 返回一个可用实例
- **AND** `getAvailableTools()` SHALL 返回空列表
- **AND** 系统 SHALL 打印 warn 级别日志提示"未发现任何工具"
