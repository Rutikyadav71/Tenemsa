package com.tenemsa.platform.service;

import com.tenemsa.platform.model.Company;
import com.tenemsa.platform.model.Notification;
import com.tenemsa.platform.model.User;
import com.tenemsa.platform.repository.CompanyRepository;
import com.tenemsa.platform.repository.NotificationRepository;
import com.tenemsa.platform.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EmployeeServiceImpl implements EmployeeService {

    @Autowired private UserRepository repo;
    @Autowired private CompanyRepository companyRepo;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private NotificationRepository notificationRepo;
    // Was a private sendWelcomeEmail() built directly on JavaMailSender here,
    // duplicating what a separate (unused) EmailService.java already
    // attempted elsewhere. Consolidated into one real, shared EmailService —
    // see that class for why, and for the other emails it now sends
    // (verification, password reset) that this class has no reason to know about.
    @Autowired private EmailService emailService;

    @Override
    public Page<User> getAll(Long companyId, Pageable pageable) {
        return repo.findAllByCompanyId(companyId, pageable);
    }

    @Override
    public List<User> getByEmpIds(Long companyId, java.util.Collection<Integer> empIds) {
        return repo.findAllByCompanyIdAndEmpIdIn(companyId, empIds);
    }

    @Override
    public User getById(Long companyId, int empId) {
        return repo.findByCompanyIdAndEmpId(companyId, empId).orElse(null);
    }

    @Override
    public User create(Long companyId, User user) {
        Company company = companyRepo.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));
        user.setCompany(company);

        // Employee numbers are per company: use the one asked for if it is free,
        // otherwise (or when none was given) the next free number.
        if (user.getEmpId() == null) {
            user.setEmpId(repo.nextEmpId(companyId));
        } else if (repo.existsByCompanyIdAndEmpId(companyId, user.getEmpId())) {
            throw new RuntimeException("Employee ID " + user.getEmpId() + " is already used in your company.");
        }

        if (repo.existsByEmailAndCompanyId(user.getEmail(), companyId)) {
            throw new RuntimeException("An employee with this email already exists in your company.");
        }

        String plainPassword = user.getPassword();
        user.setPassword(passwordEncoder.encode(plainPassword));

        User saved = repo.save(user);
        Notification notif = new Notification("Employee added: " + saved.getName());
        notif.setCompanyId(companyId);
        notificationRepo.save(notif);
        emailService.sendWelcomeEmail(user.getEmail(), user.getName(), plainPassword);

        return saved;
    }

    @Override
    public User update(Long companyId, int empId, User user) {
        User existing = repo.findByCompanyIdAndEmpId(companyId, empId).orElse(null);
        if (existing == null) return null;

        existing.setName(user.getName());
        existing.setDepartment(user.getDepartment());
        if (user.getEmail() != null && !user.getEmail().equalsIgnoreCase(existing.getEmail())
                && repo.existsByEmailAndCompanyId(user.getEmail(), companyId)) {
            throw new RuntimeException("Another employee in your company already uses this email.");
        }
        existing.setEmail(user.getEmail());
        existing.setSalary(user.getSalary());

        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            existing.setPassword(passwordEncoder.encode(user.getPassword()));
        }

        return repo.save(existing);
    }

    @Override
    public void delete(Long companyId, int empId) {
        User user = repo.findByCompanyIdAndEmpId(companyId, empId).orElse(null);
        if (user != null) {
            repo.delete(user);
        } else {
            System.out.println("Employee not found with empId: " + empId + " in company " + companyId);
        }
    }

    @Override
    public List<User> searchEmployees(Long companyId, String keyword) {
        return repo.searchUsersInCompany(companyId, keyword);
    }

    @Override
    @Transactional
    public void deleteMultiple(Long companyId, List<Integer> empIds) {
        repo.deleteAllByCompanyIdAndEmpIdIn(companyId, empIds);
    }
}
