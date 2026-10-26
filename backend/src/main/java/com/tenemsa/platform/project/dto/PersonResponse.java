package com.tenemsa.platform.dto;

import com.tenemsa.platform.model.Role;

/** A company member as shown in assignee pickers: deliberately just number, name and role (no email, no salary). */
public record PersonResponse(Integer empId, String name, Role role) {}
