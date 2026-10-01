package com.nbamanager.web.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nbamanager.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 分页参数边界拦截的回归测试。
 *
 * 对应安全测试 RISK：`/api/posts?page=abc` 过去被 Spring Data 静默回落成 200，
 * 客户端会拿到一份并非自己请求的数据；超大 page/size 则可能在 JPA 层变成 500。
 */
class PagingRequestInterceptorTest {

    private final PagingRequestInterceptor interceptor = new PagingRequestInterceptor();

    @Test
    void nonNumericPageIsRejectedInsteadOfSilentlyFallingBack() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/posts");
        request.setParameter("page", "abc");

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("page");
                });
    }

    @Test
    void nonNumericSizeIsRejected() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/players");
        request.setParameter("size", "xyz");

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("size");
                });
    }

    @Test
    void oversizedPageIsRejectedBeforeJpaOffsetOverflow() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/posts");
        request.setParameter("page", String.valueOf(Integer.MAX_VALUE));

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void oversizedSizeIsRejected() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/news");
        request.setParameter("size", "999999");

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void requestsWithoutPagingParametersAreUntouched() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/dashboard/stats");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
    }

    @Test
    void boundaryValuesInsideRangeAreAccepted() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/posts");
        request.setParameter("page", String.valueOf(PageRequestSanitizer.MAX_PAGE_NUMBER));
        request.setParameter("size", String.valueOf(PageRequestSanitizer.MAX_PAGE_SIZE));

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
    }

    @Test
    void nonPagingParameterNamedPageOnUnrelatedEndpointIsStillValidated() {
        // 参数名即契约：/api/** 下出现 page/size 就按分页语义校验，避免静默回落
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/jobs");
        request.setParameter("page", "1.5");

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(ApiException.class);
    }
}
