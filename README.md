# ZivClaw

[English](README.en.md) | 中文

一个使用 Java 编写、**零框架**的 AI Agent —— 不依赖 Spring 等任何框架，徒手实现 ReAct 循环、工具注册、上下文压缩与会话管理，通过火山引擎 Ark SDK 驱动大模型完成任务。

## 特性

- **ReAct Agent 循环**：思考 → 调用工具 → 观察结果 → 再思考，直到任务完成自动退出
- **声明式工具系统**：一个 `@AgentTool` 注解 + 一个参数 POJO 即可定义工具；classpath 自动扫描注册，JSON Schema 由参数 POJO 自动生成，永不会出现"改了 POJO 忘了改 schema"的双写漂移
- **内置工具**：`Bash`（执行命令）、`Read`（读文件）、`Write`（写文件）、`Edit`（文本块替换）
- **并行工具执行**：一轮内的多个工具调用由线程池并发执行
- **渐进式上下文压缩**：超长消息自动压缩并将原文外存到 `.ziv-claw/compacted/`，模型可通过 Read 工具自助取回；仍超长则按"轮"丢弃早期历史
- **会话持久化**：会话历史落盘到 `./.session/<sessionId>`，支持断点续聊
- **可插拔 Provider**：当前实现基于火山引擎 Ark（`ark-code-latest`），`Provider` 接口可替换为其他模型服务

## 环境要求

- JDK 17+
- Maven 3.x
- 火山引擎 Ark API Key

## 快速开始

```bash
# 1. 配置 API Key
export ARK_AGENT_KEY="your-ark-api-key"

# 2. 构建
mvn clean package

# 3. 运行（修改 Application.java 中的 userPrompt 后执行）
mvn compile exec:java -Dexec.mainClass=com.zivyou.zivclaw.Application
```

入口类为 `com.zivyou.zivclaw.Application`，在其中设置你的任务提示词（`userPrompt`）即可启动 Agent。

## 架构

```
com.zivyou.zivclaw
├── Application          # 入口
├── ReActAgent           # ReAct 主循环：think → act → observe
├── provider/            # 模型服务抽象（ArkProvider）
├── registry/            # 手搓 IoC：@AgentTool 扫描、Schema 生成、工具分发
│   ├── Tool / AbstractTool / AgentTool
│   └── DefaultRegistry
├── tool/                # 内置工具：Bash / Read / Write / Edit
├── context/             # AgentContext 与 ContextCompactor（上下文压缩）
├── session/             # 会话管理与持久化
├── prompt/              # System Prompt 组装
├── message/             # Message / ToolCall / Role 协议模型
└── reporter/            # 输出上报（ConsoleReporter）
```

## 自定义工具

只需两步：继承 `AbstractTool<A>`，加上 `@AgentTool` 注解。注册、Schema 生成、参数反序列化全部自动完成：

```java
@AgentTool(name = "read_file", description = "读取本地文件的内容")
public class ReadFileTool extends AbstractTool<ReadFileTool.Args> {
    public static class Args {
        @JsonProperty(required = true)
        public String path;
        public Integer limit;
    }

    @Override
    public ToolResult invoke(AgentContext ctx, Args args) {
        // args 已是反序列化好的强类型 POJO
        return ToolResult.ok(...);
    }
}
```

约束：工具类必须是命名的具体类（不能是匿名类 / lambda），并提供 public 无参构造函数。

## 运行原理

1. `DefaultPromptComposer` 组装 system prompt，`SessionManager` 加载/创建会话并恢复工作记忆
2. 每轮循环：`ContextCompactor` 按需压缩历史 → `Provider` 请求模型 → 若无工具调用则任务完成
3. 有工具调用时，线程池并发执行所有工具，结果作为 `TOOL` 消息回灌，进入下一轮思考
4. 每轮结束会话落盘；进程退出时通过 shutdown hook 优雅关闭线程池与 Provider

## 路线图

- [ ] 交互式 TUI 与多轮对话（见 [docs/interactive-tui-plan.md](docs/interactive-tui-plan.md)）
- [ ] 更多内置工具与 MCP 支持

## 贡献

Feel free to download / contribute。如果发现了问题，欢迎提 issue，或发邮件至 yzq529@qq.com。

## License

见 [LICENSE](LICENSE)。
