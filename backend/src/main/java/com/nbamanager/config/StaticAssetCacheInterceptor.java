package com.nbamanager.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

/** HTML is revalidated; only existing content-addressed build assets are immutable. */
public final class StaticAssetCacheInterceptor implements HandlerInterceptor {
    private static final Pattern HASHED_ASSET = Pattern.compile("assets/[\\w./-]+-[\\w-]{8,}\\.(js|css|woff2?|png|jpe?g|svg|webp|ico|gif|avif)");
    private final Resource index;
    private volatile String indexEtag;
    public StaticAssetCacheInterceptor() { this(new ClassPathResource("static/index.html")); }
    StaticAssetCacheInterceptor(Resource index) { this.index = index; }

    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!(handler instanceof ResourceHttpRequestHandler)
                || !("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()))) return true;
        String path = request.getRequestURI().substring(request.getContextPath().length()).replaceFirst("^/", "");
        // Never apply public caching to API responses or user uploads, including their 404s.
        if (path.contains("..") || path.contains("%") || path.startsWith("uploads/")
                || (SpaWebConfig.isBackendPath(path) && !path.startsWith("assets/"))) return true;
        Resource requested = new ClassPathResource("static/" + path);
        if (requested.exists() && requested.isReadable() && HASHED_ASSET.matcher(path).matches()) {
            response.setHeader("Cache-Control", "public, max-age=31536000, immutable");
            return true;
        }
        response.setHeader("Cache-Control", "no-cache");
        boolean entry = path.isEmpty() || "index.html".equals(path)
                || (!requested.exists() && !SpaWebConfig.isBackendPath(path));
        if (entry && index.exists()) {
            String etag = indexEtag;
            if (etag == null) {
                try { etag = "\"" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(index.getContentAsByteArray())) + "\""; }
                catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
                indexEtag = etag;
            }
            return !new ServletWebRequest(request, response).checkNotModified(etag);
        }
        return true;
    }
}
