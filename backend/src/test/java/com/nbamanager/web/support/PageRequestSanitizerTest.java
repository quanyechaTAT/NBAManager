package com.nbamanager.web.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nbamanager.exception.ApiException;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

/**
 * 分页/排序消毒的回归测试。
 *
 * 对应压测与安全测试报告中的 RISK：
 * - `/api/news|players|teams|notifications?sort=class.classLoader` 曾返回 500（S9：772 个 500）
 * - `/api/posts?page=2147483647` 曾因 offset 溢出返回 500
 */
class PageRequestSanitizerTest {

    private static final Set<String> ALLOWED = Set.of("createTime", "viewCount", "id");
    private static final Map<String, String> ALIASES = Map.of("createdAt", "createTime");
    private static final Sort DEFAULT = Sort.by(Sort.Direction.DESC, "createTime");

    @Test
    void unknownSortIsRejectedAsBadRequestInsteadOfFiveHundred() {
        PageRequest request = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "class.classLoader"));

        assertThatThrownBy(() -> PageRequestSanitizer.sanitize(request, ALLOWED, ALIASES, DEFAULT))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("排序字段");
                });
    }

    @Test
    void aliasIsMappedToRealEntityProperty() {
        PageRequest request = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));

        Pageable sanitized = PageRequestSanitizer.sanitize(request, ALLOWED, ALIASES, DEFAULT);

        assertThat(sanitized.getSort().getOrderFor("createdAt")).isNull();
        assertThat(sanitized.getSort().getOrderFor("createTime")).isNotNull();
        assertThat(sanitized.getSort().getOrderFor("createTime").isDescending()).isTrue();
    }

    @Test
    void oversizedPageBeyondIntOffsetRangeIsRejected() {
        PageRequest request = PageRequest.of(Integer.MAX_VALUE, 20);

        assertThatThrownBy(() -> PageRequestSanitizer.sanitize(request, ALLOWED, ALIASES, DEFAULT))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("page");
                });
    }

    @Test
    void oversizedPageSizeIsRejected() {
        PageRequest request = PageRequest.of(0, 5000);

        assertThatThrownBy(() -> PageRequestSanitizer.sanitize(request, ALLOWED, ALIASES, DEFAULT))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("size");
                });
    }

    @Test
    void defaultSortIsAppliedWhenClientDoesNotAskForOne() {
        Pageable sanitized = PageRequestSanitizer.sanitize(
                PageRequest.of(2, 10), ALLOWED, ALIASES, DEFAULT);

        assertThat(sanitized.getPageNumber()).isEqualTo(2);
        assertThat(sanitized.getPageSize()).isEqualTo(10);
        assertThat(sanitized.getSort().getOrderFor("createTime")).isNotNull();
    }

    @Test
    void duplicateSortPropertiesAreCollapsedToLastOccurrence() {
        Sort sort = Sort.by(Sort.Order.asc("viewCount"), Sort.Order.desc("viewCount"));

        Sort sanitized = PageRequestSanitizer.sanitizeSort(sort, ALLOWED, ALIASES, DEFAULT);

        assertThat(sanitized.stream().count()).isEqualTo(1);
        assertThat(sanitized.getOrderFor("viewCount").isDescending()).isTrue();
    }

    @Test
    void unpagedRequestsArePassedThrough() {
        assertThat(PageRequestSanitizer.sanitize(Pageable.unpaged(), ALLOWED, ALIASES, DEFAULT).isUnpaged())
                .isTrue();
    }

    @Test
    void documentRecommendedPageSizeCeilingIsEnforced() {
        // 安全测试建议 size <= 100；101 必须被拒，避免"把上限调大绕过"的回归
        assertThat(PageRequestSanitizer.MAX_PAGE_SIZE).isEqualTo(100);
        assertThatThrownBy(() -> PageRequestSanitizer.sanitize(
                PageRequest.of(0, 101), ALLOWED, ALIASES, DEFAULT))
                .isInstanceOf(ApiException.class);
        assertThat(PageRequestSanitizer.sanitize(
                PageRequest.of(0, 100), ALLOWED, ALIASES, DEFAULT).getPageSize()).isEqualTo(100);
    }

    @Test
    void topNCeilingIsIndependentFromPageSizeCeiling() {
        // /historical/players 的 limit 是"前 N 条"，无翻页能力，允许更大但仍有界
        assertThat(PageRequestSanitizer.MAX_TOP_N).isGreaterThan(PageRequestSanitizer.MAX_PAGE_SIZE);
    }

    @Test
    void topNRejectsInsteadOfSilentlyClamping() {
        // 历史数据页 limit 参数：静默夹紧会让客户端拿到并非自己请求的数据
        assertThat(PageRequestSanitizer.requireTopN(500, "limit")).isEqualTo(500);
        assertThatThrownBy(() -> PageRequestSanitizer.requireTopN(501, "limit"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("limit");
                });
        assertThatThrownBy(() -> PageRequestSanitizer.requireTopN(0, "limit"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void pageOnlyDropsUserSortButKeepsBounds() {
        Pageable sanitized = PageRequestSanitizer.pageOnly(
                PageRequest.of(1, 10, Sort.by(Sort.Direction.ASC, "bogus.property")));

        assertThat(sanitized.getPageNumber()).isEqualTo(1);
        assertThat(sanitized.getPageSize()).isEqualTo(10);
        assertThat(sanitized.getSort().isUnsorted()).isTrue();
    }

    @Test
    void pageOnlyStillRejectsOverflowingPage() {
        assertThatThrownBy(() -> PageRequestSanitizer.pageOnly(PageRequest.of(Integer.MAX_VALUE, 100)))
                .isInstanceOf(ApiException.class);
    }
}
