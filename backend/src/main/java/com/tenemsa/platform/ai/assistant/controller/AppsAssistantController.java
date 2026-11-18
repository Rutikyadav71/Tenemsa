package com.tenemsa.platform.ai.assistant.controller;

import com.tenemsa.platform.ai.assistant.dto.AppsAnswerResponse;
import com.tenemsa.platform.ai.assistant.dto.HrAskRequest;
import com.tenemsa.platform.ai.assistant.service.AppsAssistantService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Ask Tenemsa AI a question about your own connected apps. */
@RestController
@RequestMapping("/api/ai/apps")
public class AppsAssistantController {

    private final AppsAssistantService service;

    public AppsAssistantController(AppsAssistantService service) { this.service = service; }

    /** Today's brief from your connected apps. Made once a day per person; {@code refresh=true} makes a new one. */
    @PreAuthorize("hasAuthority('ai:assistant:self')")
    @GetMapping("/brief")
    public AppsAnswerResponse brief(@RequestParam(defaultValue = "false") boolean refresh) { return service.brief(refresh); }

    @PreAuthorize("hasAuthority('ai:assistant:self')")
    @PostMapping("/ask")
    public AppsAnswerResponse ask(@Valid @RequestBody HrAskRequest req) { return service.ask(req); }
}
