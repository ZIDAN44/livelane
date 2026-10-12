package com.tpm.project;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Caches "what role does user U have in project P" — the hottest query in the app, since every
 * REST call and every WebSocket subscription is authorised with it.
 *
 * <p>Eviction runs <em>after commit</em>: evicting inside the transaction would let a concurrent
 * request re-populate the cache with the old (still committed) role before our change lands.
 */
@Component
public class MemberRoleCache {

    public static final String CACHE = "memberRoles";

    private final ProjectMemberRepository members;
    private final CacheManager cacheManager;

    public MemberRoleCache(ProjectMemberRepository members, CacheManager cacheManager) {
        this.members = members;
        this.cacheManager = cacheManager;
    }

    /** @return the role, or {@code null} when the user is not a member (nulls are not cached). */
    @Cacheable(cacheNames = CACHE, key = "#projectId + ':' + #userId", unless = "#result == null")
    public ProjectRole find(Long projectId, Long userId) {
        return members.findRole(projectId, userId).orElse(null);
    }

    public void evictAfterCommit(Long projectId, Long userId) {
        Runnable evict = () -> {
            var cache = cacheManager.getCache(CACHE);
            if (cache != null) cache.evict(projectId + ":" + userId);
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { evict.run(); }
            });
        } else {
            evict.run();
        }
    }
}
