package com.tpm.user;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("""
            select u from User u
            where :q is null
               or lower(u.email) like lower(concat('%', cast(:q as string), '%'))
               or lower(u.fullName) like lower(concat('%', cast(:q as string), '%'))
            """)
    Page<User> search(@Param("q") String q, Pageable pageable);
}
