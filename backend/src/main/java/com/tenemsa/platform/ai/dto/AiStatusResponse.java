package com.tenemsa.platform.ai.dto;

/** "enabled" is true only when the server has a provider AND the company has not switched AI off.
 * What the UI needs to decide whether to show AI features: switched on or off, and what is left of the caller's quota. */
public record AiStatusResponse(boolean enabled, String provider, String model, int requestsLeftThisHour, int companyRequestsLeftToday,
                               boolean companyEnabled) {}
