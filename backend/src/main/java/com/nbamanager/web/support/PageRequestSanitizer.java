package com.nbamanager.web.support;

import com.nbamanager.exception.ApiException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

/**
 * 分页/排序入参消毒。
 *
 * 背景（压测与安全测试确认的 500 根因）：
 * 1) Spring Data 的 PageableHandlerMethodArgumentResolver 会把用户传入的 sort
 *    原样交给 JPA，未知属性触发 PropertyReferenceException（如
 *    /api/news?sort=class.classLoader），被全局兜底转成 500；
 * 2) PageRequest 的 offset = page * size 用 int 计算，page=2147483647 时溢出，
 *    抛 InvalidDataAccessApiUsageException 也是 500；
 * 3) 非数字 page（page=abc）会被解析器静默忽略并回落默认值，客户端拿不到反馈。
 *
 * 因此所有接收 Pageable 的列表端点统一走这里：
 * - 排序字段必须命中调用方声明的白名单（可配置别名，如 createdAt -> createTime），
 *   否则 400；
 * - page / size 超上限直接 400，不落库、不伪造成功；
 * - 未显式声明排序时给出稳定的默认字段，避免依赖实体字段名碰运气。
 */
public final class PageRequestSanitizer {

    /**
     * 单页最大条数（安全测试建议值）。防止 size=999999 这类请求把全表拉进内存。
     * 需要更多数据的场景（个人中心收藏、导出）由客户端翻页循环拉取，
     * 而不是把 size 调大绕过。
     */
    public static final int MAX_PAGE_SIZE = 100;

    /**
     * "取前 N 条"语义的上限，独立于分页 size。
     * 用于 /api/historical/players 这类 limit 参数（不是页码分页，没有翻页能力），
     * 前端历史数据页需要一次拿到榜单前列，故允许比 MAX_PAGE_SIZE 更大，但仍需有界。
     */
    public static final int MAX_TOP_N = 500;

    /**
     * 允许的最大页码。offset = page * size 必须留在 int 安全区间内，
     * 10000 * 100 = 1,000,000 远小于 Integer.MAX_VALUE。
     */
    public static final int MAX_PAGE_NUMBER = 10_000;

    private PageRequestSanitizer() {
    }

    /**
     * 校验并归一化分页参数。
     *
     * @param pageable    Spring MVC 解析出的分页参数（可能为 null / unpaged）
     * @param allowedSort 允许排序的实体属性白名单（为空表示该端点不允许自定义排序）
     * @param aliases     字段别名，如 createdAt -> createTime
     * @param defaultSort 未显式排序时使用的默认排序（为 null 表示不排序）
     */
    public static Pageable sanitize(
            Pageable pageable,
            Set<String> allowedSort,
            Map<String, String> aliases,
            Sort defaultSort) {
        if (pageable == null) {
            return build(0, 10, defaultSort, allowedSort, aliases);
        }
        if (pageable.isUnpaged()) {
            // 代码内部调用（如 Pageable.unpaged()）不经过 HTTP 层，直接放行由调用方处理
            return Pageable.unpaged();
        }

        int page = pageable.getPageNumber();
        int size = pageable.getPageSize();
        if (page < 0) {
            throw badRequest("page 不能为负数");
        }
        if (page > MAX_PAGE_NUMBER) {
            throw badRequest("page 超出上限 " + MAX_PAGE_NUMBER);
        }
        if (size < 1) {
            throw badRequest("size 必须大于 0");
        }
        if (size > MAX_PAGE_SIZE) {
            throw badRequest("size 超出上限 " + MAX_PAGE_SIZE);
        }

        return build(page, size, pageable.getSort(), allowedSort, aliases, defaultSort);
    }

    /**
     * 仅校验/归一化排序（用于已自行分页、只借用排序的场景）。
     */
    public static Sort sanitizeSort(
            Sort sort,
            Set<String> allowedSort,
            Map<String, String> aliases,
            Sort defaultSort) {
        if (sort == null || sort.isUnsorted()) {
            return defaultSort;
        }
        if (allowedSort == null || allowedSort.isEmpty()) {
            throw badRequest("该接口不支持自定义排序");
        }
        List<Sort.Order> orders = new java.util.ArrayList<>();
        Map<String, String> safeAliases = aliases == null ? Map.of() : aliases;
        for (Sort.Order order : sort) {
            String requested = order.getProperty();
            if (requested == null || requested.isBlank()) {
                throw badRequest("排序字段不能为空");
            }
            String property = safeAliases.getOrDefault(requested, requested);
            if (!allowedSort.contains(property)) {
                throw badRequest("不支持的排序字段: " + requested);
            }
            orders.add(new Sort.Order(order.getDirection(), property, order.getNullHandling()));
        }
        if (orders.isEmpty()) {
            return defaultSort;
        }
        // 多字段排序去重：同一字段重复出现时后者覆盖前者，避免 JPA 生成重复 ORDER BY
        Map<String, Sort.Order> deduped = new LinkedHashMap<>();
        for (Sort.Order order : orders) {
            deduped.put(order.getProperty(), order);
        }
        return Sort.by(new java.util.ArrayList<>(deduped.values()));
    }

    /** 便捷重载：无别名、无默认排序。 */
    public static Pageable sanitize(Pageable pageable, Set<String> allowedSort) {
        return sanitize(pageable, allowedSort, Map.of(), null);
    }

    /**
     * 只保留分页、丢弃用户排序。
     *
     * 适用于排序语义由仓储方法名固定的端点（如"热门帖子"固定按 viewCount 倒序）：
     * 这类端点既不该被非法 sort 打出 500，也不该被合法 sort 悄悄改变语义。
     */
    public static Pageable pageOnly(Pageable pageable) {
        if (pageable == null) {
            return PageRequest.of(0, 10);
        }
        if (pageable.isUnpaged()) {
            return Pageable.unpaged();
        }
        int page = pageable.getPageNumber();
        int size = pageable.getPageSize();
        if (page < 0) {
            throw badRequest("page 不能为负数");
        }
        if (page > MAX_PAGE_NUMBER) {
            throw badRequest("page 超出上限 " + MAX_PAGE_NUMBER);
        }
        if (size < 1) {
            throw badRequest("size 必须大于 0");
        }
        if (size > MAX_PAGE_SIZE) {
            throw badRequest("size 超出上限 " + MAX_PAGE_SIZE);
        }
        return PageRequest.of(page, size);
    }

    private static Pageable build(
            int page, int size, Sort sort, Set<String> allowedSort, Map<String, String> aliases) {
        return build(page, size, sort, allowedSort, aliases, null);
    }

    private static Pageable build(
            int page,
            int size,
            Sort sort,
            Set<String> allowedSort,
            Map<String, String> aliases,
            Sort defaultSort) {
        Sort safeSort = sanitizeSort(sort, allowedSort, aliases, defaultSort);
        return safeSort == null || safeSort.isUnsorted()
                ? PageRequest.of(page, size)
                : PageRequest.of(page, size, safeSort);
    }

    /**
     * 校验"取前 N 条"参数（limit）。
     *
     * 与分页 size 采用同一原则：超限直接 400，而不是静默夹紧——
     * 静默夹紧会让客户端拿到一份并非自己请求的数据（安全报告把同类行为记为 RISK）。
     */
    public static int requireTopN(int limit, String parameterName) {
        if (limit < 1) {
            throw badRequest("参数 " + parameterName + " 必须大于 0");
        }
        if (limit > MAX_TOP_N) {
            throw badRequest("参数 " + parameterName + " 超出上限 " + MAX_TOP_N);
        }
        return limit;
    }

    private static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
