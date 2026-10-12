# ZivClaw

English | [中文](README.md)

An AI agent written in Java with **zero frameworks** — no Spring, no magic. The ReAct loop, tool registry, context compaction, and session management are all hand-rolled, driving LLMs through pluggable providers (any OpenAI-compatible service, or Volcengine Ark) to get tasks done.

## Features

- **ReAct agent loop**: think → call tools → observe → think again, exiting automatically when the task is complete
- **Declarative tool system**: one `@AgentTool` annotation + one args POJO defines a tool. Classpath scanning handles registration, and the JSON Schema is derived automatically from the args POJO — no "updated the POJO but forgot the schema" drift
- **Built-in tools**: `Bash` (run commands), `Read` (read files), `Write` (write files), `Edit` (text-block replacement)
- **Parallel tool execution**: multiple tool calls within a single turn run concurrently on a thread pool
- **Progressive context compaction**: oversized messages are compacted with their originals persisted to `.ziv-claw/compacted/`, which the model can retrieve itself via the Read tool; if still over budget, early history is dropped turn by turn
- **Session persistence**: conversation history is saved to `./.session/<sessionId>` for resumable sessions
- **Pluggable providers**: ships with a generic **OpenAI-compatible provider** (DeepSeek / Moonshot / Qwen compatible mode / OpenRouter / vLLM / Ollama / OrcaRouter ... all onboarded via pure configuration, with both `responses` and `chatCompletions` wire APIs supported) and a Volcengine **Ark provider**. Adding another OpenAI-compatible vendor requires zero code; only vendors with incompatible protocols need a new `Provider` implementation
- **YAML-based configuration**: `application.yaml` plus profile files (`application-<profile>.yaml`) are merged in layers, with `${VAR:default}` placeholders injecting environment variables so secrets stay out of the repo

## Requirements

- JDK 17+
- Maven 3.x
- An API key for any supported model service (Volcengine Ark, or any OpenAI-compatible service)

## Quick Start

### 1. Configure a provider

Add model instances under `app.provider.instances` in `src/main/resources/application.yaml` (an OrcaRouter example is included by default), and pick the active one via `app.provider.active`:

```yaml
app:
  provider:
    active: orcaRouter            # overridable via the APP_PROVIDER env var
    instances:
      orcaRouter:
        type: openai-compatible
        model: "OrcaRouter"
        baseUrl: "https://api.orcarouter.ai/v1"
        wireApi: "responses"      # or chatCompletions
        apiKey: "${ORCA_KEY}"     # ${VAR:default} placeholder, injected from env at runtime
      # Add other OpenAI-compatible vendors the same way, e.g.:
      # deepseek:
      #   type: openai-compatible
      #   model: "deepseek-chat"
      #   baseUrl: "https://api.deepseek.com/v1"
      #   wireApi: "chatCompletions"
      #   apiKey: "${DEEPSEEK_KEY}"
      # Volcengine Ark:
      # ark:
      #   type: ark
      #   model: "ark-code-latest"
      #   baseUrl: "https://ark.cn-beijing.volces.com/api/plan/v3"
      #   apiKey: "${ARK_KEY}"
      #   timeout: 1800
      #   connectTimeout: 20
      #   retryTimes: 2
```

Sensitive values (e.g. real API keys) are best placed in `application-local.yaml` (already gitignored), enabled via the `APP_PROFILE=local` env var (or `-Dapp.profile=local`); profile files are deep-merged over `application.yaml`.

### 2. Build and run

```bash
# Inject the API key (matching the placeholder variable name you configured)
export ORCA_KEY="your-api-key"
# Optional: switch provider instance / enable a profile
export APP_PROVIDER="orcaRouter"
export APP_PROFILE="local"

mvn clean package
mvn compile exec:java -Dexec.mainClass=com.zivyou.zivclaw.Application
```

The entry point is `com.zivyou.zivclaw.Application` — set your task prompt (`userPrompt`) there and the agent takes it from there.

## Architecture

```
com.zivyou.zivclaw
├── Application          # Entry point
├── ReActAgent           # ReAct main loop: think → act → observe
├── config/              # YAML config loading (ConfigLoader) and provider instance config model
├── provider/            # Model service abstraction and factory
│   ├── Provider / DefaultProviderFactory
│   ├── OpenAICompatibleProvider   # Generic OpenAI-compatible protocol (responses / chatCompletions)
│   └── ArkProvider                # Volcengine Ark
├── registry/            # Hand-rolled IoC: @AgentTool scanning, schema generation, dispatch
│   ├── Tool / AbstractTool / AgentTool
│   └── DefaultRegistry
├── tool/                # Built-in tools: Bash / Read / Write / Edit
├── context/             # AgentContext and ContextCompactor (context compaction)
├── session/             # Session management and persistence
├── prompt/              # System prompt composition
├── message/             # Message / ToolCall / Role protocol models
└── reporter/            # Output reporting (ConsoleReporter)
```

## Custom Tools

Two steps: extend `AbstractTool<A>` and add the `@AgentTool` annotation. Registration, schema generation, and argument deserialization are all automatic:

```java
@AgentTool(name = "read_file", description = "Read the content of a local file")
public class ReadFileTool extends AbstractTool<ReadFileTool.Args> {
    public static class Args {
        @JsonProperty(required = true)
        public String path;
        public Integer limit;
    }

    @Override
    public ToolResult invoke(AgentContext ctx, Args args) {
        // args is already a deserialized, strongly-typed POJO
        return ToolResult.ok(...);
    }
}
```

Constraints: tool classes must be named concrete classes (no anonymous classes / lambdas) with a public no-arg constructor.

## How It Works

1. `ConfigLoader` merges the YAML configs and resolves placeholders; `DefaultProviderFactory` creates the provider for the `active` instance. `DefaultPromptComposer` assembles the system prompt; `SessionManager` loads or creates a session and restores working memory
2. Each turn: `ContextCompactor` compacts history as needed → `Provider` calls the model → if there are no tool calls, the task is done
3. When tools are called, a thread pool executes them concurrently; results are fed back as `TOOL` messages for the next turn
4. The session is persisted at the end of each turn; a shutdown hook gracefully closes the thread pool and provider on exit

## Roadmap

- [ ] Interactive TUI with multi-turn conversation (see [docs/interactive-tui-plan.md](docs/interactive-tui-plan.md))
- [ ] More built-in tools and MCP support

## Contributing

Feel free to download / contribute. If you find a bug, open an issue or email yzq529@qq.com.

## License

See [LICENSE](LICENSE).
