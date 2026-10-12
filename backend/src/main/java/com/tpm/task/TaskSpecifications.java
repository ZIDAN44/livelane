package com.tpm.task;

import java.time.LocalDate;
import java.util.ArrayList;

import org.springframework.data.jpa.domain.Specification;

import com.tpm.task.TaskDtos.TaskFilter;

import jakarta.persistence.criteria.Predicate;

/** Builds a dynamic, type-safe WHERE clause from optional search filters (JPA Criteria API). */
public final class TaskSpecifications {

    private TaskSpecifications() {}

    public static Specification<Task> inProject(Long projectId, TaskFilter f) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("project").get("id"), projectId));
            addFilters(f, root, cb, predicates);
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    public static Specification<Task> assignedTo(Long userId, boolean openOnly) {
        return (root, query, cb) -> {
            var p = cb.equal(root.get("assignee").get("id"), userId);
            return openOnly ? cb.and(p, cb.isNull(root.get("completedAt"))) : p;
        };
    }

    private static void addFilters(TaskFilter f, jakarta.persistence.criteria.Root<Task> root,
            jakarta.persistence.criteria.CriteriaBuilder cb, ArrayList<Predicate> predicates) {
        if (f == null) return;
        if (f.q() != null && !f.q().isBlank()) {
            // lower(title) LIKE %q% is served by the pg_trgm GIN index on lower(title).
            var like = "%" + escapeLike(f.q().trim().toLowerCase()) + "%";
            predicates.add(cb.like(cb.lower(root.get("title")), like, '\\'));
        }
        if (f.assigneeId() != null) predicates.add(cb.equal(root.get("assignee").get("id"), f.assigneeId()));
        if (f.priority() != null) predicates.add(cb.equal(root.get("priority"), f.priority()));
        if (f.columnId() != null) predicates.add(cb.equal(root.get("column").get("id"), f.columnId()));
        if (Boolean.TRUE.equals(f.open())) predicates.add(cb.isNull(root.get("completedAt")));
        if (Boolean.TRUE.equals(f.overdue())) {
            predicates.add(cb.isNull(root.get("completedAt")));
            predicates.add(cb.lessThan(root.get("dueDate"), LocalDate.now()));
        }
    }

    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
