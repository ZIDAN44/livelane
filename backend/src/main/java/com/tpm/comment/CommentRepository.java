package com.tpm.comment;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    @EntityGraph(attributePaths = "author")
    Page<Comment> findByTaskId(Long taskId, Pageable pageable);

    long countByTaskId(Long taskId);

    @Query("""
            select c from Comment c join fetch c.author join fetch c.task t join fetch t.project
            where c.id = :id
            """)
    Optional<Comment> findDetailed(@Param("id") Long id);

    /** Comment counts for every task on a board, in one grouped query. */
    @Query("""
            select c.task.id as taskId, count(c) as count from Comment c
            where c.task.project.id = :projectId group by c.task.id
            """)
    List<TaskCount> countByProject(@Param("projectId") Long projectId);

    @Query("""
            select c.task.id as taskId, count(c) as count from Comment c
            where c.task.id in :taskIds group by c.task.id
            """)
    List<TaskCount> countByTasks(@Param("taskIds") List<Long> taskIds);

    interface TaskCount {
        Long getTaskId();
        Long getCount();
    }
}
