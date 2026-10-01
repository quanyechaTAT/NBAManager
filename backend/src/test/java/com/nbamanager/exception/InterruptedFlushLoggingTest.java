package com.nbamanager.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/**
 * F5 回归：主动中断/客户端断连导致的响应写出失败，不应被记成"未处理异常"ERROR。
 *
 * 背景：SlowRequestGuardFilter 到点会 interrupt 工作线程，容器 flush 随即抛
 * IOException。此前它落到兜底处理器，按 ERROR + 完整堆栈记录——在慢客户端/压测场景下
 * 会刷出大量误导性错误，把真正的故障淹没（实测一次 30s 压测刷出 80 条）。
 */
class InterruptedFlushLoggingTest {

    private static boolean classify(Throwable t) throws Exception {
        Method m = GlobalExceptionHandler.class.getDeclaredMethod("isInterruptOrClientAbort", Throwable.class);
        m.setAccessible(true);
        return (boolean) m.invoke(new GlobalExceptionHandler(), t);
    }

    @Test
    void interruptedFlushIsClassifiedAsBenign() throws Exception {
        IOException interrupted = new IOException(
                "ServletOutputStream failed to flush: java.io.IOException: The current thread was interrupted",
                new InterruptedIOException("The current thread was interrupted"));

        assertThat(classify(interrupted))
                .as("主动中断导致的写出失败不是服务端故障")
                .isTrue();
    }

    @Test
    void clientAbortIsClassifiedAsBenign() throws Exception {
        IOException reset = new IOException("Connection reset by peer");
        IOException brokenPipe = new IOException("Broken pipe");

        assertThat(classify(reset)).isTrue();
        assertThat(classify(brokenPipe)).isTrue();
    }

    @Test
    void interruptedCauseChainIsDetected() throws Exception {
        IOException wrapped = new IOException("flush failed", new InterruptedException("stop"));

        assertThat(classify(wrapped)).isTrue();
    }

    @Test
    void genuineIoErrorStillTreatedAsServerFault() throws Exception {
        IOException disk = new IOException("No space left on device");

        assertThat(classify(disk))
                .as("普通 IO 错误必须仍按 500/ERROR 暴露，不能被降级掩盖")
                .isFalse();
    }
}
