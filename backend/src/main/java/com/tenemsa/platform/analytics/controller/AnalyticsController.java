package com.tenemsa.platform.analytics.controller;

import com.tenemsa.platform.analytics.dto.AnalyticsOverview;
import com.tenemsa.platform.analytics.dto.MySummary;
import com.tenemsa.platform.analytics.service.AnalyticsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService service;

    public AnalyticsController(AnalyticsService service) { this.service = service; }

    /** Company figures for Owner/Admin/HR (report:view:company); a manager gets their own teams only (report:view:team). */
    @PreAuthorize("hasAuthority('report:view:company') or hasAuthority('report:view:team')")
    @GetMapping("/overview")
    public AnalyticsOverview overview() { return service.overview(); }

    /** The caller's own numbers; every signed-in person. */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public MySummary mine() { return service.mine(); }
}
