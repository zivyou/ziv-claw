package com.zivyou.zivclaw;

import com.zivyou.zivclaw.context.Context;
import com.zivyou.zivclaw.provider.ArkProvider;
import com.zivyou.zivclaw.registry.DefaultRegistry;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Application {
    public static void main(String[] args) {
        log.info("hello world!");
        var context = Context.builder().workDir(System.getProperty("user.dir")).build();
        var userPrompt = "hello! 帮我看下src/main/java/com/zivyou/zivclaw/Application.java这个文件有多少行。";
        var provider = new ArkProvider();
        var registry = DefaultRegistry.getInstance();
        ReActAgent loop = new ReActAgent(provider, registry,  true);
        loop.start(context, userPrompt);
        log.error("SYSTEM ERROR: react loop corrupted!");
    }
}
