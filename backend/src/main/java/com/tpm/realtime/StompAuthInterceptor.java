package com.tpm.realtime;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;

import com.tpm.project.MemberRoleCache;

/**
 * Secures the WebSocket channel. Browsers can't set headers on the WebSocket handshake, so the
 * JWT is sent in the STOMP CONNECT frame and validated with the same decoder as the REST API.
 * Every SUBSCRIBE to a project topic is then checked against project membership.
 */
@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter converter;
    private final MemberRoleCache roles;

    public StompAuthInterceptor(JwtDecoder jwtDecoder, JwtAuthenticationConverter converter, MemberRoleCache roles) {
        this.jwtDecoder = jwtDecoder;
        this.converter = converter;
        this.roles = roles;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        var accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        switch (accessor.getCommand()) {
            case CONNECT -> accessor.setUser(authenticate(accessor.getFirstNativeHeader("Authorization")));
            case SUBSCRIBE -> authorizeSubscribe(accessor);
            case SEND -> requireUser(accessor);
            case null, default -> { }
        }
        return message;
    }

    private Authentication authenticate(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            throw new MessageDeliveryException("Missing bearer token");
        }
        try {
            return converter.convert(jwtDecoder.decode(header.substring(7)));
        } catch (JwtException e) {
            throw new MessageDeliveryException("Invalid token: " + e.getMessage());
        }
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        var user = requireUser(accessor);
        var destination = accessor.getDestination();
        if (destination != null && destination.startsWith("/user/queue/")) return; // user-scoped by Spring

        Long projectId = Destinations.projectIdOf(destination);
        if (projectId == null) throw new MessageDeliveryException("Unknown destination " + destination);

        boolean admin = user.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!admin && roles.find(projectId, Long.valueOf(user.getName())) == null) {
            throw new MessageDeliveryException("Not a member of project " + projectId);
        }
    }

    private static Authentication requireUser(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof Authentication auth && auth.isAuthenticated()) return auth;
        throw new MessageDeliveryException("Not authenticated");
    }
}
