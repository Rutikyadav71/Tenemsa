package com.tenemsa.platform.service;

import com.tenemsa.platform.model.User;
import com.tenemsa.platform.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/** Turns business employee numbers into display names, inside ONE company, with a single query. */
@Component
public class EmployeeNameLookup {

    @Autowired private UserRepository userRepository;

    public Map<Integer, String> namesFor(Long companyId, Collection<Integer> empIds) {
        Map<Integer, String> names = new HashMap<>();
        HashSet<Integer> wanted = new HashSet<>();
        for (Integer id : empIds) if (id != null) wanted.add(id);
        if (wanted.isEmpty()) return names;
        for (User u : userRepository.findAllByCompanyIdAndEmpIdIn(companyId, wanted)) {
            if (u.getEmpId() != null) names.put(u.getEmpId(), u.getName());
        }
        return names;
    }

    public boolean exists(Long companyId, Integer empId) {
        return empId != null && userRepository.existsByCompanyIdAndEmpId(companyId, empId);
    }

    /** Fails with a readable 400 when the number is given but is not an employee of this company. */
    public void requireExistsIfGiven(Long companyId, Integer empId, String what) {
        if (empId != null && !exists(companyId, empId)) {
            throw new IllegalArgumentException(what + " #" + empId + " does not exist in your company.");
        }
    }
}
