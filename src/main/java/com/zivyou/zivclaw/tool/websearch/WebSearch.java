package com.zivyou.zivclaw.tool.websearch;

import com.zivyou.zivclaw.config.ConfigLoader;
import com.zivyou.zivclaw.context.AgentContext;
import com.zivyou.zivclaw.registry.AbstractTool;
import com.zivyou.zivclaw.registry.AgentTool;
import com.zivyou.zivclaw.registry.ToolResult;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.ServiceLoader;

@Slf4j
@AgentTool(
        name = "WebSearch",
        description = "联网搜索内容"
)
public class WebSearch extends AbstractTool<WebSearchArg> {
    final SearchProvider searchProvider = loadProvider();

    @Override
    public ToolResult invoke(AgentContext agentContext, WebSearchArg args) {
        List<WebSearchResult> results = searchProvider.search(args);
        return ToolResult.ok(results);
    }

    /**
     * 通过 SPI 发现 classpath 上的 SearchProvider 实现:
     * 配置了 app.webSearch.providerName 时按 name() 精确匹配,否则取第一个发现的实现。
     */
    private static SearchProvider loadProvider() {
        List<SearchProvider> providers = ServiceLoader.load(SearchProvider.class).stream()
                .map(ServiceLoader.Provider::get)
                .toList();
        if (providers.isEmpty()) {
            throw new IllegalStateException(
                    "classpath 上没有找到任何 SearchProvider 实现,请引入搜索实现 jar(如 ziv-claw-search-doubao)");
        }
        WebSearchProviderConfig config = ConfigLoader.load("app.webSearch", WebSearchProviderConfig.class);
        String providerName = config != null ? config.getProviderName() : null;

        SearchProvider selected;
        if (providerName == null || providerName.isBlank()) {
            selected = providers.get(0);
            log.info("未配置 app.webSearch.providerName,使用第一个发现的 SearchProvider: {}", selected.name());
        } else {
            selected = providers.stream()
                    .filter(p -> p.name().equalsIgnoreCase(providerName))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "找不到名为 '%s' 的 SearchProvider,classpath 上可用的实现: %s".formatted(
                                    providerName,
                                    providers.stream().map(SearchProvider::name).toList())));
        }
        if (config != null) {
            selected.configure(config);
        }
        log.info("WebSearch 使用 SearchProvider: {}", selected.name());
        return selected;
    }
}
