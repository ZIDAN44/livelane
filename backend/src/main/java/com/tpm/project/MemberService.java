package com.tpm.project;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tpm.activity.ActivityLog.EntityType;
import com.tpm.activity.ActivityService;
import com.tpm.common.Exceptions.BadRequestException;
import com.tpm.common.Exceptions.ConflictException;
import com.tpm.common.Exceptions.NotFoundException;
import com.tpm.notification.Notification;
import com.tpm.notification.NotificationService;
import com.tpm.project.ProjectDtos.MemberView;
import com.tpm.realtime.BoardEvent;
import com.tpm.realtime.RealtimePublisher;
import com.tpm.task.TaskRepository;
import com.tpm.user.CurrentUser;
import com.tpm.user.UserRepository;

import org.springframework.security.access.AccessDeniedException;

/**
 * Project membership rules:
 * <ul>
 *   <li>ADMINs can add/remove MEMBERs and VIEWERs; only the OWNER can grant or revoke ADMIN.</li>
 *   <li>The OWNER can't be removed or demoted - ownership is transferred explicitly.</li>
 *   <li>Anyone except the owner can leave; their tasks are unassigned.</li>
 * </ul>
 */
@Service
public class MemberService {

    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final UserRepository users;
    private final TaskRepository tasks;
    private final ProjectAccess access;
    private final MemberRoleCache roleCache;
    private final ActivityService activity;
    private final NotificationService notifications;
    private final RealtimePublisher realtime;

    public MemberService(ProjectRepository projects, ProjectMemberRepository members, UserRepository users,
            TaskRepository tasks, ProjectAccess access, MemberRoleCache roleCache, ActivityService activity,
            NotificationService notifications, RealtimePublisher realtime) {
        this.projects = projects;
        this.members = members;
        this.users = users;
        this.tasks = tasks;
        this.access = access;
        this.roleCache = roleCache;
        this.activity = activity;
        this.notifications = notifications;
        this.realtime = realtime;
    }

    @Transactional(readOnly = true)
    public List<MemberView> list(Long projectId) {
        access.require(projectId, ProjectRole.VIEWER);
        return members.findWithUsers(projectId).stream()
                .sorted(Comparator.comparing(ProjectMember::getRole).reversed()
                        .thenComparing(m -> m.getUser().getFullName()))
                .map(MemberView::of).toList();
    }

    @Transactional
    public MemberView add(Long projectId, String email, ProjectRole role) {
        var myRole = access.require(projectId, ProjectRole.ADMIN);
        checkCanAssign(myRole, role);
        var project = projects.findById(projectId).orElseThrow(() -> new NotFoundException("Project", projectId));
        var user = users.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new NotFoundException("User with email", email));
        if (members.existsByProjectIdAndUserId(projectId, user.getId())) {
            throw new ConflictException(user.getFullName() + " is already a member");
        }
        var member = members.save(new ProjectMember(project, user, role));
        roleCache.evictAfterCommit(projectId, user.getId());
        activity.record(projectId, "MEMBER_ADDED", EntityType.MEMBER, user.getId(),
                "added %s as %s".formatted(user.getFullName(), role));
        notifications.notify(user, Notification.Type.ADDED_TO_PROJECT, "You were added to " + project.getName(),
                "You were added to project %s as %s.".formatted(project.getName(), role.name().toLowerCase()),
                "/projects/" + projectId, CurrentUser.id());
        changed(projectId);
        return MemberView.of(member);
    }

    @Transactional
    public MemberView changeRole(Long projectId, Long userId, ProjectRole newRole) {
        var myRole = access.require(projectId, ProjectRole.ADMIN);
        var member = load(projectId, userId);
        if (member.getRole() == ProjectRole.OWNER) throw new ConflictException("Use ownership transfer to change the owner");
        checkCanAssign(myRole, newRole);
        checkCanAssign(myRole, member.getRole());
        member.setRole(newRole);
        roleCache.evictAfterCommit(projectId, userId);
        activity.record(projectId, "MEMBER_ROLE_CHANGED", EntityType.MEMBER, userId,
                "changed %s's role to %s".formatted(member.getUser().getFullName(), newRole));
        changed(projectId);
        return MemberView.of(member);
    }

    @Transactional
    public void remove(Long projectId, Long userId) {
        boolean leaving = userId == CurrentUser.id();
        var myRole = access.require(projectId, leaving ? ProjectRole.VIEWER : ProjectRole.ADMIN);
        var member = load(projectId, userId);
        if (member.getRole() == ProjectRole.OWNER) {
            throw new ConflictException("The owner can't leave or be removed; transfer ownership first");
        }
        if (!leaving) checkCanAssign(myRole, member.getRole());
        tasks.unassignAll(projectId, userId);
        members.delete(member);
        roleCache.evictAfterCommit(projectId, userId);
        activity.record(projectId, leaving ? "MEMBER_LEFT" : "MEMBER_REMOVED", EntityType.MEMBER, userId,
                leaving ? "left the project" : "removed " + member.getUser().getFullName());
        changed(projectId);
    }

    @Transactional
    public void transferOwnership(Long projectId, Long newOwnerId) {
        access.require(projectId, ProjectRole.OWNER);
        var project = projects.findByIdForUpdate(projectId).orElseThrow(() -> new NotFoundException("Project", projectId));
        var target = load(projectId, newOwnerId);
        var currentOwner = load(projectId, project.getOwner().getId());
        if (target == currentOwner) throw new BadRequestException("That user already owns the project");
        currentOwner.setRole(ProjectRole.ADMIN);
        target.setRole(ProjectRole.OWNER);
        project.setOwner(target.getUser());
        roleCache.evictAfterCommit(projectId, currentOwner.getUser().getId());
        roleCache.evictAfterCommit(projectId, newOwnerId);
        activity.record(projectId, "OWNERSHIP_TRANSFERRED", EntityType.MEMBER, newOwnerId,
                "transferred ownership to " + target.getUser().getFullName());
        changed(projectId);
    }

    /** Only owners may hand out or take away ADMIN; nobody can grant OWNER through this path. */
    private static void checkCanAssign(ProjectRole actor, ProjectRole role) {
        if (role == ProjectRole.OWNER) throw new BadRequestException("Use ownership transfer to assign OWNER");
        if (role == ProjectRole.ADMIN && actor != ProjectRole.OWNER) {
            throw new AccessDeniedException("Only the project owner can manage admins");
        }
    }

    private ProjectMember load(Long projectId, Long userId) {
        return members.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new NotFoundException("Member", userId));
    }

    private void changed(Long projectId) {
        realtime.board(projectId, BoardEvent.Type.MEMBERS_CHANGED, Map.of("projectId", projectId));
    }
}
