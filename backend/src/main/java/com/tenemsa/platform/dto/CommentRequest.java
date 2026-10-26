package com.tenemsa.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentRequest(
        @NotBlank(message = "Comment cannot be empty.") @Size(max = 5000, message = "Comment is too long (5000 characters at most).")
        String body
) {}
