package com.tenemsa.platform.ai.assistant.service;

import com.tenemsa.platform.ai.assistant.dto.ReportAiOutput;
import com.tenemsa.platform.ai.assistant.dto.ReportRequest;
import com.tenemsa.platform.ai.assistant.dto.ReportResponse;
import com.tenemsa.platform.ai.assistant.dto.ReportType;
import com.tenemsa.platform.ai.model.AiFeature;
import com.tenemsa.platform.ai.service.AiGateway;
import com.tenemsa.platform.ai.service.AiResult;
import com.tenemsa.platform.ai.service.PromptCatalog;
import com.tenemsa.platform.ai.service.PromptInput;
import com.tenemsa.platform.common.ResourceNotFoundException;
import com.tenemsa.platform.employee.service.EmployeeNameLookup;
import com.tenemsa.platform.project.issue.model.Issue;
import com.tenemsa.platform.project.issue.model.IssuePriority;
import com.tenemsa.platform.project.issue.model.IssueStatus;
import com.tenemsa.platform.project.issue.model.IssueType;
import com.tenemsa.platform.project.issue.repository.IssueRepository;
import com.tenemsa.platform.project.model.Project;
import com.tenemsa.platform.project.service.ProjectService;
import com.tenemsa.platform.project.sprint.model.Sprint;
import com.tenemsa.platform.project.sprint.model.SprintStatus;
import com.tenemsa.platform.project.sprint.repository.SprintRepository;
import com.tenemsa.platform.security.AuthContext;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Status updates and sprint retrospectives. The SYSTEM computes every number from the project's own data
 * (tenant-scoped through ProjectService.require); the model only turns those facts into readable prose.
 * The computed numbers are returned too ("basedOn") so a reader can check the wording against them.
 * Assignee names are included in the facts so the report can say who is blocked or overloaded; a company
 * that does not want that can switch AI off in Company settings.
 */
@Service
public class ProjectReportService {

    private static final int MAX_LINES = 25;

    private final AiGateway gateway;
    private final PromptCatalog prompts;
    private final ProjectService projectService;
    private final IssueRepository issues;
    private final SprintRepository sprints;
    private final EmployeeNameLookup names;
    private final AuthContext authContext;

    public ProjectReportService(AiGateway gateway, PromptCatalog prompts, ProjectService projectService,
                                IssueRepository issues, SprintRepository sprints, EmployeeNameLookup names,
                                AuthContext authContext) {
        this.gateway = gateway;
        this.prompts = prompts;
        this.projectService = projectService;
        this.issues = issues;
        this.sprints = sprints;
        this.names = names;
        this.authContext = authContext;
    }

    public ReportResponse generate(Long projectId, ReportRequest req) {
        Long companyId = authContext.currentCompanyId();
        Project project = projectService.require(projectId);
        List<Issue> all = issues.findByCompanyIdAndProjectIdOrderByIdAsc(companyId, project.getId());
        Map<Integer, String> people = names.namesFor(companyId, all.stream().map(Issue::getAssigneeEmpId).toList());

        Facts facts = req.type() == ReportType.SPRINT_RETRO
                ? retroFacts(companyId, project, all, people, req.sprintId())
                : statusFacts(companyId, project, all, people, req.days() == null ? 7 : req.days());

        String system = prompts.text(PromptCatalog.PROJECT_REPORT, PromptCatalog.V1);
        String user = "Report type: " + req.type() + "\nToday's date: " + LocalDate.now() + "\n\n"
                + PromptInput.fence("FACTS", String.join("\n", facts.lines));
        AiResult<ReportAiOutput> result = gateway.generate(new AiGateway.AiCall<>(AiFeature.PROJECT_REPORT,
                PromptCatalog.versionLabel(PromptCatalog.PROJECT_REPORT, PromptCatalog.V1), system, user, ReportAiOutput.class));

        ReportAiOutput out = result.data();
        return new ReportResponse(req.type(), project.getProjectKey(), out.headline().strip(), out.summary().strip(),
                clean(out.highlights()), clean(out.risks()), clean(out.nextSteps()), facts.basedOn,
                result.model(), result.promptVersion(), LocalDateTime.now());
    }

    private record Facts(List<String> lines, List<String> basedOn) {}

    // ---- status update -------------------------------------------------------------------

    private Facts statusFacts(Long companyId, Project p, List<Issue> all, Map<Integer, String> people, int days) {
        LocalDate today = LocalDate.now();
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        Map<IssueStatus, Long> byStatus = countByStatus(all);
        List<Issue> open = all.stream().filter(i -> i.getStatus() != IssueStatus.DONE).toList();
        List<Issue> recent = all.stream().filter(i -> i.getUpdatedAt() != null && i.getUpdatedAt().isAfter(since))
                .sorted(Comparator.comparing(Issue::getUpdatedAt).reversed()).toList();
        List<Issue> overdue = open.stream().filter(i -> i.getDueDate() != null && i.getDueDate().isBefore(today))
                .sorted(Comparator.comparing(Issue::getDueDate)).toList();
        List<Issue> urgent = open.stream().filter(i -> i.getPriority() == IssuePriority.URGENT || i.getPriority() == IssuePriority.HIGH).toList();
        long unassigned = open.stream().filter(i -> i.getAssigneeEmpId() == null).count();

        List<String> based = new ArrayList<>();
        based.add("Issues: " + all.size() + " total, " + byStatus.get(IssueStatus.DONE) + " done, " + open.size() + " open");
        based.add("Open by status: To do " + byStatus.get(IssueStatus.TODO) + ", In progress " + byStatus.get(IssueStatus.IN_PROGRESS)
                + ", In review " + byStatus.get(IssueStatus.IN_REVIEW));
        based.add(recent.size() + " issue(s) changed in the last " + days + " day(s)");
        based.add(overdue.size() + " overdue, " + urgent.size() + " high or urgent, " + unassigned + " open without an assignee");

        List<String> lines = new ArrayList<>();
        lines.add("Project: " + PromptInput.oneLine(p.getProjectKey() + " - " + p.getName(), 200) + " (" + p.getStatus() + ")");
        lines.add("Recent window: last " + days + " day(s)");
        lines.addAll(based);
        sprints.findByCompanyIdAndProjectIdOrderByIdAsc(companyId, p.getId()).stream()
                .filter(s -> s.getStatus() == SprintStatus.ACTIVE).findFirst().ifPresent(s -> {
                    List<Issue> inSprint = all.stream().filter(i -> Objects.equals(i.getSprintId(), s.getId())).toList();
                    long done = inSprint.stream().filter(i -> i.getStatus() == IssueStatus.DONE).count();
                    lines.add("Active sprint: " + PromptInput.oneLine(s.getName(), 100)
                            + " | goal: " + PromptInput.oneLine(s.getGoal(), 200)
                            + " | " + s.getStartDate() + " to " + s.getEndDate()
                            + " | " + done + " of " + inSprint.size() + " issues done, story points done "
                            + points(inSprint, true) + " of " + points(inSprint, false));
                    based.add("Active sprint \"" + s.getName() + "\": " + done + " of " + inSprint.size() + " issues done");
                });
        section(lines, "Changed recently", recent, p, people, true);
        section(lines, "Overdue and still open", overdue, p, people, false);
        section(lines, "High or urgent and still open", urgent, p, people, false);
        return new Facts(lines, based);
    }

    // ---- sprint retrospective ------------------------------------------------------------

    private Facts retroFacts(Long companyId, Project p, List<Issue> all, Map<Integer, String> people, Long sprintId) {
        List<Sprint> all6 = sprints.findByCompanyIdAndProjectIdOrderByIdAsc(companyId, p.getId());
        Sprint sprint;
        if (sprintId != null) {
            sprint = all6.stream().filter(s -> s.getId().equals(sprintId)).findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Sprint not found."));
        } else {
            sprint = all6.stream().filter(s -> s.getStatus() == SprintStatus.COMPLETED)
                    .reduce((a, b) -> b).orElseThrow(() ->
                            new IllegalArgumentException("This project has no completed sprint yet."));
        }
        List<Issue> in = all.stream().filter(i -> Objects.equals(i.getSprintId(), sprint.getId())).toList();
        Map<IssueStatus, Long> byStatus = countByStatus(in);
        long bugs = in.stream().filter(i -> i.getType() == IssueType.BUG).count();

        List<String> based = new ArrayList<>();
        based.add("Sprint \"" + sprint.getName() + "\" (" + sprint.getStatus() + "), " + sprint.getStartDate() + " to " + sprint.getEndDate());
        based.add(in.size() + " issue(s) in the sprint: " + byStatus.get(IssueStatus.DONE) + " done, "
                + (in.size() - byStatus.get(IssueStatus.DONE)) + " not done");
        if (sprint.getCompletedCount() != null) {
            based.add("At completion: " + sprint.getCompletedCount() + " completed, "
                    + (sprint.getCarriedOverCount() == null ? 0 : sprint.getCarriedOverCount()) + " carried over");
        }
        based.add("Story points done " + points(in, true) + " of " + points(in, false) + "; " + bugs + " bug(s) in the sprint");

        List<String> lines = new ArrayList<>();
        lines.add("Project: " + PromptInput.oneLine(p.getProjectKey() + " - " + p.getName(), 200));
        lines.add("Sprint goal: " + PromptInput.oneLine(sprint.getGoal(), 300));
        lines.addAll(based);
        Map<String, Long> doneBy = in.stream().filter(i -> i.getStatus() == IssueStatus.DONE)
                .collect(Collectors.groupingBy(i -> assignee(i, people), Collectors.counting()));
        if (!doneBy.isEmpty()) {
            lines.add("Issues done per assignee: " + doneBy.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .map(e -> e.getKey() + " " + e.getValue()).collect(Collectors.joining(", ")));
        }
        section(lines, "Issues in the sprint", in, p, people, false);
        return new Facts(lines, based);
    }

    // ---- helpers -------------------------------------------------------------------------

    private static Map<IssueStatus, Long> countByStatus(List<Issue> list) {
        Map<IssueStatus, Long> m = new EnumMap<>(IssueStatus.class);
        for (IssueStatus s : IssueStatus.values()) m.put(s, 0L);
        for (Issue i : list) m.merge(i.getStatus(), 1L, Long::sum);
        return m;
    }

    private static int points(List<Issue> list, boolean doneOnly) {
        return list.stream().filter(i -> !doneOnly || i.getStatus() == IssueStatus.DONE)
                .mapToInt(i -> i.getStoryPoints() == null ? 0 : i.getStoryPoints()).sum();
    }

    private static String assignee(Issue i, Map<Integer, String> people) {
        return i.getAssigneeEmpId() == null ? "unassigned" : people.getOrDefault(i.getAssigneeEmpId(), "unknown");
    }

    private void section(List<String> lines, String title, List<Issue> list, Project p, Map<Integer, String> people,
                         boolean showUpdated) {
        if (list.isEmpty()) return;
        lines.add(title + " (" + list.size() + (list.size() > MAX_LINES ? ", first " + MAX_LINES + " shown" : "") + "):");
        LocalDate today = LocalDate.now();
        for (Issue i : list.stream().limit(MAX_LINES).toList()) {
            StringBuilder sb = new StringBuilder("- ").append(p.getProjectKey()).append('-').append(i.getIssueNumber())
                    .append(" | ").append(PromptInput.oneLine(i.getTitle(), 100))
                    .append(" | ").append(i.getStatus()).append(" | ").append(i.getPriority())
                    .append(" | ").append(assignee(i, people));
            if (i.getDueDate() != null && i.getStatus() != IssueStatus.DONE && i.getDueDate().isBefore(today)) {
                sb.append(" | overdue by ").append(ChronoUnit.DAYS.between(i.getDueDate(), today)).append(" day(s)");
            }
            if (showUpdated && i.getUpdatedAt() != null) sb.append(" | updated ").append(i.getUpdatedAt().toLocalDate());
            lines.add(sb.toString());
        }
    }

    private static List<String> clean(List<String> list) {
        if (list == null) return List.of();
        return list.stream().filter(s -> s != null && !s.isBlank()).map(String::strip).limit(6).toList();
    }
}
