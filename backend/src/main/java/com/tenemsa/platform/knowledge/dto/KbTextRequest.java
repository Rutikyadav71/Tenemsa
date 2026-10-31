package com.tenemsa.platform.knowledge.dto;

import com.tenemsa.platform.knowledge.model.DocumentAudience;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Add a document by pasting its text (policies, FAQs, wiki pages). */
public record KbTextRequest(
        @NotBlank(message = "Give the document a title.") @Size(max = 200, message = "Title is too long (200 characters at most).") String title,
        @NotNull(message = "Choose who may read answers from this document.") DocumentAudience audience,
        @NotBlank(message = "Paste the text.") @Size(min = 50, message = "The text is too short to be useful (50 characters at least).") String text
) {}
