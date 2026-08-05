# ArkProvider 消息转换 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `ArkProvider` 中完整实现本地 `Message` 与 Volcengine Ark SDK 2.0.19 `ChatMessage` 之间的双向转换，采用新版 `tool_calls` / `tool_call_id` 工具调用协议。

**Architecture:** 顶层两个 `convert` 方法调整为包级 `static`，负责组装顶层消息；三组私有辅助方法分别负责角色、工具调用、函数调用的双向映射。本地 `Role` 增加 `TOOL`，`ReActAgent` 使用 `Role.TOOL` 表示工具执行结果；本地 `ToolCall` 与 `Function` 添加 `@Builder` 以便构造转换结果。旧版 `FUNCTION`/`function_call`、多模态内容、本地 `refusal` 及缺失工具调用字段一律通过 `IllegalArgumentException` 明确拒绝。

**Tech Stack:** Java 17, Maven, Lombok 1.18.44, Volcengine Ark SDK 2.0.19, JUnit Jupiter 5.10.2, Maven Surefire 3.2.5。

---

## 文件结构

- 修改：`pom.xml` — 增加 JUnit Jupiter 测试依赖与 Surefire 配置。
- 修改：`src/main/java/com/zivyou/zivclaw/message/Role.java` — 增加 `TOOL`。
- 修改：`src/main/java/com/zivyou/zivclaw/message/ToolCall.java` — 添加 `@Builder`。
- 修改：`src/main/java/com/zivyou/zivclaw/message/Function.java` — 添加 `@Builder`。
- 修改：`src/main/java/com/zivyou/zivclaw/ReActAgent.java` — 工具结果消息改用 `Role.TOOL`。
- 修改：`src/main/java/com/zivyou/zivclaw/provider/ArkProvider.java` — 实现两个 `convert` 及辅助方法。
- 新建：`src/test/java/com/zivyou/zivclaw/provider/ArkProviderConvertTest.java` — 转换行为单元测试。

---

## Task 1: 配置 JUnit 5 测试环境

**Files:**
- Modify: `pom.xml`
- Create: `src/test/java/com/zivyou/zivclaw/provider/SanityCheckTest.java`

- [ ] **Step 1: 在 `pom.xml` 中加入测试依赖与 Surefire 配置**

将 `pom.xml` 的完整内容替换为：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>com.zivyou</groupId>
  <artifactId>zivclaw</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <packaging>jar</packaging>

  <properties>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <junit.jupiter.version>5.10.2</junit.jupiter.version>
    <maven.surefire.version>3.2.5</maven.surefire.version>
  </properties>

  <dependencies>
    <dependency>
      <groupId>org.projectlombok</groupId>
      <artifactId>lombok</artifactId>
      <version>1.18.44</version>
    </dependency>
    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-log4j12</artifactId>
      <version>2.0.7</version>
    </dependency>
    <dependency>
      <groupId>org.apache.commons</groupId>
      <artifactId>commons-collections4</artifactId>
      <version>4.4</version>
    </dependency>
    <dependency>
      <groupId>com.volcengine</groupId>
      <artifactId>volcengine-java-sdk-ark-runtime</artifactId>
      <version>LATEST</version>
    </dependency>
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <version>${junit.jupiter.version}</version>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-plugin</artifactId>
        <version>${maven.surefire.version}</version>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 2: 写一个最小的 Sanity 测试**

新建 `src/test/java/com/zivyou/zivclaw/provider/SanityCheckTest.java`：

```java
package com.zivyou.zivclaw.provider;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SanityCheckTest {
    @Test
    void junitJupiterIsWired() {
        assertEquals(2, 1 + 1);
    }
}
```

- [ ] **Step 3: 运行测试**

Run: `mvn -q test`
Expected: BUILD SUCCESS，`SanityCheckTest` 被执行且通过。

- [ ] **Step 4: 提交**

```bash
git add pom.xml src/test/java/com/zivyou/zivclaw/provider/SanityCheckTest.java
git commit -m "build: add JUnit 5 test dependency and surefire config"
```

---

## Task 2: 引入 `Role.TOOL` 并调整本地模型

**Files:**
- Modify: `src/main/java/com/zivyou/zivclaw/message/Role.java`
- Modify: `src/main/java/com/zivyou/zivclaw/message/ToolCall.java`
- Modify: `src/main/java/com/zivyou/zivclaw/message/Function.java`
- Modify: `src/main/java/com/zivyou/zivclaw/ReActAgent.java`

- [ ] **Step 1: 编写一个失败的测试锁定新的模型能力**

在 `src/test/java/com/zivyou/zivclaw/provider/SanityCheckTest.java` 中追加，先编写编译级别验证：将文件替换为

```java
package com.zivyou.zivclaw.provider;

import com.zivyou.zivclaw.message.Function;
import com.zivyou.zivclaw.message.Role;
import com.zivyou.zivclaw.message.ToolCall;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SanityCheckTest {
    @Test
    void junitJupiterIsWired() {
        assertEquals(2, 1 + 1);
    }

    @Test
    void localModelSupportsToolSemantics() {
        Role toolRole = Role.TOOL;
        ToolCall tc = ToolCall.builder()
                .id("call_1")
                .type("function")
                .function(Function.builder().name("echo").arguments("{\"v\":1}").build())
                .build();
        assertNotNull(toolRole);
        assertEquals("call_1", tc.getId());
        assertEquals("function", tc.getType());
        assertEquals("echo", tc.getFunction().getName());
        assertEquals("{\"v\":1}", tc.getFunction().getArguments());
    }
}
```

- [ ] **Step 2: 运行以确认测试因缺少枚举与 builder 而失败**

Run: `mvn -q test-compile`
Expected: 编译失败，报 `Role.TOOL`、`ToolCall.builder()`、`Function.builder()` 找不到。

- [ ] **Step 3: 更新 `Role` 增加 `TOOL`**

将 `src/main/java/com/zivyou/zivclaw/message/Role.java` 替换为：

```java
package com.zivyou.zivclaw.message;

public enum Role {
    SYSTEM, ASSISTANT, USER, TOOL
}
```

- [ ] **Step 4: 为 `ToolCall` 增加 `@Builder`**

将 `src/main/java/com/zivyou/zivclaw/message/ToolCall.java` 替换为：

```java
package com.zivyou.zivclaw.message;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ToolCall {
    String id;
    String type;
    Function function;
}
```

- [ ] **Step 5: 为 `Function` 增加 `@Builder`**

将 `src/main/java/com/zivyou/zivclaw/message/Function.java` 替换为：

```java
package com.zivyou.zivclaw.message;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class Function {
    String name;
    String arguments;
}
```

- [ ] **Step 6: 让 `ReActAgent` 使用 `Role.TOOL` 表示工具结果**

编辑 `src/main/java/com/zivyou/zivclaw/ReActAgent.java`，将第 65 行的工具结果消息构造改为 `Role.TOOL`。定位并替换：

```java
                    var message = Message.builder().role(Role.USER).toolCallId(result.getToolCallId()).content(result.getOutput()).build();
```

替换为：

```java
                    var message = Message.builder().role(Role.TOOL).toolCallId(result.getToolCallId()).content(result.getOutput()).build();
```

- [ ] **Step 7: 运行测试验证模型改动正确**

Run: `mvn -q test`
Expected: BUILD SUCCESS，两个 Sanity 测试通过。

- [ ] **Step 8: 提交**

```bash
git add src/main/java/com/zivyou/zivclaw/message/Role.java \
        src/main/java/com/zivyou/zivclaw/message/ToolCall.java \
        src/main/java/com/zivyou/zivclaw/message/Function.java \
        src/main/java/com/zivyou/zivclaw/ReActAgent.java \
        src/test/java/com/zivyou/zivclaw/provider/SanityCheckTest.java
git commit -m "feat(message): add Role.TOOL and builders for ToolCall/Function"
```

---

## Task 3: Ark → 本地 转换 (TDD)

**Files:**
- Create: `src/test/java/com/zivyou/zivclaw/provider/ArkProviderConvertTest.java`
- Modify: `src/main/java/com/zivyou/zivclaw/provider/ArkProvider.java`

- [ ] **Step 1: 编写 Ark→本地 的全部失败测试**

新建 `src/test/java/com/zivyou/zivclaw/provider/ArkProviderConvertTest.java`：

```java
package com.zivyou.zivclaw.provider;

import com.volcengine.ark.runtime.model.completion.chat.ChatFunctionCall;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import com.volcengine.ark.runtime.model.completion.chat.ChatToolCall;
import com.zivyou.zivclaw.message.Function;
import com.zivyou.zivclaw.message.Message;
import com.zivyou.zivclaw.message.Role;
import com.zivyou.zivclaw.message.ToolCall;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArkProviderConvertTest {

    // --- Ark -> local ---

    @Test
    void arkToLocal_mapsPlainAssistantMessage() {
        ChatMessage src = ChatMessage.builder()
                .role(ChatMessageRole.ASSISTANT)
                .content("hello")
                .name("bot")
                .build();

        Message out = ArkProvider.convert(src);

        assertEquals(Role.ASSISTANT, out.getRole());
        assertEquals("hello", out.getContent());
        assertEquals("bot", out.getName());
        assertNull(out.getToolCalls());
        assertNull(out.getToolCallId());
        assertNull(out.getRefusal());
    }

    @Test
    void arkToLocal_mapsAllSupportedRoles() {
        assertEquals(Role.SYSTEM,
                ArkProvider.convert(ChatMessage.builder().role(ChatMessageRole.SYSTEM).content("s").build()).getRole());
        assertEquals(Role.USER,
                ArkProvider.convert(ChatMessage.builder().role(ChatMessageRole.USER).content("u").build()).getRole());
        assertEquals(Role.ASSISTANT,
                ArkProvider.convert(ChatMessage.builder().role(ChatMessageRole.ASSISTANT).content("a").build()).getRole());
        assertEquals(Role.TOOL,
                ArkProvider.convert(ChatMessage.builder().role(ChatMessageRole.TOOL).content("t").toolCallId("call_1").build()).getRole());
    }

    @Test
    void arkToLocal_mapsToolResultWithToolCallId() {
        ChatMessage src = ChatMessage.builder()
                .role(ChatMessageRole.TOOL)
                .toolCallId("call_42")
                .content("42")
                .build();

        Message out = ArkProvider.convert(src);

        assertEquals(Role.TOOL, out.getRole());
        assertEquals("call_42", out.getToolCallId());
        assertEquals("42", out.getContent());
    }

    @Test
    void arkToLocal_mapsAssistantToolCalls() {
        ChatToolCall tc = new ChatToolCall("call_1", "function", new ChatFunctionCall("echo", "{\"v\":1}"));
        ChatMessage src = ChatMessage.builder()
                .role(ChatMessageRole.ASSISTANT)
                .toolCalls(List.of(tc))
                .build();

        Message out = ArkProvider.convert(src);

        assertNotNull(out.getToolCalls());
        assertEquals(1, out.getToolCalls().size());
        ToolCall got = out.getToolCalls().get(0);
        assertEquals("call_1", got.getId());
        assertEquals("function", got.getType());
        assertEquals("echo", got.getFunction().getName());
        assertEquals("{\"v\":1}", got.getFunction().getArguments());
    }

    @Test
    void arkToLocal_preservesEmptyToolCallsList() {
        ChatMessage src = ChatMessage.builder()
                .role(ChatMessageRole.ASSISTANT)
                .content("")
                .toolCalls(List.of())
                .build();

        Message out = ArkProvider.convert(src);

        assertNotNull(out.getToolCalls());
        assertTrue(out.getToolCalls().isEmpty());
    }

    @Test
    void arkToLocal_rejectsNullMessage() {
        assertThrows(IllegalArgumentException.class, () -> ArkProvider.convert((ChatMessage) null));
    }

    @Test
    void arkToLocal_rejectsNullRole() {
        ChatMessage src = ChatMessage.builder().content("x").build();
        assertThrows(IllegalArgumentException.class, () -> ArkProvider.convert(src));
    }

    @Test
    void arkToLocal_rejectsLegacyFunctionRole() {
        ChatMessage src = ChatMessage.builder()
                .role(ChatMessageRole.FUNCTION)
                .content("x")
                .build();
        assertThrows(IllegalArgumentException.class, () -> ArkProvider.convert(src));
    }

    @Test
    void arkToLocal_rejectsLegacyFunctionCall() {
        ChatMessage src = ChatMessage.builder()
                .role(ChatMessageRole.ASSISTANT)
                .functionCall(new ChatFunctionCall("legacy", "{}"))
                .build();
        assertThrows(IllegalArgumentException.class, () -> ArkProvider.convert(src));
    }

    @Test
    void arkToLocal_rejectsNonStringContent() {
        ChatMessage src = ChatMessage.builder().role(ChatMessageRole.USER).build();
        src.setContent(List.of("multimodal-part"));
        assertThrows(IllegalArgumentException.class, () -> ArkProvider.convert(src));
    }

    @Test
    void arkToLocal_rejectsNullToolCallElement() {
        ChatMessage src = ChatMessage.builder()
                .role(ChatMessageRole.ASSISTANT)
                .toolCalls(java.util.Collections.singletonList(null))
                .build();
        assertThrows(IllegalArgumentException.class, () -> ArkProvider.convert(src));
    }

    @Test
    void arkToLocal_rejectsToolCallWithoutFunction() {
        ChatToolCall tc = new ChatToolCall("call_1", "function", null);
        ChatMessage src = ChatMessage.builder()
                .role(ChatMessageRole.ASSISTANT)
                .toolCalls(List.of(tc))
                .build();
        assertThrows(IllegalArgumentException.class, () -> ArkProvider.convert(src));
    }
}
```

- [ ] **Step 2: 运行确认测试失败**

Run: `mvn -q -Dtest=ArkProviderConvertTest test`
Expected: 编译或测试失败，因为 `ArkProvider.convert` 目前返回 `null`，静态可见性为 `private`。

- [ ] **Step 3: 实现 `convert(ChatMessage)` 与相关辅助方法**

将 `src/main/java/com/zivyou/zivclaw/provider/ArkProvider.java` 完整替换为：

```java
package com.zivyou.zivclaw.provider;

import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.model.completion.chat.ChatFunctionCall;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import com.volcengine.ark.runtime.model.completion.chat.ChatToolCall;
import com.volcengine.ark.runtime.service.ArkService;
import com.zivyou.zivclaw.context.AgentContext;
import com.zivyou.zivclaw.message.Function;
import com.zivyou.zivclaw.message.Message;
import com.zivyou.zivclaw.message.Role;
import com.zivyou.zivclaw.message.ToolCall;
import com.zivyou.zivclaw.registry.ToolDefinition;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class ArkProvider implements Provider, AutoCloseable {
    private final String baseUrl = "https://ark.cn-beijing.volces.com/api/v3";
    private final String apiKey = System.getenv("ARK_AGENT_KEY");
    private final ArkService arkService;

    public ArkProvider() {
        this.arkService = ArkService.builder().baseUrl(baseUrl)
                .apiKey(apiKey)
                .timeout(Duration.ofSeconds(1800))
                .connectTimeout(Duration.ofSeconds(20))
                .retryTimes(2)
                .build();
    }

    @Override
    public Message generate(AgentContext agentContext, List<Message> messages, List<ToolDefinition> toolDefinitions) {
        ChatCompletionRequest request = ChatCompletionRequest.builder().build();
        var response = arkService.createChatCompletion(request).getChoices().get(0).getMessage();
        return convert(response);
    }

    static Message convert(ChatMessage chatMessage) {
        if (chatMessage == null) {
            throw new IllegalArgumentException("chatMessage must not be null");
        }
        if (chatMessage.getFunctionCall() != null) {
            throw new IllegalArgumentException("legacy function_call is not supported");
        }
        Role role = toLocalRole(chatMessage.getRole());
        String content = toLocalContent(chatMessage.getContent());

        List<ToolCall> localToolCalls = null;
        List<ChatToolCall> srcToolCalls = chatMessage.getToolCalls();
        if (srcToolCalls != null) {
            localToolCalls = new ArrayList<>(srcToolCalls.size());
            for (ChatToolCall tc : srcToolCalls) {
                localToolCalls.add(toLocalToolCall(tc));
            }
        }

        return Message.builder()
                .role(role)
                .content(content)
                .name(chatMessage.getName())
                .toolCalls(localToolCalls)
                .toolCallId(chatMessage.getToolCallId())
                .build();
    }

    static ChatMessage convert(Message message) {
        return null;
    }

    private static Role toLocalRole(ChatMessageRole role) {
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }
        return switch (role) {
            case SYSTEM -> Role.SYSTEM;
            case USER -> Role.USER;
            case ASSISTANT -> Role.ASSISTANT;
            case TOOL -> Role.TOOL;
            case FUNCTION -> throw new IllegalArgumentException("legacy FUNCTION role is not supported");
        };
    }

    private static String toLocalContent(Object content) {
        if (content == null) {
            return null;
        }
        if (content instanceof String s) {
            return s;
        }
        throw new IllegalArgumentException(
                "non-string content is not supported (got " + content.getClass().getName() + ")");
    }

    private static ToolCall toLocalToolCall(ChatToolCall arkToolCall) {
        if (arkToolCall == null) {
            throw new IllegalArgumentException("tool call must not be null");
        }
        ChatFunctionCall arkFunction = arkToolCall.getFunction();
        if (arkFunction == null) {
            throw new IllegalArgumentException("tool call function must not be null");
        }
        return ToolCall.builder()
                .id(arkToolCall.getId())
                .type(arkToolCall.getType())
                .function(Function.builder()
                        .name(arkFunction.getName())
                        .arguments(arkFunction.getArguments())
                        .build())
                .build();
    }

    @Override
    public void close() throws Exception {
        if (arkService != null) {
            arkService.shutdownExecutor();
        }
    }
}
```

- [ ] **Step 4: 运行测试确认 Ark→本地 场景全部通过**

Run: `mvn -q -Dtest=ArkProviderConvertTest test`
Expected: 上述 11 个 Ark→本地 测试全部通过；`convert(Message)` 未实现，任何相关测试目前不存在，无需通过。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/zivyou/zivclaw/provider/ArkProvider.java \
        src/test/java/com/zivyou/zivclaw/provider/ArkProviderConvertTest.java
git commit -m "feat(provider): implement Ark ChatMessage -> local Message conversion"
```

---

## Task 4: 本地 → Ark 转换 (TDD)

**Files:**
- Modify: `src/test/java/com/zivyou/zivclaw/provider/ArkProviderConvertTest.java`
- Modify: `src/main/java/com/zivyou/zivclaw/provider/ArkProvider.java`

- [ ] **Step 1: 在测试类底部追加 本地→Ark 的失败测试**

在 `ArkProviderConvertTest` 中，将最后一个 `}` 之前追加以下测试方法：

```java
    // --- local -> Ark ---

    @Test
    void localToArk_mapsPlainAssistantMessage() {
        Message src = Message.builder()
                .role(Role.ASSISTANT)
                .content("hi")
                .name("bot")
                .build();

        ChatMessage out = ArkProvider.convert(src);

        assertEquals(ChatMessageRole.ASSISTANT, out.getRole());
        assertEquals("hi", out.getContent());
        assertEquals("bot", out.getName());
        assertNull(out.getToolCalls());
        assertNull(out.getToolCallId());
        assertNull(out.getFunctionCall());
    }

    @Test
    void localToArk_mapsAllSupportedRoles() {
        assertEquals(ChatMessageRole.SYSTEM,
                ArkProvider.convert(Message.builder().role(Role.SYSTEM).content("s").build()).getRole());
        assertEquals(ChatMessageRole.USER,
                ArkProvider.convert(Message.builder().role(Role.USER).content("u").build()).getRole());
        assertEquals(ChatMessageRole.ASSISTANT,
                ArkProvider.convert(Message.builder().role(Role.ASSISTANT).content("a").build()).getRole());
        assertEquals(ChatMessageRole.TOOL,
                ArkProvider.convert(Message.builder().role(Role.TOOL).content("t").toolCallId("call_1").build()).getRole());
    }

    @Test
    void localToArk_mapsToolResultWithToolCallId() {
        Message src = Message.builder()
                .role(Role.TOOL)
                .toolCallId("call_9")
                .content("done")
                .build();

        ChatMessage out = ArkProvider.convert(src);

        assertEquals(ChatMessageRole.TOOL, out.getRole());
        assertEquals("call_9", out.getToolCallId());
        assertEquals("done", out.getContent());
    }

    @Test
    void localToArk_mapsAssistantToolCalls() {
        ToolCall tc = ToolCall.builder()
                .id("call_1")
                .type("function")
                .function(Function.builder().name("echo").arguments("{\"v\":1}").build())
                .build();
        Message src = Message.builder()
                .role(Role.ASSISTANT)
                .toolCalls(List.of(tc))
                .build();

        ChatMessage out = ArkProvider.convert(src);

        assertNotNull(out.getToolCalls());
        assertEquals(1, out.getToolCalls().size());
        ChatToolCall got = out.getToolCalls().get(0);
        assertEquals("call_1", got.getId());
        assertEquals("function", got.getType());
        assertNotNull(got.getFunction());
        assertEquals("echo", got.getFunction().getName());
        assertEquals("{\"v\":1}", got.getFunction().getArguments());
    }

    @Test
    void localToArk_preservesEmptyToolCallsList() {
        Message src = Message.builder()
                .role(Role.ASSISTANT)
                .content("")
                .toolCalls(List.of())
                .build();

        ChatMessage out = ArkProvider.convert(src);

        assertNotNull(out.getToolCalls());
        assertTrue(out.getToolCalls().isEmpty());
    }

    @Test
    void localToArk_rejectsNullMessage() {
        assertThrows(IllegalArgumentException.class, () -> ArkProvider.convert((Message) null));
    }

    @Test
    void localToArk_rejectsNullRole() {
        Message src = Message.builder().content("x").build();
        assertThrows(IllegalArgumentException.class, () -> ArkProvider.convert(src));
    }

    @Test
    void localToArk_rejectsNonNullRefusal() {
        Message src = Message.builder()
                .role(Role.ASSISTANT)
                .content("x")
                .refusal("nope")
                .build();
        assertThrows(IllegalArgumentException.class, () -> ArkProvider.convert(src));
    }

    @Test
    void localToArk_rejectsNullToolCallElement() {
        Message src = Message.builder()
                .role(Role.ASSISTANT)
                .toolCalls(java.util.Collections.singletonList(null))
                .build();
        assertThrows(IllegalArgumentException.class, () -> ArkProvider.convert(src));
    }

    @Test
    void localToArk_rejectsToolCallWithoutFunction() {
        ToolCall tc = ToolCall.builder().id("call_1").type("function").build();
        Message src = Message.builder()
                .role(Role.ASSISTANT)
                .toolCalls(List.of(tc))
                .build();
        assertThrows(IllegalArgumentException.class, () -> ArkProvider.convert(src));
    }
```

- [ ] **Step 2: 运行确认新增测试失败**

Run: `mvn -q -Dtest=ArkProviderConvertTest test`
Expected: 因为 `convert(Message)` 目前返回 `null`，新增的所有本地→Ark 测试失败（`NullPointerException` 或断言失败）。

- [ ] **Step 3: 实现 `convert(Message)` 及辅助方法**

编辑 `src/main/java/com/zivyou/zivclaw/provider/ArkProvider.java`，将 `static ChatMessage convert(Message message)` 方法体替换为：

```java
    static ChatMessage convert(Message message) {
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        if (message.getRefusal() != null) {
            throw new IllegalArgumentException("refusal is not supported by Ark ChatMessage");
        }

        List<ChatToolCall> arkToolCalls = null;
        List<ToolCall> srcToolCalls = message.getToolCalls();
        if (srcToolCalls != null) {
            arkToolCalls = new ArrayList<>(srcToolCalls.size());
            for (ToolCall tc : srcToolCalls) {
                arkToolCalls.add(toArkToolCall(tc));
            }
        }

        return ChatMessage.builder()
                .role(toArkRole(message.getRole()))
                .content(message.getContent())
                .name(message.getName())
                .toolCalls(arkToolCalls)
                .toolCallId(message.getToolCallId())
                .build();
    }
```

在 `toLocalToolCall` 之后追加两个新的辅助方法：

```java
    private static ChatMessageRole toArkRole(Role role) {
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }
        return switch (role) {
            case SYSTEM -> ChatMessageRole.SYSTEM;
            case USER -> ChatMessageRole.USER;
            case ASSISTANT -> ChatMessageRole.ASSISTANT;
            case TOOL -> ChatMessageRole.TOOL;
        };
    }

    private static ChatToolCall toArkToolCall(ToolCall toolCall) {
        if (toolCall == null) {
            throw new IllegalArgumentException("tool call must not be null");
        }
        Function fn = toolCall.getFunction();
        if (fn == null) {
            throw new IllegalArgumentException("tool call function must not be null");
        }
        return new ChatToolCall(
                toolCall.getId(),
                toolCall.getType(),
                new ChatFunctionCall(fn.getName(), fn.getArguments())
        );
    }
```

- [ ] **Step 4: 运行完整测试确认全部通过**

Run: `mvn -q test`
Expected: 全部测试通过，包括 `SanityCheckTest` 和 `ArkProviderConvertTest` 的所有 Ark→本地和本地→Ark 场景。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/zivyou/zivclaw/provider/ArkProvider.java \
        src/test/java/com/zivyou/zivclaw/provider/ArkProviderConvertTest.java
git commit -m "feat(provider): implement local Message -> Ark ChatMessage conversion"
```

---

## Task 5: 最终验证

**Files:** 无新文件。

- [ ] **Step 1: 清理并运行全部构建**

Run: `mvn -q clean test`
Expected: BUILD SUCCESS，所有测试通过；`Role.TOOL`、`ToolCall`/`Function` builder、`ReActAgent` 工具消息角色以及双向转换逻辑均按设计工作。

- [ ] **Step 2: 快速人工核对关键行**

Run: `grep -n "Role.TOOL" src/main/java/com/zivyou/zivclaw/ReActAgent.java`
Expected: 命中一行，位于原第 65 行工具结果消息构造处。

Run: `grep -nE "static (Message|ChatMessage) convert" src/main/java/com/zivyou/zivclaw/provider/ArkProvider.java`
Expected: 命中两个 `static` 方法定义，均为包级可见。

- [ ] **Step 3: 状态汇总**

Run: `git log --oneline -n 5`
Expected: 最近三条提交为 Task 2、Task 3、Task 4 的提交（外加 Task 1 的构建配置提交）。
