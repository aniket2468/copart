ALTER TABLE employees ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE leave_requests ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE leave_balances ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE employees
  ADD CONSTRAINT ck_employee_manager_not_self CHECK (manager_id IS NULL OR manager_id <> id);
ALTER TABLE leave_requests
  ADD CONSTRAINT ck_leave_date_order CHECK (start_date <= end_date);

CREATE INDEX idx_employee_manager ON employees(manager_id);
CREATE UNIQUE INDEX uk_employees_email_lower ON employees(lower(email));
