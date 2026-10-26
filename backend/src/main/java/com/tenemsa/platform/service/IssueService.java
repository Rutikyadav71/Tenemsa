package com.tenemsa.platform.service;

import com.tenemsa.platform.dto.CommentRequest;
import com.tenemsa.platform.dto.CommentResponse;
import com.tenemsa.platform.dto.IssueRequest;
import com.tenemsa.platform.dto.IssueResponse;
import com.tenemsa.platform.exception.ResourceNotFoundException;
import com.tenemsa.platform.model.AuditAction;
import com.tenemsa.platform.model.Issue;
import com.tenemsa.platform.model.IssueComment;
import com.tenemsa.platform.model.IssuePriority;
import com.tenemsa.platform.model.IssueStatus;
import com.tenemsa.platform.model.IssueType;
import com.tenemsa.platform.model.Project;
import com.tenemsa.platform.model.ProjectStatus;
import com.tenemsa.platform.model.User;
import com.tenemsa.platform.repository.IssueCommentRepository;
import com.tenemsa.platform.repository.IssueRepository;
import com.tenemsa.platform.repository.ProjectRepository;
import com.tenemsa.platform.repository.SprintRepository;
import com.tenemsa.platform.repository.UserRepository;
import com.tenemsa.platform.security.AuthContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Issues (tasks, bugs, stories) and their comments. Same tenant rule as projects:
 * the company comes from the JWT and every lookup is scoped by it.
 *
 * Who may change an issue: anyone holding issue:manage:any (Owner, Admin, Manager),
 * or the person who reported it, or the person it is assigned to.
 */
@Service
public class IssueService {

    @Autowired private IssueRepository issueRepository;
    @Autowired private IssueCommentRepository commentRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private SprintRepository sprintRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectService projectService;
    @Autowired private EmployeeNameLookup names;
    @Autowired private AuthContext authContext;
    @Autowired private AuditService auditService;

    private Long cid() { return authContext.currentCompanyId(); }

    private User caller() {
        return userRepository.findByEmailAndCompanyId(authContext.currentEmail(), cid())
                .orElseThrow(() -> new AccessDeniedException("Unknown user."));
    }

    private Issue requireIssue(Long id) {
        return issueRepository.findByIdAndCompanyId(id, cid())
                .orElseThrow(() -> new ResourceNotFoundException("Issue not found."));
    }

    private void requireEditable(Project p) {
        if (p.getStatus() == ProjectStatus.ARCHIVED) {
            throw new IllegalArgumentException("This project is archived and read-only.");
        }
    }

    private void requireCanModify(Issue issue, User caller) {
        if (authContext.hasAuthority("issue:manage:any")) return;
        Integer me = caller.getEmpId();
        if (me != null && (Objects.equals(me, issue.getReporterEmpId()) || Objects.equals(me, issue.getAssigneeEmpId()))) return;
        throw new AccessDeniedException("Only the reporter, the assignee or a manager can change this issue.");
    }

    // ---- reading -----------------------------------------------------------

    public List<IssueResponse> listForProject(Long projectId, IssueStatus status, Integer assignee, String q) {
        Project p = projectService.require(projectId);
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<Issue> issues = issueRepository.findByCompanyIdAndProjectIdOrderByIdAsc(cid(), p.getId()).stream()
                .filter(i -> status == null || i.getStatus() == status)
                .filter(i -> assignee == null || Objects.equals(assignee, i.getAssigneeEmpId()))
                .filter(i -> needle.isEmpty()
                        || i.getTitle().toLowerCase(Locale.ROOT).contains(needle)
                        || (p.getProjectKey() + "-" + i.getIssueNumber()).toLowerCase(Locale.ROOT).contains(needle))
                .toList();
        return toResponses(issues, Map.of(p.getId(), p));
    }

    /** Open issues assigned to the caller, across all projects of their company. */
    public List<IssueResponse> assignedToMe() {
        Integer me = caller().getEmpId();
        if (me == null) return List.of();
        List<Issue> issues = issueRepository.findByCompanyIdAndAssigneeEmpIdAndStatusNotOrderByDueDateAscIdAsc(cid(), me, IssueStatus.DONE);
        Map<Long, Project> byId = new HashMap<>();
        for (Project p : projectRepository.findByCompanyIdOrderByNameAsc(cid())) byId.put(p.getId(), p);
        return toResponses(issues, byId);
    }

    public IssueResponse get(Long id) {
        Issue i = requireIssue(id);
        return toResponses(List.of(i), Map.of(i.getProjectId(), projectService.require(i.getProjectId()))).get(0);
    }

    // ---- writing -----------------------------------------------------------

    @Transactional
    public IssueResponse create(Long projectId, IssueRequest req) {
        // Locking the project row while numbering means two simultaneous creates cannot get the same number.
        Project p = projectRepository.lockByIdAndCompanyId(projectId, cid())
                .orElseThrow(() -> new ResourceNotFoundException("Project not found."));
        requireEditable(p);
        names.requireExistsIfGiven(cid(), req.assigneeEmpId(), "Employee");

        Issue i = new Issue();
        i.setCompanyId(cid());
        i.setProjectId(p.getId());
        i.setIssueNumber(p.getNextIssueNumber());
        p.setNextIssueNumber(p.getNextIssueNumber() + 1);
        projectRepository.save(p);

        apply(i, req);
        i.setStatus(IssueStatus.TODO);
        i.setReporterEmpId(caller().getEmpId());
        i = issueRepository.save(i);
        return toResponses(List.of(i), Map.of(p.getId(), p)).get(0);
    }

    @Transactional
    public IssueResponse update(Long id, IssueRequest req) {
        Issue i = requireIssue(id);
        Project p = projectService.require(i.getProjectId());
        requireEditable(p);
        requireCanModify(i, caller());
        names.requireExistsIfGiven(cid(), req.assigneeEmpId(), "Employee");
        apply(i, req);
        i = issueRepository.save(i);
        return toResponses(List.of(i), Map.of(p.getId(), p)).get(0);
    }

    @Transactional
    public IssueResponse changeStatus(Long id, IssueStatus status) {
        Issue i = requireIssue(id);
        Project p = projectService.require(i.getProjectId());
        requireEditable(p);
        requireCanModify(i, caller());
        i.setStatus(status);
        i = issueRepository.save(i);
        return toResponses(List.of(i), Map.of(p.getId(), p)).get(0);
    }

    @Transactional
    public void delete(Long id) {
        Issue i = requireIssue(id);
        Project p = projectService.require(i.getProjectId());
        requireEditable(p);
        requireCanModify(i, caller());
        commentRepository.deleteAllForIssue(cid(), i.getId());
        issueRepository.delete(i);
        auditService.recordCurrent(AuditAction.ISSUE_DELETED, "ISSUE", String.valueOf(id),
                "Deleted " + p.getProjectKey() + "-" + i.getIssueNumber() + " (" + i.getTitle() + ")");
    }

    // ---- comments ----------------------------------------------------------

    public List<CommentResponse> comments(Long issueId) {
        Issue i = requireIssue(issueId);
        return commentRepository.findByCompanyIdAndIssueIdOrderByIdAsc(cid(), i.getId()).stream()
                .map(CommentResponse::from).toList();
    }

    @Transactional
    public CommentResponse addComment(Long issueId, CommentRequest req) {
        Issue i = requireIssue(issueId);
        requireEditable(projectService.require(i.getProjectId()));
        User me = caller();
        IssueComment c = new IssueComment();
        c.setCompanyId(cid());
        c.setIssueId(i.getId());
        c.setAuthorEmpId(me.getEmpId());
        c.setAuthorName(me.getName());
        c.setBody(req.body().trim());
        return CommentResponse.from(commentRepository.save(c));
    }

    // ---- helpers -----------------------------------------------------------

    private void apply(Issue i, IssueRequest req) {
        i.setTitle(req.title().trim());
        i.setDescription(req.description() == null || req.description().isBlank() ? null : req.description().trim());
        i.setType(req.type() != null ? req.type() : IssueType.TASK);
        i.setPriority(req.priority() != null ? req.priority() : IssuePriority.MEDIUM);
        i.setAssigneeEmpId(req.assigneeEmpId());
        i.setDueDate(req.dueDate());
        i.setStoryPoints(req.storyPoints());
        i.setLabels(normaliseLabels(req.labels()));
    }

    /** Lower-case, trimmed, de-duplicated, letters/digits/space/dash/underscore only; null when empty. */
    private static String normaliseLabels(List<String> labels) {
        if (labels == null) return null;
        LinkedHashSet<String> clean = new LinkedHashSet<>();
        for (String raw : labels) {
            if (raw == null || raw.isBlank()) continue;
            String l = raw.trim().toLowerCase(Locale.ROOT);
            if (!l.matches("^[a-z0-9][a-z0-9 _-]{0,29}$")) {
                throw new IllegalArgumentException("Labels may contain letters, digits, spaces, dashes and underscores: '" + raw.trim() + "'.");
            }
            clean.add(l);
        }
        return clean.isEmpty() ? null : String.join(",", clean);
    }

    private List<IssueResponse> toResponses(List<Issue> issues, Map<Long, Project> projects) {
        List<Integer> empIds = new ArrayList<>();
        for (Issue i : issues) {
            empIds.add(i.getAssigneeEmpId());
            empIds.add(i.getReporterEmpId());
        }
        Map<Integer, String> nameMap = names.namesFor(cid(), empIds);
        List<Long> sprintIds = issues.stream().map(Issue::getSprintId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> sprintNames = new HashMap<>();
        if (!sprintIds.isEmpty()) {
            sprintRepository.findByCompanyIdAndIdIn(cid(), sprintIds).forEach(s -> sprintNames.put(s.getId(), s.getName()));
        }
        return issues.stream().map(i -> {
            Project p = projects.get(i.getProjectId());
            return IssueResponse.from(i, p != null ? p.getProjectKey() : "?",
                    nameMap.get(i.getAssigneeEmpId()), nameMap.get(i.getReporterEmpId()),
                    i.getSprintId() == null ? null : sprintNames.get(i.getSprintId()));
        }).toList();
    }
}
