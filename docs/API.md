# API examples

Login:

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"john@example.com","password":"Welcome123!"}'
```

Submit leave with the returned token:

```bash
curl -X POST http://localhost:8080/api/leaves \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer YOUR_TOKEN' \
  -d '{"leaveType":"VACATION","startDate":"2026-11-10","endDate":"2026-11-12","reason":"Family trip"}'
```

Review actions are deliberately separate endpoints to make the permitted state transition explicit. The authenticated user is always derived from the JWT; clients cannot submit an employee or reviewer ID on these operations.

Errors use one JSON shape across controllers and Spring Security:

```json
{
  "timestamp": "2026-10-13T20:00:00Z",
  "status": 409,
  "code": "LEAVE_ALREADY_DECIDED",
  "message": "Only pending leave requests can be changed",
  "fields": {},
  "requestId": "d8d887b7-5bd5-49bb-8b11-a85fb22c9be2"
}
```

Clients should branch on `code`, not the human-readable `message`. The request ID is also returned in `X-Request-ID` for log correlation.

Infrastructure and protocol failures use the same shape. Notable codes include `MALFORMED_REQUEST`, `PAYLOAD_TOO_LARGE`, `LOGIN_RATE_LIMITED`, `RESOURCE_NOT_FOUND`, `METHOD_NOT_ALLOWED`, `TEMPORARILY_UNAVAILABLE` for bounded lock waits, and `DEPENDENCY_UNAVAILABLE` for database connectivity failures. Clients may retry the two 503 responses with backoff; they should not automatically retry validation or authorization failures.
