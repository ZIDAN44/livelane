package com.tpm.comment;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.tpm.comment.CommentService.CommentRequest;
import com.tpm.comment.CommentService.CommentView;
import com.tpm.common.PageResponse;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@Tag(name = "Comments", description = "Task discussion")
public class CommentController {

    private final CommentService comments;

    public CommentController(CommentService comments) {
        this.comments = comments;
    }

    @GetMapping("/api/tasks/{taskId}/comments")
    public PageResponse<CommentView> list(@PathVariable Long taskId,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return comments.list(taskId, pageable);
    }

    @PostMapping("/api/tasks/{taskId}/comments")
    public ResponseEntity<CommentView> add(@PathVariable Long taskId, @Valid @RequestBody CommentRequest req) {
        return ResponseEntity.status(201).body(comments.add(taskId, req.body()));
    }

    @PutMapping("/api/comments/{commentId}")
    public CommentView edit(@PathVariable Long commentId, @Valid @RequestBody CommentRequest req) {
        return comments.edit(commentId, req.body());
    }

    @DeleteMapping("/api/comments/{commentId}")
    public ResponseEntity<Void> delete(@PathVariable Long commentId) {
        comments.delete(commentId);
        return ResponseEntity.noContent().build();
    }
}
