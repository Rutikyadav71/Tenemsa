package com.tenemsa.platform.meeting.service;

import com.tenemsa.platform.ai.config.AiProperties;
import com.tenemsa.platform.ai.model.AiFeature;
import com.tenemsa.platform.ai.service.AiGateway;
import com.tenemsa.platform.ai.service.AiResult;
import com.tenemsa.platform.ai.service.PromptCatalog;
import com.tenemsa.platform.audit.model.AuditAction;
import com.tenemsa.platform.audit.service.AuditService;
import com.tenemsa.platform.common.ResourceNotFoundException;
import com.tenemsa.platform.employee.model.User;
import com.tenemsa.platform.employee.repository.UserRepository;
import com.tenemsa.platform.employee.service.EmployeeNameLookup;
import com.tenemsa.platform.meeting.dto.ActionItemInput;
import com.tenemsa.platform.meeting.dto.ActionItemResponse;
import com.tenemsa.platform.meeting.dto.CreateIssuesRequest;
import com.tenemsa.platform.meeting.dto.CreateIssuesResponse;
import com.tenemsa.platform.meeting.dto.MeetingAiOutput;
import com.tenemsa.platform.meeting.dto.MeetingCreateRequest;
import com.tenemsa.platform.meeting.dto.MeetingListItem;
import com.tenemsa.platform.meeting.dto.MeetingResponse;
import com.tenemsa.platform.meeting.dto.MeetingUpdateRequest;
import com.tenemsa.platform.meeting.model.MeetingActionItem;
import com.tenemsa.platform.meeting.model.MeetingPoint;
import com.tenemsa.platform.meeting.model.MeetingStatus;
import com.tenemsa.platform.meeting.model.MeetingSummary;
import com.tenemsa.platform.meeting.model.PointType;
import com.tenemsa.platform.meeting.repository.MeetingActionItemRepository;
import com.tenemsa.platform.meeting.repository.MeetingPointRepository;
import com.tenemsa.platform.meeting.repository.MeetingSummaryRepository;
import com.tenemsa.platform.project.issue.dto.IssueRequest;
import com.tenemsa.platform.project.issue.dto.IssueResponse;
import com.tenemsa.platform.project.issue.model.IssuePriority;
import com.tenemsa.platform.project.issue.model.IssueType;
import com.tenemsa.platform.project.issue.service.IssueService;
import com.tenemsa.platform.security.AuthContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Meeting intelligence. The flow is deliberately human-in-the-loop:
 *   notes -> AI draft (DRAFT) -> a person edits it -> a person approves it (APPROVED) ->
 *   a person chooses a project and creates issues from selected action items.
 * Nothing is created in the project board by the AI itself.
 *
 * The AI call is made OUTSIDE any database transaction (it can take many seconds); only the saving of
 * its result is transactional. Visibility: the person who created a summary, or anyone holding
 * meeting:manage:any (Owner, Admin). Everyone else gets a 404 as if it did not exist.
 */
@Service
public class MeetingSummaryService {

    private final AiGateway gateway;
    private final AiProperties aiProps;
    private final PromptCatalog prompts;
    private final MeetingSummaryRepository meetings;
    private final MeetingPointRepository points;
    private final MeetingActionItemRepository actions;
    private final UserRepository users;
    private final EmployeeNameLookup names;
    private final IssueService issueService;
    private final AuthContext authContext;
    private final AuditService audit;
    private final TransactionTemplate tx;

    public MeetingSummaryService(AiGateway gateway, AiProperties aiProps, PromptCatalog prompts,
                                 MeetingSummaryRepository meetings, MeetingPointRepository points,
                                 MeetingActionItemRepository actions, UserRepository users, EmployeeNameLookup names,
                                 IssueService issueService, AuthContext authContext, AuditService audit,
                                 PlatformTransactionManager txManager) {
        this.gateway = gateway;
        this.aiProps = aiProps;
        this.prompts = prompts;
        this.meetings = meetings;
        this.points = points;
        this.actions = actions;
        this.users = users;
        this.names = names;
        this.issueService = issueService;
        this.authContext = authContext;
        this.audit = audit;
        this.tx = new TransactionTemplate(txManager);
    }

    private Long cid() { return authContext.currentCompanyId(); }

    private User caller() {
        return users.findByEmailAndCompanyId(authContext.currentEmail(), cid())
                .orElseThrow(() -> new AccessDeniedException("Unknown user."));
    }

    private boolean canManageAny() { return authContext.hasAuthority("meeting:manage:any"); }

    private MeetingSummary require(Long id) {
        MeetingSummary m = meetings.findByIdAndCompanyId(id, cid())
                .orElseThrow(() -> new ResourceNotFoundException("Meeting summary not found."));
        if (!canManageAny() && !Objects.equals(m.getCreatedByEmpId(), caller().getEmpId())) {
            throw new ResourceNotFoundException("Meeting summary not found.");
        }
        return m;
    }

    // ---- reading -------------------------------------------------------------------------

    public List<MeetingListItem> list() {
        List<MeetingSummary> rows = canManageAny()
                ? meetings.findByCompanyIdOrderByCreatedAtDescIdDesc(cid())
                : meetings.findByCompanyIdAndCreatedByEmpIdOrderByCreatedAtDescIdDesc(cid(), caller().getEmpId());
        if (rows.isEmpty()) return List.of();
        Map<Long, List<MeetingActionItem>> items = actions.findByCompanyIdAndMeetingIdIn(cid(),
                rows.stream().map(MeetingSummary::getId).toList()).stream()
                .collect(Collectors.groupingBy(MeetingActionItem::getMeetingId));
        Map<Integer, String> creators = names.namesFor(cid(), rows.stream().map(MeetingSummary::getCreatedByEmpId).toList());
        return rows.stream().map(m -> {
            List<MeetingActionItem> mine = items.getOrDefault(m.getId(), List.of());
            return new MeetingListItem(m.getId(), m.getTitle(), m.getMeetingDate(), m.getStatus(), mine.size(),
                    (int) mine.stream().filter(MeetingActionItem::isConverted).count(),
                    creators.get(m.getCreatedByEmpId()), m.getCreatedAt());
        }).toList();
    }

    public MeetingResponse get(Long id) {
        return toResponse(require(id));
    }

    // ---- generating ----------------------------------------------------------------------

    /** Sends the notes to the model, then saves the (validated, trimmed) result as a DRAFT. */
    public MeetingResponse generate(MeetingCreateRequest req) {
        String notes = req.notes().strip();
        if (notes.length() > aiProps.getMaxInputChars()) {
            throw new IllegalArgumentException("The notes are too long (" + notes.length() + " characters). The limit is "
                    + aiProps.getMaxInputChars() + "; split the meeting into parts.");
        }
        User caller = caller();

        String system = prompts.text(PromptCatalog.MEETING_SUMMARY, PromptCatalog.MEETING_SUMMARY_VERSION);
        String user = "Today's date: " + LocalDate.now() + "\n"
                + "Meeting title: " + oneLine(req.title()) + "\n"
                + "Meeting date: " + (req.meetingDate() == null ? "not given" : req.meetingDate().toString()) + "\n\n"
                + "<<<NOTES\n" + neutraliseMarkers(notes) + "\nNOTES>>>";

        AiResult<MeetingAiOutput> result = gateway.generate(new AiGateway.AiCall<>(
                AiFeature.MEETING_SUMMARY,
                PromptCatalog.versionLabel(PromptCatalog.MEETING_SUMMARY, PromptCatalog.MEETING_SUMMARY_VERSION),
                system, user, MeetingAiOutput.class));

        Long savedId = tx.execute(status -> save(req, notes, caller, result));
        audit.recordCurrent(AuditAction.AI_MEETING_SUMMARY_GENERATED, "MEETING", String.valueOf(savedId),
                "Generated a meeting summary with " + result.model());
        return toResponse(meetings.findByIdAndCompanyId(savedId, cid()).orElseThrow());
    }

    private Long save(MeetingCreateRequest req, String notes, User caller, AiResult<MeetingAiOutput> result) {
        MeetingAiOutput out = result.data();
        MeetingSummary m = new MeetingSummary();
        m.setCompanyId(cid());
        m.setCreatedByEmpId(caller.getEmpId());
        m.setTitle(req.title().strip());
        m.setMeetingDate(req.meetingDate());
        m.setSourceRef(blankToNull(req.sourceRef(), 300));
        m.setSourceText(notes);
        m.setSummary(trim(out.summary(), 4000));
        m.setStatus(MeetingStatus.DRAFT);
        m.setModel(result.model());
        m.setPromptVersion(result.promptVersion());
        m.setAiRequestLogId(result.logId());
        m = meetings.save(m);

        savePoints(m, PointType.DECISION, out.decisions());
        savePoints(m, PointType.OPEN_QUESTION, out.openQuestions());

        Map<String, Integer> employeesByName = employeesByName();
        int pos = 0;
        for (MeetingAiOutput.AiActionItem ai : orEmpty(out.actionItems())) {
            if (ai == null || ai.title() == null || ai.title().isBlank()) continue;
            MeetingActionItem a = new MeetingActionItem();
            a.setCompanyId(cid());
            a.setMeetingId(m.getId());
            a.setTitle(trim(ai.title(), 200));
            a.setDescription(blankToNull(ai.description(), 2000));
            a.setOwnerName(blankToNull(ai.owner(), 150));
            a.setOwnerEmpId(ai.owner() == null ? null : employeesByName.get(key(ai.owner())));
            a.setDueDate(parseDate(ai.deadline()));
            a.setPriority(parsePriority(ai.priority()));
            a.setPosition(pos++);
            actions.save(a);
        }
        return m.getId();
    }

    private void savePoints(MeetingSummary m, PointType type, List<String> texts) {
        int pos = 0;
        for (String t : orEmpty(texts)) {
            if (t == null || t.isBlank()) continue;
            MeetingPoint p = new MeetingPoint();
            p.setCompanyId(cid());
            p.setMeetingId(m.getId());
            p.setType(type);
            p.setText(trim(t, 1000));
            p.setPosition(pos++);
            points.save(p);
        }
    }

    // ---- reviewing -----------------------------------------------------------------------

    /** Replaces the AI draft with the reviewer's version. Only while DRAFT. */
    @Transactional
    public MeetingResponse update(Long id, MeetingUpdateRequest req) {
        MeetingSummary m = require(id);
        requireDraft(m);
        m.setTitle(req.title().strip());
        m.setMeetingDate(req.meetingDate());
        m.setSummary(req.summary().strip());
        meetings.save(m);

        points.deleteAllForMeeting(cid(), m.getId());
        points.flush();
        savePoints(m, PointType.DECISION, req.decisions());
        savePoints(m, PointType.OPEN_QUESTION, req.openQuestions());

        Map<Long, MeetingActionItem> existing = actions.findByCompanyIdAndMeetingIdOrderByPositionAsc(cid(), m.getId())
                .stream().collect(Collectors.toMap(MeetingActionItem::getId, a -> a, (x, y) -> x, LinkedHashMap::new));
        Set<Long> kept = new java.util.HashSet<>();
        int pos = 0;
        for (ActionItemInput in : orEmpty(req.actionItems())) {
            names.requireExistsIfGiven(cid(), in.ownerEmpId(), "Owner");
            MeetingActionItem a;
            if (in.id() == null) {
                a = new MeetingActionItem();
                a.setCompanyId(cid());
                a.setMeetingId(m.getId());
            } else {
                a = existing.get(in.id());
                if (a == null) throw new IllegalArgumentException("Action item #" + in.id() + " does not belong to this meeting.");
                kept.add(a.getId());
            }
            a.setTitle(in.title().strip());
            a.setDescription(blankToNull(in.description(), 2000));
            a.setOwnerName(blankToNull(in.ownerName(), 150));
            a.setOwnerEmpId(in.ownerEmpId());
            a.setDueDate(in.dueDate());
            a.setPriority(in.priority() == null ? IssuePriority.MEDIUM : in.priority());
            a.setPosition(pos++);
            actions.save(a);
        }
        for (MeetingActionItem old : existing.values()) {
            if (!kept.contains(old.getId())) actions.delete(old);
        }
        return toResponse(m);
    }

    @Transactional
    public MeetingResponse approve(Long id) {
        MeetingSummary m = require(id);
        requireDraft(m);
        m.setStatus(MeetingStatus.APPROVED);
        m.setApprovedByEmpId(caller().getEmpId());
        m.setApprovedAt(LocalDateTime.now());
        meetings.save(m);
        audit.recordCurrent(AuditAction.MEETING_SUMMARY_APPROVED, "MEETING", String.valueOf(m.getId()),
                "Approved meeting summary " + m.getTitle());
        return toResponse(m);
    }

    // ---- turning action items into issues -----------------------------------------------------

    /**
     * Creates one issue per selected action item, all or nothing. The request itself is the human
     * confirmation; each item can be converted only once, and only from an APPROVED summary.
     */
    @Transactional
    public CreateIssuesResponse createIssues(Long id, CreateIssuesRequest req) {
        MeetingSummary m = require(id);
        if (m.getStatus() != MeetingStatus.APPROVED) {
            throw new IllegalArgumentException("Approve the summary before creating issues from it.");
        }
        List<MeetingActionItem> all = actions.findByCompanyIdAndMeetingIdOrderByPositionAsc(cid(), m.getId());
        List<MeetingActionItem> chosen;
        if (req.actionItemIds() == null || req.actionItemIds().isEmpty()) {
            chosen = all.stream().filter(a -> !a.isConverted()).toList();
        } else {
            Map<Long, MeetingActionItem> byId = all.stream().collect(Collectors.toMap(MeetingActionItem::getId, a -> a));
            chosen = new ArrayList<>();
            for (Long itemId : new java.util.LinkedHashSet<>(req.actionItemIds())) {
                MeetingActionItem a = byId.get(itemId);
                if (a == null) throw new IllegalArgumentException("Action item #" + itemId + " does not belong to this meeting.");
                if (a.isConverted()) throw new IllegalArgumentException("\"" + a.getTitle() + "\" was already created as " + a.getIssueKey() + ".");
                chosen.add(a);
            }
        }
        if (chosen.isEmpty()) throw new IllegalArgumentException("There are no action items left to create.");

        List<CreateIssuesResponse.Created> created = new ArrayList<>();
        for (MeetingActionItem a : chosen) {
            String description = (a.getDescription() == null ? "" : a.getDescription() + "\n\n")
                    + "Created from meeting summary \"" + m.getTitle() + "\" (#" + m.getId() + ").";
            IssueResponse issue = issueService.create(req.projectId(), new IssueRequest(
                    a.getTitle(), description, IssueType.TASK, a.getPriority(), a.getOwnerEmpId(), a.getDueDate(),
                    null, List.of("meeting")));
            a.setIssueId(issue.id());
            a.setIssueKey(issue.key());
            a.setConvertedAt(LocalDateTime.now());
            actions.save(a);
            created.add(new CreateIssuesResponse.Created(a.getId(), issue.id(), issue.key()));
        }
        m.setProjectId(req.projectId());
        meetings.save(m);
        audit.recordCurrent(AuditAction.MEETING_ISSUES_CREATED, "MEETING", String.valueOf(m.getId()),
                "Created " + created.size() + " issue(s) from meeting summary " + m.getTitle());
        return new CreateIssuesResponse(created);
    }

    @Transactional
    public void delete(Long id) {
        MeetingSummary m = require(id);
        points.deleteAllForMeeting(cid(), m.getId());
        actions.deleteAllForMeeting(cid(), m.getId());
        meetings.delete(m);
        audit.recordCurrent(AuditAction.MEETING_SUMMARY_DELETED, "MEETING", String.valueOf(id),
                "Deleted meeting summary " + m.getTitle());
    }

    // ---- helpers -------------------------------------------------------------------------

    private void requireDraft(MeetingSummary m) {
        if (m.getStatus() != MeetingStatus.DRAFT) {
            throw new IllegalArgumentException("An approved summary can no longer be edited.");
        }
    }

    private MeetingResponse toResponse(MeetingSummary m) {
        List<MeetingPoint> pts = points.findByCompanyIdAndMeetingIdOrderByPositionAsc(cid(), m.getId());
        List<String> decisions = pts.stream().filter(p -> p.getType() == PointType.DECISION).map(MeetingPoint::getText).toList();
        List<String> questions = pts.stream().filter(p -> p.getType() == PointType.OPEN_QUESTION).map(MeetingPoint::getText).toList();
        List<ActionItemResponse> items = actions.findByCompanyIdAndMeetingIdOrderByPositionAsc(cid(), m.getId())
                .stream().map(ActionItemResponse::from).toList();
        String creator = names.namesFor(cid(), List.of(m.getCreatedByEmpId() == null ? -1 : m.getCreatedByEmpId()))
                .get(m.getCreatedByEmpId());
        return new MeetingResponse(m.getId(), m.getTitle(), m.getMeetingDate(), m.getSourceRef(), m.getSourceText(),
                m.getSummary(), decisions, questions, items, m.getStatus(), m.getProjectId(), m.getModel(),
                m.getPromptVersion(), m.getCreatedByEmpId(), creator, m.getApprovedAt(), m.getCreatedAt(), m.getUpdatedAt());
    }

    /** Lower-cased full name -> empId, only for names that belong to exactly one employee (ambiguity gives no match). */
    private Map<String, Integer> employeesByName() {
        Map<String, Integer> byName = new HashMap<>();
        Set<String> ambiguous = new java.util.HashSet<>();
        for (User u : users.findAllByCompanyId(cid())) {
            if (u.getEmpId() == null || u.getName() == null) continue;
            register(byName, ambiguous, key(u.getName()), u.getEmpId());
            String first = key(u.getName().split("\\s+")[0]);
            register(byName, ambiguous, first, u.getEmpId());
        }
        ambiguous.forEach(byName::remove);
        return byName;
    }

    private static void register(Map<String, Integer> map, Set<String> ambiguous, String k, Integer empId) {
        if (k.isEmpty()) return;
        Integer prev = map.putIfAbsent(k, empId);
        if (prev != null && !prev.equals(empId)) ambiguous.add(k);
    }

    private static String key(String s) { return s == null ? "" : s.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " "); }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            LocalDate d = LocalDate.parse(s.strip());
            return d.getYear() < 2000 || d.getYear() > LocalDate.now().getYear() + 5 ? null : d;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static IssuePriority parsePriority(String s) {
        if (s == null) return IssuePriority.MEDIUM;
        try { return IssuePriority.valueOf(s.strip().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { return IssuePriority.MEDIUM; }
    }

    private static String oneLine(String s) { return s == null ? "" : s.replaceAll("\\s+", " ").strip(); }

    /** Notes cannot contain the markers that fence them, so they cannot "close" the data block early. */
    private static String neutraliseMarkers(String s) {
        return s.replace("<<<NOTES", "< < <NOTES").replace("NOTES>>>", "NOTES> > >");
    }

    private static String trim(String s, int max) {
        String t = s == null ? "" : s.strip();
        return t.length() <= max ? t : t.substring(0, max - 1).stripTrailing() + "…";
    }

    private static String blankToNull(String s, int max) {
        return s == null || s.isBlank() ? null : trim(s, max);
    }

    private static <T> List<T> orEmpty(List<T> l) { return l == null ? List.of() : l; }
}
