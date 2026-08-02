package com.zivyou.zivclaw;

import com.zivyou.zivclaw.context.Context;
import com.zivyou.zivclaw.provider.ArkProvider;
import com.zivyou.zivclaw.registry.DefaultRegistry;
import com.zivyou.zivclaw.reporter.ConsoleReporter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Application {
    public static void main(String[] args) {
        log.info("hello world!");
        var context = Context.builder().workDir(System.getProperty("user.dir")).build();
        // var userPrompt = "hello! 帮我看下src/main/java/com/zivyou/zivclaw/README.md这个文件，我在其中预留的邮箱地址有问题，请帮我修改成youziqi529@outlook.com";
        var userPrompt = "hello! 帮我看下src/main/java/com/zivyou/zivclaw/下有几个文件，每个文件分别有多大";
        var provider = new ArkProvider();
        var registry = DefaultRegistry.getInstance();
        var reporter = new ConsoleReporter();
        ReActAgent loop = new ReActAgent(provider, registry,  reporter);
        loop.start(context, userPrompt);
        log.error("SYSTEM ERROR: react loop corrupted!");
    }
}
