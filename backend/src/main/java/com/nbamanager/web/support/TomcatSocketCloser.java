package com.nbamanager.web.support;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestWrapper;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.channels.SocketChannel;
import lombok.extern.slf4j.Slf4j;

/**
 * 关闭请求底层的客户端连接（{@link SlowRequestGuardFilter} 的"硬中断"手段）。
 *
 * 为什么 {@code Thread.interrupt()} 不够：它只能打断 sleep/wait/可中断通道，
 * 对 Tomcat NIO 的阻塞式 socket 读/写<b>无效</b>。第 5 轮实测：慢客户端
 * （每 64B 停 50ms 的慢请求体）触发守卫超时后，线程仍卡在读上，
 * 直到客户端停止滴水、再等满一个 soTimeout 才收到 408——单请求被拖到 64.82s。
 * 唯一可靠的回收手段是<b>从看门狗线程直接关闭这条连接</b>：
 * 阻塞中的读/写会立刻以 IOException 失败，工作线程随即归还线程池。
 *
 * Tomcat 没有公开"从 Request 拿 socket"的 API，这里走反射链：
 * <pre>
 * RequestFacade.request(字段) → connector.Request.getCoyoteRequest()
 *   → coyote.Request.hook(字段，即 processor) → AbstractProcessor.socketWrapper(字段)
 *   → SocketWrapperBase.getSocket() → NioChannel.getIOChannel() → SocketChannel.close()
 * </pre>
 * 反射句柄在类加载时解析一次；任何一步失败都 fail-open（{@link #close} 返回 false，
 * 调用方退回 interrupt-only），保护机制自身绝不能成为请求失败的原因。
 * Tomcat 升级若改名导致解析失败，{@link #isAvailable()} 变为 false——
 * 单测钉住了这一点，避免机制被静默禁用而无人察觉。
 */
@Slf4j
final class TomcatSocketCloser {

    private static final Field FACADE_REQUEST_FIELD;
    private static final Method CONNECTOR_GET_COYOTE;
    private static final Field COYOTE_HOOK;
    private static final Field PROCESSOR_SOCKET_WRAPPER;
    private static final Method WRAPPER_GET_SOCKET;
    private static final Method CHANNEL_GET_IO_CHANNEL;
    private static final boolean AVAILABLE;

    static {
        Field facadeRequestField = null;
        Method connectorGetCoyote = null;
        Field coyoteHook = null;
        Field processorSocketWrapper = null;
        Method wrapperGetSocket = null;
        Method channelGetIo = null;
        try {
            Class<?> facade = Class.forName("org.apache.catalina.connector.RequestFacade");
            Class<?> connectorRequest = Class.forName("org.apache.catalina.connector.Request");
            Class<?> coyoteRequest = Class.forName("org.apache.coyote.Request");
            Class<?> processor = Class.forName("org.apache.coyote.AbstractProcessor");
            Class<?> socketWrapper = Class.forName("org.apache.tomcat.util.net.SocketWrapperBase");
            Class<?> nioChannel = Class.forName("org.apache.tomcat.util.net.NioChannel");

            facadeRequestField = facade.getDeclaredField("request");
            facadeRequestField.setAccessible(true);
            connectorGetCoyote = connectorRequest.getMethod("getCoyoteRequest");
            coyoteHook = coyoteRequest.getDeclaredField("hook");
            coyoteHook.setAccessible(true);
            processorSocketWrapper = processor.getDeclaredField("socketWrapper");
            processorSocketWrapper.setAccessible(true);
            wrapperGetSocket = socketWrapper.getMethod("getSocket");
            channelGetIo = nioChannel.getMethod("getIOChannel");
        } catch (Throwable t) {
            log.warn("TomcatSocketCloser 初始化失败，慢请求守卫退化为 interrupt-only: {}", t.toString());
        }
        FACADE_REQUEST_FIELD = facadeRequestField;
        CONNECTOR_GET_COYOTE = connectorGetCoyote;
        COYOTE_HOOK = coyoteHook;
        PROCESSOR_SOCKET_WRAPPER = processorSocketWrapper;
        WRAPPER_GET_SOCKET = wrapperGetSocket;
        CHANNEL_GET_IO_CHANNEL = channelGetIo;
        AVAILABLE = facadeRequestField != null;
    }

    private TomcatSocketCloser() {}

    static boolean isAvailable() {
        return AVAILABLE;
    }

    /**
     * 尽力关闭该请求的底层客户端连接。绝不抛出；失败返回 false（调用方退回 interrupt）。
     */
    static boolean close(HttpServletRequest request) {
        if (!AVAILABLE) {
            return false;
        }
        try {
            Object req = request;
            while (req instanceof ServletRequestWrapper wrapper) {
                req = wrapper.getRequest();
            }
            Object connectorRequest = FACADE_REQUEST_FIELD.get(req);
            if (connectorRequest == null) {
                return false;
            }
            Object coyoteRequest = CONNECTOR_GET_COYOTE.invoke(connectorRequest);
            if (coyoteRequest == null) {
                return false;
            }
            Object proc = COYOTE_HOOK.get(coyoteRequest);
            if (proc == null) {
                return false;
            }
            Object wrapper = PROCESSOR_SOCKET_WRAPPER.get(proc);
            if (wrapper == null) {
                return false;
            }
            Object socket = WRAPPER_GET_SOCKET.invoke(wrapper);
            if (socket == null) {
                return false;
            }
            Object ioChannel = CHANNEL_GET_IO_CHANNEL.invoke(socket);
            if (ioChannel instanceof SocketChannel channel) {
                channel.close();
                return true;
            }
            if (socket instanceof AutoCloseable closeable) {
                closeable.close();
                return true;
            }
            return false;
        } catch (Throwable t) {
            // fail-open：关不掉就退回 interrupt-only，不能影响请求处理
            return false;
        }
    }
}
