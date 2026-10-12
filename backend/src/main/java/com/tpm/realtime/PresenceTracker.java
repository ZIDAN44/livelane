package com.tpm.realtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import com.tpm.user.UserDtos.UserSummary;
import com.tpm.user.UserRepository;

/**
 * Tracks which users are looking at which board ("Alice and Bob are viewing") from STOMP
 * subscribe / unsubscribe / disconnect events, and broadcasts the list on every change.
 * Single-instance, in-memory state; a multi-instance deployment would keep this in Redis.
 */
@Controller
public class PresenceTracker {

    private record Sub(String sessionId, String subscriptionId) {}

    /** subscription -> (projectId, userId) */
    private final Map<Sub, long[]> subscriptions = new HashMap<>();

    private final SimpMessagingTemplate messaging;
    private final UserRepository users;

    public PresenceTracker(SimpMessagingTemplate messaging, UserRepository users) {
        this.messaging = messaging;
        this.users = users;
    }

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        var accessor = StompHeaderAccessor.wrap(event.getMessage());
        var destination = accessor.getDestination();
        // Only the main board topic counts as "viewing"; /presence and /app subscriptions don't.
        if (destination == null || !destination.startsWith("/topic/") || destination.endsWith("/presence")) return;
        Long projectId = Destinations.projectIdOf(destination);
        if (projectId == null || event.getUser() == null) return;
        synchronized (this) {
            subscriptions.put(new Sub(accessor.getSessionId(), accessor.getSubscriptionId()),
                    new long[] {projectId, Long.parseLong(event.getUser().getName())});
        }
        broadcast(projectId);
    }

    @EventListener
    public void onUnsubscribe(SessionUnsubscribeEvent event) {
        var accessor = StompHeaderAccessor.wrap(event.getMessage());
        long[] removed;
        synchronized (this) {
            removed = subscriptions.remove(new Sub(accessor.getSessionId(), accessor.getSubscriptionId()));
        }
        if (removed != null) broadcast(removed[0]);
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        var affected = new LinkedHashSet<Long>();
        synchronized (this) {
            subscriptions.entrySet().removeIf(e -> {
                if (!e.getKey().sessionId().equals(event.getSessionId())) return false;
                affected.add(e.getValue()[0]);
                return true;
            });
        }
        affected.forEach(this::broadcast);
    }

    /** One-shot snapshot when a client subscribes to {@code /app/projects/{id}/presence}. */
    @SubscribeMapping("/projects/{projectId}/presence")
    public List<UserSummary> snapshot(@DestinationVariable Long projectId) {
        return viewers(projectId);
    }

    public List<UserSummary> viewers(Long projectId) {
        Set<Long> ids;
        synchronized (this) {
            ids = new LinkedHashSet<>();
            for (var v : subscriptions.values()) if (v[0] == projectId) ids.add(v[1]);
        }
        var result = new ArrayList<UserSummary>();
        users.findAllById(ids).forEach(u -> result.add(UserSummary.of(u)));
        result.sort(Comparator.comparing(UserSummary::fullName));
        return result;
    }

    private void broadcast(Long projectId) {
        messaging.convertAndSend(Destinations.presence(projectId), viewers(projectId));
    }
}
