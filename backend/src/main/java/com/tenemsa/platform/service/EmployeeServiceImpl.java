package com.tenemsa.platform.service;

import com.tenemsa.platform.model.Company;
import com.tenemsa.platform.model.Notification;
import com.tenemsa.platform.model.User;
import com.tenemsa.platform.repository.CompanyRepository;
import com.tenemsa.platform.repository.NotificationRepository;
import com.tenemsa.platform.repository.UserRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EmployeeServiceImpl implements EmployeeService {

    @Autowired private UserRepository repo;
    @Autowired private CompanyRepository companyRepo;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JavaMailSender mailSender;
    @Autowired private NotificationRepository notificationRepo;

    @Value("${app.email.enabled}")
    private boolean emailEnabled;

    @Override
    public List<User> getAll(Long companyId) {
        return repo.findAllByCompanyId(companyId);
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

        String plainPassword = user.getPassword();
        user.setPassword(passwordEncoder.encode(plainPassword));

        User saved = repo.save(user);
        notificationRepo.save(new Notification("Employee added: " + saved.getName()));

        if (emailEnabled) {
            try {
                sendWelcomeEmail(user.getEmail(), user.getName(), plainPassword);
            } catch (MessagingException e) {
                System.err.println("Failed to send email to " + user.getEmail());
                e.printStackTrace();
            }
        } else {
            System.out.println("Email disabled. Skipping email to: " + user.getEmail());
        }

        return saved;
    }

    @Override
    public User update(Long companyId, int empId, User user) {
        User existing = repo.findByCompanyIdAndEmpId(companyId, empId).orElse(null);
        if (existing == null) return null;

        existing.setName(user.getName());
        existing.setDepartment(user.getDepartment());
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

    private void sendWelcomeEmail(String toEmail, String name, String password) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);

        helper.setTo(toEmail);
        helper.setSubject("Welcome to EMS - Your Login Credentials");
        helper.setText(
                "<p>Hello <strong>" + name + "</strong>,</p>" +
                        "<p>You have been successfully added as an employee to our EMS system.</p>" +
                        "<p><strong>Email:</strong> " + toEmail + "<br>" +
                        "<strong>Password:</strong> " + password + "</p>" +
                        "<p>Regards,<br>EMS Admin</p>",
                true
        );

        mailSender.send(message);
        System.out.println("Email sent successfully to " + toEmail);
    }
}
