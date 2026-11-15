package com.tenemsa.platform.ai.assistant.dto;

import java.util.List;

/** {@code appsRead} is what was looked at for this answer; {@code unavailable} explains any app that could not be read. */
public record AppsAnswerResponse(String answer, List<String> appsRead, List<String> unavailable, String model, String promptVersion, Long conversationId) {}
