package com.zivyou.zivclaw.tool.websearch;

import lombok.Data;

import java.util.Map;

@Data
public class WebSearchResult {
    String title;
    String url;
    String description;
    Map<String, Object> frontMatter;
}
