package com.tenemsa.platform.ai.assistant.controller;

import com.tenemsa.platform.ai.assistant.dto.CommunicationRequest;
import com.tenemsa.platform.ai.assistant.dto.CommunicationResponse;
import com.tenemsa.platform.ai.assistant.dto.HrAnswerResponse;
import com.tenemsa.platform.ai.assistant.dto.HrAskRequest;
import com.tenemsa.platform.ai.assistant.dto.ReportRequest;
import com.tenemsa.platform.ai.assistant.dto.ReportResponse;
import com.tenemsa.platform.ai.assistant.dto.TaskCreateRequest;
import com.tenemsa.platform.ai.assistant.dto.TaskCreateResponse;
import com.tenemsa.platform.ai.assistant.dto.TaskGenerateRequest;
import com.tenemsa.platform.ai.assistant.dto.TaskSuggestionsResponse;
import com.tenemsa.platform.ai.assistant.service.CommunicationWriterService;
import com.tenemsa.platform.ai.assistant.service.HrAssistantService;
import com.tenemsa.platform.ai.assistant.service.ProjectReportService;
import com.tenemsa.platform.ai.assistant.service.TaskGeneratorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** AI helpers beyond meeting summaries. Every endpoint is permission-gated, tenant-scoped and goes through AiGateway. */
@RestController
@RequestMapping("/api/ai")
public class AssistantController {

    private final TaskGeneratorService tasks;
    private final CommunicationWriterService writer;
    private final ProjectReportService reports;
    private final HrAssistantService hr;

    public AssistantController(TaskGeneratorService tasks, CommunicationWriterService writer,
                               ProjectReportService reports, HrAssistantService hr) {
        this.tasks = tasks;
        this.writer = writer;
        this.reports = reports;
        this.hr = hr;
    }

    @PreAuthorize("hasAuthority('ai:use') and hasAuthority('issue:create')")
    @PostMapping("/tasks/generate")
    public TaskSuggestionsResponse generateTasks(@Valid @RequestBody TaskGenerateRequest req) {
        return tasks.generate(req);
    }

    @PreAuthorize("hasAuthority('ai:use') and hasAuthority('issue:create')")
    @PostMapping("/tasks/create")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskCreateResponse createTasks(@Valid @RequestBody TaskCreateRequest req) {
        return tasks.create(req);
    }

    @PreAuthorize("hasAuthority('ai:use')")
    @PostMapping("/writer/draft")
    public CommunicationResponse draftMessage(@Valid @RequestBody CommunicationRequest req) {
        return writer.draft(req);
    }

    @PreAuthorize("hasAuthority('ai:use') and hasAuthority('project:view')")
    @PostMapping("/projects/{id}/report")
    public ReportResponse report(@PathVariable Long id, @Valid @RequestBody ReportRequest req) {
        return reports.generate(id, req);
    }

    @PreAuthorize("hasAuthority('ai:assistant:self')")
    @PostMapping("/hr-assistant/ask")
    public HrAnswerResponse ask(@Valid @RequestBody HrAskRequest req) {
        return hr.ask(req);
    }
}
