package com.tenemsa.platform.repository;

import com.tenemsa.platform.model.Role;
import com.tenemsa.platform.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // Email is globally unique (see uniqueConstraint on User) so this needs
    // no company scoping — used by login, which doesn't have a company
    // context to filter by yet.
    Optional<User> findByEmail(String email);

    // empId is only unique per company, so lookups by empId alone are
    // ambiguous across tenants and must always be scoped.
    Optional<User> findByCompanyIdAndEmpId(Long companyId, Integer empId);

    boolean existsByCompanyIdAndEmpId(Long companyId, Integer empId);

    List<User> findAllByCompanyId(Long companyId);

    List<User> findAllByCompanyIdAndRole(Long companyId, Role role);

    long countByCompanyIdAndRoleIn(Long companyId, List<Role> roles);

    @Modifying
    @Transactional
    void deleteAllByCompanyIdAndEmpIdIn(Long companyId, List<Integer> empIds);

    // Search is now scoped to the caller's company — previously
    // searchEmployees() had no tenant boundary at all (a pre-existing gap
    // that only mattered once >1 company could exist, which is exactly what
    // this phase introduces, so it has to be closed here rather than later).
    // Role is no longer searched as free text since it's an enum now, not a
    // partial-match string.
    @Query("""
       SELECT u FROM User u
       WHERE u.company.id = :companyId
         AND (LOWER(u.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
          OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')))
    """)
    List<User> searchUsersInCompany(@Param("companyId") Long companyId, @Param("keyword") String keyword);
}
