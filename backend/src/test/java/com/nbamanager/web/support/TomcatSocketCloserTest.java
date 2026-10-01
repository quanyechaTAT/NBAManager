package com.nbamanager.web.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * 慢请求守卫的"硬中断"手段：从看门狗线程关闭请求底层客户端连接。
 *
 * 背景：{@code Thread.interrupt()} 对 Tomcat NIO 阻塞式 socket 读/写无效
 * （第 5 轮实测：慢客户端触发守卫超时后，线程仍卡在读上直到 soTimeout，
 * 单请求被拖到 64.82s）。唯一可靠的回收手段是关闭底层连接。
 *
 * 反射链（RequestFacade→coyote.Request.hook→processor.socketWrapper→SocketChannel）
 * 依赖 Tomcat 内部结构：本测试钉住两点——
 * 1. 链在当前 Tomcat 版本上必须解析成功（升级后断链会被发现，而不是静默失效）；
 * 2. 对非 Tomcat 请求（如 Mock）必须 fail-open 返回 false，绝不抛出。
 */
class TomcatSocketCloserTest {

    @Test
    void reflectiveChainResolvesOnCurrentTomcat() {
        assertThat(TomcatSocketCloser.isAvailable())
                .as("反射链应解析成功；若 Tomcat 升级改名导致断链，请同步更新 TomcatSocketCloser")
                .isTrue();
    }

    @Test
    void failsOpenForNonTomcatRequests() {
        // MockHttpServletRequest 没有 RequestFacade 的内部结构：
        // close 必须安静地返回 false（fail-open），不能抛出
        assertThat(TomcatSocketCloser.close(new MockHttpServletRequest())).isFalse();
        assertThat(TomcatSocketCloser.close(null)).isFalse();
    }
}
