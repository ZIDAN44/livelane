package com.tpm.activity;

import java.time.Instant;

import com.tpm.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Immutable audit-trail entry ("who did what, to which entity, when"). */
@Entity
@Table(name = "activity_logs")
public class ActivityLog {

    public enum EntityType { PROJECT, TASK, COLUMN, COMMENT, MEMBER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id")
    private Long projectId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Column(nullable = false)
    private String action;

    @Column(name = "entity_type", nullable = false)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private EntityType entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(nullable = false)
    private String summary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ActivityLog() {}

    public ActivityLog(Long projectId, User actor, String action, EntityType entityType, Long entityId, String summary) {
        this.projectId = projectId;
        this.actor = actor;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.summary = summary.length() > 500 ? summary.substring(0, 497) + "..." : summary;
    }

    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public User getActor() { return actor; }
    public String getAction() { return action; }
    public EntityType getEntityType() { return entityType; }
    public Long getEntityId() { return entityId; }
    public String getSummary() { return summary; }
    public Instant getCreatedAt() { return createdAt; }
}
