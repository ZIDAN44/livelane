package com.tpm.notification;

import java.time.LocalDate;
import java.time.ZoneOffset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.tpm.task.TaskRepository;
import com.tpm.task.TaskService;

/**
 * Sends "due soon / overdue" reminders for open tasks due by tomorrow, at most once per due date.
 *
 * <p>Rows are claimed with {@code SELECT ... FOR UPDATE SKIP LOCKED}, so running several app
 * instances never double-sends and never blocks: each instance just skips rows another one holds.
 * {@code reminder_sent_at} makes the job idempotent; changing the due date re-arms it.
 */
@Component
public class DeadlineReminderJob {

    private static final Logger log = LoggerFactory.getLogger(DeadlineReminderJob.class);
    private static final int BATCH = 200;

    private final TaskRepository tasks;
    private final NotificationService notifications;

    public DeadlineReminderJob(TaskRepository tasks, NotificationService notifications) {
        this.tasks = tasks;
        this.notifications = notifications;
    }

    @Scheduled(cron = "${app.reminders.cron}")
    @Transactional
    public void run() {
        var today = LocalDate.now(ZoneOffset.UTC);
        var due = tasks.lockDueForReminder(today.plusDays(1), BATCH);
        for (var task : due) {
            var when = task.getDueDate().isBefore(today) ? "is overdue"
                    : task.getDueDate().isEqual(today) ? "is due today" : "is due tomorrow";
            notifications.notify(task.getAssignee(), Notification.Type.TASK_DUE_SOON,
                    "[%s] %s".formatted(task.getKey(), when),
                    "%s \"%s\" %s (%s).".formatted(task.getKey(), task.getTitle(), when, task.getDueDate()),
                    TaskService.link(task), null);
            task.markReminderSent();
        }
        if (!due.isEmpty()) log.info("Sent {} deadline reminders", due.size());
    }
}
