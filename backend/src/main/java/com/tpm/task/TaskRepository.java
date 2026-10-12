package com.tpm.task;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.QueryHint;

public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    /** Whole board in one query (assignee fetched eagerly to avoid N+1). */
    @Query("""
            select t from Task t left join fetch t.assignee
            where t.project.id = :projectId order by t.position asc, t.id asc
            """)
    List<Task> findBoardTasks(@Param("projectId") Long projectId);

    @Query("""
            select t from Task t
            join fetch t.project join fetch t.column join fetch t.reporter left join fetch t.assignee
            where t.id = :id
            """)
    Optional<Task> findDetailed(@Param("id") Long id);

    @Override
    @EntityGraph(attributePaths = {"assignee", "column", "project"})
    Page<Task> findAll(Specification<Task> spec, Pageable pageable);

    /** Smallest key greater than {@code position} in the column, i.e. the successor slot. */
    @Query("""
            select min(t.position) from Task t
            where t.column.id = :columnId and t.position > :position and t.id <> :excludeId
            """)
    Optional<String> findNextPosition(@Param("columnId") Long columnId, @Param("position") String position,
            @Param("excludeId") Long excludeId);

    @Query("select max(t.position) from Task t where t.column.id = :columnId")
    Optional<String> findLastPosition(@Param("columnId") Long columnId);

    @Query("select t from Task t where t.column.id = :columnId order by t.position asc, t.id asc")
    List<Task> findInColumnOrdered(@Param("columnId") Long columnId);

    long countByColumnId(Long columnId);

    /**
     * Tasks due soon that still need a reminder. {@code SKIP LOCKED} lets several app instances run
     * the job concurrently without double-sending: each instance claims a disjoint set of rows.
     */
    @Query(value = """
            select * from tasks
            where completed_at is null and reminder_sent_at is null and assignee_id is not null
              and due_date <= :until
            order by due_date
            limit :limit
            for update skip locked
            """, nativeQuery = true)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    List<Task> lockDueForReminder(@Param("until") LocalDate until, @Param("limit") int limit);

    @Modifying
    @Query("update Task t set t.assignee = null where t.project.id = :projectId and t.assignee.id = :userId")
    int unassignAll(@Param("projectId") Long projectId, @Param("userId") Long userId);

    @Query("""
            select t.project.id as projectId, count(t) as total,
                   sum(case when t.completedAt is not null then 1 else 0 end) as done
            from Task t where t.project.id in :ids group by t.project.id
            """)
    List<ProjectTaskStats> statsByProjects(@Param("ids") Collection<Long> ids);

    interface ProjectTaskStats {
        Long getProjectId();
        Long getTotal();
        Long getDone();
    }
}
