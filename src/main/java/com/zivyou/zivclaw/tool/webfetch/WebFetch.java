package com.zivyou.zivclaw.tool.webfetch;

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import com.zivyou.zivclaw.context.AgentContext;
import com.zivyou.zivclaw.registry.AbstractTool;
import com.zivyou.zivclaw.registry.AgentTool;
import com.zivyou.zivclaw.registry.ToolResult;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 抓取网页正文并转为 Markdown 返回给模型。
 *
 * <p>分层职责:OkHttp 负责下载(超时 / UA / 大小上限可控),jsoup 负责解析与
 * 清洗(剔除 script/nav 等噪音、定位正文容器),flexmark 负责 HTML → Markdown。
 * 失败一律返回 {@link ToolResult#error(String)},让模型看到原因后自行决定是否重试。</p>
 */
@Slf4j
@AgentTool(
        name = "WebFetch",
        description = "抓取指定网页的正文内容,清洗后转为 Markdown 返回。输入为完整的 http/https URL,"
                + "输出为页面正文的 Markdown(超长会截断)。适合阅读文章、文档、博客等静态页面;"
                + "不能执行 JavaScript,依赖 JS 动态渲染的页面可能抓不到内容。"
)
public class WebFetch extends AbstractTool<WebFetchArg> {

    /** 下载体积上限,防止超大页面耗尽内存 */
    private static final int MAX_BODY_BYTES = 10 * 1024 * 1024;
    /** 输出字符上限,防止撑爆模型上下文 */
    private static final int MAX_OUTPUT_CHARS = 50_000;

    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; zivclaw-webfetch/1.0)";

    /** 噪音标签:对正文没有贡献,转换前整体剔除 */
    private static final String NOISE_SELECTOR =
            "script, style, noscript, template, iframe, form, svg, nav, aside, footer, header";

    /** 正文容器候选,按优先级取第一个命中的;都取不到则退回 body */
    private static final String[] CONTENT_SELECTORS = {"article", "main", "[role=main]"};

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(30))
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build();

    private static final FlexmarkHtmlConverter htmlConverter =
            FlexmarkHtmlConverter.builder().build();

    @Override
    public ToolResult invoke(AgentContext agentContext, WebFetchArg args) {
        String url = args.getUri();
        String invalidReason = validateUrl(url);
        if (invalidReason != null) {
            return ToolResult.error(invalidReason);
        }

        String html;
        try {
            html = download(url);
        } catch (IOException e) {
            log.warn("WebFetch download failed, url={}: {}", url, e.getMessage());
            return ToolResult.error("抓取失败: " + e.getMessage());
        }

        try {
            String markdown = toMarkdown(html, url);
            if (markdown.isBlank()) {
                return ToolResult.error("抓取成功但未能提取到正文内容,页面可能是 JS 动态渲染的: " + url);
            }
            log.debug("WebFetch ok, url={}, markdownChars={}", url, markdown.length());
            return ToolResult.ok(markdown);
        } catch (Exception e) {
            log.warn("WebFetch parse failed, url={}", url, e);
            return ToolResult.error("页面解析失败: " + e.getMessage());
        }
    }

    /**
     * URL 准入校验,返回 null 表示合法。SSRF 加固(如禁止内网地址)也在这里扩展。
     */
    private String validateUrl(String url) {
        if (url == null || url.isBlank()) {
            return "URL 不能为空";
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return "仅支持 http/https URL,收到: " + url;
        }
        return null;
    }

    private String download(String url) throws IOException {
        var request = new Request.Builder().url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,text/plain;q=0.9,*/*;q=0.8")
                .get()
                .build();
        try (var response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " " + response.message());
            }
            var body = response.body();
            if (body == null) {
                throw new IOException("响应体为空");
            }
            var contentType = body.contentType();
            if (contentType != null && !isTextual(contentType.type(), contentType.subtype())) {
                throw new IOException("非文本内容,无法转换: " + contentType);
            }
            Charset charset = contentType != null
                    ? contentType.charset(StandardCharsets.UTF_8)
                    : StandardCharsets.UTF_8;
            return new String(readBounded(body.byteStream()), charset);
        }
    }

    private boolean isTextual(String type, String subtype) {
        return "text".equals(type)
                || subtype.contains("html")
                || subtype.contains("xml")
                || subtype.contains("json");
    }

    /** 有界读取,超过 {@link #MAX_BODY_BYTES} 直接失败 */
    private byte[] readBounded(InputStream in) throws IOException {
        try (in; var out = new ByteArrayOutputStream()) {
            var buf = new byte[8192];
            int total = 0;
            int n;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > MAX_BODY_BYTES) {
                    throw new IOException("页面过大,超过 " + (MAX_BODY_BYTES / 1024 / 1024) + "MB 上限");
                }
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    private String toMarkdown(String html, String baseUri) {
        Document doc = Jsoup.parse(html, baseUri);
        doc.select(NOISE_SELECTOR).remove();

        Element root = null;
        for (var selector : CONTENT_SELECTORS) {
            root = doc.selectFirst(selector);
            if (root != null) {
                break;
            }
        }
        if (root == null) {
            root = doc.body();
        }

        String markdown = htmlConverter.convert(root != null ? root.html() : "")
                .replaceAll("\\n{3,}", "\\n\\n")
                .trim();

        var out = new StringBuilder();
        if (doc.title() != null && !doc.title().isBlank()) {
            out.append("# ").append(doc.title().trim()).append("\n\n");
        }
        out.append(markdown);

        if (out.length() > MAX_OUTPUT_CHARS) {
            out.setLength(MAX_OUTPUT_CHARS);
            out.append("\n\n... [内容过长,已截断。如需后续内容,可用更具体的页面内链接继续抓取]");
        }
        return out.toString();
    }
}
