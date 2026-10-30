package vn.edu.uit.edulens.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService customUserDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (!StringUtils.hasText(authHeader)) {
                authHeader = accessor.getFirstNativeHeader("authorization");
            }
            if (!StringUtils.hasText(authHeader)) {
                authHeader = accessor.getPasscode();
            }

            if (StringUtils.hasText(authHeader)) {
                String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7).trim() : authHeader.trim();
                try {
                    if (jwtTokenProvider.validateToken(token)) {
                        UUID userId = jwtTokenProvider.getUserIdFromToken(token);
                        UserDetails userDetails = customUserDetailsService.loadUserById(userId);
                        if (userDetails != null && userDetails.isEnabled()) {
                            UsernamePasswordAuthenticationToken authentication =
                                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                            if (accessor.isMutable()) {
                                accessor.setUser(authentication);
                            } else {
                                StompHeaderAccessor mutableAccessor = StompHeaderAccessor.create(accessor.getCommand());
                                mutableAccessor.copyHeaders(accessor.toMap());
                                mutableAccessor.setUser(authentication);
                                message = org.springframework.messaging.support.MessageBuilder.createMessage(message.getPayload(), mutableAccessor.getMessageHeaders());
                            }
                            log.info("WebSocket connection authenticated for user: {}", userDetails.getUsername());
                        }
                    } else {
                        log.warn("WebSocket CONNECT rejected: invalid JWT token");
                    }
                } catch (Exception ex) {
                    log.error("WebSocket authentication error: {}", ex.getMessage());
                }
            } else {
                log.debug("WebSocket CONNECT without Authorization header");
            }
        }
        return message;
    }
}
