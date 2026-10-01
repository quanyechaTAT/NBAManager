package com.nbamanager.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * F4 回归：SPA 回退不得吞掉后端路径。
 *
 * 安全报告 §十 / 任务书 F4：`GET /api/ai-replay/games/1/explain`（未匹配子路径）
 * 此前返回 200 + index.html，健康检查与前端错误处理都会误判。
 * 修复：/api、/uploads、/actuator 前缀不再回退到 index.html，由各自处理链给出真实状态码。
 */
class SpaWebConfigTest {

    @Test
    void apiPathsAreNotTreatedAsSpaRoutes() {
        assertThat(SpaWebConfig.isBackendPath("api/ai-replay/games/1/explain")).isTrue();
        assertThat(SpaWebConfig.isBackendPath("api/definitely-not-exist")).isTrue();
        assertThat(SpaWebConfig.isBackendPath("api/profile")).isTrue();
        // 带前导斜杠也应识别
        assertThat(SpaWebConfig.isBackendPath("/api/posts")).isTrue();
        // 裸前缀
        assertThat(SpaWebConfig.isBackendPath("api")).isTrue();
    }

    @Test
    void uploadAndActuatorPathsAreNotTreatedAsSpaRoutes() {
        assertThat(SpaWebConfig.isBackendPath("uploads/chat/401881722/nope.png")).isTrue();
        assertThat(SpaWebConfig.isBackendPath("/uploads/avatars/nope.png")).isTrue();
        assertThat(SpaWebConfig.isBackendPath("actuator/health")).isTrue();
    }

    @Test
    void missingAssetsAreNotTreatedAsSpaRoutes() {
        // 第 7 轮 M7：缺失的 /assets/*.js.map 此前回退 index.html + 200，
        // 被安全扫描误判为 source map 泄露（实测响应体是 index.html，全仓并无 .map）
        assertThat(SpaWebConfig.isBackendPath("assets/index-DTdWdomv.js.map")).isTrue();
        assertThat(SpaWebConfig.isBackendPath("/assets/nope.js")).isTrue();
    }

    @Test
    void frontendRoutesStillFallBackToIndexHtml() {
        // 前端路由（hash 模式下的路径）必须继续回退，否则刷新页面会 404
        assertThat(SpaWebConfig.isBackendPath("posts/123")).isFalse();
        assertThat(SpaWebConfig.isBackendPath("teams/detail")).isFalse();
        assertThat(SpaWebConfig.isBackendPath("smart-search")).isFalse();
        assertThat(SpaWebConfig.isBackendPath("")).isFalse();
        assertThat(SpaWebConfig.isBackendPath(null)).isFalse();
    }

    @Test
    void staticAssetsAreServedByExistenceCheckBeforeBackendPathJudgment() {
        // 语义更新（第 7 轮 M7）：assets/ 现在属于"非回退前缀"——
        // 真实资产在 PathResourceResolver 里由 exists() 检查先行命中（照常服务），
        // 缺失的资产（如探测的 *.js.map）返回 404，而不是 index.html + 200。
        // 非资产前缀的静态文件（favicon.svg）不属于后端路径判定。
        assertThat(SpaWebConfig.isBackendPath("assets/index-abc.js")).isTrue();
        assertThat(SpaWebConfig.isBackendPath("favicon.svg")).isFalse();
    }

    @Test
    void similarButDifferentPrefixesAreStillSpaRoutes() {
        // 必须按完整路径段匹配，避免 "apixxx" 被误判为 api
        assertThat(SpaWebConfig.isBackendPath("apixyz/page")).isFalse();
        assertThat(SpaWebConfig.isBackendPath("uploadstuff/page")).isFalse();
    }
}