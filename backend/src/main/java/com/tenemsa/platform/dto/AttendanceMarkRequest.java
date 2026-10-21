package com.tenemsa.platform.dto;

import com.tenemsa.platform.model.AttendanceStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Body of POST /api/attendance/mark (HR marks or corrects a day).
 * Work hours are always computed on the server from check-in and check-out.
 */
public record AttendanceMarkRequest(
        @NotNull(message = "Employee ID is required.") @Positive(message = "Employee ID must be a positive number.")
        Integer empId,
        @NotNull(message = "Date is required.")
        LocalDate date,
        @NotNull(message = "Status is required.")
        AttendanceStatus status,
        LocalTime checkIn,
        LocalTime checkOut,
        @Size(max = 255, message = "Notes are too long (255 characters at most).")
        String notes
) {
    @AssertTrue(message = "Check-out must be after check-in.")
    public boolean isCheckOutAfterCheckIn() {
        return checkIn == null || checkOut == null || checkOut.isAfter(checkIn);
    }
}
