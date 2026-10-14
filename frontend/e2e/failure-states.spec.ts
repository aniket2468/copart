import {test, expect, type Page} from '@playwright/test';

const admin = {id: 1, employeeId: 'ADMIN', firstName: 'Alex', lastName: 'Admin', email: 'admin@example.com',
  jobTitle: 'Administrator', department: 'Operations', role: 'ADMIN', dateOfJoining: '2020-01-01', active: true, createdAt: '2020-01-01T00:00:00Z'};
const leave = {id: 10, employee: {id: 2, employeeId: 'EMP', name: 'John Employee', jobTitle: 'Engineer', department: 'Tech'},
  leaveType: 'VACATION', startDate: '2026-11-02', endDate: '2026-11-02', days: 1, reason: 'Trip', status: 'PENDING', requestedAt: '2026-10-14T12:00:00Z'};
const delay = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));

async function session(page: Page) {
  await page.addInitScript(() => localStorage.setItem('pf_token', 'test-session'));
  await page.route('**/api/**', route => {
    const path = new URL(route.request().url()).pathname;
    return route.fulfill({json: path === '/api/employees/me' ? admin : path.includes('conflicts') ? {conflictingEmployees: 0, days: []} : []});
  });
}

for (const [path, endpoint, empty] of [
  ['/leave', '/api/leaves/me', 'No leave requests'],
  ['/approvals', '/api/manager/leave-requests', 'There are no pending leave requests for your team.'],
  ['/team-calendar', '/api/manager/leave-requests/calendar*', 'No approved or pending leave this month.'],
] as const) {
  test(`${path} shows failures and recovers on retry`, async ({page}) => {
    await session(page);
    let fail = true;
    await page.route(`**${endpoint}`, route => route.fulfill(fail ? {status: 503, json: {message: 'Database temporarily unavailable'}} : {json: []}));
    await page.goto(path);
    await expect(page.getByRole('alert')).toContainText('Database temporarily unavailable');
    await expect(page.getByText(empty, {exact: true})).not.toBeVisible();
    fail = false;
    await page.getByRole('button', {name: 'Try again'}).click();
    await expect(page.getByText(empty, {exact: true})).toBeVisible();
  });
}

test('temporary session verification failure preserves the token and offers retry', async ({page}) => {
  await session(page);
  let fail = true;
  await page.route('**/api/employees/me', route => route.fulfill(fail ? {status: 503, json: {message: 'Session service unavailable'}} : {json: admin}));
  await page.goto('/profile');
  await expect(page.getByRole('alert')).toContainText('Session service unavailable');
  expect(await page.evaluate(() => localStorage.getItem('pf_token'))).toBe('test-session');
  fail = false;
  await page.getByRole('button', {name: 'Try again'}).click();
  await expect(page.getByRole('heading', {name: 'My profile'})).toBeVisible();
});

test('expired session clears storage and redirects to login', async ({page}) => {
  await session(page);
  // Seed once so a redirect cannot recreate the expired token.
  await page.addInitScript(() => {
    if (sessionStorage.getItem('expired-seeded')) localStorage.removeItem('pf_token');
    else sessionStorage.setItem('expired-seeded', 'yes');
  });
  await page.route('**/api/employees/me', route => route.fulfill({status: 401, json: {message: 'Expired'}}));
  await page.goto('/profile');
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole('button', {name: 'Sign in', exact: true})).toBeVisible();
  expect(await page.evaluate(() => localStorage.getItem('pf_token'))).toBeNull();
});

test('cancellation blocks duplicate submits and displays the server error', async ({page}) => {
  await session(page);
  let calls = 0;
  await page.route('**/api/leaves/me', route => route.fulfill({json: [leave]}));
  await page.route('**/api/leaves/10/cancel', async route => {
    calls++;
    await delay(400);
    await route.fulfill({status: 503, json: {message: 'Cancellation temporarily unavailable'}});
  });
  page.on('dialog', dialog => dialog.accept());
  await page.goto('/leave');
  await page.getByRole('button', {name: 'Cancel', exact: true}).click();
  await expect(page.getByRole('button', {name: /Cancelling/})).toBeDisabled();
  await expect(page.getByText('Cancellation temporarily unavailable')).toBeVisible();
  expect(calls).toBe(1);
  await expect(page.getByRole('button', {name: 'Cancel', exact: true})).toBeEnabled();
});

test('audit filter changes cannot be overwritten by an older response', async ({page}) => {
  await session(page);
  await page.route('**/api/admin/audit-logs?*', async route => {
    const filtered = new URL(route.request().url()).searchParams.has('action');
    if (!filtered) await delay(600);
    await route.fulfill({json: {content: [{id: filtered ? 2 : 1, actorName: 'Alex', action: 'EMPLOYEE_CREATED', entityType: 'Employee', entityId: 1,
      description: filtered ? 'Current filter result' : 'Obsolete result', timestamp: '2026-10-14T12:00:00Z'}], totalElements: 1, totalPages: 1, number: 0, size: 15}});
  });
  await page.goto('/admin/audit-logs');
  await page.getByRole('combobox', {name: 'Filter by action'}).selectOption('EMPLOYEE_CREATED');
  await expect(page.getByText('Current filter result')).toBeVisible();
  await delay(700);
  await expect(page.getByText('Obsolete result')).not.toBeVisible();
});

test('switching employees cannot display a previous employee balance', async ({page}) => {
  await session(page);
  await page.route('**/api/admin/employees', route => route.fulfill({json: [admin, {...admin, id: 2, firstName: 'John', lastName: 'Employee'}]}));
  await page.route('**/api/admin/employees/*/leave-balances', async route => {
    const first = route.request().url().includes('/1/');
    if (first) await delay(600);
    await route.fulfill({json: [{id: first ? 1 : 2, leaveType: 'VACATION', totalAllocated: first ? 15 : 25, remaining: first ? 15 : 25, used: 0, year: 2026}]});
  });
  await page.goto('/admin/balances');
  await page.getByRole('button', {name: /Alex Admin/}).click();
  await page.getByRole('button', {name: /John Employee/}).click();
  await expect(page.getByText('/ 25')).toBeVisible();
  await delay(700);
  await expect(page.getByText('/ 15')).not.toBeVisible();
});

test('editing an employee omits an unchanged password and blocks duplicate saves', async ({page}) => {
  await session(page);
  let calls = 0;
  await page.route('**/api/employees', route => route.fulfill({json: [admin]}));
  await page.route('**/api/employees/1', async route => {
    calls++;
    expect(route.request().postDataJSON()).not.toHaveProperty('password');
    await delay(400);
    await route.fulfill({json: admin});
  });
  await page.goto('/admin/employees');
  await page.getByTitle('Edit', {exact: true}).click();
  await page.getByRole('button', {name: 'Save changes', exact: true}).click();
  await expect(page.getByRole('button', {name: 'Save changes', exact: true})).toBeDisabled();
  await expect(page.getByText('Employee updated', {exact: true})).toBeVisible();
  expect(calls).toBe(1);
});
