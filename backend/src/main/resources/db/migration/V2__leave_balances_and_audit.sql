CREATE TABLE leave_balances (
  id BIGSERIAL PRIMARY KEY,
  employee_id BIGINT NOT NULL REFERENCES employees(id),
  leave_type VARCHAR(50) NOT NULL,
  total_allocated INTEGER NOT NULL CHECK (total_allocated >= 0),
  used INTEGER NOT NULL CHECK (used >= 0 AND used <= total_allocated),
  year INTEGER NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uk_leave_balance_employee_type_year UNIQUE(employee_id,leave_type,year)
);

CREATE INDEX idx_leave_balance_employee_year ON leave_balances(employee_id,year);

CREATE TABLE audit_logs (
  id BIGSERIAL PRIMARY KEY,
  actor_employee_id BIGINT REFERENCES employees(id),
  action VARCHAR(80) NOT NULL,
  entity_type VARCHAR(80) NOT NULL,
  entity_id BIGINT NOT NULL,
  description VARCHAR(1500) NOT NULL,
  timestamp TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_audit_timestamp ON audit_logs(timestamp DESC);
CREATE INDEX idx_audit_actor ON audit_logs(actor_employee_id);
CREATE INDEX idx_audit_action_entity ON audit_logs(action,entity_type);

