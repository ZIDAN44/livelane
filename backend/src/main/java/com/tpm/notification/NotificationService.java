package com.tpm.notification;

import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import com.tpm.common.Exceptions.NotFoundException;
import com.tpm.common.PageResponse;
import com.tpm.realtime.Destinations;
import com.tpm.user.CurrentUser;
import com.tpm.user.User;

/**
 * Creates in-app notifications and fans them out (WebSocket push + e-mail) <em>after commit</em>.
 * E-mail is sent asynchronously on a virtual thread so a slow SMTP server never blocks a request.
 */
@Service
public class NotificationService {

    private final NotificationRepository repo;
    private final ApplicationEventPublisher events;
    private final SimpMessagingTemplate messaging;
    private final EmailService email;

    public NotificationService(NotificationRepository repo, ApplicationEventPublisher events,
            SimpMessagingTemplate messaging, EmailService email) {
        this.repo = repo;
        this.events = events;
        this.messaging = messaging;
        this.email = email;
    }

    public record NotificationView(Long id, Notification.Type type, String message, String link, boolean read,
            Instant createdAt) {
        static NotificationView of(Notification n) {
            return new NotificationView(n.getId(), n.getType(), n.getMessage(), n.getLink(), n.getReadAt() != null,
                    n.getCreatedAt());
        }
    }

    /** Published inside the transaction, handled after commit. */
    record Created(Long userId, String email, boolean wantsEmail, String subject, NotificationView view) {}

    /**
     * Notify {@code recipient} unless they caused the change themselves.
     *
     * @param link frontend-relative link, e.g. {@code /projects/1?task=5}
     */
    @Transactional
    public void notify(User recipient, Notification.Type type, String subject, String message, String link,
            Long actorId) {
        if (recipient == null || !recipient.isEnabled() || recipient.getId().equals(actorId)) return;
        var saved = repo.save(new Notification(recipient, type, message, link));
        events.publishEvent(new Created(recipient.getId(), recipient.getEmail(), recipient.isEmailNotifications(),
                subject, NotificationView.of(saved)));
    }

    @TransactionalEventListener
    void pushOverWebSocket(Created c) {
        messaging.convertAndSendToUser(String.valueOf(c.userId()), Destinations.NOTIFICATIONS_QUEUE, c.view());
    }

    @Async
    @TransactionalEventListener
    void sendEmail(Created c) {
        if (c.wantsEmail()) email.send(c.email(), c.subject(), c.view().message(), c.view().link());
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationView> mine(Pageable pageable) {
        return PageResponse.of(repo.findByUserId(CurrentUser.id(), pageable), NotificationView::of);
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return repo.countByUserIdAndReadAtIsNull(CurrentUser.id());
    }

    @Transactional
    public void markRead(Long id) {
        var n = repo.findById(id)
                .filter(x -> x.getUser().getId().equals(CurrentUser.id()))
                .orElseThrow(() -> new NotFoundException("Notification", id));
        n.markRead();
    }

    @Transactional
    public int markAllRead() {
        return repo.markAllRead(CurrentUser.id(), Instant.now());
    }
}
