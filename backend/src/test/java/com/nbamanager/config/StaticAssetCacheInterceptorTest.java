package com.nbamanager.config;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

class StaticAssetCacheInterceptorTest {
    private final StaticAssetCacheInterceptor headers = new StaticAssetCacheInterceptor(new ByteArrayResource("<html>entry</html>".getBytes()));
    private final ResourceHttpRequestHandler resourceHandler = new ResourceHttpRequestHandler();
    @Test void onlyExistingHashedAssetsAreImmutable() throws Exception {
        var response = new MockHttpServletResponse();
        assertThat(headers.preHandle(new MockHttpServletRequest("GET", "/assets/cache-fixture-AbCd1234.js"), response, resourceHandler)).isTrue();
        assertThat(response.getHeader("Cache-Control")).isEqualTo("public, max-age=31536000, immutable");
        response = new MockHttpServletResponse();
        headers.preHandle(new MockHttpServletRequest("GET", "/assets/missing-AbCd1234.js"), response, resourceHandler);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-cache");
    }
    @Test void entryAndSpaRoutesRevalidateWithEtagIncludingHead() throws Exception {
        var response = new MockHttpServletResponse();
        headers.preHandle(new MockHttpServletRequest("GET", "/"), response, resourceHandler);
        String etag = response.getHeader("ETag");
        assertThat(etag).isNotBlank();
        for (String method : new String[]{"GET", "HEAD"}) {
            var request = new MockHttpServletRequest(method, "/login"); request.addHeader("If-None-Match", etag);
            response = new MockHttpServletResponse();
            assertThat(headers.preHandle(request, response, resourceHandler)).isFalse();
            assertThat(response.getStatus()).isEqualTo(304);
            assertThat(response.getContentAsByteArray()).isEmpty();
            assertThat(response.getHeader("Cache-Control")).isEqualTo("no-cache");
        }
    }
    @Test void controllersUploadsAndMissingApiRetainTheirPrivatePolicy() throws Exception {
        for (String path : new String[]{"/api/profile", "/api/missing", "/uploads/avatar.png", "/actuator/health"}) {
            var response = new MockHttpServletResponse(); response.setHeader("Cache-Control", "no-store");
            headers.preHandle(new MockHttpServletRequest("GET", path), response, resourceHandler);
            assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        }
        var response = new MockHttpServletResponse(); response.setHeader("Cache-Control", "no-store");
        headers.preHandle(new MockHttpServletRequest("GET", "/"), response, new Object());
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    }
}
