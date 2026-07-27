package com.zivyou.zivclaw;

import com.zivyou.zivclaw.context.Context;
import com.zivyou.zivclaw.provider.ArkProvider;
import com.zivyou.zivclaw.tool.DefaultRegistry;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Application {
    public static void main(String[] args) {
        log.info("hello world!");
        var context = new Context();
        var userPrompt = "hello?";
        var provider = new ArkProvider();
        var registry = new DefaultRegistry();
        ReActAgent loop = new ReActAgent(provider, registry, ".", true);
        loop.start(context, userPrompt);
        log.error("SYSTEM ERROR: react loop corrupted!");
    }
}
