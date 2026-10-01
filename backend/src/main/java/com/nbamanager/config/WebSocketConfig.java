package com.nbamanager.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor authInterceptor;

    @Value("${app.websocket.allowed-origins:http://localhost:*,http://127.0.0.1:*}")
    private String allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        /*
         * 必须包含 "/queue"：
         * 服务端用 convertAndSendToUser(..., "/queue/chat-errors") 给用户回执（限流/参数错误），
         * UserDestinationMessageHandler 会把 /user 目的地解析成 "/queue/xxx-user<session>" 再投给 broker。
         * 此前 simple broker 只订阅了 "/topic"，解析后的用户队列在 broker 层没有匹配的订阅，
         * 消息无处投递并被退回走"未解析"分支，最终表现是：
         *   - 客户端永远收不到限流/校验错误回执（前端 /user/queue/chat-errors 订阅形同虚设）
         *   - 服务端刷 "Failed to send GenericMessage ... Expected mutable SimpMessageHeaderAccessor"
         *     ERROR（压测期间 2.2% 的日志量），因为被退回的消息二次进入 OrderedMessageChannelDecorator。
         * 加上 "/queue" 后回执可达，退回分支不再触发。
         */
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 保留收发顺序：聊天消息必须按发送顺序广播，避免同一客户端的消息乱序
        registry.setPreserveReceiveOrder(true);
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins.split(","))
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }
}
