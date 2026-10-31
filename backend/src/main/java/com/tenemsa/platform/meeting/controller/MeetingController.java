package com.tenemsa.platform.meeting.controller;

import com.tenemsa.platform.meeting.dto.CreateIssuesRequest;
import com.tenemsa.platform.meeting.dto.CreateIssuesResponse;
import com.tenemsa.platform.meeting.dto.MeetingCreateRequest;
import com.tenemsa.platform.meeting.dto.MeetingListItem;
import com.tenemsa.platform.meeting.dto.MeetingResponse;
import com.tenemsa.platform.meeting.dto.MeetingUpdateRequest;
import com.tenemsa.platform.meeting.service.MeetingSummaryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI meeting summaries. All endpoints need ai:use. Creating issues additionally needs issue:create,
 * because the issues are created with the caller's own permissions, never the AI's.
 */
@Tag(name = "Meetings", description = "AI meeting summaries and action items")
@RestController
@RequestMapping("/api/meetings")
@PreAuthorize("hasAuthority('ai:use')")
public class MeetingController {

    @Autowired private MeetingSummaryService service;

    @GetMapping
    public List<MeetingListItem> list() { return service.list(); }

    @PostMapping
    public ResponseEntity<MeetingResponse> generate(@Valid @RequestBody MeetingCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.generate(req));
    }

    @GetMapping("/{id}")
    public MeetingResponse get(@PathVariable Long id) { return service.get(id); }

    @PutMapping("/{id}")
    public MeetingResponse update(@PathVariable Long id, @Valid @RequestBody MeetingUpdateRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/approve")
    public MeetingResponse approve(@PathVariable Long id) { return service.approve(id); }

    @PreAuthorize("hasAuthority('ai:use') and hasAuthority('issue:create')")
    @PostMapping("/{id}/create-issues")
    public CreateIssuesResponse createIssues(@PathVariable Long id, @Valid @RequestBody CreateIssuesRequest req) {
        return service.createIssues(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
