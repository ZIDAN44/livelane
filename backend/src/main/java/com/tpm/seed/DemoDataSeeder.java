package com.tpm.seed;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.tpm.activity.ActivityLog;
import com.tpm.activity.ActivityLog.EntityType;
import com.tpm.activity.ActivityRepository;
import com.tpm.comment.Comment;
import com.tpm.comment.CommentRepository;
import com.tpm.common.FractionalIndex;
import com.tpm.config.AppProperties;
import com.tpm.project.BoardColumn;
import com.tpm.project.BoardColumnRepository;
import com.tpm.project.Project;
import com.tpm.project.ProjectMember;
import com.tpm.project.ProjectMemberRepository;
import com.tpm.project.ProjectRepository;
import com.tpm.project.ProjectRole;
import com.tpm.task.Task;
import com.tpm.task.Task.Priority;
import com.tpm.task.TaskRepository;
import com.tpm.user.User;
import com.tpm.user.UserRepository;

/**
 * Seeds demo users and two realistic projects on first start (idempotent), and optionally wipes
 * and re-seeds the public demo on a schedule ({@code app.demo.reset-cron}).
 */
@Component
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private record T(String title, String column, Priority priority, String assignee, Integer dueInDays,
            String description) {}

    private final AppProperties props;
    private final UserRepository users;
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final BoardColumnRepository columns;
    private final TaskRepository tasks;
    private final CommentRepository comments;
    private final ActivityRepository activity;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate tx;
    private final JdbcClient jdbc;
    private final CacheManager cacheManager;

    public DemoDataSeeder(AppProperties props, UserRepository users, ProjectRepository projects,
            ProjectMemberRepository members, BoardColumnRepository columns, TaskRepository tasks,
            CommentRepository comments, ActivityRepository activity, PasswordEncoder passwordEncoder,
            TransactionTemplate tx, JdbcClient jdbc, CacheManager cacheManager) {
        this.props = props;
        this.users = users;
        this.projects = projects;
        this.members = members;
        this.columns = columns;
        this.tasks = tasks;
        this.comments = comments;
        this.activity = activity;
        this.passwordEncoder = passwordEncoder;
        this.tx = tx;
        this.jdbc = jdbc;
        this.cacheManager = cacheManager;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!props.seed().enabled()) return;
        if (users.existsByEmail(DemoAccount.ALL.getFirst().email())) {
            log.info("Demo data already present");
            return;
        }
        tx.executeWithoutResult(_ -> seed());
    }

    /** Nightly reset for the public demo so visitors always find a tidy board. */
    @Scheduled(cron = "${app.demo.reset-cron}")
    public void reset() {
        if (!props.seed().enabled()) return;
        log.info("Resetting demo data");
        tx.executeWithoutResult(_ -> {
            jdbc.sql("""
                    truncate table notifications, activity_logs, comments, tasks, board_columns,
                                   project_members, projects, refresh_tokens, users restart identity cascade
                    """).update();
            seed();
        });
        cacheManager.getCacheNames().forEach(n -> {
            var cache = cacheManager.getCache(n);
            if (cache != null) cache.clear();
        });
    }

    private void seed() {
        var hash = passwordEncoder.encode(DemoAccount.PASSWORD);
        var u = new HashMap<String, User>();
        for (var a : DemoAccount.ALL) {
            var user = users.save(new User(a.email(), hash, a.fullName(), a.role(), a.color()));
            u.put(a.email().substring(0, a.email().indexOf('@')), user);
        }

        var web = project("WEB", "Website Relaunch",
                "Redesign and relaunch the marketing website with a faster stack and a new design system.",
                u.get("alice"), Map.of(u.get("carol"), ProjectRole.ADMIN, u.get("bob"), ProjectRole.MEMBER,
                        u.get("victor"), ProjectRole.VIEWER));
        var webTasks = tasks(web, u, List.of(
                new T("Set up CI pipeline with GitHub Actions", "Done", Priority.HIGH, "bob", -6,
                        "Build, test and lint on every pull request. Cache Maven and npm dependencies."),
                new T("Define color palette & typography", "Done", Priority.MEDIUM, "carol", -4, null),
                new T("Implement JWT authentication", "Done", Priority.URGENT, "bob", -2,
                        "Short-lived access tokens + rotating refresh tokens in an httpOnly cookie."),
                new T("Homepage hero section", "In Review", Priority.HIGH, "carol", 1,
                        "Responsive hero with headline, sub-copy, CTA and illustration."),
                new T("Pricing page", "In Progress", Priority.MEDIUM, "carol", 3, null),
                new T("Migrate blog posts to new CMS", "In Progress", Priority.LOW, "bob", -1,
                        "~120 posts. Keep URLs stable and add 301 redirects where slugs change."),
                new T("Contact form with spam protection", "In Progress", Priority.HIGH, "alice", 0, null),
                new T("Lighthouse performance audit", "To Do", Priority.MEDIUM, null, 7,
                        "Target: LCP < 2.5s, CLS < 0.1 on mobile."),
                new T("Cookie consent banner (GDPR)", "To Do", Priority.URGENT, "alice", 2, null),
                new T("Write launch announcement", "To Do", Priority.LOW, "victor", 10, null),
                new T("Set up uptime monitoring", "To Do", Priority.MEDIUM, "bob", null, null),
                new T("Accessibility review (WCAG 2.2 AA)", "To Do", Priority.HIGH, "carol", 5, null)));

        comment(webTasks.get(3), u.get("alice"), "Looks great! Can we try a darker variant of the background?");
        comment(webTasks.get(3), u.get("carol"), "Sure - pushed a dark variant, see the latest preview link.");
        comment(webTasks.get(5), u.get("bob"), "Half of the posts are migrated, images still need re-uploading.");
        comment(webTasks.get(8), u.get("victor"), "Legal asked that we also support 'reject all' on the first layer.");

        var app = project("APP", "Mobile App MVP",
                "Cross-platform mobile app for customers to track their orders.",
                u.get("carol"), Map.of(u.get("alice"), ProjectRole.MEMBER, u.get("bob"), ProjectRole.MEMBER));
        tasks(app, u, List.of(
                new T("User research interviews", "Done", Priority.HIGH, "carol", -10, null),
                new T("Wireframes for onboarding", "In Review", Priority.MEDIUM, "carol", 1, null),
                new T("Push notification service", "In Progress", Priority.HIGH, "bob", 4, null),
                new T("Order tracking screen", "To Do", Priority.MEDIUM, "alice", 6, null),
                new T("App store listing copy", "To Do", Priority.LOW, null, 14, null)));

        log.info("Seeded demo data: {} users, 2 projects. Password for all demo users: {}",
                DemoAccount.ALL.size(), DemoAccount.PASSWORD);
    }

    private Project project(String key, String name, String description, User owner, Map<User, ProjectRole> others) {
        var p = projects.save(new Project(key, name, description, owner));
        members.save(new ProjectMember(p, owner, ProjectRole.OWNER));
        others.forEach((user, role) -> members.save(new ProjectMember(p, user, role)));
        activity.save(new ActivityLog(p.getId(), owner, "PROJECT_CREATED", EntityType.PROJECT, p.getId(),
                "created project " + name));
        return p;
    }

    private List<Task> tasks(Project project, Map<String, User> u, List<T> specs) {
        var names = List.of("To Do", "In Progress", "In Review", "Done");
        var wip = new Integer[] {null, 5, 3, null};
        var colPositions = FractionalIndex.evenlySpaced(names.size());
        var cols = new HashMap<String, BoardColumn>();
        for (int i = 0; i < names.size(); i++) {
            cols.put(names.get(i), columns.save(new BoardColumn(project, names.get(i), colPositions.get(i), wip[i],
                    names.get(i).equals("Done"))));
        }
        var lastKey = new HashMap<String, String>();
        var today = LocalDate.now(ZoneOffset.UTC);
        var created = new java.util.ArrayList<Task>();
        for (var s : specs) {
            var position = FractionalIndex.between(lastKey.get(s.column()), null);
            lastKey.put(s.column(), position);
            var reporter = project.getOwner();
            var task = new Task(project, cols.get(s.column()), project.nextTaskNumber(), s.title(), s.description(),
                    s.priority(), position, reporter);
            task.setAssignee(s.assignee() == null ? null : u.get(s.assignee()));
            task.setDueDate(s.dueInDays() == null ? null : today.plusDays(s.dueInDays()));
            tasks.save(task);
            created.add(task);
            activity.save(new ActivityLog(project.getId(), reporter, "TASK_CREATED", EntityType.TASK, task.getId(),
                    "created %s \"%s\"".formatted(task.getKey(), task.getTitle())));
        }
        return created;
    }

    private void comment(Task task, User author, String body) {
        comments.save(new Comment(task, author, body));
        activity.save(new ActivityLog(task.getProject().getId(), author, "COMMENT_ADDED", EntityType.TASK,
                task.getId(), "commented on " + task.getKey()));
    }
}
