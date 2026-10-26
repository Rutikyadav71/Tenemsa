package com.tenemsa.platform.controller;

import com.tenemsa.platform.dto.CommentRequest;
import com.tenemsa.platform.dto.CommentResponse;
import com.tenemsa.platform.dto.IssueRequest;
import com.tenemsa.platform.dto.IssueResponse;
import com.tenemsa.platform.dto.IssueStatusRequest;
import com.tenemsa.platform.service.IssueService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Single-issue operations. Whether the caller may CHANGE a particular issue (manager,
 * reporter or assignee) depends on the record, so it is checked in IssueService.
 */
@Tag(name = "Issues", description = "Issues, status moves and comments")
@RestController
@RequestMapping("/api/issues")
public class IssueController {

    @Autowired private IssueService issueService;

    @PreAuthorize("hasAuthority('project:view')")
    @GetMapping("/mine")
    public List<IssueResponse> mine() {
        return issueService.assignedToMe();
    }

    @PreAuthorize("hasAuthority('project:view')")
    @GetMapping("/{id}")
    public IssueResponse get(@PathVariable Long id) {
        return issueService.get(id);
    }

    @PreAuthorize("hasAuthority('issue:create')")
    @PutMapping("/{id}")
    public IssueResponse update(@PathVariable Long id, @Valid @RequestBody IssueRequest req) {
        return issueService.update(id, req);
    }

    @PreAuthorize("hasAuthority('issue:create')")
    @PatchMapping("/{id}/status")
    public IssueResponse changeStatus(@PathVariable Long id, @Valid @RequestBody IssueStatusRequest req) {
        return issueService.changeStatus(id, req.status());
    }

    @PreAuthorize("hasAuthority('issue:create')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        issueService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAuthority('project:view')")
    @GetMapping("/{id}/comments")
    public List<CommentResponse> comments(@PathVariable Long id) {
        return issueService.comments(id);
    }

    @PreAuthorize("hasAuthority('issue:create')")
    @PostMapping("/{id}/comments")
    public ResponseEntity<CommentResponse> addComment(@PathVariable Long id, @Valid @RequestBody CommentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(issueService.addComment(id, req));
    }
}
