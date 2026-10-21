package com.zivyou.zivclaw;

import com.zivyou.zivclaw.context.AgentContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;

@Slf4j
public class Application {
    public static void main(String[] args) {
        var context = AgentContext.builder().workDir(System.getProperty("user.dir")).build();
        ReActAgent agent = new ReActAgent();
        if (args.length > 0) {
            String userPrompt = String.join(" ", args);
            agent.start(context, userPrompt);
            log.info("[Application(one-shot模式)] agent正常退出.");
            agent.shutdown();
            return;
        }
        TUI tui = new TUI(agent, context);
        tui.start();
        log.info("[Application] agent正常退出.");
    }
}


class TUI {
    private ReActAgent agent;
    private final AgentContext agentContext;

    TUI(ReActAgent agent, AgentContext agentContext) {
        this.agent = agent;
        this.agentContext = agentContext;
    }
    public void start() {
        try {
            Terminal terminal = TerminalBuilder.builder().system(true).build();
            LineReader reader = LineReaderBuilder.builder().terminal(terminal).build();
            while (true) {
                String line = reader.readLine("> ");
                if ("/exit".equals(line)) {
                    break;
                }
                if ("/clear".equals(line)) {
                    agent.resetConversation(agentContext);
                    agent.shutdown();
                    agentContext.setSessionId(null);
                    agent = new ReActAgent();
                }
                terminal.writer().println(line);
                terminal.flush();
                if (StringUtils.isBlank(line)) {
                    continue;
                }
                agent.start(agentContext, line);
            }
            terminal.writer().println("Bye!");
            terminal.close();
        } catch (IOException e) {
            System.err.println(e.getMessage());
        } finally {
            agent.shutdown();
        }
    }
}
