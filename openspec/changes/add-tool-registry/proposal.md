# Proposal: add-tool-registry

## Why

`DefaultRegistry` 目前是一个空壳:`loadAgentTools()` 里 `getResources("com.zivyou.zivclaw.tool")` 传的是包名而不是路径,永远返回空;`execute()` 也直接返回 `null`。`ReActAgent` 的整个 tool-use 回路依赖它,现在跑起来会拿不到任何工具、任何工具调用都会失败。

我们需要一个"手搓的 IoC/工具注册表",满足:

- 只做工具装配这一件事,不引 Spring / Spring Boot。
- 工具作者写一个类 + 一个参数 POJO 就能被 `ReActAgent` 使用,无需在任何地方登记。
- LLM 看到的 `inputSchema` 永远和 POJO 同步,不需要人肉双写。

## What Changes

- **BREAKING**: `AgentTool` 注解从 `@Target(METHOD)` 改为 `@Target(TYPE)`,并增加 `name()` / `description()` 字段。
- **BREAKING**: `ToolDefinition` 由无参 DTO 变为带构造/字段的不可变值对象(`name` / `description` / `inputSchema`)。
- 新增 `Tool<A>` 接口:定义 `name / description / inputSchema / argsType / invoke` 五件套。
- 新增 `AbstractTool<A>` 抽象基类:通过反射父类泛型自动实现 `argsType()`,工具作者只需继承并实现 `invoke`。
- 重写 `DefaultRegistry`:
  - 单例(私有构造 + `getInstance()`)。
  - 构造时用 Reflections 扫描 `com.zivyou.zivclaw` 下所有 `@AgentTool` 类,`newInstance()` 后放入 `Map<String, Tool<?>>`。
  - `inputSchema` 通过 `victools/jsonschema-generator` 从 `argsType()` 自动生成。**该处以中文注释说明为什么这么做、和 `@AgentTool` 的关系、以及作者只需要维护 POJO 字段即可保持 schema 同步。**
  - `execute()` 用 Jackson 把 `ToolCall.function.arguments` 反序列化到 `tool.argsType()`,再调 `tool.invoke(ctx, args)`。
  - name 冲突 / 构造异常 / 缺无参构造 → 立刻抛异常,失败在启动期。
- 在 `pom.xml` 引入 `com.fasterxml.jackson.core:jackson-databind` 和 `com.github.victools:jsonschema-generator`。

## Impact

- Affected specs: **tool-registry** (ADDED)
- Affected code:
  - `src/main/java/com/zivyou/zivclaw/tool/AgentTool.java`  (BREAKING: target + 新字段)
  - `src/main/java/com/zivyou/zivclaw/tool/ToolDefinition.java`  (BREAKING: 结构变化)
  - `src/main/java/com/zivyou/zivclaw/tool/DefaultRegistry.java`  (重写)
  - `src/main/java/com/zivyou/zivclaw/tool/Tool.java`  (NEW)
  - `src/main/java/com/zivyou/zivclaw/tool/AbstractTool.java`  (NEW)
  - `src/main/java/com/zivyou/zivclaw/ReActAgent.java`  (Registry 注入方式可能微调)
  - `pom.xml`  (新增两个依赖)
- 现在没有工具实现类;首个改动落地时 Registry 扫到 0 个 tool 是合法状态(打日志提示,不抛异常),后续 change 再补首批工具。
