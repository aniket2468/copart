// Run only against scripts/compose.verify.yml. Tests mutate its disposable database.
import assert from 'node:assert/strict';
import {execFileSync, spawn} from 'node:child_process';
import {request as httpRequest} from 'node:http';
import {fileURLToPath} from 'node:url';

const composeFile = fileURLToPath(new URL('./compose.verify.yml', import.meta.url));
const composeArgs = ['compose', '-p', 'copartverify', '-f', composeFile];
const base = 'http://127.0.0.1:18080';
const docker = (...args) => execFileSync('docker', [...composeArgs, ...args], {encoding: 'utf8'});
const sql = query => docker('exec', '-T', 'db', 'psql', '-U', 'verification', '-d', 'verification', '-v', 'ON_ERROR_STOP=1', '-Atc', query).trim();
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
let passed = 0, failed = 0;
async function check(name, test) {
  try {await test(); passed++; console.log(`PASS ${name}`);}
  catch (error) {failed++; console.error(`FAIL ${name}: ${error.message}`);}
}
async function api(path, {token, method = 'GET', body, raw, headers = {}} = {}) {
  const response = await fetch(base + path, {method, headers: {
    ...(token ? {Authorization: `Bearer ${token}`} : {}),
    ...(body !== undefined || raw !== undefined ? {'Content-Type': 'application/json'} : {}), ...headers},
    body: raw ?? (body === undefined ? undefined : JSON.stringify(body)), signal: AbortSignal.timeout(15000)});
  const text = await response.text();
  let data; try {data = JSON.parse(text);} catch {data = text;}
  return {status: response.status, data, headers: response.headers};
}
function expect(result, status, code) {
  assert.equal(result.status, status, JSON.stringify(result.data));
  if (code) assert.equal(result.data.code, code);
  return result.data;
}
async function login(email) {
  return expect(await api('/api/auth/login', {method: 'POST', body: {email, password: 'Welcome123!'}}), 200);
}
async function ready() {
  for (let i = 0; i < 90; i++) {
    try {if ((await api('/api/auth/login')).status === 405) return;} catch {}
    await sleep(1000);
  }
  throw Error('Verification API did not become ready');
}
await ready();
assert.equal(sql('select current_database()'), 'verification');
const admin = await login('admin@example.com');
const manager = await login('sarah@example.com');
const employee = await login('john@example.com');
const emma = await login('emma@example.com');
const employees = expect(await api('/api/admin/employees', {token: admin.token}), 200);
const john = employees.find(e => e.email === 'john@example.com');
const sarah = employees.find(e => e.email === 'sarah@example.com');
const emmaUser = employees.find(e => e.email === 'emma@example.com');
const employeeBody = {employeeId: `VERIFY-${Date.now()}`, firstName: 'Test', lastName: 'Employee', email: `verify-${Date.now()}@example.com`,
  password: 'Welcome123!', jobTitle: 'Engineer', department: 'Verification', role: 'EMPLOYEE', managerId: sarah.id, dateOfJoining: '2020-01-01'};
const year = Number(new Intl.DateTimeFormat('en', {timeZone: 'America/Indiana/Indianapolis', year: 'numeric'}).format(new Date())) + 1;
let nextDay = new Date(Date.UTC(year, 0, 4));
function weekday() {
  do {nextDay.setUTCDate(nextDay.getUTCDate() + 1);} while ([0, 6].includes(nextDay.getUTCDay()));
  return nextDay.toISOString().slice(0, 10);
}
const leaveBody = (day = weekday(), type = 'OTHER') => ({leaveType: type, startDate: day, endDate: day, reason: 'Verification'});
const create = async (token, body = leaveBody()) => expect(await api('/api/leaves', {token, method: 'POST', body}), 201);
const decide = (id, decision = 'approve', token = manager.token, body = {comment: 'Verification'}) => api(`/api/manager/leave-requests/${id}/${decision}`, {token, method: 'PATCH', body});

await check('Fresh Flyway V1-V6 migration and schema validation', async () => {
  assert.equal(sql('select count(*) from flyway_schema_history where success and version is not null'), '6');
  assert.match(docker('logs', '--no-color', 'backend'), /Started EmployeeManagementApplication/);
});
await check('Backend container runs as non-root', () => assert.notEqual(docker('exec', '-T', 'backend', 'id', '-u').trim(), '0'));
await check('Unauthenticated requests return typed 401', async () => expect(await api('/api/employees/me'), 401, 'AUTHENTICATION_REQUIRED'));
await check('Employee and manager cannot enter admin portal', async () => {
  for (const token of [employee.token, manager.token]) expect(await api('/api/admin/dashboard', {token}), 403, 'ACCESS_DENIED');
});
await check('Invalid JWT remains typed 401', async () => expect(await api('/api/employees/me', {token: 'not-a-jwt'}), 401, 'AUTHENTICATION_REQUIRED'));
await check('Configured CORS preflight succeeds', async () => {
  const r = await api('/api/auth/login', {method: 'OPTIONS', headers: {Origin: 'http://localhost:15173', 'Access-Control-Request-Method': 'POST'}});
  expect(r, 200); assert.equal(r.headers.get('Access-Control-Allow-Origin'), 'http://localhost:15173');
});
await check('Unconfigured CORS origin is rejected', async () => expect(await api('/api/auth/login', {method: 'OPTIONS', headers: {Origin: 'https://evil.example', 'Access-Control-Request-Method': 'POST'}}), 403));
await check('Trailing JSON garbage is rejected', async () => expect(await api('/api/auth/login', {method: 'POST', raw: '{"email":"nobody@example.com","password":"Welcome123!"} garbage'}), 400, 'MALFORMED_REQUEST'));
await check('String and numeric Boolean coercion are rejected', async () => {
  for (const active of ['false', 0, 1]) expect(await api(`/api/admin/employees/${john.id}/status`, {token: admin.token, method: 'PATCH', body: {active}}), 400, 'MALFORMED_REQUEST');
});
await check('Missing active value cannot deactivate employee', async () => expect(await api(`/api/admin/employees/${john.id}/status`, {token: admin.token, method: 'PATCH', body: {}}), 400, 'VALIDATION_FAILED'));
await check('Oversized employee fields fail validation', async () => {
  for (const field of ['employeeId', 'firstName', 'lastName', 'jobTitle', 'department']) {
    expect(await api('/api/admin/employees', {token: admin.token, method: 'POST', body: {...employeeBody, [field]: 'x'.repeat(256)}}), 400, 'VALIDATION_FAILED');
  }
});
await check('Extreme leave and calendar dates fail validation', async () => {
  for (const date of ['+999999999-01-01', '-999999999-01-01']) {
    expect(await api('/api/leaves', {token: employee.token, method: 'POST', body: {...leaveBody(), startDate: date, endDate: date}}), 400, 'DATE_OUT_OF_RANGE');
    expect(await api(`/api/manager/leave-requests/calendar?start=${encodeURIComponent(date)}&end=${encodeURIComponent(date)}`, {token: manager.token}), 400, 'DATE_OUT_OF_RANGE');
  }
});
await check('Extreme joining dates and oversized Unicode passwords fail validation', async () => {
  expect(await api('/api/admin/employees', {token: admin.token, method: 'POST', body: {...employeeBody, dateOfJoining: '-999999999-01-01'}}), 400, 'VALIDATION_FAILED');
  expect(await api('/api/admin/employees', {token: admin.token, method: 'POST', body: {...employeeBody, password: '\u00e9'.repeat(40)}}), 400, 'VALIDATION_FAILED');
  expect(await api('/api/auth/login', {method: 'POST', body: {email: 'john@example.com', password: '\u00e9'.repeat(40)}}), 400, 'VALIDATION_FAILED');
});
await check('Integer maximum leave allocation is rejected', async () => expect(await api(`/api/admin/employees/${john.id}/leave-balances/VACATION`, {token: admin.token, method: 'PATCH', body: {totalAllocated: 2147483647, reason: 'Verification'}}), 400, 'VALIDATION_FAILED'));
await check('Declared body above 1 MiB returns 413', async () => expect(await api('/api/auth/login', {method: 'POST', raw: 'x'.repeat(2 * 1024 * 1024)}), 413, 'PAYLOAD_TOO_LARGE'));
await check('Chunked body above 1 MiB returns 413', () => new Promise((resolve, reject) => {
  const req = httpRequest(base + '/api/auth/login', {method: 'POST', headers: {'Content-Type': 'application/json', 'Transfer-Encoding': 'chunked', 'X-Request-ID': 'verify-chunked'}}, res => {
    let body = ''; res.on('data', chunk => body += chunk); res.on('end', () => {
      try {assert.equal(res.statusCode, 413); assert.equal(JSON.parse(body).code, 'PAYLOAD_TOO_LARGE'); resolve();} catch (e) {reject(e);}
    });
  });
  req.on('error', reject); req.setTimeout(15000, () => req.destroy(Error('Chunked body request timed out')));
  for (let i = 0; i < 128; i++) req.write('x'.repeat(16384)); req.end();
}));
await check('Missing route returns 404 and unsupported method returns 405', async () => {
  expect(await api('/api/missing-route', {token: employee.token}), 404, 'RESOURCE_NOT_FOUND');
  expect(await api('/api/auth/login'), 405, 'METHOD_NOT_ALLOWED');
});
await check('Incoming request ID is returned consistently', async () => {
  const r = await api('/api/missing-route', {token: employee.token, headers: {'X-Request-ID': 'verification-request'}});
  assert.equal(r.headers.get('X-Request-ID'), 'verification-request'); assert.equal(r.data.requestId, 'verification-request');
});
await check('Past, reversed, cross-year and weekend-only leave are rejected', async () => {
  for (const [startDate, endDate, code] of [
    ['2020-01-01', '2020-01-01', 'LEAVE_START_IN_PAST'],
    [`${year}-01-06`, `${year}-01-05`, 'INVALID_DATE_RANGE'],
    [`${year}-12-31`, `${year + 1}-01-01`, 'CROSS_YEAR_LEAVE_NOT_SUPPORTED'],
  ]) expect(await api('/api/leaves', {token: employee.token, method: 'POST', body: {...leaveBody(), startDate, endDate}}), 400, code);
  let saturday = new Date(Date.UTC(year, 5, 1)); while (saturday.getUTCDay() !== 6) saturday.setUTCDate(saturday.getUTCDate() + 1);
  const day = saturday.toISOString().slice(0, 10);
  expect(await api('/api/leaves', {token: employee.token, method: 'POST', body: {...leaveBody(), startDate: day, endDate: day}}), 400, 'NO_WORKING_DAYS');
});
await check('Concurrent overlapping submissions create exactly one request', async () => {
  const body = leaveBody();
  const results = await Promise.all(Array.from({length: 12}, () => api('/api/leaves', {token: employee.token, method: 'POST', body})));
  assert.equal(results.filter(r => r.status === 201).length, 1);
  for (const r of results.filter(r => r.status !== 201)) expect(r, 409, 'OVERLAPPING_LEAVE_REQUEST');
});
await check('First annual balance initialization survives concurrent requests', async () => {
  const created = expect(await api('/api/admin/employees', {token: admin.token, method: 'POST', body: employeeBody}), 201);
  sql(`delete from leave_balances where employee_id=${created.id}`);
  const responses = await Promise.all(Array.from({length: 16}, () => api(`/api/admin/employees/${created.id}/leave-balances`, {token: admin.token})));
  for (const r of responses) {expect(r, 200); assert.equal(r.data.length, 3);}
  assert.equal(sql(`select count(*) from leave_balances where employee_id=${created.id}`), '3');
});
await check('Future-year approval deducts the request year balance', async () => {
  const request = await create(employee.token, leaveBody(undefined, 'VACATION'));
  expect(await decide(request.id), 200);
  assert.equal(sql(`select used from leave_balances where employee_id=${john.id} and balance_year=${year} and leave_type='VACATION'`), '1');
});
await check('Unauthorized employee and self-review are forbidden', async () => {
  const request = await create(employee.token);
  expect(await decide(request.id, 'approve', emma.token), 403);
  const own = await create(manager.token);
  expect(await decide(own.id), 403, 'SELF_REVIEW_FORBIDDEN');
});
await check('Blank and oversized rejection comments are rejected', async () => {
  const request = await create(employee.token);
  for (const comment of ['', '   ', 'x'.repeat(1001)]) expect(await decide(request.id, 'reject', manager.token, {comment}), 400, 'VALIDATION_FAILED');
});
await check('Rejection and cancellation leave balances unchanged', async () => {
  const before = sql(`select used from leave_balances where employee_id=${john.id} and balance_year=${year} and leave_type='VACATION'`);
  const rejected = await create(employee.token, leaveBody(undefined, 'VACATION'));
  expect(await decide(rejected.id, 'reject'), 200);
  const cancelled = await create(employee.token, leaveBody(undefined, 'VACATION'));
  expect(await api(`/api/leaves/${cancelled.id}/cancel`, {token: employee.token, method: 'PATCH'}), 200);
  assert.equal(sql(`select used from leave_balances where employee_id=${john.id} and balance_year=${year} and leave_type='VACATION'`), before);
});
await check('Duplicate decisions produce one transition and audit event', async () => {
  for (let i = 0; i < 20; i++) {
    const request = await create(employee.token);
    const results = await Promise.all([decide(request.id), decide(request.id)]);
    assert.deepEqual(results.map(r => r.status).sort(), [200, 409]);
    assert.equal(sql(`select count(*) from audit_logs where entity_type='LeaveRequest' and entity_id=${request.id} and action='LEAVE_REQUEST_APPROVED'`), '1');
  }
});
await check('Approve/reject and approve/cancel races have one terminal event', async () => {
  for (const decision of ['reject', 'cancel']) for (let i = 0; i < 5; i++) {
    const request = await create(employee.token);
    const other = decision === 'cancel' ? api(`/api/leaves/${request.id}/cancel`, {token: employee.token, method: 'PATCH'}) : decide(request.id, 'reject');
    const results = await Promise.all([decide(request.id), other]);
    assert.deepEqual(results.map(r => r.status).sort(), [200, 409]);
    assert.equal(sql(`select count(*) from audit_logs where entity_type='LeaveRequest' and entity_id=${request.id} and action in ('LEAVE_REQUEST_APPROVED','LEAVE_REQUEST_REJECTED','LEAVE_REQUEST_CANCELLED')`), '1');
  }
});
await check('Concurrent approvals cannot overdraw a balance', async () => {
  sql(`update leave_balances set total_allocated=used+1 where employee_id=${john.id} and balance_year=${year} and leave_type='VACATION'`);
  const requests = [await create(employee.token, leaveBody(undefined, 'VACATION')), await create(employee.token, leaveBody(undefined, 'VACATION'))];
  const results = await Promise.all(requests.map(r => decide(r.id)));
  assert.deepEqual(results.map(r => r.status).sort(), [200, 409]);
  assert.equal(sql(`select total_allocated-used from leave_balances where employee_id=${john.id} and balance_year=${year} and leave_type='VACATION'`), '0');
});
await check('Ordinary employees cannot be managers and self-management is forbidden', async () => {
  for (const managerId of [john.id, emmaUser.id]) expect(await api(`/api/admin/employees/${john.id}/manager`, {token: admin.token, method: 'PATCH', body: {managerId}}), managerId === john.id ? 400 : 409);
});
await check('Managers with reports cannot be demoted or deactivated', async () => {
  expect(await api(`/api/admin/employees/${sarah.id}/status`, {token: admin.token, method: 'PATCH', body: {active: false}}), 409, 'MANAGER_HAS_DIRECT_REPORTS');
  expect(await api(`/api/admin/employees/${sarah.id}/role`, {token: admin.token, method: 'PATCH', body: {role: 'EMPLOYEE'}}), 409, 'MANAGER_HAS_DIRECT_REPORTS');
});
await check('Final active administrator cannot be removed', async () => expect(await api(`/api/admin/employees/${admin.user.id}/status`, {token: admin.token, method: 'PATCH', body: {active: false}}), 409, 'LAST_ACTIVE_ADMIN_REQUIRED'));
await check('Inactive accounts reject both login and previously issued JWTs', async () => {
  expect(await api(`/api/admin/employees/${emmaUser.id}/status`, {token: admin.token, method: 'PATCH', body: {active: false}}), 200);
  try {
    expect(await api('/api/employees/me', {token: emma.token}), 401, 'AUTHENTICATION_REQUIRED');
    expect(await api('/api/auth/login', {method: 'POST', body: {email: 'emma@example.com', password: 'Welcome123!'}}), 401, 'AUTHENTICATION_REQUIRED');
  } finally {
    expect(await api(`/api/admin/employees/${emmaUser.id}/status`, {token: admin.token, method: 'PATCH', body: {active: true}}), 200);
    await login('admin@example.com');
  }
});
await check('Email normalization prevents case and whitespace duplicates', async () => {
  expect(await api('/api/admin/employees', {token: admin.token, method: 'POST', body: {...employeeBody, email: 'JOHN@EXAMPLE.COM'}}), 409, 'DUPLICATE_EMAIL');
  expect(await api('/api/admin/employees', {token: admin.token, method: 'POST', body: {...employeeBody, email: `unique-${Date.now()}@example.com`, employeeId: ' EMP-001 '}}), 409, 'DUPLICATE_EMPLOYEE_ID');
});
await check('Pending requests follow the current manager and cycles are rejected', async () => {
  const created = expect(await api('/api/admin/employees', {token: admin.token, method: 'POST', body: {...employeeBody,
    email: `manager-${Date.now()}@example.com`, employeeId: `MANAGER-${Date.now()}`, role: 'MANAGER', managerId: null}}), 201);
  const newManager = await login(created.email);
  const request = await create(employee.token);
  expect(await api(`/api/admin/employees/${john.id}/manager`, {token: admin.token, method: 'PATCH', body: {managerId: created.id}}), 200);
  try {
    expect(await decide(request.id), 403);
    expect(await decide(request.id, 'approve', newManager.token), 200);
    expect(await api(`/api/admin/employees/${created.id}/manager`, {token: admin.token, method: 'PATCH', body: {managerId: sarah.id}}), 200);
    expect(await api(`/api/admin/employees/${sarah.id}/manager`, {token: admin.token, method: 'PATCH', body: {managerId: created.id}}), 400);
  } finally {
    expect(await api(`/api/admin/employees/${john.id}/manager`, {token: admin.token, method: 'PATCH', body: {managerId: sarah.id}}), 200);
  }
});
await check('Concurrent final-admin demotions retain an active administrator', async () => {
  const other = expect(await api('/api/admin/employees', {token: admin.token, method: 'POST', body: {...employeeBody,
    email: `admin-${Date.now()}@example.com`, employeeId: `ADMIN-${Date.now()}`, role: 'ADMIN', managerId: null}}), 201);
  const results = await Promise.all([admin.user.id, other.id].map(id => api(`/api/admin/employees/${id}/role`, {token: admin.token, method: 'PATCH', body: {role: 'EMPLOYEE'}})));
  assert.deepEqual(results.map(r => r.status).sort(), [200, 409]);
  assert.equal(sql("select count(*) from employees where role='ADMIN' and active"), '1');
  // Restore the fixture using the remaining administrator's credentials.
  const remaining = await login(results[0].status === 200 ? other.email : admin.user.email);
  const removedId = results[0].status === 200 ? admin.user.id : other.id;
  expect(await api(`/api/admin/employees/${removedId}/role`, {token: remaining.token, method: 'PATCH', body: {role: 'ADMIN'}}), 200);
  expect(await api(`/api/admin/employees/${other.id}/role`, {token: admin.token, method: 'PATCH', body: {role: 'EMPLOYEE'}}), 200);
});
await check('Audit page size is bounded at 100 rows', async () => {
  const r = expect(await api('/api/admin/audit-logs?size=2147483647', {token: admin.token}), 200);
  assert.equal(r.size, 100); assert(r.content.length <= 100);
});
await check('Audit update, delete and truncate are blocked', () => {
  for (const statement of ["update audit_logs set description='tampered' where id=(select min(id) from audit_logs)", 'delete from audit_logs', 'truncate audit_logs']) {
    assert.throws(() => sql(statement), /audit history is append-only/);
  }
});
await check('Held PostgreSQL row lock returns bounded retryable 503', async () => {
  const request = await create(employee.token);
  const lock = spawn('docker', [...composeArgs, 'exec', '-T', 'db', 'psql', '-U', 'verification', '-d', 'verification', '-Atc',
    `begin; select id from leave_requests where id=${request.id} for update; select pg_sleep(8); rollback;`]);
  const done = new Promise((resolve, reject) => {lock.on('exit', code => code === 0 ? resolve() : reject(Error(`Lock holder exit ${code}`))); lock.on('error', reject);});
  for (let i = 0; i < 30; i++) {
    if (Number(sql("select count(*) from pg_stat_activity where application_name='psql' and wait_event='PgSleep'")) > 0) break;
    await sleep(100);
  }
  const start = performance.now();
  try {expect(await decide(request.id), 503, 'TEMPORARILY_UNAVAILABLE'); assert(performance.now() - start < 6000);}
  finally {await done;}
});
await check('Database outage returns correlated bounded 503 for JWT and login', async () => {
  docker('stop', 'db');
  try {
    for (const [path, options, id] of [
      ['/api/employees/me', {token: employee.token}, 'verification-jwt-outage'],
      ['/api/auth/login', {method: 'POST', body: {email: 'john@example.com', password: 'Welcome123!'}}, 'verification-login-outage'],
    ]) {
      const start = performance.now();
      const r = await api(path, {...options, headers: {'X-Request-ID': id}});
      expect(r, 503, 'DEPENDENCY_UNAVAILABLE'); assert.equal(r.data.requestId, id); assert(performance.now() - start < 10000);
      assert.match(docker('logs', '--no-color', 'backend'), new RegExp(`requestId=${id}`));
    }
  } finally {docker('start', 'db'); await sleep(2000);}
});
await check('Thirty simultaneous failed logins permit at most five attempts', async () => {
  // All successful logins above reset this address; put throttling last.
  const results = await Promise.all(Array.from({length: 30}, () => api('/api/auth/login', {method: 'POST', body: {email: 'nobody@example.com', password: 'Welcome123!'}})));
  assert.equal(results.filter(r => r.status === 401).length, 5);
  assert.equal(results.filter(r => r.status === 429 && r.data.code === 'LOGIN_RATE_LIMITED').length, 25);
});
console.log(`Verification API: ${passed} passed, ${failed} failed`);
if (failed) process.exitCode = 1;
