package com.tpm.project;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface BoardColumnRepository extends JpaRepository<BoardColumn, Long> {

    List<BoardColumn> findByProjectIdOrderByPositionAscIdAsc(Long projectId);

    Optional<BoardColumn> findFirstByProjectIdAndPositionGreaterThanAndIdNotOrderByPositionAsc(
            Long projectId, String position, Long excludeId);

    Optional<BoardColumn> findFirstByProjectIdAndIdNotOrderByPositionAsc(Long projectId, Long excludeId);

    Optional<BoardColumn> findFirstByProjectIdOrderByPositionDesc(Long projectId);

    Optional<BoardColumn> findFirstByProjectIdAndDoneColumnFalseOrderByPositionAsc(Long projectId);

    long countByProjectId(Long projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from BoardColumn c join fetch c.project where c.id = :id")
    Optional<BoardColumn> findByIdForUpdate(@Param("id") Long id);

    @Modifying
    @Query("update BoardColumn c set c.position = :position where c.id = :id")
    void updatePosition(@Param("id") Long id, @Param("position") String position);
}
