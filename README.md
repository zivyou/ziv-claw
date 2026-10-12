# ZivClaw

[English](README.en.md) | 中文

一个使用 Java 编写、**零框架**的 AI Agent —— 不依赖 Spring 等任何框架，徒手实现 ReAct 循环、工具注册、上下文压缩与会话管理，通过可插拔的 Provider（任意 OpenAI 兼容服务或火山引擎 Ark）驱动大模型完成任务。

## 特性

- **ReAct Agent 循环**：思考 → 调用工具 → 观察结果 → 再思考，直到任务完成自动退出
- **声明式工具系统**：一个 `@AgentTool` 注解 + 一个参数 POJO 即可定义工具；classpath 自动扫描注册，JSON Schema 由参数 POJO 自动生成，永不会出现"改了 POJO 忘了改 schema"的双写漂移
- **内置工具**：`Bash`（执行命令）、`Read`（读文件）、`Write`（写文件）、`Edit`（文本块替换）
- **并行工具执行**：一轮内的多个工具调用由线程池并发执行
- **渐进式上下文压缩**：超长消息自动压缩并将原文外存到 `.ziv-claw/compacted/`，模型可通过 Read 工具自助取回；仍超长则按"轮"丢弃早期历史
- **会话持久化**：会话历史落盘到 `./.session/<sessionId>`，支持断点续聊
- **可插拔 Provider**：内置通用 **OpenAI 兼容 Provider**（DeepSeek / Moonshot / Qwen 兼容模式 / OpenRouter / vLLM / Ollama / OrcaRouter 等均可纯配置接入，支持 `responses` 与 `chatCompletions` 两种 wire 协议）和火山引擎 **Ark Provider**；新增 OpenAI 兼容厂商零代码，协议不兼容的厂商才需实现 `Provider` 接口
- **YAML 配置体系**：`application.yaml` + profile 文件（`application-<profile>.yaml`）分层合并，支持 `${VAR:default}` 占位符注入环境变量，敏感信息不入库

## 环境要求

- JDK 17+
- Maven 3.x
- 任一可用模型服务的 API Key（火山引擎 Ark，或任意 OpenAI 兼容服务）

## 快速开始

### 1. 配置 Provider

在 `src/main/resources/application.yaml` 的 `app.provider.instances` 下配置模型实例（默认已含 OrcaRouter 示例），并用 `app.provider.active` 指定启用的实例：

```yaml
app:
  provider:
    active: orcaRouter            # 可被 APP_PROVIDER 环境变量覆盖
    instances:
      orcaRouter:
        type: openai-compatible
        model: "OrcaRouter"
        baseUrl: "https://api.orcarouter.ai/v1"
        wireApi: "responses"      # 或 chatCompletions
        apiKey: "${ORCA_KEY}"     # ${VAR:default} 占位符，运行时从环境变量注入
      # 新增其他 OpenAI 兼容厂商照此添加即可，例如:
      # deepseek:
      #   type: openai-compatible
      #   model: "deepseek-chat"
      #   baseUrl: "https://api.deepseek.com/v1"
      #   wireApi: "chatCompletions"
      #   apiKey: "${DEEPSEEK_KEY}"
      # 火山引擎 Ark:
      # ark:
      #   type: ark
      #   model: "ark-code-latest"
      #   baseUrl: "https://ark.cn-beijing.volces.com/api/plan/v3"
      #   apiKey: "${ARK_KEY}"
      #   timeout: 1800
      #   connectTimeout: 20
      #   retryTimes: 2
```

敏感配置（如真实 API Key）建议放到 `application-local.yaml`（已 gitignore），并通过 `APP_PROFILE=local` 环境变量（或 `-Dapp.profile=local`）启用；profile 文件会深合并覆盖 `application.yaml`。

### 2. 构建并运行

```bash
# 注入 API Key（按你配置的占位符变量名）
export ORCA_KEY="your-api-key"
# 可选：切换 provider 实例 / 启用 profile
export APP_PROVIDER="orcaRouter"
export APP_PROFILE="local"

mvn clean package
mvn compile exec:java -Dexec.mainClass=com.zivyou.zivclaw.Application
```

入口类为 `com.zivyou.zivclaw.Application`，在其中设置你的任务提示词（`userPrompt`）即可启动 Agent。

## 架构

```
com.zivyou.zivclaw
├── Application          # 入口
├── ReActAgent           # ReAct 主循环：think → act → observe
├── config/              # YAML 配置加载（ConfigLoader）与 Provider 实例配置模型
├── provider/            # 模型服务抽象与工厂
│   ├── Provider / DefaultProviderFactory
│   ├── OpenAICompatibleProvider   # 通用 OpenAI 兼容协议（responses / chatCompletions）
│   └── ArkProvider                # 火山引擎 Ark
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

1. `ConfigLoader` 合并 YAML 配置并解析占位符，`DefaultProviderFactory` 按 `active` 实例创建 Provider；`DefaultPromptComposer` 组装 system prompt，`SessionManager` 加载/创建会话并恢复工作记忆
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
