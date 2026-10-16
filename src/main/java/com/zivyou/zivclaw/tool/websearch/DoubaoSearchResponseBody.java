package com.zivyou.zivclaw.tool.websearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 豆包搜索 Custom 版响应体。
 * 文档中未在此建模的字段(如 ImageResults、CardResults、WebItem 的 InlineImages/RuyiInfo 等)
 * 统一通过 {@code ignoreUnknown = true} 忽略。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DoubaoSearchResponseBody(
        ResponseMetadata ResponseMetadata,
        Result Result
) {
    /** 请求是否成功:Result 非空且无接口层错误。失败时 Result 为 null,错误在 ResponseMetadata.Error。 */
    public boolean isSuccess() {
        return Result != null && (ResponseMetadata == null || ResponseMetadata.Error() == null);
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
record ResponseMetadata(
        String RequestId,
        String Action,
        String Version,
        String Service,
        String Region,
        ApiError Error
) {
}

/** 接口层错误信息,如 10406(免费额度用尽)、700429(QPS 限流)等。 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ApiError(
        Long CodeN,
        String Code,
        String Message
) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record Result(
        Integer ResultCount,
        List<WebItem> WebResults,
        SearchContext SearchContext,
        Long TimeCost,
        String LogId
) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record SearchContext(
        String SearchType,
        String OriginQuery
) {
}

/**
 * 文搜文结果项。
 * 注意:Snippet 仅约 200 字,文档强烈不建议用于大模型场景;大模型场景优先取
 * Summary(500~1000 字相关摘要)或 Content(正文)。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record WebItem(
        String Id,
        Long SortId,
        String Title,
        String SiteName,
        String Url,
        String Snippet,
        String Summary,
        String Content,
        String PublishTime,
        String LogoUrl,
        Float RankScore,
        String AuthInfoDes,
        Integer AuthInfoLevel
) {
}
