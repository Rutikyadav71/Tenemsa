package com.tenemsa.platform.knowledge.dto;

import java.util.List;

/** found=false means no relevant passage was found (or the answer could not be tied to one): nothing is guessed. */
public record KbAnswerResponse(boolean found, String answer, List<Source> sources, String model, String promptVersion) {
    public record Source(int n, Long documentId, String title, String snippet, double score) {}
}
