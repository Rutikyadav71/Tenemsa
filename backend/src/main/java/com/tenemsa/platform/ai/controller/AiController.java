package com.tenemsa.platform.ai.controller;

import com.tenemsa.platform.ai.dto.AiStatusResponse;
import com.tenemsa.platform.ai.dto.AiUsageResponse;
import com.tenemsa.platform.ai.service.AiUsageService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI", description = "AI availability, quota and usage")
@RestController
@RequestMapping("/api/ai")
public class AiController {

    @Autowired private AiUsageService usageService;

    /** Is AI on, and how many requests does the caller have left. Safe for any AI user to read. */
    @PreAuthorize("hasAuthority('ai:use')")
    @GetMapping("/status")
    public AiStatusResponse status() { return usageService.status(); }

    /** Company-wide usage for the last N days (default 30). Owner and Admin only. */
    @PreAuthorize("hasAuthority('ai:usage:view')")
    @GetMapping("/usage")
    public AiUsageResponse usage(@RequestParam(defaultValue = "30") int days) { return usageService.usage(days); }
}
