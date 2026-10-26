package com.tenemsa.platform.controller;

import com.tenemsa.platform.dto.IssueRequest;
import com.tenemsa.platform.dto.IssueResponse;
import com.tenemsa.platform.dto.PersonResponse;
import com.tenemsa.platform.dto.ProjectRequest;
import com.tenemsa.platform.dto.ProjectResponse;
import com.tenemsa.platform.dto.ProjectUpdateRequest;
import com.tenemsa.platform.model.IssueStatus;
import com.tenemsa.platform.service.IssueService;
import com.tenemsa.platform.service.ProjectService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Projects and the issues inside them. Everything is scoped to the caller's company (see ProjectService). */
@Tag(name = "Projects", description = "Company-scoped projects and their issues")
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    @Autowired private ProjectService projectService;
    @Autowired private IssueService issueService;

    @PreAuthorize("hasAuthority('project:view')")
    @GetMapping
    public List<ProjectResponse> list() {
        return projectService.list();
    }

    /** Names for assignee dropdowns; open to everyone who can see projects. */
    @PreAuthorize("hasAuthority('project:view')")
    @GetMapping("/people")
    public List<PersonResponse> people() {
        return projectService.people();
    }

    @PreAuthorize("hasAuthority('project:manage')")
    @PostMapping
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody ProjectRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectService.create(req));
    }

    @PreAuthorize("hasAuthority('project:view')")
    @GetMapping("/{id}")
    public ProjectResponse get(@PathVariable Long id) {
        return projectService.get(id);
    }

    @PreAuthorize("hasAuthority('project:manage')")
    @PutMapping("/{id}")
    public ProjectResponse update(@PathVariable Long id, @Valid @RequestBody ProjectUpdateRequest req) {
        return projectService.update(id, req);
    }

    @PreAuthorize("hasAuthority('project:manage')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAuthority('project:view')")
    @GetMapping("/{id}/issues")
    public List<IssueResponse> issues(@PathVariable Long id,
                                      @RequestParam(required = false) IssueStatus status,
                                      @RequestParam(required = false) Integer assignee,
                                      @RequestParam(required = false) String q) {
        return issueService.listForProject(id, status, assignee, q);
    }

    @PreAuthorize("hasAuthority('issue:create')")
    @PostMapping("/{id}/issues")
    public ResponseEntity<IssueResponse> createIssue(@PathVariable Long id, @Valid @RequestBody IssueRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(issueService.create(id, req));
    }
}
