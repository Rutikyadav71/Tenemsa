package com.tenemsa.platform.controller;

import com.tenemsa.platform.dto.TeamRequest;
import com.tenemsa.platform.model.Team;
import com.tenemsa.platform.model.TeamStatus;
import com.tenemsa.platform.repository.TeamRepository;
import com.tenemsa.platform.repository.UserRepository;
import com.tenemsa.platform.security.AuthContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/teams")
public class TeamController {

    @Autowired TeamRepository repo;
    @Autowired AuthContext authContext;
    @Autowired UserRepository userRepository;

    private Long cid() { return authContext.currentCompanyId(); }

    private static final String MANAGE_ANY_OR_OWN =
        "hasAuthority('team:manage:any') or " +
        "(hasAuthority('team:manage:own') and @teamSecurity.isTeamLead(#id))";

    /** Get ALL teams — accessible to any authenticated user */
    @GetMapping
    public List<Team> getAll() {
        return repo.findByCompanyId(cid());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Team> getById(@PathVariable Long id) {
        return repo.findByIdAndCompanyId(id, cid())
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get teams for a specific employee.
     * Uses Java stream filter to avoid JPA ElementCollection query issues.
     * The Integer autoboxing / unboxing is handled explicitly.
     */
    @GetMapping("/employee/{empId}")
    public List<Team> getByEmployee(@PathVariable int empId) {
        return repo.findByCompanyId(cid()).stream()
            .filter(t -> {
                if (t.getMemberIds() == null || t.getMemberIds().isEmpty()) return false;
                // Explicit Integer comparison — avoid autoboxing ambiguity
                for (Integer id : t.getMemberIds()) {
                    if (id != null && id == empId) return true;
                }
                return false;
            })
            .collect(Collectors.toList());
    }

    // Creating/deleting a team outright is Owner/Admin only — a Manager
    // manages the membership and details of a team they already lead
    // (team:manage:own, below), but doesn't stand up or tear down teams.
    @PreAuthorize("hasAuthority('team:manage:any')")
    @PostMapping
    public Team create(@Valid @RequestBody TeamRequest req) {
        requireKnownEmployees(req.memberIds(), req.teamLeadId());
        Team team = new Team();         // always a new row, never an overwrite by id
        team.setCompanyId(cid());       // never trust a client-supplied companyId
        team.setCreatedDate(LocalDate.now());
        apply(team, req);
        return repo.save(team);
    }

    /** Copies the editable fields of a request onto a team. */
    private void apply(Team t, TeamRequest req) {
        t.setName(req.name().trim());
        t.setDescription(req.description());
        t.setProjectName(req.projectName());
        t.setDeadline(req.deadline());
        t.setStatus(req.status() != null ? req.status() : TeamStatus.ACTIVE);
        t.setMemberIds(req.memberIds() != null ? new ArrayList<>(new LinkedHashSet<>(req.memberIds())) : new ArrayList<>());
        t.setTeamLeadId(req.teamLeadId());
    }

    /** Members and the lead must be real employees of the caller's own company. */
    private void requireKnownEmployees(Collection<Integer> memberIds, Integer leadId) {
        Set<Integer> ids = new LinkedHashSet<>();
        if (memberIds != null) ids.addAll(memberIds);
        if (leadId != null) ids.add(leadId);
        for (Integer id : ids) {
            if (!userRepository.existsByCompanyIdAndEmpId(cid(), id)) {
                throw new IllegalArgumentException("Employee #" + id + " does not exist in your company.");
            }
        }
    }

    @PreAuthorize(MANAGE_ANY_OR_OWN)
    @PutMapping("/{id}")
    public ResponseEntity<Team> update(@PathVariable Long id, @Valid @RequestBody TeamRequest req) {
        return repo.findByIdAndCompanyId(id, cid()).map(t -> {
            requireKnownEmployees(req.memberIds(), req.teamLeadId());
            apply(t, req);
            return ResponseEntity.ok(repo.save(t));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAuthority('team:manage:any')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return repo.findByIdAndCompanyId(id, cid()).map(t -> {
            repo.delete(t);
            return ResponseEntity.ok().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize(MANAGE_ANY_OR_OWN)
    @PostMapping("/{id}/members/{empId}")
    public ResponseEntity<?> addMember(@PathVariable Long id, @PathVariable int empId) {
        return repo.findByIdAndCompanyId(id, cid()).map(t -> {
            requireKnownEmployees(List.of(empId), null);
            if (!t.getMemberIds().contains(empId)) {
                t.getMemberIds().add(empId);
            }
            return ResponseEntity.ok(repo.save(t));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize(MANAGE_ANY_OR_OWN)
    @DeleteMapping("/{id}/members/{empId}")
    public ResponseEntity<?> removeMember(@PathVariable Long id, @PathVariable int empId) {
        return repo.findByIdAndCompanyId(id, cid()).map(t -> {
            t.getMemberIds().removeIf(mid -> mid != null && mid == empId);
            if (Objects.equals(t.getTeamLeadId(), empId)) t.setTeamLeadId(null);
            return ResponseEntity.ok(repo.save(t));
        }).orElse(ResponseEntity.notFound().build());
    }
}
