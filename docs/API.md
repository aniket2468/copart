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
