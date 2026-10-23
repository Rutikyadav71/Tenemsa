package com.tenemsa.platform.service;

import com.tenemsa.platform.dto.PersonResponse;
import com.tenemsa.platform.dto.ProjectRequest;
import com.tenemsa.platform.dto.ProjectResponse;
import com.tenemsa.platform.dto.ProjectUpdateRequest;
import com.tenemsa.platform.exception.ResourceNotFoundException;
import com.tenemsa.platform.model.AuditAction;
import com.tenemsa.platform.model.IssueStatus;
import com.tenemsa.platform.model.Project;
import com.tenemsa.platform.model.ProjectStatus;
import com.tenemsa.platform.model.User;
import com.tenemsa.platform.repository.IssueCommentRepository;
import com.tenemsa.platform.repository.IssueRepository;
import com.tenemsa.platform.repository.ProjectRepository;
import com.tenemsa.platform.repository.UserRepository;
import com.tenemsa.platform.security.AuthContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Projects of ONE company. The company always comes from the caller's JWT and every
 * lookup is scoped by it, so another company's project id is simply "not found".
 */
@Service
public class ProjectService {

    @Autowired private ProjectRepository projectRepository;
    @Autowired private IssueRepository issueRepository;
    @Autowired private IssueCommentRepository commentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EmployeeNameLookup names;
    @Autowired private AuthContext authContext;
    @Autowired private AuditService auditService;

    private Long cid() { return authContext.currentCompanyId(); }

    /** The project, or a 404 when it does not exist in the caller's company. */
    public Project require(Long id) {
        return projectRepository.findByIdAndCompanyId(id, cid())
                .orElseThrow(() -> new ResourceNotFoundException("Project not found."));
    }

    public List<ProjectResponse> list() {
        Long companyId = cid();
        List<Project> all = projectRepository.findByCompanyIdOrderByNameAsc(companyId);
        Map<Long, long[]> counts = new HashMap<>(); // projectId -> [total, done]
        for (Object[] row : issueRepository.countByProjectAndStatus(companyId)) {
            long[] c = counts.computeIfAbsent((Long) row[0], k -> new long[2]);
            long n = ((Number) row[2]).longValue();
            c[0] += n;
            if (row[1] == IssueStatus.DONE) c[1] += n;
        }
        Map<Integer, String> leadNames = names.namesFor(companyId, all.stream().map(Project::getLeadEmpId).toList());
        return all.stream().map(p -> {
            long[] c = counts.getOrDefault(p.getId(), new long[2]);
            String lead = p.getLeadEmpId() == null ? null : leadNames.get(p.getLeadEmpId());
            return ProjectResponse.from(p, lead, c[0], c[1]);
        }).toList();
    }

    /** Everyone in the caller's company who has an employee number, for assignee pickers. */
    public List<PersonResponse> people() {
        return userRepository.findAllByCompanyId(cid()).stream()
                .filter(u -> u.getEmpId() != null)
                .map(u -> new PersonResponse(u.getEmpId(), u.getName(), u.getRole()))
                .sorted((x, y) -> x.name().compareToIgnoreCase(y.name()))
                .toList();
    }

    public ProjectResponse get(Long id) {
        Project p = require(id);
        long total = 0, done = 0;
        for (Object[] row : issueRepository.countByProjectAndStatus(cid())) {
            if (!p.getId().equals(row[0])) continue;
            long n = ((Number) row[2]).longValue();
            total += n;
            if (row[1] == IssueStatus.DONE) done += n;
        }
        String leadName = p.getLeadEmpId() == null ? null
                : names.namesFor(cid(), List.of(p.getLeadEmpId())).get(p.getLeadEmpId());
        return ProjectResponse.from(p, leadName, total, done);
    }

    @Transactional
    public ProjectResponse create(ProjectRequest req) {
        Long companyId = cid();
        String key = req.key().trim();
        if (projectRepository.existsByCompanyIdAndProjectKey(companyId, key)) {
            throw new IllegalArgumentException("A project with key " + key + " already exists in your company.");
        }
        names.requireExistsIfGiven(companyId, req.leadEmpId(), "Employee");

        Project p = new Project();
        p.setCompanyId(companyId);
        p.setProjectKey(key);
        p.setName(req.name().trim());
        p.setDescription(blankToNull(req.description()));
        p.setLeadEmpId(req.leadEmpId());
        p.setStatus(ProjectStatus.ACTIVE);
        userRepository.findByEmailAndCompanyId(authContext.currentEmail(), companyId)
                .map(User::getId).ifPresent(p::setCreatedBy);
        p = projectRepository.save(p);

        auditService.recordCurrent(AuditAction.PROJECT_CREATED, "PROJECT", String.valueOf(p.getId()),
                "Created project " + p.getProjectKey() + " (" + p.getName() + ")");
        return get(p.getId());
    }

    @Transactional
    public ProjectResponse update(Long id, ProjectUpdateRequest req) {
        Project p = require(id);
        names.requireExistsIfGiven(cid(), req.leadEmpId(), "Employee");
        p.setName(req.name().trim());
        p.setDescription(blankToNull(req.description()));
        p.setLeadEmpId(req.leadEmpId());
        if (req.status() != null) p.setStatus(req.status());
        projectRepository.save(p);

        auditService.recordCurrent(AuditAction.PROJECT_UPDATED, "PROJECT", String.valueOf(p.getId()),
                "Updated project " + p.getProjectKey() + " (status " + p.getStatus() + ")");
        return get(p.getId());
    }

    /** Only an archived project can be deleted; this removes its issues and comments too. */
    @Transactional
    public void delete(Long id) {
        Project p = require(id);
        if (p.getStatus() != ProjectStatus.ARCHIVED) {
            throw new IllegalArgumentException("Archive the project first; only archived projects can be deleted.");
        }
        commentRepository.deleteAllInProject(cid(), p.getId());
        issueRepository.deleteAllInProject(cid(), p.getId());
        projectRepository.delete(p);
        auditService.recordCurrent(AuditAction.PROJECT_DELETED, "PROJECT", String.valueOf(id),
                "Deleted project " + p.getProjectKey() + " (" + p.getName() + ") with all its issues");
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
