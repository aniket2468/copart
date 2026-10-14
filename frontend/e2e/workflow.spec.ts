import {test, expect} from '@playwright/test';

test('employee requests leave, manager approves, and employee sees deducted balance', async ({page, request}) => {
  test.skip(!process.env.E2E_BASE_URL, 'Requires the disposable Docker verification stack');
  const login = await request.post('/api/auth/login', {data: {email: 'admin@example.com', password: 'Welcome123!'}});
  expect(login.ok()).toBeTruthy();
  const {token} = await login.json();
  const suffix = Date.now();
  const employees = await request.get('/api/admin/employees', {headers: {Authorization: `Bearer ${token}`}});
  const sarah = (await employees.json()).find((employee: {email: string}) => employee.email === 'sarah@example.com');
  const email = `browser-${suffix}@example.com`;
  const employee = await request.post('/api/admin/employees', {headers: {Authorization: `Bearer ${token}`}, data: {
    employeeId: `BROWSER-${suffix}`, firstName: 'Browser', lastName: `Test${suffix}`, email, password: 'Welcome123!',
    jobTitle: 'Engineer', department: 'Verification', role: 'EMPLOYEE', managerId: sarah.id, dateOfJoining: '2020-01-01',
  }});
  expect(employee.status()).toBe(201);
  async function signIn(account: string) {
    await page.goto('/login');
    await page.getByLabel('Work email').fill(account);
    await page.getByLabel('Password', {exact: true}).fill('Welcome123!');
    await page.getByRole('button', {name: 'Sign in', exact: true}).click();
    await expect(page.getByRole('heading', {name: /Good to see you/})).toBeVisible();
  }
  await signIn(email);
  await page.getByRole('link', {name: 'My leave', exact: true}).click();
  await page.getByRole('button', {name: 'New request'}).click();
  const date = new Date();
  date.setDate(date.getDate() + 7);
  while ([0, 6].includes(date.getDay())) date.setDate(date.getDate() + 1);
  const day = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  await page.locator('input[type=date]').nth(0).fill(day);
  await page.locator('input[type=date]').nth(1).fill(day);
  await page.locator('textarea').fill(`Browser approval ${suffix}`);
  await page.getByRole('button', {name: 'Submit request'}).click();
  await expect(page.getByRole('row').filter({hasText: `Browser approval ${suffix}`})).toContainText('Pending');
  await page.getByRole('button', {name: 'Sign out', exact: true}).click();
  await signIn('sarah@example.com');
  await page.getByRole('link', {name: 'Approvals', exact: true}).click();
  const card = page.locator('div.p-5.md\\:p-6').filter({hasText: `Browser approval ${suffix}`});
  await card.getByRole('button', {name: 'Approve', exact: true}).click();
  await expect(card).not.toBeVisible();
  await page.getByRole('button', {name: 'Sign out', exact: true}).click();
  await signIn(email);
  await expect(page.getByText('14', {exact: true})).toBeVisible();
  await page.getByRole('link', {name: 'My leave', exact: true}).click();
  await expect(page.getByRole('row').filter({hasText: `Browser approval ${suffix}`})).toContainText('Approved');
});
