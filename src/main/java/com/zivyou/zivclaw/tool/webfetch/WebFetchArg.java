package com.zivyou.zivclaw.tool.webfetch;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;

@Data
public class WebFetchArg {
    @JsonProperty(required = true)
    @JsonPropertyDescription("抓取的网页的URI")
    String uri;
}
