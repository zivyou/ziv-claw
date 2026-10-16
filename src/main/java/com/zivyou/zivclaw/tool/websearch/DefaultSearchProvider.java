package com.zivyou.zivclaw.tool.websearch;

import com.zivyou.zivclaw.config.ConfigLoader;
import com.zivyou.zivclaw.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 豆包搜索 Custom 版(原联网搜索/融合信息搜索)实现。
 * 接口约定见 docs/doubao-search-custom-api.md。
 */
@Slf4j
public class DefaultSearchProvider implements SearchProvider {
    /** APIKey 接入的官方地址;application.yaml 里 app.webSearch.baseUrl 为空时回退到这里。 */
    private static final String DEFAULT_BASE_URL = "https://open.feedcoopapi.com/search_api/web_search";
    /** 文搜文 Count 上限(文档:最多 50 条,默认 10 条)。 */
    private static final int DEFAULT_COUNT = 10;
    private static final int MAX_COUNT = 50;
    /** 文档约定:Sites 最多 20 个站点,BlockHosts 最多 5 个。 */
    private static final int MAX_SITES = 20;
    private static final int MAX_BLOCK_HOSTS = 5;
    private static final MediaType JSON = MediaType.parse("application/json");

    private static final WebSearchProviderConfig config = ConfigLoader.load("app.webSearch", WebSearchProviderConfig.class);
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(30))
            .writeTimeout(Duration.ofSeconds(30))
            .retryOnConnectionFailure(true).build();

    @Override
    public List<WebSearchResult> search(WebSearchArg args) {
        String raw = rawRequest(args);
        var response = JsonUtil.parse(raw, DoubaoSearchResponseBody.class);
        if (response == null) {
            throw new IllegalStateException("豆包搜索响应解析失败: " + raw);
        }
        if (!response.isSuccess()) {
            ApiError error = response.ResponseMetadata() != null ? response.ResponseMetadata().Error() : null;
            String message = error != null
                    ? "豆包搜索调用失败: code=%s, message=%s".formatted(error.Code(), error.Message())
                    : "豆包搜索调用失败: 响应缺少 Result";
            log.error("{} (raw={})", message, raw);
            throw new IllegalStateException(message);
        }
        List<WebItem> items = response.Result().WebResults();
        if (items == null) {
            return List.of();
        }
        return items.stream().map(DefaultSearchProvider::toSearchResult).toList();
    }

    private String rawRequest(WebSearchArg args) {
        var requestBody = buildRequestBody(args);
        var body = RequestBody.create(JsonUtil.stringify(requestBody), JSON);
        var request = new Request.Builder()
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + config.getApiKey())
                .url(baseUrl())
                .post(body)
                .build();
        try (var response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IllegalStateException(
                        "豆包搜索 HTTP 请求失败: status=%d, body=%s".formatted(response.code(), responseBody));
            }
            return responseBody;
        } catch (IOException e) {
            throw new IllegalStateException("豆包搜索请求异常: " + e.getMessage(), e);
        }
    }

    private static DoubaoSearchRequestBody buildRequestBody(WebSearchArg args) {
        if (args.getQuery() == null || args.getQuery().isBlank()) {
            throw new IllegalArgumentException("豆包搜索 Query 不能为空(文档要求 1~100 个字符)");
        }
        int count = args.getMaxResults() == null
                ? DEFAULT_COUNT
                : Math.min(Math.max(args.getMaxResults(), 1), MAX_COUNT);
        String sites = joinLimit(args.getAllowedDomains(), MAX_SITES);
        String blockHosts = joinLimit(args.getBlockedDomains(), MAX_BLOCK_HOSTS);
        var filter = (sites == null && blockHosts == null)
                ? null
                : new DoubaoSearchRequestBody.Filter(sites, blockHosts);
        return new DoubaoSearchRequestBody(args.getQuery(), "web", count, filter, null);
    }

    /** 文档约定:多个站点用 '|' 分隔,Sites 最多 20 个、BlockHosts 最多 5 个,超出截断。 */
    private static String joinLimit(List<String> domains, int limit) {
        if (domains == null || domains.isEmpty()) {
            return null;
        }
        return String.join("|", domains.stream().limit(limit).toList());
    }

    private static WebSearchResult toSearchResult(WebItem item) {
        var result = new WebSearchResult();
        result.setTitle(item.Title());
        result.setUrl(item.Url());
        // 大模型场景优先取 Summary(500~1000 字摘要),退化为 Snippet(约 200 字,文档不建议给大模型用)。
        String description = item.Summary() != null && !item.Summary().isBlank()
                ? item.Summary()
                : item.Snippet();
        result.setDescription(description);
        Map<String, Object> frontMatter = new HashMap<>();
        if (item.SiteName() != null) frontMatter.put("siteName", item.SiteName());
        if (item.PublishTime() != null) frontMatter.put("publishTime", item.PublishTime());
        if (item.RankScore() != null) frontMatter.put("rankScore", item.RankScore());
        if (item.AuthInfoLevel() != null) frontMatter.put("authInfoLevel", item.AuthInfoLevel());
        if (item.AuthInfoDes() != null) frontMatter.put("authInfoDes", item.AuthInfoDes());
        if (item.LogoUrl() != null) frontMatter.put("logoUrl", item.LogoUrl());
        result.setFrontMatter(frontMatter);
        return result;
    }

    private static String baseUrl() {
        String baseUrl = config.getBaseUrl();
        return baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl;
    }
}
