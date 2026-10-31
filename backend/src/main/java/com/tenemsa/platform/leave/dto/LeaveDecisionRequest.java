package com.tenemsa.platform.leave.dto;

import jakarta.validation.constraints.Size;

/** Optional note with a decision. A rejection must explain itself (checked in the service). */
public record LeaveDecisionRequest(@Size(max = 300, message = "The note can be 300 characters at most.") String note) {}
