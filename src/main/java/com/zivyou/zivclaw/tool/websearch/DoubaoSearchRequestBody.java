package com.zivyou.zivclaw.tool.websearch;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 豆包搜索 Custom 版(原联网搜索/融合信息搜索)请求体。
 * 字段名遵循接口文档的 PascalCase 命名,null 字段不序列化。
 *
 * @see <a href="https://docs.volcengine.com/docs/Networkedsearch/Networkedsearch-2">豆包搜索 Custom 版</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DoubaoSearchRequestBody(
        String Query,
        String SearchType,
        Integer Count,
        Filter Filter,
        QueryControl QueryControl
) {

    /** 过滤条件。Sites / BlockHosts 需完整域名,多个用 '|' 分隔(最多 20 / 5 个)。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Filter(
            String Sites,
            String BlockHosts
    ) {
    }

    /** Query 控制参数。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record QueryControl(
            Boolean QueryRewrite
    ) {
    }
}
