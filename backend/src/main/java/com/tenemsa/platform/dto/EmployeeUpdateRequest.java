package com.tenemsa.platform.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Body of PUT /api/employees/{empId}. The role, company, empId and the
 * frozen/primary-owner flags can never be changed through this endpoint.
 * The password is optional: leave it out (or blank) to keep the current one.
 */
public record EmployeeUpdateRequest(
        @NotBlank(message = "Name is required.") @Size(max = 150, message = "Name is too long.")
        String name,
        @NotBlank(message = "Email is required.") @Email(message = "Enter a valid email address.")
        @Size(max = 255, message = "Email is too long.")
        String email,
        // Blank means "unchanged"; a non-blank value must satisfy the same rule as on create.
        @Size(max = 128, message = "Password is too long.")
        String password,
        @Size(max = 100, message = "Department is too long.")
        String department,
        @DecimalMin(value = "0.0", message = "Salary cannot be negative.")
        @Digits(integer = 10, fraction = 2, message = "Salary must have at most 10 digits and 2 decimals.")
        BigDecimal salary
) {
    /** True when a new password was supplied and is too short. */
    @jakarta.validation.constraints.AssertTrue(message = "Password must be at least 6 characters.")
    public boolean isPasswordLongEnough() {
        return password == null || password.isEmpty() || password.length() >= 6;
    }
}
