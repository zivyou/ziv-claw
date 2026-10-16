package com.zivyou.zivclaw.tool.websearch;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class WebSearchArg {
    @JsonProperty(required = true)
    String query;
    @JsonProperty(required = true)
    Integer maxResults = 10;
    @JsonProperty(required = false)
    List<String> allowedDomains;
    @JsonProperty(required = false)
    List<String> blockedDomains;
}
