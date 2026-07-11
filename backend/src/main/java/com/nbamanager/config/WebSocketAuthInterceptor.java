package com.nbamanager.config;

import com.nbamanager.security.JwtService;
import com.nbamanager.security.UserDetailsServiceImpl;
import com.nbamanager.security.UserPrincipal;
import java.security.Principal;
import java.util.Map;
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

    private static final String SESSION_AUTH_KEY = "AUTHENTICATED_USER";

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
                        UsernamePasswordAuthenticationToken auth =
                                new UsernamePasswordAuthenticationToken(userPrincipal, null, userPrincipal.getAuthorities());

                        accessor.setUser(auth);

                        // 持久化到 session attributes，确保后续 SUBSCRIBE/SEND 帧能恢复身份
                        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
                        if (sessionAttributes != null) {
                            sessionAttributes.put(SESSION_AUTH_KEY, auth);
                        }

                        log.info("WebSocket CONNECT authenticated: username={}, sessionId={}",
                                username, accessor.getSessionId());
                        return message;
                    }
                } catch (Exception e) {
                    log.warn("WebSocket JWT校验失败: {}", e.getMessage());
                }
            }

            log.warn("WebSocket CONNECT rejected: invalid auth, sessionId={}", accessor.getSessionId());
            return null;
        }

        if (StompCommand.SEND.equals(command) || StompCommand.SUBSCRIBE.equals(command)) {
            Principal user = accessor.getUser();

            // 如果 accessor.getUser() 为空，从 session attributes 恢复
            if (user == null) {
                Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
                Object storedAuth = sessionAttributes != null ? sessionAttributes.get(SESSION_AUTH_KEY) : null;
                if (storedAuth instanceof Principal principal) {
                    accessor.setUser(principal);
                    user = principal;
                }
            }

            if (user == null) {
                log.warn("Rejected unauthenticated STOMP {}: sessionId={}, destination={}",
                        command, accessor.getSessionId(), accessor.getDestination());
                return null;
            }

            log.info("STOMP {} accepted: sessionId={}, user={}, destination={}",
                    command, accessor.getSessionId(), user.getName(), accessor.getDestination());
        }

        return message;
    }
}
