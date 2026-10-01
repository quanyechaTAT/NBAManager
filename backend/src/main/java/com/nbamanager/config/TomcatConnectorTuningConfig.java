package com.nbamanager.config;

import java.lang.reflect.Method;
import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.AbstractProtocol;
import org.apache.coyote.ProtocolHandler;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.SocketProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

/**
 * Tomcat connector 超时调优（压测报告 §十 10.6 P5）。
 *
 * 现象：慢客户端（每 64B 停 50ms）能把单请求拖到 <b>59.5s</b>，服务端全程不主动断开，
 * 属 Slowloris 类资源占用面——少量连接即可长期占住工作线程。
 *
 * 实测结论（本轮逐一验证，避免重复踩坑）：
 * <table>
 *   <tr><th>机制</th><th>是否有效</th><th>说明</th></tr>
 *   <tr><td>connection-timeout</td><td>部分</td>
 *       <td>只约束"读取请求行/请求头"的空闲；实测请求头不全时 6.1s 即断开</td></tr>
 *   <tr><td>socket soTimeout</td><td>否（对该模式）</td>
 *       <td>约束"两次读之间的空闲"；慢客户端每 50ms 就发数据，空闲维度永不触发</td></tr>
 *   <tr><td>connectionUploadTimeout</td><td>否（对该模式）</td>
 *       <td>仅对 {@code Expect: 100-continue}/multipart 等上传语义生效</td></tr>
 *   <tr><td>应用层总时长上限</td><td><b>是</b></td>
 *       <td>{@code SlowRequestGuardFilter}：按"总时长"封顶，是真正的防线</td></tr>
 * </table>
 *
 * 因此本类只作为"纵深防御"的一层：把 connector 的读空闲超时从默认 60s 收到 30s，
 * 缩短 Half-Open/停滞连接的占用窗口；真正兜住"持续极慢发送"的是
 * {@link com.nbamanager.web.support.SlowRequestGuardFilter}。
 */
@Slf4j
@Component
public class TomcatConnectorTuningConfig
        implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    @Value("${app.http.socket-so-timeout-ms:${TOMCAT_SOCKET_SO_TIMEOUT_MS:30000}}")
    private int socketSoTimeoutMs;

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        factory.addConnectorCustomizers(connector -> {
            ProtocolHandler handler = connector.getProtocolHandler();
            if (!(handler instanceof AbstractProtocol<?> protocol)) {
                return;
            }
            protocol.setConnectionTimeout(socketSoTimeoutMs);
            protocol.setKeepAliveTimeout(socketSoTimeoutMs);
            boolean socketApplied = applySocketSoTimeout(protocol);
            boolean uploadApplied = applyUploadTimeout(handler);
            log.info("Tomcat connector 超时已设置: connectionTimeout={}ms, socket soTimeout={}, upload timeout={}",
                    socketSoTimeoutMs,
                    socketApplied ? socketSoTimeoutMs + "ms" : "未应用",
                    uploadApplied ? socketSoTimeoutMs + "ms" : "未应用");
        });
    }

    /**
     * 请求体读取阶段的超时（Slowloris 的关键防线）。
     *
     * 实测：慢客户端"持续但极慢地"发 body（每 50ms 64B）时，
     * connection-timeout 与 socket soTimeout 都不触发——两者都只看"空闲多久"，
     * 而每 50ms 就有新数据。Tomcat 对此提供的专用开关是
     * {@code disableUploadTimeout=false} + {@code connectionUploadTimeout}：
     * 前者启用上传超时，后者给出 body 读取的时长上限。
     * 这也解释了为什么该慢连接此前能拖到 59.5s 且请求根本没进到 servlet（过滤器测不到）。
     */
    private boolean applyUploadTimeout(ProtocolHandler handler) {
        try {
            if (handler instanceof org.apache.coyote.http11.AbstractHttp11Protocol<?> http) {
                http.setConnectionUploadTimeout(socketSoTimeoutMs);
                http.setDisableUploadTimeout(false);
                return true;
            }
        } catch (RuntimeException e) {
            log.warn("设置 upload timeout 失败: {}", e.toString());
        }
        return false;
    }

    /** 通过反射拿到 protected 的 endpoint，再设置其 SocketProperties.soTimeout。 */
    private boolean applySocketSoTimeout(AbstractProtocol<?> protocol) {
        try {
            Method getEndpoint = AbstractProtocol.class.getDeclaredMethod("getEndpoint");
            getEndpoint.setAccessible(true);
            Object endpoint = getEndpoint.invoke(protocol);
            if (!(endpoint instanceof AbstractEndpoint<?, ?> ep)) {
                return false;
            }
            SocketProperties props = ep.getSocketProperties();
            if (props == null) {
                return false;
            }
            props.setSoTimeout(socketSoTimeoutMs);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            log.warn("设置 socket soTimeout 失败（慢客户端保护降级为仅 connection-timeout）: {}", e.toString());
            return false;
        }
    }
}
