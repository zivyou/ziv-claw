package com.zivyou.zivclaw;

import com.zivyou.zivclaw.context.AgentContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
        TUI tui = new TUI(agent, context);
        tui.start();
        log.info("[Application] agent正常退出.");
    }
}

@RequiredArgsConstructor
class TUI {
    private final ReActAgent agent;
    private final AgentContext agentContext;
    public void start() {
        try {
            Terminal terminal = TerminalBuilder.builder().system(true).build();
            LineReader reader = LineReaderBuilder.builder().terminal(terminal).build();
            while (true) {
                String line = reader.readLine("> ");
                if ("/exit".equals(line)) {
                    break;
                }
                terminal.writer().println(line);
                terminal.flush();
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
