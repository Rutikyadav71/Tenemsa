package com.tenemsa.platform.project.dto;

import com.tenemsa.platform.employee.model.Role;

/** A company member as shown in assignee pickers: deliberately just number, name and role (no email, no salary). */
public record PersonResponse(Integer empId, String name, Role role) {}
