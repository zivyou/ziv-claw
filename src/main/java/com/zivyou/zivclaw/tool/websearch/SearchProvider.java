package com.zivyou.zivclaw.tool.websearch;

import java.util.List;

/**
 * 搜索服务 SPI。实现方打包为独立 jar,在 META-INF/services/com.zivyou.zivclaw.tool.websearch.SearchProvider
 * 中声明实现类全限定名即可被核心包通过 ServiceLoader 发现。
 * 实现类必须提供无参构造函数;配置由核心包读取后通过 {@link #configure} 注入,实现方不直接读取配置。
 */
public interface SearchProvider {
    /** Provider 标识名,用于配置项 app.webSearch.providerName 选择实现,如 "doubao"。 */
    String name();

    /** 由核心包在选中后调用一次,注入配置。 */
    default void configure(WebSearchProviderConfig config) {
    }

    List<WebSearchResult> search(WebSearchArg args);
}
