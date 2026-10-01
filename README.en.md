# ZivClaw

English | [中文](README.md)

An AI agent written in Java with **zero frameworks** — no Spring, no magic. The ReAct loop, tool registry, context compaction, and session management are all hand-rolled, driving an LLM via the Volcengine Ark SDK to get tasks done.

## Features

- **ReAct agent loop**: think → call tools → observe → think again, exiting automatically when the task is complete
- **Declarative tool system**: one `@AgentTool` annotation + one args POJO defines a tool. Classpath scanning handles registration, and the JSON Schema is derived automatically from the args POJO — no "updated the POJO but forgot the schema" drift
- **Built-in tools**: `Bash` (run commands), `Read` (read files), `Write` (write files), `Edit` (text-block replacement)
- **Parallel tool execution**: multiple tool calls within a single turn run concurrently on a thread pool
- **Progressive context compaction**: oversized messages are compacted with their originals persisted to `.ziv-claw/compacted/`, which the model can retrieve itself via the Read tool; if still over budget, early history is dropped turn by turn
- **Session persistence**: conversation history is saved to `./.session/<sessionId>` for resumable sessions
- **Pluggable provider**: currently backed by Volcengine Ark (`ark-code-latest`); the `Provider` interface can be swapped for other model services

## Requirements

- JDK 17+
- Maven 3.x
- A Volcengine Ark API key

## Quick Start

```bash
# 1. Configure your API key
export ARK_AGENT_KEY="your-ark-api-key"

# 2. Build
mvn clean package

# 3. Run (edit userPrompt in Application.java first)
mvn compile exec:java -Dexec.mainClass=com.zivyou.zivclaw.Application
```

The entry point is `com.zivyou.zivclaw.Application` — set your task prompt (`userPrompt`) there and the agent takes it from there.

## Architecture

```
com.zivyou.zivclaw
├── Application          # Entry point
├── ReActAgent           # ReAct main loop: think → act → observe
├── provider/            # Model service abstraction (ArkProvider)
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

1. `DefaultPromptComposer` assembles the system prompt; `SessionManager` loads or creates a session and restores working memory
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
