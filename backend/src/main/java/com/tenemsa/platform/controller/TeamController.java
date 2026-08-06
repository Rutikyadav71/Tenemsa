package com.tenemsa.platform.controller;

import com.tenemsa.platform.model.Team;
import com.tenemsa.platform.repository.TeamRepository;
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

    private static final String MANAGE_ANY_OR_OWN =
        "hasAuthority('team:manage:any') or " +
        "(hasAuthority('team:manage:own') and @teamSecurity.isTeamLead(#id))";

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

    // Creating/deleting a team outright is Owner/Admin only — a Manager
    // manages the membership and details of a team they already lead
    // (team:manage:own, below), but doesn't stand up or tear down teams.
    @PreAuthorize("hasAuthority('team:manage:any')")
    @PostMapping
    public Team create(@RequestBody Team team) {
        if (team.getCreatedDate() == null) team.setCreatedDate(LocalDate.now());
        return repo.save(team);
    }

    @PreAuthorize(MANAGE_ANY_OR_OWN)
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

    @PreAuthorize("hasAuthority('team:manage:any')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        repo.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize(MANAGE_ANY_OR_OWN)
    @PostMapping("/{id}/members/{empId}")
    public ResponseEntity<?> addMember(@PathVariable Long id, @PathVariable int empId) {
        return repo.findById(id).map(t -> {
            if (!t.getMemberIds().contains(empId)) {
                t.getMemberIds().add(empId);
            }
            return ResponseEntity.ok(repo.save(t));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize(MANAGE_ANY_OR_OWN)
    @DeleteMapping("/{id}/members/{empId}")
    public ResponseEntity<?> removeMember(@PathVariable Long id, @PathVariable int empId) {
        return repo.findById(id).map(t -> {
            t.getMemberIds().removeIf(mid -> mid != null && mid == empId);
            if (Objects.equals(t.getTeamLeadId(), empId)) t.setTeamLeadId(null);
            return ResponseEntity.ok(repo.save(t));
        }).orElse(ResponseEntity.notFound().build());
    }
}
