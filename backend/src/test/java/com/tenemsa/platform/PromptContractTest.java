package com.tenemsa.platform;

import com.tenemsa.platform.ai.service.PromptCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A prompt is part of the program: if someone edits one and drops the untrusted-data rule, a fence marker or a field
 * the Java code reads, this fails before it reaches users. It cannot judge the QUALITY of an answer (that needs a real
 * model); it protects the contract between the prompt files and the code.
 */
class PromptContractTest {

    private record Contract(String name, List<String> markers, List<String> jsonKeys) {}

    private static final List<Contract> CONTRACTS = List.of(
            new Contract(PromptCatalog.MEETING_SUMMARY, List.of("<<<NOTES", "NOTES>>>"),
                    List.of("summary", "decisions", "actionItems", "title", "owner", "deadline", "priority", "openQuestions")),
            new Contract(PromptCatalog.TASK_GENERATOR, List.of("<<<EXISTING", "EXISTING>>>", "<<<GOAL", "GOAL>>>"),
                    List.of("tasks", "title", "description", "type", "priority", "storyPoints", "labels")),
            new Contract(PromptCatalog.COMMUNICATION_WRITER, List.of("<<<POINTS", "POINTS>>>"), List.of("subject", "body")),
            new Contract(PromptCatalog.PROJECT_REPORT, List.of("<<<FACTS", "FACTS>>>"),
                    List.of("headline", "summary", "highlights", "risks", "nextSteps")),
            new Contract(PromptCatalog.HR_ASSISTANT, List.of("<<<QUESTION", "QUESTION>>>", "<<<FACTS", "FACTS>>>"),
                    List.of("answer", "factsUsed")),
            new Contract(PromptCatalog.KB_ANSWER, List.of("<<<QUESTION", "QUESTION>>>", "<<<SOURCES", "SOURCES>>>"),
                    List.of("answerable", "answer", "citations")));

    @Test
    void everyPromptKeepsItsSafetyRulesAndOutputShape() {
        PromptCatalog catalog = new PromptCatalog();
        for (Contract c : CONTRACTS) for (int version : versionsOf(c.name())) {
            String text = catalog.text(c.name(), version);
            assertThat(text).as(c.name() + " must call user text untrusted").contains("UNTRUSTED");
            assertThat(text).as(c.name() + " must forbid following instructions inside the data").containsIgnoringCase("never");
            assertThat(text).as(c.name() + " must demand JSON only").contains("ONLY one JSON object");
            for (String marker : c.markers()) assertThat(text).as(c.name() + " marker " + marker).contains(marker);
            for (String key : c.jsonKeys()) assertThat(text).as(c.name() + " key " + key).contains("\"" + key + "\"");
        }
    }

    /** Every shipped version of a prompt must keep the contract; hr-assistant and kb-answer also have v2 (chat history). */
    private static int[] versionsOf(String name) {
        return PromptCatalog.HR_ASSISTANT.equals(name) || PromptCatalog.KB_ANSWER.equals(name) ? new int[]{1, 2} : new int[]{1};
    }

    @Test
    void versionLabelsIncludeNameAndVersion() {
        assertThat(PromptCatalog.versionLabel(PromptCatalog.HR_ASSISTANT, 1)).isEqualTo("hr-assistant.v1");
    }
}
