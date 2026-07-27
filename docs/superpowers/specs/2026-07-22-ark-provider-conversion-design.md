# ArkProvider 消息转换设计

## 背景与目标

`ArkProvider` 需要在 ziv-claw 的本地消息模型与 Volcengine Ark SDK 2.0.19 的聊天消息模型之间进行双向转换。本次实现采用 Ark 当前的 `tool_calls` / `tool_call_id` 工具调用协议，并同步改造本地模型，使工具执行结果具有明确的 `TOOL` 角色。

目标如下：

- 完整转换普通文本消息及新版工具调用消息。
- 保持本地领域模型不依赖 Ark SDK 类型。
- 对不受支持或可能导致数据丢失的输入立即给出明确异常。
- 使用无网络依赖的单元测试验证转换行为。

## 范围

本次改动包括：

- 实现 `ArkProvider` 中的两个 `convert` 方法。
- 增加双向角色、工具调用和函数调用转换辅助方法。
- 为本地 `Role` 增加 `TOOL`。
- 将 `ReActAgent` 创建的工具结果消息改为 `Role.TOOL`。
- 为本地 `ToolCall` 和 `Function` 增加 Builder，以便构造转换结果。
- 增加 JUnit 5 单元测试及必要的测试依赖。

本次不包括：

- Ark 旧版 `FUNCTION` / `function_call` 协议兼容。
- 多模态内容支持。
- Ark `reasoningContent` 或 `encryptedContent` 的本地建模。
- 流式响应以及 `ChatToolCall.index` 的本地建模。
- 对函数参数 JSON 内容或工具类型值进行业务校验。

## 架构与职责

`ArkProvider` 是 Ark SDK 模型与本地域模型之间唯一的适配边界：

- `convert(ChatMessage)` 将 Ark 响应转换为本地 `Message`。
- `convert(Message)` 将本地消息转换为 Ark `ChatMessage`。
- 私有辅助方法负责以下类型的双向转换：
  - `Role` 与 `ChatMessageRole`
  - `ToolCall` 与 `ChatToolCall`
  - `Function` 与 `ChatFunctionCall`

本地消息类不引用 Ark SDK 类型。两个顶层 `convert` 方法调整为包级 `static`，供同包测试直接调用；辅助方法保持私有。

## 模型调整

### Role

本地角色集合调整为：

- `SYSTEM`
- `USER`
- `ASSISTANT`
- `TOOL`

`ReActAgent` 在工具执行成功后创建 `Role.TOOL` 消息，并设置对应的 `toolCallId` 和文本结果。

### ToolCall 与 Function

为两个类增加 Lombok `@Builder`，转换器使用 Builder 构造完整对象：

- `ToolCall`: `id`、`type`、`function`
- `Function`: `name`、`arguments`

不增加 Ark 特有的 `index` 字段。

## 数据流

### Ark 到本地

1. 校验输入 `ChatMessage` 非空。
2. 若 `functionCall` 非空，拒绝旧版协议。
3. 将 Ark 角色显式映射为本地角色；`FUNCTION` 角色不受支持。
4. 处理内容：
   - `null` 保持为 `null`。
   - `String` 原样复制。
   - 其他类型视为当前不支持的多模态内容并拒绝。
5. 复制 `name` 和 `toolCallId`。
6. 将每个 `ChatToolCall` 及其 `ChatFunctionCall` 转换成本地对象。
7. 本地 `refusal` 保持为 `null`。

### 本地到 Ark

1. 校验输入 `Message` 非空。
2. 若本地 `refusal` 非空，拒绝转换，避免静默丢失。
3. 将四种受支持的本地角色显式映射为 Ark 角色。
4. 复制 `content`、`name` 和 `toolCallId`。
5. 将每个本地 `ToolCall` 及其 `Function` 转换为 Ark 对象。
6. 不写入旧版 `functionCall` 字段。

两个方向都保留 `toolCalls == null` 与空列表之间的区别。

## 错误处理

转换失败统一抛出 `IllegalArgumentException`，异常信息指出具体字段或不受支持的能力。

以下情况明确拒绝：

- 顶层消息为 `null`。
- 消息角色为 `null`。
- Ark 消息使用 `FUNCTION` 角色。
- Ark 消息包含旧版 `function_call`。
- Ark `content` 非空且不是字符串。
- 本地 `refusal` 非空。
- 工具调用列表中存在 `null` 元素。
- 工具调用的函数对象为 `null`。

转换层不解析 `arguments` 是否为有效 JSON，也不强制 `type` 必须等于 `function`；这些规则由 Ark 服务端或更高层业务负责。

## 测试方案

新增同包 JUnit 5 单元测试，不实例化 `ArkProvider`，不调用网络，也不依赖 `ARK_AGENT_KEY`。

测试覆盖：

- `SYSTEM`、`USER`、`ASSISTANT`、`TOOL` 四种角色的双向映射。
- 普通文本消息的 `content` 与 `name`。
- assistant 工具调用的 `id`、`type`、函数名和参数。
- `TOOL` 工具结果的 `toolCallId` 与文本内容。
- `null` 和空 `toolCalls` 列表的保留。
- 所有已定义的非法输入和不支持能力。

在 `pom.xml` 中固定 JUnit Jupiter 测试依赖，并确保 Maven Surefire 能执行 JUnit 5 测试。

## 验收标准

- 两个方向的消息与工具调用字段均按本设计转换。
- `ReActAgent` 使用 `Role.TOOL` 表示工具结果。
- 不受支持的旧协议、多模态内容和本地拒绝内容不会被静默丢弃。
- `mvn test` 成功，所有新增测试通过且项目编译成功。
