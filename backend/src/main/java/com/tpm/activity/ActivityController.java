package com.tpm.activity;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tpm.activity.ActivityService.ActivityView;
import com.tpm.common.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Activity", description = "Audit log of project changes")
public class ActivityController {

    private final ActivityService activity;

    public ActivityController(ActivityService activity) {
        this.activity = activity;
    }

    @GetMapping("/api/projects/{projectId}/activity")
    @PreAuthorize("@projectAccess.canView(#projectId)")
    @Operation(summary = "Paginated audit log for a project (optionally for one task)")
    public PageResponse<ActivityView> list(@PathVariable Long projectId, @RequestParam(required = false) Long taskId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return activity.forProject(projectId, taskId, pageable);
    }
}
