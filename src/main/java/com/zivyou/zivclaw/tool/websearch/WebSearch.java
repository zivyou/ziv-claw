package com.zivyou.zivclaw.tool.websearch;

import com.zivyou.zivclaw.context.AgentContext;
import com.zivyou.zivclaw.registry.AbstractTool;
import com.zivyou.zivclaw.registry.AgentTool;
import com.zivyou.zivclaw.registry.ToolResult;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@AgentTool(
        name = "WebSearch",
        description = "联网搜索内容"
)
public class WebSearch extends AbstractTool<WebSearchArg> {
    DefaultSearchProvider searchProvider = new DefaultSearchProvider();
    @Override
    public ToolResult invoke(AgentContext agentContext, WebSearchArg args) {
        List<WebSearchResult> results = searchProvider.search(args);
        return ToolResult.ok(results);
    }
}
