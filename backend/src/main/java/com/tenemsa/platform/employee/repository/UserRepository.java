package com.tenemsa.platform.repository;

import com.tenemsa.platform.model.Role;
import com.tenemsa.platform.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // Email is unique per company only (uk_users_company_email), so the
    // same address can exist in several workspaces. Two finders follow:
    // findByEmailAndCompanyId for anything with a tenant (JWT callers) and
    // findAllByEmail for the pre-authentication flows (login, forgot
    // password, Google sign-in) that must resolve the workspace themselves.
    //
    // JOIN FETCH u.company on every method below: every caller eventually
    // needs company data (companyId/companyName in responses, tenant claims
    // in the JWT), and every method here is a realistic candidate for
    // having its result serialized straight to JSON by a controller. Without
    // eager fetching, User.company stays an unloaded lazy proxy — which
    // throws LazyInitializationException if touched outside an active
    // session (see OAuth2LoginSuccessHandler's history), or a Jackson
    // InvalidDefinitionException on the raw Hibernate proxy object if
    // serialized directly even WITHIN an active session (see
    // EmployeeController's history — different failure mode, same root
    // cause: an uninitialized/proxy-wrapped relation reaching code that
    // wasn't written to expect one). Fetching eagerly here removes the
    // possibility of either, everywhere this repository is used, rather
    // than patching each call site reactively as new ones get hit.
    @Query("SELECT u FROM User u JOIN FETCH u.company WHERE u.email = :email AND u.company.id = :companyId")
    Optional<User> findByEmailAndCompanyId(@Param("email") String email, @Param("companyId") Long companyId);

    @Query("SELECT u FROM User u JOIN FETCH u.company WHERE u.email = :email")
    List<User> findAllByEmail(@Param("email") String email);

    boolean existsByEmailAndCompanyId(String email, Long companyId);

    // empId is only unique per company, so lookups by empId alone are
    // ambiguous across tenants and must always be scoped.
    @Query("SELECT u FROM User u JOIN FETCH u.company WHERE u.company.id = :companyId AND u.empId = :empId")
    Optional<User> findByCompanyIdAndEmpId(@Param("companyId") Long companyId, @Param("empId") Integer empId);

    // Surrogate-id lookup that cannot cross tenants. Plain findById(id) is
    // unscoped: any caller who guesses an id could reach another company's user.
    @Query("SELECT u FROM User u JOIN FETCH u.company WHERE u.company.id = :companyId AND u.id = :id")
    Optional<User> findByIdAndCompanyId(@Param("id") Long id, @Param("companyId") Long companyId);

    // Next business id inside ONE company (empId is per-company, see uk_users_company_empid).
    @Query("SELECT COALESCE(MAX(u.empId), 0) + 1 FROM User u WHERE u.company.id = :companyId")
    int nextEmpId(@Param("companyId") Long companyId);

    long countByCompanyId(Long companyId);

    boolean existsByCompanyIdAndEmpId(Long companyId, Integer empId);

    @Query("SELECT u FROM User u JOIN FETCH u.company WHERE u.company.id = :companyId")
    List<User> findAllByCompanyId(@Param("companyId") Long companyId);

    // Paginated variant for GET /api/employees — safe to combine JOIN FETCH
    // with Pageable here specifically because company is a @ManyToOne
    // (to-one) relation. Pagination + JOIN FETCH only becomes a problem
    // (Hibernate falls back to in-memory pagination, defeating the point)
    // for @OneToMany/@ManyToMany collection fetches, which this isn't.
    @Query("SELECT u FROM User u JOIN FETCH u.company WHERE u.company.id = :companyId")
    Page<User> findAllByCompanyId(@Param("companyId") Long companyId, Pageable pageable);

    @Query("SELECT u FROM User u JOIN FETCH u.company WHERE u.company.id = :companyId AND u.role = :role")
    List<User> findAllByCompanyIdAndRole(@Param("companyId") Long companyId, @Param("role") Role role);

    // Manager-scoped employee listing (holds user:view:team, not
    // user:view:any) — see EmployeeController.getAll() and
    // TeamSecurity.teamMemberEmpIds().
    @Query("SELECT u FROM User u JOIN FETCH u.company WHERE u.company.id = :companyId AND u.empId IN :empIds")
    List<User> findAllByCompanyIdAndEmpIdIn(@Param("companyId") Long companyId, @Param("empIds") java.util.Collection<Integer> empIds);

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
       SELECT u FROM User u JOIN FETCH u.company
       WHERE u.company.id = :companyId
         AND (LOWER(u.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
          OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')))
    """)
    List<User> searchUsersInCompany(@Param("companyId") Long companyId, @Param("keyword") String keyword);
}
