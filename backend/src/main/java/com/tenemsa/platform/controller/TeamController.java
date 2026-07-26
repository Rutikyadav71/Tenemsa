package com.tenemsa.platform.controller;

import com.tenemsa.platform.model.Team;
import com.tenemsa.platform.repository.TeamRepository;
import com.tenemsa.platform.security.SecurityRoles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/teams")
public class TeamController {

    @Autowired TeamRepository repo;
    // NOTE: original file also injected EmployeeRepository here but never
    // called it anywhere in the class — dead field, removed (same pattern
    // found in AttendanceController).

    /** Get ALL teams — accessible to any authenticated user */
    @GetMapping
    public List<Team> getAll() {
        return repo.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Team> getById(@PathVariable Long id) {
        return repo.findById(id)
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
        return repo.findAll().stream()
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

    // Write operations previously had no @PreAuthorize at all — any
    // authenticated employee (not just admins) could create, edit, or delete
    // any team, or add/remove members from any team. Same class of gap
    // already fixed elsewhere; closing it here too.
    @PreAuthorize(SecurityRoles.IS_ADMIN)
    @PostMapping
    public Team create(@RequestBody Team team) {
        if (team.getCreatedDate() == null) team.setCreatedDate(LocalDate.now());
        return repo.save(team);
    }

    @PreAuthorize(SecurityRoles.IS_ADMIN)
    @PutMapping("/{id}")
    public ResponseEntity<Team> update(@PathVariable Long id, @RequestBody Team team) {
        return repo.findById(id).map(t -> {
            t.setName(team.getName());
            t.setDescription(team.getDescription());
            t.setProjectName(team.getProjectName());
            t.setDeadline(team.getDeadline());
            t.setStatus(team.getStatus());
            t.setMemberIds(team.getMemberIds() != null ? team.getMemberIds() : new ArrayList<>());
            t.setTeamLeadId(team.getTeamLeadId());
            return ResponseEntity.ok(repo.save(t));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize(SecurityRoles.IS_ADMIN)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        repo.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize(SecurityRoles.IS_ADMIN)
    @PostMapping("/{id}/members/{empId}")
    public ResponseEntity<?> addMember(@PathVariable Long id, @PathVariable int empId) {
        return repo.findById(id).map(t -> {
            if (!t.getMemberIds().contains(empId)) {
                t.getMemberIds().add(empId);
            }
            return ResponseEntity.ok(repo.save(t));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize(SecurityRoles.IS_ADMIN)
    @DeleteMapping("/{id}/members/{empId}")
    public ResponseEntity<?> removeMember(@PathVariable Long id, @PathVariable int empId) {
        return repo.findById(id).map(t -> {
            t.getMemberIds().removeIf(mid -> mid != null && mid == empId);
            if (Objects.equals(t.getTeamLeadId(), empId)) t.setTeamLeadId(null);
            return ResponseEntity.ok(repo.save(t));
        }).orElse(ResponseEntity.notFound().build());
    }
}
