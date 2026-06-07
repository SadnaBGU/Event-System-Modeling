package com.eventsystem.infrastructure.config;

import com.eventsystem.application.security.ITokenService;
import com.eventsystem.domain.member.MemberId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.simp.config.ChannelRegistration;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationsWebSocketConfigTest {

    @Mock
    ITokenService tokenService;

    // use a real ChannelRegistration subclass to capture interceptors
    // (Mockito cannot reliably intercept varargs on this final-ish class in all environments)
    // ChannelRegistration registration;

    @SuppressWarnings("null")
    @Test
    void configureClientInboundChannel_registersInterceptor_and_preSend_behaviors() {
        NotificationsWebSocketConfig cfg = new NotificationsWebSocketConfig(tokenService);

        // capture the interceptor array when registration.interceptors(...) is called
        final AtomicReference<ChannelInterceptor[]> holder = new AtomicReference<>();

        ChannelRegistration realRegistration = new ChannelRegistration() {
            @Override
            public ChannelRegistration interceptors(ChannelInterceptor... interceptors) {
                holder.set(interceptors);
                return this;
            }
        };

        cfg.configureClientInboundChannel(realRegistration);

        ChannelInterceptor[] interceptors = holder.get();
        assertThat(interceptors).isNotNull().isNotEmpty();
        ChannelInterceptor interceptor = interceptors[0];

        // 1) missing Authorization header -> expect IllegalArgumentException
        StompHeaderAccessor a1 = StompHeaderAccessor.create(StompCommand.CONNECT);
        a1.setLeaveMutable(true);
        Message<byte[]> m1 = MessageBuilder.createMessage(new byte[0], a1.getMessageHeaders());
        assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(m1, mock(MessageChannel.class)));

        // 2) malformed Authorization header -> expect IllegalArgumentException
        StompHeaderAccessor a2 = StompHeaderAccessor.create(StompCommand.CONNECT);
        a2.setLeaveMutable(true);
        a2.setNativeHeader("Authorization", "Token abc");
        Message<byte[]> m2 = MessageBuilder.createMessage(new byte[0], a2.getMessageHeaders());
        assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(m2, mock(MessageChannel.class)));

        // 3) valid Bearer token -> tokenService.verifyToken returns claims and user is set
        StompHeaderAccessor a3 = StompHeaderAccessor.create(StompCommand.CONNECT);
        a3.setLeaveMutable(true);
        a3.setNativeHeader("Authorization", "Bearer good-token");
        Message<byte[]> m3 = MessageBuilder.createMessage(new byte[0], a3.getMessageHeaders());

        when(tokenService.verifyToken("good-token")).thenReturn(new ITokenService.TokenClaims(new MemberId("actor-42"), Instant.now(), Instant.now().plusSeconds(60)));

        @SuppressWarnings("null")
        Message<?> out = interceptor.preSend(m3, mock(MessageChannel.class));
        @SuppressWarnings("null")
        StompHeaderAccessor outAcc = MessageHeaderAccessor.getAccessor(out, StompHeaderAccessor.class);
        assertThat(outAcc.getUser()).isNotNull();
        assertThat(outAcc.getUser().getName()).isEqualTo("actor-42");
    }
}
