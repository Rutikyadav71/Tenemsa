package com.tenemsa.platform.knowledge.dto;

import java.util.List;

/** found=false means no relevant passage was found (or the answer could not be tied to one): nothing is guessed. */
public record KbAnswerResponse(boolean found, String answer, List<Source> sources, String model, String promptVersion, Long conversationId) {
    public record Source(int n, Long documentId, String title, String snippet, double score) {}

    public KbAnswerResponse withConversation(Long id) { return new KbAnswerResponse(found, answer, sources, model, promptVersion, id); }
}
