package com.tpm.task;

import java.time.Instant;
import java.time.LocalDate;

import com.tpm.project.BoardColumn;
import com.tpm.project.Project;
import com.tpm.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "tasks")
public class Task {

    public enum Priority { LOW, MEDIUM, HIGH, URGENT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "column_id")
    private BoardColumn column;

    /** Sequential number within the project (TPM-1, TPM-2, ...). */
    @Column(nullable = false, updatable = false)
    private int number;

    @Column(nullable = false)
    private String title;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority = Priority.MEDIUM;

    /** Fractional-index key ordering the task inside its column. */
    @Column(nullable = false)
    private String position;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id")
    private User reporter;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "reminder_sent_at")
    private Instant reminderSentAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    /** Optimistic-locking version: concurrent edits of the same task yield HTTP 409. */
    @Version
    private long version;

    protected Task() {}

    public Task(Project project, BoardColumn column, int number, String title, String description,
            Priority priority, String position, User reporter) {
        this.project = project;
        this.column = column;
        this.number = number;
        this.title = title;
        this.description = description;
        this.priority = priority == null ? Priority.MEDIUM : priority;
        this.position = position;
        this.reporter = reporter;
        if (column.isDoneColumn()) completedAt = Instant.now();
    }

    @PreUpdate
    void touch() { updatedAt = Instant.now(); }

    /** Moves the task, keeping {@code completedAt} consistent with the target column. */
    public void moveTo(BoardColumn target, String newPosition) {
        if (target.isDoneColumn() && completedAt == null) completedAt = Instant.now();
        if (!target.isDoneColumn()) completedAt = null;
        this.column = target;
        this.position = newPosition;
    }

    public void setDueDate(LocalDate dueDate) {
        if (dueDate == null || !dueDate.equals(this.dueDate)) reminderSentAt = null; // re-arm the reminder
        this.dueDate = dueDate;
    }

    public String getKey() { return project.getKey() + "-" + number; }

    public Long getId() { return id; }
    public Project getProject() { return project; }
    public BoardColumn getColumn() { return column; }
    public int getNumber() { return number; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Priority getPriority() { return priority; }
    public String getPosition() { return position; }
    public User getAssignee() { return assignee; }
    public User getReporter() { return reporter; }
    public LocalDate getDueDate() { return dueDate; }
    public Instant getReminderSentAt() { return reminderSentAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setPriority(Priority priority) { this.priority = priority; }
    public void setAssignee(User assignee) { this.assignee = assignee; }
    public void setPosition(String position) { this.position = position; }
    public void markReminderSent() { this.reminderSentAt = Instant.now(); }
}
