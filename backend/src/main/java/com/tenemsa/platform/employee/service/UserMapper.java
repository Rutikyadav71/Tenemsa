package com.tenemsa.platform.employee.service;

import com.tenemsa.platform.employee.dto.UserResponseDTO;
import com.tenemsa.platform.employee.model.User;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The one place User -> UserResponseDTO conversion happens. Controllers
 * call this instead of returning entities directly (see UserResponseDTO's
 * class comment for what that fixes). Requires the User passed in to have
 * an already-initialized company — see UserRepository, every method there
 * uses JOIN FETCH specifically so callers of this mapper never hand it an
 * entity with a lazy, uninitialized company proxy.
 */
@Component
public class UserMapper {

    public UserResponseDTO toDto(User user) {
        if (user == null) return null;
        UserResponseDTO dto = new UserResponseDTO();
        dto.setId(user.getId());
        dto.setEmpId(user.getEmpId());
        dto.setName(user.getName());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole());
        dto.setDepartment(user.getDepartment());
        dto.setSalary(user.getSalary());
        dto.setProfileImageUrl(user.getProfileImageUrl());
        dto.setFrozen(user.isFrozen());
        dto.setPrimaryOwner(user.isPrimaryOwner());
        dto.setEmailVerified(user.isEmailVerified());
        dto.setCompanyId(user.getCompany().getId());
        dto.setCompanyName(user.getCompany().getName());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setUpdatedAt(user.getUpdatedAt());
        return dto;
    }

    public List<UserResponseDTO> toDtoList(List<User> users) {
        return users.stream().map(this::toDto).toList();
    }
}
