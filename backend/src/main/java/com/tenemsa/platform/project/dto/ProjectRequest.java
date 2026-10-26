package com.tenemsa.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Body of POST /api/projects. The key becomes the prefix of every issue (ACME-1) and cannot change later. */
public record ProjectRequest(
        @NotBlank(message = "Project key is required.")
        @Pattern(regexp = "^[A-Z][A-Z0-9]{1,9}$", message = "Key must be 2 to 10 capital letters or digits, starting with a letter.")
        String key,
        @NotBlank(message = "Project name is required.") @Size(max = 150, message = "Project name is too long.")
        String name,
        @Size(max = 1000, message = "Description is too long (1000 characters at most).")
        String description,
        @Positive(message = "Lead must be a valid employee number.")
        Integer leadEmpId
) {}
