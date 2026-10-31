package com.tenemsa.platform.ai.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prompts live in src/main/resources/prompts/{name}.v{N}.txt, not inside Java strings, so they can be
 * reviewed and diffed like code. The version is part of the file name and is stored with every AI call
 * (ai_request_logs.prompt_version) and every generated record. To improve a prompt, add a NEW version
 * file and bump the constant that uses it; old records keep pointing at the version that produced them.
 */
@Component
public class PromptCatalog {

    public static final String MEETING_SUMMARY = "meeting-summary";
    public static final int MEETING_SUMMARY_VERSION = 1;
    public static final String TASK_GENERATOR = "task-generator";
    public static final String COMMUNICATION_WRITER = "communication-writer";
    public static final String PROJECT_REPORT = "project-report";
    public static final String HR_ASSISTANT = "hr-assistant";
    /** Current version of each of the four prompts above (all v1 so far). */
    public static final int V1 = 1;

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String text(String name, int version) {
        return cache.computeIfAbsent(name + ".v" + version, key -> {
            try {
                return new String(new ClassPathResource("prompts/" + key + ".txt").getInputStream().readAllBytes(),
                        StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("Missing prompt file prompts/" + key + ".txt", e);
            }
        });
    }

    public static String versionLabel(String name, int version) { return name + ".v" + version; }
}
