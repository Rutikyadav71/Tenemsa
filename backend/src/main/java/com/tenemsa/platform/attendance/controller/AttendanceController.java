package com.tenemsa.platform.controller;

import com.tenemsa.platform.dto.AttendanceMarkRequest;
import com.tenemsa.platform.model.Attendance;
import com.tenemsa.platform.model.AttendanceStatus;
import com.tenemsa.platform.repository.AttendanceRepository;
import com.tenemsa.platform.repository.UserRepository;
import com.tenemsa.platform.security.AuthContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    @Autowired AttendanceRepository repo;
    @Autowired AuthContext authContext;
    @Autowired UserRepository userRepository;

    /** Tenant of the caller, from the JWT — every repository call below is scoped by it. */
    private Long cid() { return authContext.currentCompanyId(); }

    private static final String VIEW_SCOPED =
        "hasAuthority('attendance:manage:any') or " +
        "(hasAuthority('attendance:view:team') and @teamSecurity.managesEmployee(#empId)) or " +
        "@employeeSecurity.isSelf(#empId)";

    private static final String MARK_SELF_OR_MANAGE =
        "hasAuthority('attendance:manage:any') or " +
        "(hasAuthority('attendance:mark:self') and @employeeSecurity.isSelf(#empId))";

    /** Employee self check-in, or HR marking someone in directly. */
    @PreAuthorize(MARK_SELF_OR_MANAGE)
    @PostMapping("/checkin/{empId}")
    public ResponseEntity<?> checkIn(@PathVariable int empId) {
        LocalDate today = LocalDate.now();
        Optional<Attendance> existing = repo.findByCompanyIdAndEmpIdAndDate(cid(), empId, today);
        if (existing.isPresent() && existing.get().getCheckIn() != null)
            return ResponseEntity.badRequest().body("Already checked in today");

        Attendance a = existing.orElseGet(() -> {
            Attendance n = new Attendance();
            n.setCompanyId(cid()); n.setEmpId(empId); n.setDate(today); return n;
        });
        a.setCheckIn(LocalTime.now().withSecond(0).withNano(0));
        a.setStatus(LocalTime.now().isAfter(LocalTime.of(10, 0))
            ? AttendanceStatus.LATE : AttendanceStatus.PRESENT);
        a.setMarkedBy("SELF");
        return ResponseEntity.ok(repo.save(a));
    }

    /** Employee self check-out */
    @PreAuthorize(MARK_SELF_OR_MANAGE)
    @PostMapping("/checkout/{empId}")
    public ResponseEntity<?> checkOut(@PathVariable int empId) {
        Optional<Attendance> opt = repo.findByCompanyIdAndEmpIdAndDate(cid(), empId, LocalDate.now());
        if (opt.isEmpty() || opt.get().getCheckIn() == null)
            return ResponseEntity.badRequest().body("No check-in found for today");
        Attendance a = opt.get();
        if (a.getCheckOut() != null)
            return ResponseEntity.badRequest().body("Already checked out");

        LocalTime out = LocalTime.now().withSecond(0).withNano(0);
        a.setCheckOut(out);
        double hrs = Duration.between(a.getCheckIn(), out).toMinutes() / 60.0;
        a.setWorkHours(Math.round(hrs * 100.0) / 100.0);
        if      (hrs < 4)                                    a.setStatus(AttendanceStatus.HALF_DAY);
        else if (a.getCheckIn().isAfter(LocalTime.of(10, 0))) a.setStatus(AttendanceStatus.LATE);
        else                                                   a.setStatus(AttendanceStatus.PRESENT);
        return ResponseEntity.ok(repo.save(a));
    }

    /** Today's record for an employee — any-view (HR), team-view (Manager, scoped), or self. */
    @PreAuthorize(VIEW_SCOPED)
    @GetMapping("/today/{empId}")
    public ResponseEntity<?> getToday(@PathVariable int empId) {
        return ResponseEntity.ok(repo.findByCompanyIdAndEmpIdAndDate(cid(), empId, LocalDate.now()).orElse(null));
    }

    /** Employee's records in a date range */
    @PreAuthorize(VIEW_SCOPED)
    @GetMapping("/employee/{empId}")
    public ResponseEntity<List<Attendance>> getByEmployee(
            @PathVariable int empId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        if (start == null) start = LocalDate.now().withDayOfMonth(1);
        if (end   == null) end   = LocalDate.now();
        return ResponseEntity.ok(repo.findByCompanyIdAndEmpIdAndDateBetween(cid(), empId, start, end));
    }

    /** All attendance for a specific date — company-wide, HR/Owner only. */
    @PreAuthorize("hasAuthority('attendance:manage:any')")
    @GetMapping("/date/{date}")
    public ResponseEntity<List<Attendance>> getByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(repo.findByCompanyIdAndDate(cid(), date));
    }

    /** HR: mark / update attendance for any employee */
    @PreAuthorize("hasAuthority('attendance:manage:any')")
    @PostMapping("/mark")
    public ResponseEntity<Attendance> mark(@Valid @RequestBody AttendanceMarkRequest req) {
        if (!userRepository.existsByCompanyIdAndEmpId(cid(), req.empId())) {
            throw new IllegalArgumentException("Employee #" + req.empId() + " does not exist in your company.");
        }
        // Built from the request, so a client can never set the company, id or work hours itself.
        Attendance a = new Attendance();
        a.setEmpId(req.empId());
        a.setDate(req.date());
        a.setStatus(req.status());
        a.setCheckIn(req.checkIn());
        a.setCheckOut(req.checkOut());
        a.setNotes(req.notes());
        // Was hardcoded to the literal string "ADMIN" regardless of which
        // admin actually made the change — real caller identity was
        // available via the JWT the whole time, just never used for this.
        String marker = authContext.currentEmail();
        a.setMarkedBy(marker);
        a.setCompanyId(cid()); // never trust a client-supplied companyId
        Optional<Attendance> existing = repo.findByCompanyIdAndEmpIdAndDate(cid(), a.getEmpId(), a.getDate());
        if (existing.isPresent()) {
            Attendance ex = existing.get();
            ex.setStatus(a.getStatus());
            if (a.getCheckIn()  != null) ex.setCheckIn(a.getCheckIn());
            if (a.getCheckOut() != null) ex.setCheckOut(a.getCheckOut());
            if (a.getNotes()    != null) ex.setNotes(a.getNotes());
            ex.setMarkedBy(marker);
            if (ex.getCheckIn() != null && ex.getCheckOut() != null) {
                double h = Duration.between(ex.getCheckIn(), ex.getCheckOut()).toMinutes() / 60.0;
                ex.setWorkHours(Math.round(h * 100.0) / 100.0);
            }
            return ResponseEntity.ok(repo.save(ex));
        }
        if (a.getCheckIn() != null && a.getCheckOut() != null) {
            double h = Duration.between(a.getCheckIn(), a.getCheckOut()).toMinutes() / 60.0;
            a.setWorkHours(Math.round(h * 100.0) / 100.0);
        }
        return ResponseEntity.ok(repo.save(a));
    }

    /** HR: all attendance between two dates */
    @PreAuthorize("hasAuthority('attendance:manage:any')")
    @GetMapping("/range")
    public ResponseEntity<List<Attendance>> getRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ResponseEntity.ok(repo.findByCompanyIdAndDateBetween(cid(), start, end));
    }

    /** Monthly summary stats for one employee */
    @PreAuthorize(VIEW_SCOPED)
    @GetMapping("/summary/{empId}")
    public ResponseEntity<Map<String, Object>> summary(
            @PathVariable int empId,
            @RequestParam(defaultValue = "0") int month,
            @RequestParam(defaultValue = "0") int year) {
        if (month == 0) month = LocalDate.now().getMonthValue();
        if (year  == 0) year  = LocalDate.now().getYear();
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end   = start.withDayOfMonth(start.lengthOfMonth());
        List<Attendance> recs = repo.findByCompanyIdAndEmpIdAndDateBetween(cid(), empId, start, end);

        long present  = recs.stream().filter(r -> r.getStatus() == AttendanceStatus.PRESENT).count();
        long late     = recs.stream().filter(r -> r.getStatus() == AttendanceStatus.LATE).count();
        long absent   = recs.stream().filter(r -> r.getStatus() == AttendanceStatus.ABSENT).count();
        long halfDay  = recs.stream().filter(r -> r.getStatus() == AttendanceStatus.HALF_DAY).count();
        long onLeave  = recs.stream().filter(r -> r.getStatus() == AttendanceStatus.ON_LEAVE).count();
        double totalH = recs.stream().mapToDouble(r -> r.getWorkHours() != null ? r.getWorkHours() : 0).sum();
        int workDays  = start.lengthOfMonth();

        Map<String, Object> s = new LinkedHashMap<>();
        s.put("empId", empId); s.put("month", month); s.put("year", year);
        s.put("present", present); s.put("late", late);
        s.put("absent", absent);   s.put("halfDay", halfDay);
        s.put("onLeave", onLeave); s.put("totalWorkHours", Math.round(totalH * 100.0) / 100.0);
        s.put("attendanceRate", workDays > 0
            ? Math.round((present + late) * 100.0 / workDays * 100.0) / 100.0 : 0);
        return ResponseEntity.ok(s);
    }
}
