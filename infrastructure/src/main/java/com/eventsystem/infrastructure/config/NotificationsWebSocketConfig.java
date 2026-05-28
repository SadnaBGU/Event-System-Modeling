package com.eventsystem.infrastructure.config;

import com.eventsystem.application.member.NotificationBroadcaster;
import com.eventsystem.application.security.ITokenService;
import com.eventsystem.application.security.ITokenService.TokenClaims;
import com.eventsystem.domain.member.Notification;
import com.eventsystem.infrastructure.api.notifications.NotificationDto;

import io.micrometer.common.lang.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;
import java.time.ZoneOffset;
import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
public class NotificationsWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger log = LoggerFactory.getLogger(NotificationsWebSocketConfig.class);

    private final ITokenService tokenService;

    public NotificationsWebSocketConfig(ITokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/queue");
        config.setUserDestinationPrefix("/user");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/api/notifications/stream").setAllowedOriginPatterns("*").withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    List<String> auth = accessor.getNativeHeader("Authorization");
                    if (auth == null || auth.isEmpty()) {
                        throw new IllegalArgumentException("Missing Authorization header in STOMP CONNECT");
                    }
                    String header = auth.get(0);
                    if (!header.startsWith("Bearer ")) {
                        throw new IllegalArgumentException("Malformed Authorization header in STOMP CONNECT");
                    }
                    String token = header.substring(7);
                    TokenClaims claims = tokenService.verifyToken(token);
                    Principal user = new Principal() {
                        @Override
                        public String getName() {
                            return claims.subject().value();
                        }
                    };
                    accessor.setUser(user);
                }
                return message;
            }
        });
    }

    @Bean
    public NotificationBroadcaster notificationBroadcaster(SimpMessagingTemplate template) {
        return (memberId, notification) -> {
            NotificationDto dto = new NotificationDto(
                    notification.getNotificationId(),
                    notification.getType().name(),
                    notification.getContent(),
                    notification.getCreatedAt().atOffset(ZoneOffset.UTC).toString(),
                    notification.isDelivered());
            template.convertAndSendToUser(memberId, "/queue/notifications", dto);
        };
    }
}
