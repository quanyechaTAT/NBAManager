package com.nbamanager.config;

import com.nbamanager.security.JwtService;
import com.nbamanager.security.UserDetailsServiceImpl;
import com.nbamanager.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        StompCommand command = accessor.getCommand();
        if (command == null) return message;

        if (StompCommand.CONNECT.equals(command)) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                try {
                    String username = jwtService.extractUsername(token);
                    if (username != null && jwtService.isTokenValid(token, username)) {
                        UserPrincipal userPrincipal = (UserPrincipal) userDetailsService.loadUserByUsername(username);
                        // Store UserPrincipal as the principal so controller can cast directly
                        UsernamePasswordAuthenticationToken auth =
                                new UsernamePasswordAuthenticationToken(userPrincipal, null, userPrincipal.getAuthorities());
                        accessor.setUser(auth);
                        log.debug("WebSocket认证成功: {}", username);
                        return message;
                    }
                } catch (Exception e) {
                    log.warn("WebSocket JWT校验失败: {}", e.getMessage());
                }
            }
            log.warn("WebSocket连接拒绝: 无效的认证信息");
            return null;
        }

        if (StompCommand.SEND.equals(command) || StompCommand.SUBSCRIBE.equals(command)) {
            if (accessor.getUser() == null) {
                log.warn("拒绝未认证的{}操作: destination={}", command, accessor.getDestination());
                return null;
            }
        }

        return message;
    }
}
