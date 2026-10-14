package com.nbamanager.config;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * SPA 前端路由回退。
 *
 * 前端用 createWebHashHistory，实际只需要 index.html 兜底；但这里对
 * 非静态资源路径一律回退到 index.html。此前回退范围包含 /api/**，
 * 导致未匹配的 API 路径返回 200 + index.html 而不是 404 JSON
 * （安全报告 §十 10.5 / 任务书 F4）：
 * 健康检查与探测脚本会误判"接口存在但返回异常"，前端错误处理也拿不到真实状态码。
 *
 * 现在只对"非后端路径"回退，把 /api、/uploads、/actuator、/assets 留给
 * 各自的处理链（控制器 / 静态目录 / 404）。
 *
 * assets/ 是第 7 轮 M7 的补充：缺失的静态资产（如探测的 *.js.map）此前会
 * 回退成 index.html + 200，安全扫描误判为 source map 泄露（实测 200 的响应体
 * 是 index.html，全仓并无 .map 文件）；真实资产由静态目录照常服务，
 * 缺失的返回 404 才是正确语义。
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new StaticAssetCacheInterceptor());
    }

    /** 这些前缀下的未匹配路径不应回退到 index.html，而应返回真实状态码。 */
    private static final String[] NON_SPA_PREFIXES = { "api/", "uploads/", "actuator/", "assets/" };

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        if (isBackendPath(resourcePath)) {
                            // 返回 null 让请求以 404 结束，而不是回退成 SPA 页面
                            return null;
                        }
                        return new ClassPathResource("/static/index.html");
                    }
                });
    }

    /**
     * 判断是否为后端路径（去掉前导 '/' 后按前缀匹配）。
     * 同时忽略查询串已在框架层被剥离，这里只处理路径本身。
     */
    static boolean isBackendPath(String resourcePath) {
        if (resourcePath == null) {
            return false;
        }
        String normalized = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
        for (String prefix : NON_SPA_PREFIXES) {
            if (normalized.equals(prefix.substring(0, prefix.length() - 1)) || normalized.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
