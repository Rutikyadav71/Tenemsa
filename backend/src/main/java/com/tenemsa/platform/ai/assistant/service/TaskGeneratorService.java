package com.tenemsa.platform.ai.assistant.service;

import com.tenemsa.platform.ai.assistant.dto.SuggestedTask;
import com.tenemsa.platform.ai.assistant.dto.TaskAiOutput;
import com.tenemsa.platform.ai.assistant.dto.TaskCreateRequest;
import com.tenemsa.platform.ai.assistant.dto.TaskCreateResponse;
import com.tenemsa.platform.ai.assistant.dto.TaskGenerateRequest;
import com.tenemsa.platform.ai.assistant.dto.TaskSuggestionsResponse;
import com.tenemsa.platform.ai.model.AiFeature;
import com.tenemsa.platform.ai.service.AiGateway;
import com.tenemsa.platform.ai.service.AiResult;
import com.tenemsa.platform.ai.service.PromptCatalog;
import com.tenemsa.platform.ai.service.PromptInput;
import com.tenemsa.platform.audit.model.AuditAction;
import com.tenemsa.platform.audit.service.AuditService;
import com.tenemsa.platform.project.issue.dto.IssueRequest;
import com.tenemsa.platform.project.issue.dto.IssueResponse;
import com.tenemsa.platform.project.issue.model.Issue;
import com.tenemsa.platform.project.issue.model.IssuePriority;
import com.tenemsa.platform.project.issue.model.IssueType;
import com.tenemsa.platform.project.issue.repository.IssueRepository;
import com.tenemsa.platform.project.issue.service.IssueService;
import com.tenemsa.platform.project.model.Project;
import com.tenemsa.platform.project.model.ProjectStatus;
import com.tenemsa.platform.project.service.ProjectService;
import com.tenemsa.platform.security.AuthContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Turns a goal into suggested tasks. The AI only SUGGESTS: nothing exists until a person reviews the list
 * and calls create(), which makes the issues with the caller's own permissions and tags them "ai-draft".
 */
@Service
public class TaskGeneratorService {

    private static final int DEFAULT_MAX_TASKS = 8;
    private static final int EXISTING_TITLES_SHOWN = 40;
    public static final String AI_LABEL = "ai-draft";

    private final AiGateway gateway;
    private final PromptCatalog prompts;
    private final ProjectService projectService;
    private final IssueRepository issues;
    private final IssueService issueService;
    private final AuthContext authContext;
    private final AuditService audit;

    public TaskGeneratorService(AiGateway gateway, PromptCatalog prompts, ProjectService projectService,
                                IssueRepository issues, IssueService issueService, AuthContext authContext,
                                AuditService audit) {
        this.gateway = gateway;
        this.prompts = prompts;
        this.projectService = projectService;
        this.issues = issues;
        this.issueService = issueService;
        this.authContext = authContext;
        this.audit = audit;
    }

    /** Not transactional on purpose: the model call can take many seconds and must not hold a database connection. */
    public TaskSuggestionsResponse generate(TaskGenerateRequest req) {
        Project project = projectService.require(req.projectId());          // 404 outside the caller's company
        if (project.getStatus() == ProjectStatus.ARCHIVED) {
            throw new IllegalArgumentException("This project is archived and read-only.");
        }
        int max = req.maxTasks() == null ? DEFAULT_MAX_TASKS : req.maxTasks();
        List<Issue> existing = issues.findByCompanyIdAndProjectIdOrderByIdAsc(authContext.currentCompanyId(), project.getId());
        Set<String> existingTitles = existing.stream().map(i -> key(i.getTitle())).collect(Collectors.toSet());
        String recent = existing.stream().skip(Math.max(0, existing.size() - EXISTING_TITLES_SHOWN))
                .map(i -> "- " + PromptInput.oneLine(i.getTitle(), 120)).collect(Collectors.joining("\n"));

        String system = prompts.text(PromptCatalog.TASK_GENERATOR, PromptCatalog.V1);
        String user = "Project: " + PromptInput.oneLine(project.getProjectKey() + " - " + project.getName(), 200) + "\n"
                + "Project description: " + PromptInput.oneLine(project.getDescription(), 500) + "\n"
                + "Max tasks: " + max + "\n\n"
                + "Existing issue titles:\n" + PromptInput.fence("EXISTING", recent.isEmpty() ? "(none yet)" : recent) + "\n\n"
                + "Goal:\n" + PromptInput.fence("GOAL", req.goal());

        AiResult<TaskAiOutput> result = gateway.generate(new AiGateway.AiCall<>(AiFeature.TASK_GENERATION,
                PromptCatalog.versionLabel(PromptCatalog.TASK_GENERATOR, PromptCatalog.V1), system, user, TaskAiOutput.class));

        List<SuggestedTask> tasks = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (TaskAiOutput.AiTask t : result.data().tasks()) {
            if (t == null || t.title() == null || t.title().isBlank() || tasks.size() >= max) continue;
            String title = limit(t.title().strip().replaceAll("\\s+", " "), 200);
            if (!seen.add(key(title))) continue;                              // the model repeated itself
            tasks.add(new SuggestedTask(title, limit(blankToNull(t.description()), 2000), parseType(t.type()),
                    parsePriority(t.priority()), points(t.storyPoints()), labels(t.labels()),
                    existingTitles.contains(key(title))));
        }
        return new TaskSuggestionsResponse(project.getId(), tasks, result.model(), result.promptVersion());
    }

    /** All-or-nothing: if any task is rejected (for example the project was archived meanwhile) none are created. */
    @Transactional
    public TaskCreateResponse create(TaskCreateRequest req) {
        projectService.require(req.projectId());
        List<IssueResponse> created = new ArrayList<>();
        for (TaskCreateRequest.TaskDraft d : req.tasks()) {
            List<String> labels = new ArrayList<>(d.labels() == null ? List.of() : labels(d.labels()));
            if (!labels.contains(AI_LABEL)) labels.add(AI_LABEL);
            created.add(issueService.create(req.projectId(), new IssueRequest(d.title().strip(), d.description(),
                    d.type() == null ? IssueType.TASK : d.type(),
                    d.priority() == null ? IssuePriority.MEDIUM : d.priority(),
                    null, null, d.storyPoints(), labels)));
        }
        audit.recordCurrent(AuditAction.AI_TASKS_CREATED, "PROJECT", String.valueOf(req.projectId()),
                "Created " + created.size() + " AI-suggested task(s)");
        return new TaskCreateResponse(created);
    }

    // ---- cleaning the model's output -----------------------------------------------------

    private static String key(String title) { return title == null ? "" : title.strip().toLowerCase(Locale.ROOT); }

    private static IssueType parseType(String s) {
        try { return IssueType.valueOf(s.strip().toUpperCase(Locale.ROOT)); } catch (Exception e) { return IssueType.TASK; }
    }

    private static IssuePriority parsePriority(String s) {
        try { return IssuePriority.valueOf(s.strip().toUpperCase(Locale.ROOT)); } catch (Exception e) { return IssuePriority.MEDIUM; }
    }

    /** Story points outside 0..13 are not credible for one small task; clamp instead of failing. */
    private static Integer points(Integer p) {
        return p == null || p < 0 ? null : Math.min(p, 13);
    }

    private static List<String> labels(List<String> raw) {
        if (raw == null) return List.of();
        return raw.stream().filter(l -> l != null && !l.isBlank())
                .map(l -> limit(l.strip().toLowerCase(Locale.ROOT).replace(",", " ").replaceAll("\\s+", "-"), 30))
                .distinct().limit(3).toList();
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.strip(); }

    private static String limit(String s, int max) {
        return s == null ? null : (s.length() <= max ? s : s.substring(0, max));
    }
}
