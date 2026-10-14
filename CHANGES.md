# Production Readiness Changes

## Review approach

I reviewed the application as a complete workflow rather than checking individual screens in isolation. I traced leave submission, approval, rejection, cancellation, balance adjustment, employee administration, authentication failures, database migrations, and Docker startup from the HTTP request through the controller, service, repository, and database layers.

I also walked through boundary cases that were not covered by the original happy-path tests, including simultaneous decisions, year boundaries, past dates, overlapping leave, invalid reporting relationships, removal of the final administrator, malformed requests, expired sessions, and production startup without safe configuration.

## Leave transition concurrency

### What I found

Approval, rejection, and cancellation loaded a leave request with a normal `findById` call. Two transactions could therefore read the same request as `PENDING` and both continue. This could cause duplicate approvals, two audit events, or an approval racing with cancellation and leaving a cancelled request with deducted balance.

Balance deduction locked the balance row, but an administrator's allocation adjustment did not. An adjustment could overwrite a concurrent deduction with stale data.

### What I changed and why

I added a pessimistic write lookup for leave requests and routed approval, rejection, and cancellation through it. The leave row is now locked before the status is checked or any balance mutation occurs. Only one transaction can own a transition, and a later transaction receives `LEAVE_ALREADY_DECIDED`.

I made balance deductions and manual adjustments use the same locked balance lookup. I also added optimistic version columns to employees, leave requests, and leave balances as protection against future code accidentally performing unlocked updates.

Decisions lock the leave request before its balance and audit insert. When annual balances do not exist yet, initialization locks the employee row before creating them. Submissions lock the employee before checking overlap, so simultaneous submissions cannot both pass the overlap check.

## Date and leave-policy correctness

### What I found

Balance validation and deduction used `Year.now()`. A request for a different year could be charged to the year in which it happened to be submitted or approved. Business dates also depended on the host machine's timezone.

The backend accepted past leave, pre-employment leave, cross-year ranges, overlapping requests, and arbitrarily large date ranges. Working-day calculation iterated over every date, so an extreme range could occupy an application thread for an unreasonable amount of time.

### What I changed and why

I introduced an injected business `Clock` configured through `BUSINESS_ZONE`. Date-sensitive code now uses that clock instead of the host timezone.

Balance operations require an explicit year derived from the leave request's start date. A 2027 request is therefore checked against and deducted from the 2027 balance even if it is handled at another time.

I added backend validation for:

- Start date after end date.
- Start date before the current business date.
- Start date before the employee's joining date.
- Ranges containing no Monday-to-Friday working day.
- Ranges spanning more than one calendar year.
- Ranges longer than the configured maximum of 366 calendar days.
- Overlap with an existing pending or approved request for the same employee.

I replaced the day-by-day duration loop with a full-week calculation plus at most six remainder checks. This keeps duration calculation bounded even when invalid input contains a very large date range.

For the current scope, a working day means Monday through Friday. Public holidays are not modeled.

## Authorization and hierarchy integrity

### What I found

The application prevented direct reporting cycles but allowed any active employee to be assigned as a manager. It also allowed a manager to be deactivated or demoted while employees still reported to them. The final active administrator could be removed, leaving the organization without an account capable of administering it.

Conflict lookup also substituted the acting administrator as the manager when the request owner had no manager. That returned conflicts for the administrator's team even though no such reporting relationship existed.

### What I changed and why

I added one manager-candidate rule used by employee creation, employee editing, and manager changes. A manager must be active and have either the `MANAGER` or `ADMIN` role.

I block deactivation or demotion to `EMPLOYEE` when the person still has direct reports. Reports must be explicitly reassigned first, which avoids silently orphaning them.

I added a locked active-administrator check before demoting or deactivating an administrator. The operation returns `LAST_ACTIVE_ADMIN_REQUIRED` if it would remove the last active administrator.

For employees without a manager, conflict lookup now returns an empty team result instead of borrowing the acting administrator's reporting hierarchy.

## Validation and data integrity

### What I found

An empty status-change body could silently become `active=false` because the DTO used primitive `boolean`. Rejection comments could be blank. Email and employee ID uniqueness checks ran before normalization, and employee ID uniqueness loaded the entire employee table into memory.

Several important invariants existed only in service code and were not protected by the database.

### What I changed and why

I changed the status field to a validated nullable `Boolean`, so a missing value returns HTTP 400 instead of deactivating an employee.

Rejection now has its own request DTO and requires a nonblank comment of at most 1,000 characters. The service repeats the invariant so internal callers cannot bypass controller validation.

I normalize email and employee ID before checking or persisting them. Targeted repository existence queries replace the full-table employee ID scan.

Flyway migration V4 adds:

- Optimistic version columns.
- A constraint preventing self-management.
- A constraint requiring leave start date to be on or before end date.
- An index on `employees.manager_id`.
- A unique index on `lower(email)` for case-insensitive email uniqueness.

## Failure behavior and observability

### What I found

Domain failures returned human-readable messages without stable machine codes. Malformed JSON, invalid enum values, type mismatches, database conflicts, and stale updates could fall through to a generic response. Spring Security used `sendError`, so authentication failures did not follow the same JSON contract as controller failures.

Unexpected exceptions were hidden from the client, which was correct, but they were also not logged for operators. There was no request identifier connecting a client failure to a backend log entry.

### What I changed and why

I introduced a typed error response containing timestamp, status, stable code, message, field errors, and request ID. Spring Security authentication and authorization failures use the same response structure.

I added explicit handling for malformed requests, validation failures, data-integrity conflicts, optimistic locking failures, and temporary database-lock failures. Clients can now use the stable code instead of parsing message text.

The request-ID filter accepts a safe incoming `X-Request-ID` or generates one, returns it in the response header, and makes it available to error responses and logs. Unexpected failures are logged with the stack trace, request method, path, and request ID without exposing credentials or tokens to the client.

## Frontend behavior

### What I found

The frontend trusted cached user data from `localStorage` when the application started. A stale or expired session could briefly render protected content. The API client cleared storage after a 401 but continued processing the response. Approval and rejection buttons also allowed duplicate submissions.

The employee form displayed active ordinary employees as possible managers even though that conflicts with the organization role model.

### What I changed and why

The authentication provider now starts in a loading state and validates the stored token by requesting `/api/employees/me`. Cached user data is replaced with the server response, and an invalid session is cleared before protected routes render.

The API client throws immediately after handling a 401. It also sends `Content-Type: application/json` only when a request has a body.

Approval and rejection operations track the request currently being changed and disable their buttons while the mutation is in progress. The rejection action remains disabled until a nonblank reason is entered.

Manager selectors now show only active managers and administrators. The backend remains the authoritative enforcement point.

## Security and deployment

### What I found

The application accepted a known JWT signing secret by default, seeded predictable administrator credentials in every profile, automatically baselined unknown nonempty schemas, hardcoded the Compose CORS origin, installed frontend packages without enforcing the lockfile, and ran the backend container as root.

### What I changed and why

The application now requires `JWT_SECRET` to be injected. Docker Compose supplies the local demo value explicitly, but production startup has no built-in fallback.

Demo seeding is restricted to the `demo` Spring profile, which Compose enables explicitly. Starting the application without that profile does not create predictable users.

I disabled automatic Flyway baselining so an unexpected existing schema fails rather than being silently treated as valid. Compose reads allowed CORS origins from configuration, the frontend image uses `npm ci`, and the backend runtime image uses a non-root account.

## Tests added or strengthened

I expanded the backend tests to cover:

- Business-clock handling of past dates.
- Cross-year rejection.
- Overlapping-request rejection.
- Explicit request-year balance deduction.
- Blank rejection reasons.
- Oversized calendar ranges.
- Conflict behavior for an employee without a manager.
- JSON authentication failures with a stable code and request ID.

The current backend suite passes with 55 tests on JDK 21. The frontend TypeScript/Vite production build and ESLint checks pass. The retained Playwright suite passes all 10 browser tests, including the real employee-to-manager approval workflow.

## Verification rerun: October 14, 2026

The previous pasted review ended during final verification. I checked the actual
workspace and reran the checks instead of relying on its reported pass counts.
The original two disposable API harnesses were not present in this checkout;
new retained verification scripts now cover the original defect categories,
PostgreSQL races, outages, deployment checks, and browser behavior.

### Additional defects corrected

- JWT authentication no longer swallows database failures as invalid credentials.
  Both login and JWT-protected requests return correlated `503 DEPENDENCY_UNAVAILABLE`
  during an outage; connection acquisition remains bounded at five seconds.
- The request-size filter reads at most the configured limit plus one byte and
  enforces the 1 MiB limit for both declared and chunked bodies.
- Annual balance creation is serialized on the employee row. Sixteen simultaneous
  first-load requests succeed and leave exactly three annual balances.
- Leave submission locks the employee before the overlap check. Twelve concurrent
  submissions for the same dates produce one creation and eleven conflicts.
- The login limiter now bounds retained addresses to 10,000 and reclaims expired
  windows. Unit tests verify concurrency, success reset, capacity, and expiry.
- Flyway V6 blocks audit `TRUNCATE`, complementing V5 update/delete protection.
- Joining dates outside years 1-9999 and passwords above 72 UTF-8 bytes return
  validation errors before persistence or BCrypt processing.
- Employee edits omit an unchanged password rather than submitting an invalid
  empty password, and employee saves have duplicate-submit protection.
- A shared frontend query hook aborts obsolete requests, hides stale results, and
  derives loading/error states without synchronous updates inside effects.
  Calendar, audit, balances, approvals, employees, and dashboard screens use it.
- Temporary session verification failures preserve the token and show a retry;
  expired sessions still clear storage and redirect to login.
- Audit requests are capped at 100 rows per page.

### Dependency and container remediation

Local Trivy scans found high and critical vulnerabilities in the original Spring
Boot 3.4.1 libraries and Nginx 1.27 container. The backend now uses Spring Boot
4.1.1, its modular web/JPA/security test starters, and Jackson 3. The JWT library
still uses Jackson 2 internally. Jackson BOMs are patched to 3.1.7 and 2.21.7,
and Tomcat to 11.0.27. Nginx is updated to 1.30.5, and both runtime images apply
available Alpine package updates.

The migration follows the official [Spring Boot migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide).
Hibernate's per-query lock-timeout hint was removed because its reset operation
masked PostgreSQL lock failures after the transaction was aborted. The existing
connection-level PostgreSQL timeout now consistently produces the typed retryable
503 response, verified by a held-row-lock test.

### Current results

| Check | Result |
| --- | --- |
| `mvn clean test` | 55 passed; no failures, errors, or skips |
| Retained PostgreSQL/API harness | 40 grouped checks passed |
| Fresh Flyway V1-V6 and Hibernate schema validation | Passed |
| V3/V4/V5-to-V6 upgrades | Passed |
| Non-demo seeding and missing/weak JWT secrets | Passed |
| Backend container user | Non-root |
| Playwright Chromium | 10 passed, including real leave submission/approval/balance workflow |
| `npm ci`, `npm run lint`, `npm run build` | Passed |
| `npm audit --omit=dev` | Zero production dependency vulnerabilities |
| Local Trivy container vulnerability scans | Zero findings in both final images |

The API harness repeats duplicate approval twenty times and approve/reject and
approve/cancel races five times each. It also checks balance overdraw, final-admin
removal races, current-manager ownership, disabled accounts, normalization,
validation, CORS, typed 404/405 responses, request IDs, and audit immutability.
Failure injection confirms the two-second lock timeout and five-second connection
acquisition timeout, with incoming request IDs recorded in outage logs.

The retained scripts and repeat commands are in [scripts/README.md](scripts/README.md).
Tests use the isolated `copartverify` Compose project and its own database volume;
the existing application stack and database are not changed. No commit or push
was performed.

## What I intentionally did not change

I did not treat the following larger changes as if they were solved by this revision:

- PostgreSQL concurrency checks are retained in `scripts/verify-api.mjs`, but are not part of the default Maven lifecycle or a Testcontainers suite.
- Authentication tokens remain in `localStorage`. Moving to HttpOnly secure cookies requires a coordinated authentication and CSRF design change.
- Holidays are not part of the working-day policy.
- The login limiter is process-local. A multi-instance production deployment still needs a shared limiter or ingress-level enforcement.
- The audit triggers reject normal update, delete, and truncate statements, but production should still use a restricted runtime database role that cannot disable the trigger.
- Actuator health/readiness endpoints, pagination for employee/leave directories, automated CI wiring, and production ingress hardening remain follow-up work.
- The application limits both declared and chunked request bodies. Production ingress still needs connection/read timeouts and deployment-specific request controls.
- Duplicate employee administration routes and repository-wide formatting cleanup were left separate to avoid mixing broad structural changes into the correctness fixes.

These items should be treated as explicit follow-up risks rather than assumed to be complete.
