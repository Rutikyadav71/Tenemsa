package com.tenemsa.platform.connections.provider;

import java.time.Instant;
import java.util.List;

/**
 * What a question is about, worked out by the server (not by the AI): an optional time window and a few search
 * words the person put in quotes. Providers use it to fetch the right slice of an app instead of "the latest 25".
 * Everything here ends up in a query to a fixed app API, so providers must encode it.
 */
public record QueryHints(Instant from, Instant to, List<String> terms, String label) {

    public static final QueryHints NONE = new QueryHints(null, null, List.of(), "");

    public QueryHints {
        terms = terms == null ? List.of() : List.copyOf(terms);
        label = label == null ? "" : label;
    }

    public boolean isEmpty() { return from == null && to == null && terms.isEmpty(); }
    public boolean hasRange() { return from != null || to != null; }
    public String firstTerm() { return terms.isEmpty() ? null : terms.get(0); }
}
