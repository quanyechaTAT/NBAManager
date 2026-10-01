package com.nbamanager.web.support;

import com.nbamanager.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 分页查询参数边界校验（对所有请求生效，不依赖参数解析器注册顺序）。
 *
 * 为什么需要它：
 * - Spring Data 的 Pageable 解析器对非法 page/size 是静默容忍的：
 *   `page=abc` 会回落默认值并返回 200（安全测试记为 RISK —— 客户端拿到
 *   一份并非自己请求的数据）；`size=999999` 会把大结果集拉进内存；
 * - `page=2147483647` 更严重：PageRequest 的 offset = page * size 用 int 计算，
 *   溢出后在 JPA 层抛 InvalidDataAccessApiUsageException，被兜底成 500。
 *
 * 因此在进入 Controller 之前统一把关：非法即 400。
 * 排序字段白名单仍在各 Service 内维护（语义属于业务层）。
 */
@Component
public class PagingRequestInterceptor implements HandlerInterceptor {

    static final String PAGE_PARAM = "page";
    static final String SIZE_PARAM = "size";

    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler) {
        // 只校验落在分页语义上的 query 参数；其他接口即使带同名参数也不会被误伤
        if (!request.getParameterMap().containsKey(PAGE_PARAM)
                && !request.getParameterMap().containsKey(SIZE_PARAM)) {
            return true;
        }
        requireWithinRange(request, PAGE_PARAM, 0, PageRequestSanitizer.MAX_PAGE_NUMBER);
        requireWithinRange(request, SIZE_PARAM, 1, PageRequestSanitizer.MAX_PAGE_SIZE);
        return true;
    }

    private static void requireWithinRange(
            HttpServletRequest request, String parameter, int min, int max) {
        String raw = request.getParameter(parameter);
        if (raw == null || raw.isBlank()) {
            return;
        }
        int value;
        try {
            value = Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "参数 " + parameter + " 必须是整数");
        }
        if (value < min || value > max) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "参数 " + parameter + " 必须在 " + min + " 到 " + max + " 之间");
        }
    }
}
