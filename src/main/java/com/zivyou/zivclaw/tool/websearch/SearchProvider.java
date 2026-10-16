package com.zivyou.zivclaw.tool.websearch;

import java.util.List;

public interface SearchProvider {
    List<WebSearchResult> search(WebSearchArg args);
}
