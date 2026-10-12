package com.tpm.activity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityRepository extends JpaRepository<ActivityLog, Long> {

    @EntityGraph(attributePaths = "actor")
    Page<ActivityLog> findByProjectId(Long projectId, Pageable pageable);

    @EntityGraph(attributePaths = "actor")
    Page<ActivityLog> findByProjectIdAndEntityTypeAndEntityId(Long projectId, ActivityLog.EntityType entityType,
            Long entityId, Pageable pageable);
}
