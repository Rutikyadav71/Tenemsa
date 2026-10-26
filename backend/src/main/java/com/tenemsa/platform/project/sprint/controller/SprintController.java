package com.tenemsa.platform.controller;

import com.tenemsa.platform.dto.IssueResponse;
import com.tenemsa.platform.dto.IssueSprintRequest;
import com.tenemsa.platform.dto.SprintCompleteRequest;
import com.tenemsa.platform.dto.SprintRequest;
import com.tenemsa.platform.dto.SprintResponse;
import com.tenemsa.platform.service.SprintService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Sprint planning. Seeing sprints is open to everyone who can see projects; creating, starting,
 * completing and deleting them, and moving issues in and out, is a planning decision for
 * Owner, Admin and Manager (project:manage / issue:manage:any).
 */
@Tag(name = "Sprints", description = "Sprints and sprint planning")
@RestController
@RequestMapping("/api")
public class SprintController {

    @Autowired private SprintService sprintService;

    @PreAuthorize("hasAuthority('project:view')")
    @GetMapping("/projects/{projectId}/sprints")
    public List<SprintResponse> list(@PathVariable Long projectId) {
        return sprintService.list(projectId);
    }

    @PreAuthorize("hasAuthority('project:manage')")
    @PostMapping("/projects/{projectId}/sprints")
    public ResponseEntity<SprintResponse> create(@PathVariable Long projectId, @Valid @RequestBody SprintRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sprintService.create(projectId, req));
    }

    @PreAuthorize("hasAuthority('project:manage')")
    @PutMapping("/sprints/{id}")
    public SprintResponse update(@PathVariable Long id, @Valid @RequestBody SprintRequest req) {
        return sprintService.update(id, req);
    }

    @PreAuthorize("hasAuthority('project:manage')")
    @PostMapping("/sprints/{id}/start")
    public SprintResponse start(@PathVariable Long id) {
        return sprintService.start(id);
    }

    @PreAuthorize("hasAuthority('project:manage')")
    @PostMapping("/sprints/{id}/complete")
    public SprintResponse complete(@PathVariable Long id, @RequestBody(required = false) SprintCompleteRequest req) {
        return sprintService.complete(id, req);
    }

    @PreAuthorize("hasAuthority('project:manage')")
    @DeleteMapping("/sprints/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        sprintService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAuthority('issue:manage:any')")
    @PatchMapping("/issues/{id}/sprint")
    public IssueResponse assign(@PathVariable Long id, @RequestBody IssueSprintRequest req) {
        return sprintService.assign(id, req.sprintId());
    }
}
