package com.tpm.activity;

import java.time.Instant;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.tpm.activity.ActivityLog.EntityType;
import com.tpm.common.PageResponse;
import com.tpm.user.CurrentUser;
import com.tpm.user.UserDtos.UserSummary;
import com.tpm.user.UserRepository;

/**
 * Audit logging. Entries are written in the <em>same</em> transaction as the change they describe,
 * so the log can never claim something happened that was rolled back (and vice versa).
 */
@Service
public class ActivityService {

    private final ActivityRepository repo;
    private final UserRepository users;

    public ActivityService(ActivityRepository repo, UserRepository users) {
        this.repo = repo;
        this.users = users;
    }

    public record ActivityView(Long id, String action, EntityType entityType, Long entityId, String summary,
            UserSummary actor, Instant createdAt) {
        static ActivityView of(ActivityLog a) {
            return new ActivityView(a.getId(), a.getAction(), a.getEntityType(), a.getEntityId(), a.getSummary(),
                    UserSummary.of(a.getActor()), a.getCreatedAt());
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long projectId, String action, EntityType type, Long entityId, String summary) {
        repo.save(new ActivityLog(projectId, users.getReferenceById(CurrentUser.id()), action, type, entityId, summary));
    }

    @Transactional(readOnly = true)
    public PageResponse<ActivityView> forProject(Long projectId, Long taskId, Pageable pageable) {
        var page = taskId == null
                ? repo.findByProjectId(projectId, pageable)
                : repo.findByProjectIdAndEntityTypeAndEntityId(projectId, EntityType.TASK, taskId, pageable);
        return PageResponse.of(page, ActivityView::of);
    }
}
