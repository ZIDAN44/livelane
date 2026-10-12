package com.tpm.realtime;

import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.tpm.user.CurrentUser;

/**
 * Bridges domain changes to WebSocket subscribers.
 *
 * <p>Services publish a {@link BoardEvent} as a Spring application event inside their transaction;
 * it is only pushed to clients <em>after commit</em>. That way clients never see a change that is
 * later rolled back, and anyone who refetches on receipt reads the committed state.
 */
@Component
public class RealtimePublisher {

    private final ApplicationEventPublisher events;
    private final SimpMessagingTemplate messaging;

    public RealtimePublisher(ApplicationEventPublisher events, SimpMessagingTemplate messaging) {
        this.events = events;
        this.messaging = messaging;
    }

    public void board(Long projectId, BoardEvent.Type type, Object payload) {
        events.publishEvent(new BoardEvent(type, projectId, CurrentUser.id(), payload, Instant.now()));
    }

    @TransactionalEventListener(fallbackExecution = true)
    void onBoardEvent(BoardEvent event) {
        messaging.convertAndSend(Destinations.project(event.projectId()), event);
    }
}
