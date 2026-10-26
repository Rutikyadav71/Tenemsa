package com.tenemsa.platform.company.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Public "create your workspace" request: one company + its first (Owner) account. */
public record CompanyRegisterRequest(
        @NotBlank(message = "Company name is required.") @Size(max = 100, message = "Company name is too long.")
        String companyName,
        @NotBlank(message = "Your name is required.") @Size(max = 100, message = "Name is too long.")
        String ownerName,
        @NotBlank(message = "Email is required.") @Email(message = "Enter a valid email address.")
        @Size(max = 255, message = "Email is too long.")
        String email,
        // 128 also blocks absurdly long inputs sent only to burn hashing time.
        @NotBlank(message = "Password is required.") @Size(max = 128, message = "Password is too long.")
        String password
) {}
