package com.tenemsa.platform.ai.assistant.controller;

import com.tenemsa.platform.ai.assistant.dto.AppsAnswerResponse;
import com.tenemsa.platform.ai.assistant.dto.HrAskRequest;
import com.tenemsa.platform.ai.assistant.service.AppsAssistantService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Ask Tenemsa AI a question about your own connected apps. */
@RestController
@RequestMapping("/api/ai/apps")
public class AppsAssistantController {

    private final AppsAssistantService service;

    public AppsAssistantController(AppsAssistantService service) { this.service = service; }

    @PreAuthorize("hasAuthority('ai:assistant:self')")
    @PostMapping("/ask")
    public AppsAnswerResponse ask(@Valid @RequestBody HrAskRequest req) { return service.ask(req); }
}
