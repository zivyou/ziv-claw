package com.zivyou.zivclaw.tool.websearch;

import com.zivyou.zivclaw.context.AgentContext;
import com.zivyou.zivclaw.registry.AbstractTool;
import com.zivyou.zivclaw.registry.ToolResult;

import java.util.List;

public class WebSearch extends AbstractTool<WebSearchArg> {
    DefaultSearchProvider searchProvider = new DefaultSearchProvider();
    @Override
    public ToolResult invoke(AgentContext agentContext, WebSearchArg args) {
        List<WebSearchResult> results = searchProvider.search(args);
        return ToolResult.ok(results);
    }
}
