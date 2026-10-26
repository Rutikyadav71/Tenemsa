package com.tenemsa.platform.project.sprint.service;

import com.tenemsa.platform.audit.model.AuditAction;
import com.tenemsa.platform.audit.service.AuditService;
import com.tenemsa.platform.common.ResourceNotFoundException;
import com.tenemsa.platform.project.issue.dto.IssueResponse;
import com.tenemsa.platform.project.issue.model.Issue;
import com.tenemsa.platform.project.issue.model.IssueStatus;
import com.tenemsa.platform.project.issue.repository.IssueRepository;
import com.tenemsa.platform.project.issue.service.IssueService;
import com.tenemsa.platform.project.model.Project;
import com.tenemsa.platform.project.model.ProjectStatus;
import com.tenemsa.platform.project.repository.ProjectRepository;
import com.tenemsa.platform.project.service.ProjectService;
import com.tenemsa.platform.project.sprint.dto.SprintCompleteRequest;
import com.tenemsa.platform.project.sprint.dto.SprintRequest;
import com.tenemsa.platform.project.sprint.dto.SprintResponse;
import com.tenemsa.platform.project.sprint.model.Sprint;
import com.tenemsa.platform.project.sprint.model.SprintStatus;
import com.tenemsa.platform.project.sprint.repository.SprintRepository;
import com.tenemsa.platform.security.AuthContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Sprints of one project. Rules: at most one ACTIVE sprint per project; only a PLANNED sprint
 * can be started or deleted; a COMPLETED sprint and the issues in it are frozen; completing a
 * sprint moves its unfinished issues to a PLANNED sprint or back to the backlog.
 * The company always comes from the JWT; other companies' ids are "not found".
 */
@Service
public class SprintService {

    public static final int DEFAULT_SPRINT_DAYS = 14;

    @Autowired private SprintRepository sprintRepository;
    @Autowired private IssueRepository issueRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectService projectService;
    @Autowired private IssueService issueService;
    @Autowired private AuthContext authContext;
    @Autowired private AuditService auditService;

    private Long cid() { return authContext.currentCompanyId(); }

    private Sprint requireSprint(Long id) {
        return sprintRepository.findByIdAndCompanyId(id, cid())
                .orElseThrow(() -> new ResourceNotFoundException("Sprint not found."));
    }

    private Project requireEditableProject(Long projectId) {
        Project p = projectService.require(projectId);
        if (p.getStatus() == ProjectStatus.ARCHIVED) {
            throw new IllegalArgumentException("This project is archived and read-only.");
        }
        return p;
    }

    // ---- reading -----------------------------------------------------------

    public List<SprintResponse> list(Long projectId) {
        projectService.require(projectId);
        List<Issue> issues = issueRepository.findByCompanyIdAndProjectIdOrderByIdAsc(cid(), projectId);
        return sprintRepository.findByCompanyIdAndProjectIdOrderByIdAsc(cid(), projectId).stream()
                .map(s -> toResponse(s, issues)).toList();
    }

    private SprintResponse toResponse(Sprint s, List<Issue> projectIssues) {
        int total = 0, done = 0, points = 0, donePoints = 0;
        for (Issue i : projectIssues) {
            if (!s.getId().equals(i.getSprintId())) continue;
            total++;
            int p = i.getStoryPoints() == null ? 0 : i.getStoryPoints();
            points += p;
            if (i.getStatus() == IssueStatus.DONE) { done++; donePoints += p; }
        }
        return SprintResponse.from(s, total, done, points, donePoints);
    }

    private SprintResponse toResponse(Sprint s) {
        return toResponse(s, issueRepository.findByCompanyIdAndProjectIdOrderByIdAsc(cid(), s.getProjectId()));
    }

    // ---- lifecycle ---------------------------------------------------------

    @Transactional
    public SprintResponse create(Long projectId, SprintRequest req) {
        requireEditableProject(projectId);
        Sprint s = new Sprint();
        s.setCompanyId(cid());
        s.setProjectId(projectId);
        apply(s, req);
        return toResponse(sprintRepository.save(s));
    }

    @Transactional
    public SprintResponse update(Long id, SprintRequest req) {
        Sprint s = requireSprint(id);
        requireEditableProject(s.getProjectId());
        if (s.getStatus() == SprintStatus.COMPLETED) throw new IllegalArgumentException("A completed sprint cannot be edited.");
        apply(s, req);
        return toResponse(sprintRepository.save(s));
    }

    @Transactional
    public SprintResponse start(Long id) {
        Sprint s = requireSprint(id);
        // Lock the project row so two simultaneous starts cannot both succeed.
        projectRepository.lockByIdAndCompanyId(s.getProjectId(), cid())
                .orElseThrow(() -> new ResourceNotFoundException("Project not found."));
        requireEditableProject(s.getProjectId());
        if (s.getStatus() != SprintStatus.PLANNED) throw new IllegalArgumentException("Only a planned sprint can be started.");
        if (sprintRepository.existsByCompanyIdAndProjectIdAndStatus(cid(), s.getProjectId(), SprintStatus.ACTIVE)) {
            throw new IllegalArgumentException("Complete the active sprint first; a project can have only one.");
        }
        if (s.getStartDate() == null) s.setStartDate(LocalDate.now());
        if (s.getEndDate() == null) s.setEndDate(s.getStartDate().plusDays(DEFAULT_SPRINT_DAYS));
        s.setStatus(SprintStatus.ACTIVE);
        s.setStartedAt(LocalDateTime.now());
        sprintRepository.save(s);
        auditService.recordCurrent(AuditAction.SPRINT_STARTED, "SPRINT", String.valueOf(s.getId()),
                "Started sprint " + s.getName());
        return toResponse(s);
    }

    @Transactional
    public SprintResponse complete(Long id, SprintCompleteRequest req) {
        Sprint s = requireSprint(id);
        requireEditableProject(s.getProjectId());
        if (s.getStatus() != SprintStatus.ACTIVE) throw new IllegalArgumentException("Only an active sprint can be completed.");

        Long targetId = req == null ? null : req.moveUnfinishedTo();
        if (targetId != null) {
            Sprint target = requireSprint(targetId);
            if (!target.getProjectId().equals(s.getProjectId()) || target.getStatus() != SprintStatus.PLANNED) {
                throw new IllegalArgumentException("Unfinished issues can only move to a planned sprint of the same project.");
            }
        }

        int done = 0, carried = 0;
        for (Issue i : issueRepository.findByCompanyIdAndProjectIdOrderByIdAsc(cid(), s.getProjectId())) {
            if (!s.getId().equals(i.getSprintId())) continue;
            if (i.getStatus() == IssueStatus.DONE) {
                done++;
            } else {
                carried++;
                i.setSprintId(targetId); // null = back to the backlog
                issueRepository.save(i);
            }
        }
        s.setStatus(SprintStatus.COMPLETED);
        s.setCompletedAt(LocalDateTime.now());
        s.setCompletedCount(done);
        s.setCarriedOverCount(carried);
        sprintRepository.save(s);
        auditService.recordCurrent(AuditAction.SPRINT_COMPLETED, "SPRINT", String.valueOf(s.getId()),
                "Completed sprint " + s.getName() + ": " + done + " done, " + carried + " carried over");
        return toResponse(s);
    }

    /** Only a planned sprint can be deleted; its issues go back to the backlog. */
    @Transactional
    public void delete(Long id) {
        Sprint s = requireSprint(id);
        requireEditableProject(s.getProjectId());
        if (s.getStatus() != SprintStatus.PLANNED) {
            throw new IllegalArgumentException("Only a planned sprint can be deleted.");
        }
        for (Issue i : issueRepository.findByCompanyIdAndProjectIdOrderByIdAsc(cid(), s.getProjectId())) {
            if (s.getId().equals(i.getSprintId())) {
                i.setSprintId(null);
                issueRepository.save(i);
            }
        }
        sprintRepository.delete(s);
    }

    // ---- planning ------------------------------------------------------------

    /** Puts an issue into a sprint of ITS project, or back into the backlog when sprintId is null. */
    @Transactional
    public IssueResponse assign(Long issueId, Long sprintId) {
        Issue issue = issueRepository.findByIdAndCompanyId(issueId, cid())
                .orElseThrow(() -> new ResourceNotFoundException("Issue not found."));
        requireEditableProject(issue.getProjectId());

        if (issue.getSprintId() != null) {
            Sprint current = sprintRepository.findByIdAndCompanyId(issue.getSprintId(), cid()).orElse(null);
            if (current != null && current.getStatus() == SprintStatus.COMPLETED) {
                throw new IllegalArgumentException("Issues in a completed sprint cannot be moved.");
            }
        }
        if (sprintId != null) {
            Sprint target = requireSprint(sprintId);
            if (!target.getProjectId().equals(issue.getProjectId())) {
                throw new IllegalArgumentException("That sprint belongs to a different project.");
            }
            if (target.getStatus() == SprintStatus.COMPLETED) {
                throw new IllegalArgumentException("A completed sprint cannot receive issues.");
            }
        }
        issue.setSprintId(sprintId);
        issueRepository.save(issue);
        return issueService.get(issueId);
    }

    private void apply(Sprint s, SprintRequest req) {
        s.setName(req.name().trim());
        s.setGoal(req.goal() == null || req.goal().isBlank() ? null : req.goal().trim());
        s.setStartDate(req.startDate());
        s.setEndDate(req.endDate());
    }
}
