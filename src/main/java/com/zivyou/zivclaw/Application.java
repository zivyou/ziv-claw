package com.zivyou.zivclaw;

import com.zivyou.zivclaw.context.AgentContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Application {
    public static void main(String[] args) {
        var context = AgentContext.builder().workDir(System.getProperty("user.dir")).build();
        // var userPrompt = "hello! 帮我看下src/main/java/com/zivyou/zivclaw/README.md这个文件，我在其中预留的邮箱地址有问题，请帮我修改成youziqi529@outlook.com";
        var userPrompt = "hello! 帮我看下src/main/java/com/zivyou/zivclaw/下有几个文件，每个文件分别有多大";
        ReActAgent agent = new ReActAgent();
        Runtime.getRuntime().addShutdownHook(new Thread(agent::shutdown, "shutdown-hook"));
        try {
            agent.start(context, userPrompt);
        } finally {
            agent.shutdown();
        }
        log.info("[Application] agent正常退出.");
    }
}
