package com.nbamanager.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.support.AbstractSubscribableChannel;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * WebSocket broker 配置回归测试。
 *
 * 背景：服务端用 convertAndSendToUser(..., "/queue/chat-errors") 下发限流/校验回执，
 * UserDestinationMessageHandler 会把 /user 目的地解析成 "/queue/xxx-user&lt;session&gt;"。
 * 若 simple broker 只注册 "/topic"，这些用户队列在 broker 层没有匹配订阅，回执无处投递
 * 并被退回走"未解析"分支，最终表现为：
 *   - 前端 /user/queue/chat-errors 永远收不到错误回执（订阅形同虚设）
 *   - 服务端刷 "Expected mutable SimpMessageHeaderAccessor" ERROR
 *     （压测报告记录 6241 条，占日志 2.2%；实测仅给 /topic 时 12 并发必现）
 * 因此这里锁住"/queue 必须注册"这一契约。
 */
class WebSocketBrokerConfigTest {

    private static MessageBrokerRegistry newRegistry() {
        SubscribableChannel inbound = new ExecutorSubscribableChannel();
        MessageChannel outbound = new ExecutorSubscribableChannel();
        return new MessageBrokerRegistry(inbound, outbound);
    }

    @Test
    void simpleBrokerCoversUserQueuesInAdditionToTopics() {
        MessageBrokerRegistry registry = newRegistry();

        new WebSocketConfig(null).configureMessageBroker(registry);

        Object simpleBroker = ReflectionTestUtils.getField(registry, "simpleBrokerRegistration");
        assertThat(simpleBroker).as("configureMessageBroker 必须注册 simple broker").isNotNull();
        Collection<String> prefixes = (Collection<String>) ReflectionTestUtils.invokeGetterMethod(
                simpleBroker, "destinationPrefixes");
        assertThat(prefixes)
                .as("用户回执队列走 /queue，未注册会导致回执丢失并刷 ERROR 日志")
                .contains("/topic", "/queue");
    }

    @Test
    void userDestinationPrefixRemainsUser() {
        MessageBrokerRegistry registry = newRegistry();

        new WebSocketConfig(null).configureMessageBroker(registry);

        assertThat(ReflectionTestUtils.invokeGetterMethod(registry, "userDestinationPrefix"))
                .isEqualTo("/user");
    }

    @Test
    void applicationDestinationPrefixRemainsApp() {
        MessageBrokerRegistry registry = newRegistry();

        new WebSocketConfig(null).configureMessageBroker(registry);

        assertThat((Collection<String>) ReflectionTestUtils.invokeGetterMethod(
                registry, "applicationDestinationPrefixes")).contains("/app");
    }
}
