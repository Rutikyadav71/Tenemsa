package com.tenemsa.platform.dto;

import com.tenemsa.platform.model.Role;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Body of POST /api/employees.
 *
 * Only the fields an admin is allowed to choose are listed here. Before this
 * existed the endpoint bound straight onto the User entity, so a caller could
 * also send primaryOwner, frozen, emailVerified, companyId and so on.
 * Anything not declared below is simply ignored.
 */
public record EmployeeCreateRequest(
        // Optional: when absent the next free number in the company is assigned.
        @Positive(message = "Employee ID must be a positive number.")
        Integer empId,
        @NotBlank(message = "Name is required.") @Size(max = 150, message = "Name is too long.")
        String name,
        @NotBlank(message = "Email is required.") @Email(message = "Enter a valid email address.")
        @Size(max = 255, message = "Email is too long.")
        String email,
        @NotBlank(message = "Password is required.")
        @Size(min = 6, max = 128, message = "Password must be between 6 and 128 characters.")
        String password,
        @NotNull(message = "Role is required.")
        Role role,
        @Size(max = 100, message = "Department is too long.")
        String department,
        @DecimalMin(value = "0.0", message = "Salary cannot be negative.")
        @Digits(integer = 10, fraction = 2, message = "Salary must have at most 10 digits and 2 decimals.")
        BigDecimal salary
) {}
