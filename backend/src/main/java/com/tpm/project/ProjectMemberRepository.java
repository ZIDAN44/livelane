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

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {

    @Query("select m.role from ProjectMember m where m.project.id = :projectId and m.user.id = :userId")
    Optional<ProjectRole> findRole(@Param("projectId") Long projectId, @Param("userId") Long userId);

    Optional<ProjectMember> findByProjectIdAndUserId(Long projectId, Long userId);

    boolean existsByProjectIdAndUserId(Long projectId, Long userId);

    @Query("""
            select m from ProjectMember m join fetch m.user
            where m.project.id = :projectId order by m.role desc, m.user.fullName
            """)
    List<ProjectMember> findWithUsers(@Param("projectId") Long projectId);

    @Query("""
            select m from ProjectMember m join fetch m.project p join fetch p.owner
            where m.user.id = :userId order by p.updatedAt desc
            """)
    List<ProjectMember> findMembershipsOf(@Param("userId") Long userId);

    @Query("select m.user.id from ProjectMember m where m.project.id = :projectId")
    List<Long> findUserIds(@Param("projectId") Long projectId);

    @Query("""
            select m.project.id as projectId, count(m) as count from ProjectMember m
            where m.project.id in :ids group by m.project.id
            """)
    List<CountRow> countByProjects(@Param("ids") Collection<Long> ids);

    interface CountRow {
        Long getProjectId();
        Long getCount();
    }
}
