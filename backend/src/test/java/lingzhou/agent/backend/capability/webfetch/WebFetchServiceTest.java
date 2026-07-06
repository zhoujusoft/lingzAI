package lingzhou.agent.backend.capability.webfetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import lingzhou.agent.backend.common.lzException.TaskException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class WebFetchServiceTest {

    @Test
    void shouldParseWechatArticleContentFromHtml() {
        WebFetchService service = new WebFetchService();
        String html =
                """
                <!doctype html>
                <html>
                <head><title>微信公众平台</title></head>
                <body>
                  <h1 id="activity-name"> SpringBoot 4.1 正式发布 </h1>
                  <span id="js_name"> JAVA架构日记 </span>
                  <span id="js_author_name"> PIGCLOUD </span>
                  <meta name="description" content="文章摘要" />
                  <script>var msg_title = "SpringBoot 4.1 正式发布";</script>
                  <script>var msg_desc = "微信文章摘要";</script>
                  <script>var msg_cdn_url = "https://mmbiz.qpic.cn/cover.png";</script>
                  <script>var __biz = "MzDemoBiz";</script>
                  <script>var mid = "123";</script>
                  <script>var idx = "1";</script>
                  <script>var ct = "1781143209";</script>
                  <div id="js_content">
                    <h2>一、核心新特性</h2>
                    <p>先关注，不错过更新</p>
                    <p>以下是关于 Spring Boot 4.1 的新特性。</p>
                    <img data-src="https://mmbiz.qpic.cn/test.png" />
                    <a href="/s/next">相关阅读</a>
                  </div>
                </body>
                </html>
                """;

        WebFetchResult result = service.parse(
                "https://mp.weixin.qq.com/s/demo",
                200,
                "text/html; charset=UTF-8",
                html,
                new WebFetchRequest(null, null, 1000, true, true, null),
                1000);

        assertThat(result.success()).isTrue();
        assertThat(result.errorCode()).isEmpty();
        assertThat(result.wechatArticle()).isTrue();
        assertThat(result.title()).isEqualTo("SpringBoot 4.1 正式发布");
        assertThat(result.description()).isEqualTo("微信文章摘要");
        assertThat(result.accountName()).isEqualTo("JAVA架构日记");
        assertThat(result.author()).isEqualTo("PIGCLOUD");
        assertThat(result.publishTime()).isEqualTo("1781143209");
        assertThat(result.coverImage()).isEqualTo("https://mmbiz.qpic.cn/cover.png");
        assertThat(result.content()).contains("先关注，不错过更新", "Spring Boot 4.1");
        assertThat(result.markdown()).contains("## 一、核心新特性", "![");
        assertThat(result.metadata())
                .containsEntry("biz", "MzDemoBiz")
                .containsEntry("mid", "123")
                .containsEntry("idx", "1");
        assertThat(result.images()).containsExactly("https://mmbiz.qpic.cn/test.png");
        assertThat(result.links()).hasSize(1);
        assertThat(result.links().get(0).url()).isEqualTo("https://mp.weixin.qq.com/s/next");
    }

    @Test
    void shouldBlockLocalhost() {
        WebFetchService service = new WebFetchService();

        assertThatThrownBy(() -> service.fetch(new WebFetchRequest(
                        "http://127.0.0.1:8080/article", null, null, null, null, null)))
                .isInstanceOf(TaskException.class)
                .hasMessageContaining("禁止访问内网或本机地址");
    }

    @Test
    void shouldDetectWechatVerificationPage() {
        WebFetchService service = new WebFetchService();

        WebFetchResult result = service.parse(
                "https://mp.weixin.qq.com/s/demo",
                200,
                "text/html",
                "<html><body><h2>环境异常</h2><p>当前环境异常，完成验证后即可继续访问。</p></body></html>",
                new WebFetchRequest(null, null, 1000, false, false, null),
                1000);

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("WECHAT_VERIFICATION");
        assertThat(result.verificationPage()).isTrue();
        assertThat(result.errorMessage()).contains("验证");
    }

    @Test
    void shouldSelectMainContentByTextDensityWhenNoSelectorMatches() {
        WebFetchService service = new WebFetchService();
        String html =
                """
                <!doctype html>
                <html>
                <head><title>普通文章</title></head>
                <body>
                  <div class="nav"><a href="/a">首页</a><a href="/b">菜单</a></div>
                  <div class="random-wrapper">
                    <p>这是第一段正文，包含足够长的文本用于评分。</p>
                    <p>这是第二段正文，应该被识别为主内容。</p>
                  </div>
                  <div class="footer"><a href="/c">关于我们</a></div>
                </body>
                </html>
                """;

        WebFetchResult result = service.parse(
                "https://example.com/a", 200, "text/html", html, new WebFetchRequest(null, null, 1000, false, false, null), 1000);

        assertThat(result.content()).contains("第一段正文", "第二段正文");
        assertThat(result.content()).doesNotContain("关于我们");
    }

    @Test
    @Disabled("Manual integration test: depends on external network and WeChat risk control.")
    void shouldFetchRealWechatArticleUrl() throws Exception {
        WebFetchService service = new WebFetchService();

        WebFetchResult result = service.fetch(new WebFetchRequest(
                "https://mp.weixin.qq.com/s/dC42gvofc4C7ez5Ey-lDXg", "#js_content", 5000, true, false, null));

        assertThat(result.success()).isTrue();
        assertThat(result.wechatArticle()).isTrue();
        assertThat(result.verificationPage()).isFalse();
        assertThat(result.title()).contains("SpringBoot");
        assertThat(result.accountName()).contains("JAVA");
        assertThat(result.content()).contains("Spring Boot 4.1");
        assertThat(result.markdown()).contains("Spring Boot 4.1");
        assertThat(result.images()).isNotEmpty();
        System.out.println(result);
    }

}
