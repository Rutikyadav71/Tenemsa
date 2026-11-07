package com.tenemsa.platform.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Fills the database with a LOT of realistic demo data so every feature can be tested at scale.
 *
 * Off by default. Turn it on with {@code --tenemsa.seed.enabled=true} (see docs/seed-data.md).
 * Everything it creates lives in companies whose slug starts with {@code demo-}, so it can be
 * wiped with {@code --tenemsa.seed.reset=true} without touching real companies.
 *
 * It writes with plain SQL (JdbcTemplate) on purpose: it is fast, needs no changes to the
 * entities, and the SQL is plain enough to run on both MySQL and PostgreSQL.
 * Same options => same data (a fixed random seed), so two testers see the same thing.
 */
@Component
@ConditionalOnProperty(name = "tenemsa.seed.enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    static final String SLUG_PREFIX = "demo-";
    static final String PASSWORD = "Demo@1234";
    private static final int CHUNK = 1000;

    private static final String[] FIRST = {"Asha", "Rohan", "Meera", "Dev", "Priya", "Arjun", "Neha", "Karan", "Isha", "Vikram",
            "Sana", "Aditya", "Riya", "Kabir", "Anaya", "Rahul", "Tara", "Nikhil", "Divya", "Sameer", "Pooja", "Aman",
            "Kavya", "Yash", "Ira", "Harsh", "Lata", "Omar", "Zoya", "Ethan", "Maya", "Liam", "Nora", "Lucas", "Emma"};
    private static final String[] LAST = {"Nair", "Shah", "Patel", "Iyer", "Gupta", "Mehta", "Rao", "Khan", "Joshi", "Verma",
            "Singh", "Reddy", "Kulkarni", "Das", "Bose", "Menon", "Pillai", "Sharma", "Kapoor", "Malhotra", "Chopra",
            "Yadav", "Jain", "Bhat", "Sen", "Ghosh", "Ali", "Fernandes", "Walker", "Brown"};
    private static final String[] COMPANY_WORDS = {"Northwind", "Bluepeak", "Orbit", "Helix", "Maple", "Quartz", "Zenith",
            "Lumen", "Vertex", "Cobalt", "Nimbus", "Ember", "Atlas", "Pioneer", "Summit", "Harbor", "Falcon", "Cedar",
            "Sparrow", "Granite", "Aurora", "Kite", "Willow", "Tangent", "Meridian"};
    private static final String[] COMPANY_KINDS = {"Labs", "Systems", "Works", "Analytics", "Logistics", "Health", "Retail",
            "Finance", "Media", "Energy"};
    private static final String[] DEPARTMENTS = {"Engineering", "Product", "Design", "Finance", "Sales", "Marketing", "Support", "Operations"};
    private static final String[] PROJECT_NAMES = {"Website revamp", "Mobile app", "Billing engine", "Customer portal", "Data pipeline",
            "Internal tools", "Onboarding flow", "Search upgrade", "Payments v2", "Analytics dashboard", "Security audit", "Partner API"};
    private static final String[] PROJECT_KEYS = {"WEB", "APP", "BILL", "PORT", "DATA", "TOOL", "ONB", "SRCH", "PAY", "DASH", "SEC", "API"};
    private static final String[] ISSUE_TITLES = {"Build login API", "Fix crash on save", "Design empty states", "Write unit tests",
            "Refactor service layer", "Add CSV export", "Improve page load time", "Update onboarding copy", "Fix timezone bug",
            "Add role filter", "Migrate to new library", "Review accessibility", "Set up CI pipeline", "Handle failed payments",
            "Polish mobile layout", "Add audit trail", "Cache search results", "Document the API", "Investigate memory leak",
            "Add dark mode", "Rate limit public endpoints", "Support bulk import", "Fix duplicate emails", "Add search filters"};
    private static final String[] LABELS = {"api", "ui", "backend", "bug", "docs", "perf", "security", "ux", "infra"};
    private static final String[] COMMENTS = {"Looks good to me.", "Can we split this into two tasks?", "Blocked on the design review.",
            "Fixed in the latest commit.", "Needs more detail before I can start.", "Moved to next sprint.", "Tested on staging, works.",
            "Please add a test for the edge case.", "Waiting for the client's answer."};
    private static final String[] AGENTS = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0 Safari/537.36",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) Safari/604.1",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Firefox/121.0"};
    private static final String[] CHAT_LINES = {"Hi, do you have a minute?", "Sure, tell me.", "Can you review my leave request?",
            "Approved, enjoy!", "Thanks a lot.", "The report is ready.", "Great, sharing it in the meeting.", "Did you push the fix?",
            "Yes, it is merged.", "Let's sync tomorrow morning.", "Works for me.", "Reminder: sprint review at 4.", "On my way.",
            "Can you send the document?", "Uploaded it to Documents."};
    private static final String[] DOC_TITLES = {"Leave policy", "Employee handbook", "Code of conduct", "Expense policy",
            "Remote work policy", "Security guidelines", "Onboarding checklist", "Holiday calendar", "Travel policy", "Performance review guide",
            "Benefits overview", "IT acceptable use"};
    private static final String[] MEETINGS = {"Sprint planning", "Weekly sync", "Retro", "Client kickoff", "Design review",
            "Quarterly planning", "Incident review", "Hiring sync", "Roadmap discussion", "Budget review"};
    private static final String[] HR_QUESTIONS = {"How many leave days do I have left?", "Who is on leave next week?",
            "Summarise my team's attendance this month.", "What is our remote work policy?", "Who has pending leave requests?"};
    private static final String[] AI_FEATURES = {"HR_ASSISTANT", "HR_ASSISTANT", "MEETING_SUMMARY", "TASK_GENERATION",
            "COMMUNICATION_DRAFT", "PROJECT_REPORT", "KB_ANSWER", "KB_EMBEDDING"};

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Environment env;
    private final ApplicationContext ctx;

    @Value("${tenemsa.seed.companies:20}") private int companies;
    @Value("${tenemsa.seed.employees:30}") private int employees;
    @Value("${tenemsa.seed.attendance-days:45}") private int attendanceDays;
    @Value("${tenemsa.seed.reset:false}") private boolean reset;
    @Value("${tenemsa.seed.exit:true}") private boolean exitAfter;
    @Value("${tenemsa.seed.allow-production:false}") private boolean allowProduction;
    @Value("${tenemsa.seed.random:42}") private long randomSeed;

    private String passwordHash;
    private final LocalDate today = LocalDate.now();
    private final LocalDateTime now = LocalDateTime.now();

    public DemoDataSeeder(JdbcTemplate jdbc, PlatformTransactionManager txm, Environment env, ApplicationContext ctx) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txm);
        this.env = env;
        this.ctx = ctx;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String p : env.getActiveProfiles()) {
            if (!allowProduction && (p.equalsIgnoreCase("prod") || p.equalsIgnoreCase("production"))) {
                throw new IllegalStateException("Refusing to seed demo data while the '" + p + "' profile is active. "
                        + "Pass --tenemsa.seed.allow-production=true only if you really mean it.");
            }
        }
        long started = System.currentTimeMillis();
        if (reset) removeDemoData();
        if (companies > 0) {
            passwordHash = new BCryptPasswordEncoder().encode(PASSWORD);
            int created = 0;
            for (int c = 1; c <= companies; c++) {
                String slug = slugFor(c);
                Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM companies WHERE slug = ?", Integer.class, slug);
                if (exists != null && exists > 0) { log.info("Seed: {} already exists, skipping (use reset=true to rebuild)", slug); continue; }
                final int idx = c;
                tx.executeWithoutResult(s -> seedCompany(idx));
                created++;
            }
            log.info("Seed: created {} demo companies in {} s. Password for every demo account: {}", created,
                    (System.currentTimeMillis() - started) / 1000, PASSWORD);
            log.info("Seed: log in as owner@{}.test / {} (see docs/seed-data.md for all accounts)", slugFor(1), PASSWORD);
        }
        if (exitAfter) System.exit(SpringApplication.exit(ctx));
    }

    static String slugFor(int c) { return SLUG_PREFIX + String.format("%03d", c); }

    // ---------------------------------------------------------------- reset

    private void removeDemoData() {
        List<Long> ids = jdbc.queryForList("SELECT id FROM companies WHERE slug LIKE ?", Long.class, SLUG_PREFIX + "%");
        for (Long id : ids) tx.executeWithoutResult(s -> wipe(id));
        log.info("Seed: removed {} demo companies", ids.size());
    }

    private void wipe(Long c) {
        String in = "(SELECT id FROM users WHERE company_id = ?)";
        jdbc.update("DELETE FROM ai_conversation_messages WHERE conversation_id IN (SELECT id FROM ai_conversations WHERE company_id = ?)", c);
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id IN " + in, c);
        jdbc.update("DELETE FROM verification_tokens WHERE user_id IN " + in, c);
        jdbc.update("DELETE FROM team_members WHERE team_id IN (SELECT id FROM teams WHERE company_id = ?)", c);
        jdbc.update("DELETE FROM document_files WHERE document_id IN (SELECT id FROM documents WHERE company_id = ?)", c);
        jdbc.update("DELETE FROM chat_attachment_files WHERE attachment_id IN (SELECT id FROM chat_attachments WHERE company_id = ?)", c);
        for (String t : new String[]{"ai_conversations", "chat_attachments", "chat_messages", "documents", "kb_chunks", "kb_documents",
                "meeting_points", "meeting_action_items", "meeting_summaries", "ai_request_logs", "issue_comments", "issues", "sprints",
                "projects", "notification_preferences", "notifications", "invitations", "audit_logs", "attendance", "leaves", "teams",
                "profile_images"}) {
            jdbc.update("DELETE FROM " + t + " WHERE company_id = ?", c);
        }
        jdbc.update("DELETE FROM users WHERE company_id = ?", c);
        jdbc.update("DELETE FROM companies WHERE id = ?", c);
    }

    // ---------------------------------------------------------------- one company

    /** Per-company working data the later steps need. */
    private static final class Person {
        long id; int emp; String name; String email; String role; String dept; boolean frozen;
        boolean admin() { return role.equals("OWNER") || role.equals("ADMIN"); }
        boolean manager() { return role.equals("MANAGER"); }
    }

    private void seedCompany(int c) {
        Random r = new Random(randomSeed * 1000 + c);
        String slug = slugFor(c);
        double[] size = {0.5, 1, 1, 1.5, 3};              // a mix of small and big companies
        int n = Math.max(6, (int) Math.round(employees * size[(c - 1) % size.length]));
        String name = COMPANY_WORDS[(c - 1) % COMPANY_WORDS.length] + " " + COMPANY_KINDS[(c - 1) / COMPANY_WORDS.length % COMPANY_KINDS.length]
                + (c > COMPANY_WORDS.length * COMPANY_KINDS.length ? " " + c : "");
        LocalDateTime created = now.minusDays(500 + r.nextInt(300));
        boolean aiOn = c % 7 != 0;                          // every 7th company has the AI switch off
        jdbc.update("INSERT INTO companies (name, slug, created_at, ai_enabled) VALUES (?,?,?,?)", name, slug, created, aiOn);
        long cid = jdbc.queryForObject("SELECT id FROM companies WHERE slug = ?", Long.class, slug);

        List<Person> people = seedUsers(r, cid, slug, n, created);
        seedSessions(r, people);
        seedTeams(r, cid, people);
        seedAttendance(r, cid, people);
        seedLeaves(r, cid, people);
        List<long[]> projects = seedProjects(r, cid, people);
        seedIssues(r, cid, people, projects);
        seedMeetings(r, cid, people, projects);
        seedAi(r, cid, people, aiOn);
        seedKnowledge(r, cid, people);
        seedDocuments(r, cid, people);
        seedChat(r, cid, people);
        seedInvitations(r, cid, people);
        seedAudit(r, cid, people);
        log.info("Seed: {} ({}) -> {} people", slug, name, n);
    }

    private List<Person> seedUsers(Random r, long cid, String slug, int n, LocalDateTime companyCreated) {
        int admins = Math.max(1, n / 60) + 1;               // owner counts separately
        int hr = Math.max(1, n / 25);
        int managers = Math.max(2, n / 8);
        int interns = n * 8 / 100;
        List<Person> people = new ArrayList<>();
        List<Object[]> rows = new ArrayList<>();
        boolean haveEmployee = false, haveIntern = false, haveManager = false, haveHr = false, haveAdmin = false;
        for (int i = 1; i <= n; i++) {
            Person p = new Person();
            p.emp = i;
            if (i == 1) p.role = "OWNER";
            else if (i <= 1 + admins) p.role = "ADMIN";
            else if (i <= 1 + admins + hr) p.role = "HR";
            else if (i <= 1 + admins + hr + managers) p.role = "MANAGER";
            else if (i > n - interns) p.role = "INTERN";
            else p.role = "EMPLOYEE";
            p.dept = switch (p.role) {
                case "OWNER", "ADMIN" -> "Management";
                case "HR" -> "Human Resources";
                default -> DEPARTMENTS[r.nextInt(DEPARTMENTS.length)];
            };
            String first = FIRST[r.nextInt(FIRST.length)], last = LAST[r.nextInt(LAST.length)];
            p.name = first + " " + last;
            // One fixed, easy-to-remember login per role; everyone else gets name.empId.
            String local = null;
            switch (p.role) {
                case "OWNER" -> local = "owner";
                case "ADMIN" -> { if (!haveAdmin) { local = "admin"; haveAdmin = true; } }
                case "HR" -> { if (!haveHr) { local = "hr"; haveHr = true; } }
                case "MANAGER" -> { if (!haveManager) { local = "manager"; haveManager = true; } }
                case "INTERN" -> { if (!haveIntern) { local = "intern"; haveIntern = true; } }
                default -> { if (!haveEmployee) { local = "employee"; haveEmployee = true; } }
            }
            if (local == null) local = first.toLowerCase() + "." + last.toLowerCase() + i;
            p.email = local + "@" + slug + ".test";
            p.frozen = !p.admin() && r.nextInt(100) < 2;
            LocalDateTime joined = companyCreated.plusDays(i == 1 ? 0 : r.nextInt(400));
            if (joined.isAfter(now)) joined = now.minusDays(5);
            double salary = switch (p.role) {
                case "OWNER" -> 250000; case "ADMIN" -> 150000; case "HR" -> 90000; case "MANAGER" -> 120000;
                case "INTERN" -> 20000; default -> 50000 + r.nextInt(50000);
            };
            rows.add(new Object[]{cid, p.emp, p.name, p.email, passwordHash, "LOCAL", true, p.role, p.dept,
                    salary, p.frozen, i == 1, joined, joined});
            people.add(p);
        }
        batch("INSERT INTO users (company_id, emp_id, name, email, password, auth_provider, email_verified, role, department, "
                + "salary, frozen, primary_owner, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)", rows);
        for (Person p : people) {
            p.id = jdbc.queryForObject("SELECT id FROM users WHERE company_id = ? AND emp_id = ?", Long.class, cid, p.emp);
        }
        return people;
    }

    private void seedSessions(Random r, List<Person> people) {
        List<Object[]> rows = new ArrayList<>();
        for (Person p : people) {
            if (!p.admin() && !p.role.equals("HR")) continue;
            int k = 1 + r.nextInt(3);
            for (int i = 0; i < k; i++) {
                rows.add(new Object[]{p.id, sha("session-" + p.id + "-" + i + "-" + r.nextLong()), AGENTS[i % AGENTS.length],
                        now.plusDays(10 + r.nextInt(20)), false, now.minusDays(r.nextInt(10))});
            }
        }
        batch("INSERT INTO refresh_tokens (user_id, token_hash, device_label, expires_at, revoked, created_at) VALUES (?,?,?,?,?,?)", rows);
    }

    private void seedTeams(Random r, long cid, List<Person> people) {
        List<Person> managers = people.stream().filter(Person::manager).toList();
        String[] st = {"ACTIVE", "ACTIVE", "ACTIVE", "ON_HOLD", "COMPLETED", "CANCELLED"};
        int teamCount = Math.min(managers.size(), 3 + people.size() / 12);
        List<Object[]> teams = new ArrayList<>();
        for (int t = 0; t < teamCount; t++) {
            teams.add(new Object[]{cid, DEPARTMENTS[t % DEPARTMENTS.length] + " team " + (t / DEPARTMENTS.length + 1),
                    "Delivery team for " + PROJECT_NAMES[t % PROJECT_NAMES.length], PROJECT_NAMES[t % PROJECT_NAMES.length],
                    today.minusDays(100 + r.nextInt(200)), today.plusDays(r.nextInt(180) - 30), st[r.nextInt(st.length)], managers.get(t).emp});
        }
        batch("INSERT INTO teams (company_id, name, description, project_name, created_date, deadline, status, team_lead_id) VALUES (?,?,?,?,?,?,?,?)", teams);
        List<Long> ids = jdbc.queryForList("SELECT id FROM teams WHERE company_id = ? ORDER BY id", Long.class, cid);
        List<Object[]> members = new ArrayList<>();
        List<Person> staff = people.stream().filter(p -> !p.admin()).toList();
        for (int t = 0; t < ids.size(); t++) {
            members.add(new Object[]{ids.get(t), managers.get(t).emp});
            int size = 3 + r.nextInt(8);
            for (int k = 0; k < size; k++) {
                Person m = staff.get(r.nextInt(staff.size()));
                if (m.emp != managers.get(t).emp) members.add(new Object[]{ids.get(t), m.emp});
            }
        }
        batch("INSERT INTO team_members (team_id, emp_id) VALUES (?,?)", members);
    }

    private void seedAttendance(Random r, long cid, List<Person> people) {
        List<Object[]> rows = new ArrayList<>();
        for (Person p : people) {
            for (int d = attendanceDays; d >= 0; d--) {
                LocalDate day = today.minusDays(d);
                if (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) continue;
                if (d == 0 && r.nextInt(100) < 25) continue;       // some people have not checked in yet today
                int roll = r.nextInt(100);
                String status; LocalTime in = null, out = null; Double hours = null; String note = null;
                if (roll < 76) { status = "PRESENT"; in = LocalTime.of(8, 30 + r.nextInt(30), 0); out = LocalTime.of(17, 0).plusMinutes(30 + r.nextInt(60)); }
                else if (roll < 85) { status = "LATE"; in = LocalTime.of(10, r.nextInt(50), 0); out = LocalTime.of(18, r.nextInt(50), 0); note = "Traffic"; }
                else if (roll < 89) { status = "HALF_DAY"; in = LocalTime.of(9, r.nextInt(30), 0); out = LocalTime.of(13, r.nextInt(30), 0); }
                else if (roll < 95) { status = "ON_LEAVE"; }
                else { status = "ABSENT"; }
                if (in != null && out != null) {
                    double h = (out.toSecondOfDay() - in.toSecondOfDay()) / 3600.0;
                    hours = Math.round(h * 100) / 100.0;
                    if (d == 0) { out = null; hours = null; }          // still working today
                }
                rows.add(new Object[]{cid, p.emp, day, in, out, status, hours, note, "SEED"});
            }
        }
        batch("INSERT INTO attendance (company_id, emp_id, date, check_in, check_out, status, work_hours, notes, marked_by) VALUES (?,?,?,?,?,?,?,?,?)", rows);
    }

    private void seedLeaves(Random r, long cid, List<Person> people) {
        List<Person> deciders = people.stream().filter(p -> p.role.equals("HR") || p.manager() || p.admin()).toList();
        String[] reasons = {"Family function", "Medical appointment", "Vacation", "Personal work", "Festival", "Travel", "Not feeling well", null};
        List<Object[]> rows = new ArrayList<>();
        List<Object[]> notes = new ArrayList<>();
        for (Person p : people) {
            int k = r.nextInt(5);
            for (int i = 0; i < k; i++) {
                int offset = r.nextInt(120) - 90;                      // from 90 days ago to 30 days ahead
                LocalDate start = today.plusDays(offset);
                if (start.getDayOfWeek() == DayOfWeek.SATURDAY) start = start.plusDays(2);
                if (start.getDayOfWeek() == DayOfWeek.SUNDAY) start = start.plusDays(1);
                LocalDate end = start.plusDays(r.nextInt(4));
                String status; Integer by = null; LocalDateTime at = null; String note = null;
                if (offset > -3 && r.nextInt(100) < 70) status = "PENDING";
                else if (r.nextInt(100) < 80) status = "APPROVED"; else status = "REJECTED";
                if (!status.equals("PENDING")) {
                    Person d = deciders.get(r.nextInt(deciders.size()));
                    by = d.emp; at = start.minusDays(1 + r.nextInt(5)).atTime(10, 0);
                    if (at.isAfter(now)) at = now.minusHours(2);
                    note = status.equals("APPROVED") ? "Approved" : "Team is short-staffed that week";
                }
                rows.add(new Object[]{cid, p.emp, start, end, reasons[r.nextInt(reasons.length)], status, by, at, note});
                if (status.equals("PENDING")) {
                    notes.add(new Object[]{cid, p.name + " applied for leave (" + start + " to " + end + ")", r.nextInt(100) < 40, now.minusHours(r.nextInt(72)),
                            deciders.get(r.nextInt(deciders.size())).emp, "LEAVE_REQUESTED"});
                } else {
                    notes.add(new Object[]{cid, "Your leave from " + start + " was " + status.toLowerCase(), r.nextInt(100) < 70,
                            at, p.emp, "LEAVE_DECIDED"});
                }
            }
        }
        batch("INSERT INTO leaves (company_id, emp_id, start_date, end_date, reason, status, decided_by_emp_id, decided_at, decision_note) VALUES (?,?,?,?,?,?,?,?,?)", rows);
        // extra, non-leave notifications so the bell has variety
        for (Person p : people) {
            if (r.nextInt(100) < 60) notes.add(new Object[]{cid, "Welcome to the workspace, " + p.name.split(" ")[0] + "!", true, now.minusDays(30 + r.nextInt(100)), p.emp, "WELCOME"});
            if (r.nextInt(100) < 40) notes.add(new Object[]{cid, "New issue assigned to you", r.nextInt(100) < 50, now.minusHours(r.nextInt(200)), p.emp, "ISSUE_ASSIGNED"});
        }
        batch("INSERT INTO notifications (company_id, message, is_read, created_at, recipient_emp_id, type) VALUES (?,?,?,?,?,?)", notes);
        List<Object[]> prefs = new ArrayList<>();
        for (Person p : people) if (r.nextInt(100) < 12) prefs.add(new Object[]{cid, p.emp, false});
        batch("INSERT INTO notification_preferences (company_id, emp_id, email_enabled) VALUES (?,?,?)", prefs);
    }

    /** @return one long[]{projectId, leadEmp, issueCount-to-be} per project */
    private List<long[]> seedProjects(Random r, long cid, List<Person> people) {
        List<Person> leads = people.stream().filter(p -> p.manager() || p.role.equals("ADMIN")).toList();
        int count = Math.min(PROJECT_NAMES.length, 3 + people.size() / 12);
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            LocalDateTime t = now.minusDays(30 + r.nextInt(300));
            rows.add(new Object[]{cid, PROJECT_KEYS[i], PROJECT_NAMES[i], "Work on " + PROJECT_NAMES[i].toLowerCase() + " for the company.",
                    i == count - 1 && count > 4 ? "ARCHIVED" : "ACTIVE", leads.get(r.nextInt(leads.size())).emp, 1, t, t});
        }
        batch("INSERT INTO projects (company_id, project_key, name, description, status, lead_emp_id, next_issue_number, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?)", rows);
        List<long[]> out = new ArrayList<>();
        jdbc.query("SELECT id, lead_emp_id FROM projects WHERE company_id = ? ORDER BY id", rs -> {
            out.add(new long[]{rs.getLong(1), rs.getLong(2)});
        }, cid);
        return out;
    }

    private void seedIssues(Random r, long cid, List<Person> people, List<long[]> projects) {
        List<Person> active = people.stream().filter(p -> !p.frozen).toList();
        String[] types = {"TASK", "TASK", "TASK", "BUG", "BUG", "STORY"};
        String[] prios = {"LOW", "MEDIUM", "MEDIUM", "HIGH", "URGENT"};
        int[] points = {1, 2, 3, 5, 8, 13};
        for (long[] pr : projects) {
            long pid = pr[0];
            // sprints: 1-3 finished, one running, one planned
            int done = 1 + r.nextInt(3);
            List<Object[]> sp = new ArrayList<>();
            for (int s = 1; s <= done + 2; s++) {
                boolean finished = s <= done, running = s == done + 1;
                LocalDate start = today.minusDays((done + 1 - s) * 14L + 7), end = start.plusDays(13);
                String status = finished ? "COMPLETED" : running ? "ACTIVE" : "PLANNED";
                sp.add(new Object[]{cid, pid, "Sprint " + s, finished ? "Finish milestone " + s : "Keep the board moving", status,
                        status.equals("PLANNED") ? null : start, status.equals("PLANNED") ? null : end,
                        status.equals("PLANNED") ? null : start.atTime(9, 0), finished ? end.atTime(17, 0) : null,
                        finished ? 5 + r.nextInt(10) : null, finished ? r.nextInt(5) : null, now.minusDays(60), now.minusDays(1)});
            }
            batch("INSERT INTO sprints (company_id, project_id, name, goal, status, start_date, end_date, started_at, completed_at, "
                    + "completed_count, carried_over_count, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)", sp);
            List<long[]> sprints = new ArrayList<>();
            jdbc.query("SELECT id FROM sprints WHERE project_id = ? ORDER BY id", rs -> { sprints.add(new long[]{rs.getLong(1)}); }, pid);

            int total = 20 + r.nextInt(40);
            List<Object[]> rows = new ArrayList<>();
            for (int i = 1; i <= total; i++) {
                int bucket = r.nextInt(sprints.size() + 1);             // last bucket = backlog
                Long sprintId = bucket < sprints.size() ? sprints.get(bucket)[0] : null;
                boolean inFinished = bucket < done;
                String status = inFinished ? (r.nextInt(100) < 85 ? "DONE" : "TODO")
                        : new String[]{"TODO", "TODO", "IN_PROGRESS", "IN_REVIEW", "DONE"}[r.nextInt(5)];
                Person assignee = r.nextInt(100) < 85 ? active.get(r.nextInt(active.size())) : null;
                Person reporter = active.get(r.nextInt(active.size()));
                LocalDate due = r.nextInt(100) < 60 ? today.plusDays(r.nextInt(40) - 12) : null;   // some overdue on purpose
                String l1 = LABELS[r.nextInt(LABELS.length)], l2 = LABELS[r.nextInt(LABELS.length)];
                String labels = r.nextInt(100) < 50 ? (r.nextBoolean() && !l1.equals(l2) ? l1 + "," + l2 : l1) : null;   // never the same label twice
                LocalDateTime created = now.minusDays(r.nextInt(120));
                rows.add(new Object[]{cid, pid, i, ISSUE_TITLES[r.nextInt(ISSUE_TITLES.length)], "Details for issue " + i + ".",
                        types[r.nextInt(types.length)], status, prios[r.nextInt(prios.length)],
                        assignee == null ? null : assignee.emp, reporter.emp, due, created, created, sprintId,
                        r.nextInt(100) < 75 ? points[r.nextInt(points.length)] : null, labels});
            }
            batch("INSERT INTO issues (company_id, project_id, issue_number, title, description, type, status, priority, assignee_emp_id, "
                    + "reporter_emp_id, due_date, created_at, updated_at, sprint_id, story_points, labels) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", rows);
            jdbc.update("UPDATE projects SET next_issue_number = ? WHERE id = ?", total + 1, pid);

            List<Object[]> comments = new ArrayList<>();
            jdbc.query("SELECT id FROM issues WHERE project_id = ?", rs -> {
                long iid = rs.getLong(1);
                int k = r.nextInt(100) < 45 ? 1 + r.nextInt(3) : 0;
                for (int j = 0; j < k; j++) {
                    Person a = active.get(r.nextInt(active.size()));
                    comments.add(new Object[]{cid, iid, a.emp, a.name, COMMENTS[r.nextInt(COMMENTS.length)], now.minusDays(r.nextInt(60))});
                }
            }, pid);
            batch("INSERT INTO issue_comments (company_id, issue_id, author_emp_id, author_name, body, created_at) VALUES (?,?,?,?,?,?)", comments);
        }
    }

    private void seedMeetings(Random r, long cid, List<Person> people, List<long[]> projects) {
        int count = Math.max(4, people.size() / 5);
        List<Person> creators = people.stream().filter(p -> !p.role.equals("INTERN") && !p.frozen).toList();
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            boolean approved = r.nextInt(100) < 55;
            Person by = creators.get(r.nextInt(creators.size()));
            LocalDate date = today.minusDays(r.nextInt(90));
            LocalDateTime t = date.atTime(15, 0).plusDays(1);
            if (t.isAfter(now)) t = now.minusHours(1);
            rows.add(new Object[]{cid, by.emp, MEETINGS[r.nextInt(MEETINGS.length)] + " #" + (i + 1), date, "notes-" + i + ".txt",
                    "Raw notes: we discussed scope, owners and deadlines. Several follow-ups were agreed.",
                    "The team reviewed progress, agreed priorities and assigned follow-ups.", approved ? "APPROVED" : "DRAFT",
                    r.nextInt(100) < 60 ? projects.get(r.nextInt(projects.size()))[0] : null, "demo-model", "meeting-summary-v2",
                    approved ? by.emp : null, approved ? t : null, t, t});
        }
        batch("INSERT INTO meeting_summaries (company_id, created_by_emp_id, title, meeting_date, source_ref, source_text, summary, status, "
                + "project_id, model, prompt_version, approved_by_emp_id, approved_at, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", rows);
        List<Long> ids = jdbc.queryForList("SELECT id FROM meeting_summaries WHERE company_id = ? ORDER BY id", Long.class, cid);
        List<Object[]> points = new ArrayList<>(), items = new ArrayList<>();
        String[] prios = {"LOW", "MEDIUM", "HIGH", "URGENT"};
        for (Long mid : ids) {
            int d = 1 + r.nextInt(3), q = r.nextInt(3), a = 1 + r.nextInt(4), order = 0;
            for (int i = 0; i < d; i++) points.add(new Object[]{cid, mid, "DECISION", "Decision " + (i + 1) + ": ship the smaller scope first.", order++});
            for (int i = 0; i < q; i++) points.add(new Object[]{cid, mid, "OPEN_QUESTION", "Who owns the follow-up for item " + (i + 1) + "?", order++});
            for (int i = 0; i < a; i++) {
                Person o = creators.get(r.nextInt(creators.size()));
                items.add(new Object[]{cid, mid, "Action " + (i + 1) + ": prepare the draft", "Prepare and share before the next meeting.",
                        o.name, o.emp, today.plusDays(r.nextInt(30) - 5), prios[r.nextInt(prios.length)], i});
            }
        }
        batch("INSERT INTO meeting_points (company_id, meeting_id, type, content, sort_order) VALUES (?,?,?,?,?)", points);
        batch("INSERT INTO meeting_action_items (company_id, meeting_id, title, description, owner_name, owner_emp_id, due_date, priority, sort_order) VALUES (?,?,?,?,?,?,?,?,?)", items);
    }

    private void seedAi(Random r, long cid, List<Person> people, boolean aiOn) {
        List<Object[]> logs = new ArrayList<>();
        String[] status = {"SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "FAILED", "INVALID_OUTPUT", "TIMEOUT", "RATE_LIMITED"};
        int count = aiOn ? people.size() * 6 : people.size() / 2;
        for (int i = 0; i < count; i++) {
            Person p = people.get(r.nextInt(people.size()));
            String s = status[r.nextInt(status.length)];
            boolean ok = s.equals("SUCCESS");
            logs.add(new Object[]{cid, p.emp, AI_FEATURES[r.nextInt(AI_FEATURES.length)], "v" + (1 + r.nextInt(2)), "demo", "demo-model", s,
                    ok ? 400L + r.nextInt(4000) : null, ok ? 200 + r.nextInt(2500) : null, ok ? 100 + r.nextInt(1500) : null,
                    ok ? null : s.toLowerCase(), now.minusDays(r.nextInt(45)).minusMinutes(r.nextInt(1440))});
        }
        batch("INSERT INTO ai_request_logs (company_id, emp_id, feature, prompt_version, provider, model, status, latency_ms, input_tokens, "
                + "output_tokens, error_code, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", logs);

        // Saved private chats (Phase 8): about a quarter of the people have one or two.
        List<Object[]> convs = new ArrayList<>();
        for (Person p : people) {
            if (r.nextInt(100) >= 25) continue;
            int k = 1 + r.nextInt(2);
            for (int i = 0; i < k; i++) {
                LocalDateTime t = now.minusDays(r.nextInt(30));
                String q = HR_QUESTIONS[r.nextInt(HR_QUESTIONS.length)];
                convs.add(new Object[]{cid, p.id, r.nextBoolean() ? "HR_ASSISTANT" : "KB_ASK", q.length() > 100 ? q.substring(0, 100) : q, t, t});
            }
        }
        batch("INSERT INTO ai_conversations (company_id, user_id, kind, title, created_at, updated_at) VALUES (?,?,?,?,?,?)", convs);
        List<Object[]> msgs = new ArrayList<>();
        jdbc.query("SELECT id, title, updated_at FROM ai_conversations WHERE company_id = ? ORDER BY id", rs -> {
            long id = rs.getLong(1);
            LocalDateTime t = rs.getTimestamp(3).toLocalDateTime();
            msgs.add(new Object[]{id, "USER", rs.getString(2), null, t.minusMinutes(1)});
            msgs.add(new Object[]{id, "ASSISTANT", "This is a seeded demo answer. Ask a live question to see a real response.", null, t});
        }, cid);
        batch("INSERT INTO ai_conversation_messages (conversation_id, role, content, metadata, created_at) VALUES (?,?,?,?,?)", msgs);
    }

    private void seedKnowledge(Random r, long cid, List<Person> people) {
        int count = 4 + r.nextInt(5);
        List<Object[]> docs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String status = i == count - 1 ? "FAILED" : i == count - 2 && r.nextBoolean() ? "PROCESSING" : "READY";
            LocalDateTime t = now.minusDays(r.nextInt(120));
            int chunks = status.equals("READY") ? 3 + r.nextInt(8) : 0;
            docs.add(new Object[]{cid, DOC_TITLES[i % DOC_TITLES.length], DOC_TITLES[i % DOC_TITLES.length].toLowerCase().replace(' ', '-') + ".pdf",
                    i % 4 == 3 ? "MANAGEMENT" : "ALL_STAFF", status, status.equals("FAILED") ? "No readable text found in the file." : null,
                    sha("kb-" + cid + "-" + i), chunks * 800, chunks, null, 1, t, t});
        }
        batch("INSERT INTO kb_documents (company_id, title, file_name, audience, status, error_message, content_hash, char_count, chunk_count, "
                + "embedding_model, uploaded_by_emp_id, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)", docs);
        List<Object[]> chunks = new ArrayList<>();
        jdbc.query("SELECT id, title, chunk_count FROM kb_documents WHERE company_id = ? ORDER BY id", rs -> {
            for (int i = 0; i < rs.getInt(3); i++) {
                chunks.add(new Object[]{cid, rs.getLong(1), i, rs.getString(2) + " - section " + (i + 1) + ". Employees should follow the guidance in this section."});
            }
        }, cid);
        batch("INSERT INTO kb_chunks (company_id, document_id, chunk_index, content) VALUES (?,?,?,?)", chunks);
    }

    private void seedDocuments(Random r, long cid, List<Person> people) {
        String[] cats = {"POLICY", "HANDBOOK", "CONTRACT", "FORM", "PERSONAL", "OTHER"};
        List<Object[]> rows = new ArrayList<>();
        int count = 6 + r.nextInt(10);
        for (int i = 0; i < count; i++) {
            String cat = cats[r.nextInt(cats.length)];
            boolean personal = cat.equals("PERSONAL") || cat.equals("CONTRACT");
            Person owner = personal ? people.get(r.nextInt(people.size())) : null;
            String vis = personal ? "PRIVATE" : r.nextInt(100) < 30 ? "MANAGEMENT" : "ALL_STAFF";
            String title = personal ? (cat.equals("CONTRACT") ? "Employment contract - " : "Payslip - ") + owner.name : DOC_TITLES[i % DOC_TITLES.length] + " v" + (1 + r.nextInt(3));
            byte[] body = ("Seeded demo document: " + title + "\n").getBytes(StandardCharsets.UTF_8);
            rows.add(new Object[]{cid, title, cat, vis, owner == null ? null : owner.emp, title.toLowerCase().replaceAll("[^a-z0-9]+", "-") + ".txt",
                    "text/plain", (long) body.length, 1, now.minusDays(r.nextInt(200))});
        }
        batch("INSERT INTO documents (company_id, title, category, visibility, owner_emp_id, file_name, content_type, size_bytes, uploaded_by_emp_id, created_at) VALUES (?,?,?,?,?,?,?,?,?,?)", rows);
        List<Object[]> files = new ArrayList<>();
        jdbc.query("SELECT id, title FROM documents WHERE company_id = ? ORDER BY id", rs -> {
            files.add(new Object[]{rs.getLong(1), ("Seeded demo document: " + rs.getString(2) + "\n").getBytes(StandardCharsets.UTF_8)});
        }, cid);
        batch("INSERT INTO document_files (document_id, content) VALUES (?,?)", files);
    }

    private void seedChat(Random r, long cid, List<Person> people) {
        // Chat routes by tag: ADMIN-tagged people (owner/admin) use users.id, everyone else uses emp_id.
        List<Object[]> rows = new ArrayList<>();
        int conversations = Math.max(8, people.size() / 2);
        for (int c = 0; c < conversations; c++) {
            Person a = people.get(r.nextInt(people.size())), b = people.get(r.nextInt(people.size()));
            if (a == b) continue;
            LocalDateTime t = now.minusDays(r.nextInt(14)).minusMinutes(r.nextInt(600));
            int lines = 3 + r.nextInt(8);
            for (int i = 0; i < lines; i++) {
                Person from = i % 2 == 0 ? a : b, to = i % 2 == 0 ? b : a;
                t = t.plusMinutes(1 + r.nextInt(30));
                if (t.isAfter(now)) t = now.minusMinutes(1);
                rows.add(new Object[]{cid, chatId(from), tag(from), chatId(to), tag(to), CHAT_LINES[r.nextInt(CHAT_LINES.length)], t,
                        i < lines - 2 || r.nextBoolean() ? "READ" : "SENT", r.nextInt(100) < 5 ? "👍" : null, r.nextInt(100) < 4});
            }
        }
        batch("INSERT INTO chat_messages (company_id, sender_id, sender_role, receiver_id, receiver_role, message, timestamp, status, reaction, starred) VALUES (?,?,?,?,?,?,?,?,?,?)", rows);
    }

    private static String tag(Person p) { return p.admin() ? "ADMIN" : "USER"; }
    private static int chatId(Person p) { return p.admin() ? (int) p.id : p.emp; }

    private void seedInvitations(Random r, long cid, List<Person> people) {
        String[] roles = {"EMPLOYEE", "EMPLOYEE", "MANAGER", "HR", "INTERN"};
        List<Object[]> rows = new ArrayList<>();
        int count = 3 + r.nextInt(6);
        for (int i = 0; i < count; i++) {
            int kind = r.nextInt(4);   // 0 pending, 1 accepted, 2 revoked, 3 expired (pending but past its date)
            LocalDateTime t = now.minusDays(r.nextInt(20));
            String first = FIRST[r.nextInt(FIRST.length)], last = LAST[r.nextInt(LAST.length)];
            rows.add(new Object[]{cid, "invite." + first.toLowerCase() + i + "@example.test", first + " " + last, roles[r.nextInt(roles.length)],
                    DEPARTMENTS[r.nextInt(DEPARTMENTS.length)], sha("invite-" + cid + "-" + i), kind == 1 ? "ACCEPTED" : kind == 2 ? "REVOKED" : "PENDING",
                    people.get(0).id, kind == 3 ? t.minusDays(1) : t.plusDays(7), kind == 1 ? t.plusHours(5) : null, t});
        }
        batch("INSERT INTO invitations (company_id, email, name, role, department, token_hash, status, invited_by_id, expires_at, accepted_at, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)", rows);
    }

    private void seedAudit(Random r, long cid, List<Person> people) {
        String[] actions = {"LOGIN_SUCCESS", "LOGIN_SUCCESS", "LOGIN_SUCCESS", "LOGIN_FAILED", "LEAVE_APPROVED", "LEAVE_REJECTED", "USER_CREATED",
                "USER_UPDATED", "PROJECT_CREATED", "SPRINT_STARTED", "SPRINT_COMPLETED", "KB_DOCUMENT_ADDED", "DOCUMENT_UPLOADED", "INVITE_SENT",
                "PASSWORD_CHANGED", "AI_MEETING_SUMMARY_GENERATED", "MEETING_SUMMARY_APPROVED"};
        List<Person> actors = people.stream().filter(p -> p.admin() || p.role.equals("HR") || p.manager()).toList();
        List<Object[]> rows = new ArrayList<>();
        int count = people.size() * 6;
        for (int i = 0; i < count; i++) {
            Person p = r.nextInt(100) < 50 ? people.get(r.nextInt(people.size())) : actors.get(r.nextInt(actors.size()));
            String a = actions[r.nextInt(actions.length)];
            rows.add(new Object[]{cid, p.id, p.name, a, a.startsWith("LOGIN") ? "User" : a.split("_")[0].charAt(0) + a.split("_")[0].substring(1).toLowerCase(),
                    String.valueOf(1 + r.nextInt(50)), a.replace('_', ' ').toLowerCase() + " (seed data)", "10.0." + r.nextInt(255) + "." + (1 + r.nextInt(254)),
                    now.minusDays(r.nextInt(90)).minusMinutes(r.nextInt(1440))});
        }
        batch("INSERT INTO audit_logs (company_id, actor_id, actor_name, action, entity_type, entity_id, details, ip_address, created_at) VALUES (?,?,?,?,?,?,?,?,?)", rows);
    }

    // ---------------------------------------------------------------- helpers

    private void batch(String sql, List<Object[]> rows) {
        for (int i = 0; i < rows.size(); i += CHUNK) {
            jdbc.batchUpdate(sql, new ArrayList<>(rows.subList(i, Math.min(rows.size(), i + CHUNK))));
        }
    }

    private static String sha(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
