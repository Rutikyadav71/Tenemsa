package com.tenemsa.platform.ai.assistant.dto;

import java.util.List;

/**
 * {@code appsRead} is what was looked at for this answer; {@code unavailable} explains any app that could not be read;
 * {@code links} are the items the answer relied on (taken from the app's own data, never typed by the AI);
 * {@code range} says which time window was fetched when the question named one.
 */
public record AppsAnswerResponse(String answer, List<String> appsRead, List<String> unavailable, String model, String promptVersion,
                                 Long conversationId, List<AppLink> links, String range) {
    public record AppLink(String app, String title, String url) {}
}
