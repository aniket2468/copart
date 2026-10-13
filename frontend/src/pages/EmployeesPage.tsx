import { useEffect, useMemo, useState, type FormEvent } from "react";
import { Pencil, Plus, Search, UserCheck, UserX, X } from "lucide-react";
import { api, fmtDate } from "../lib/api";
import type { Employee, Role } from "../types";
import { Toast } from "../components/Toast";
const blank = {
  employeeId: "",
  firstName: "",
  lastName: "",
  email: "",
  password: "",
  jobTitle: "",
  department: "",
  role: "EMPLOYEE" as Role,
  managerId: "",
  dateOfJoining: new Date().toISOString().slice(0, 10),
};
export function EmployeesPage() {
  const [rows, setRows] = useState<Employee[]>([]);
  const [query, setQuery] = useState("");
  const [department, setDepartment] = useState("");
  const [manager, setManager] = useState("");
  const [role, setRole] = useState("");
  const [status, setStatus] = useState("");
  const [editing, setEditing] = useState<Employee | null | undefined>(
    undefined,
  );
  const [form, setForm] = useState(blank);
  const [error, setError] = useState("");
  const [toast, setToast] = useState("");
  const load = () => api<Employee[]>("/api/employees").then(setRows);
  useEffect(() => {
    load();
  }, []);
  const shown = useMemo(
    () =>
      rows.filter((e) => {
        const matchesSearch = `${e.firstName} ${e.lastName} ${e.email} ${e.employeeId} ${e.jobTitle}`
          .toLowerCase().includes(query.toLowerCase());
        return matchesSearch && (!department || e.department === department) &&
          (!manager || String(e.manager?.id ?? "") === manager) &&
          (!role || e.role === role) &&
          (!status || String(e.active) === status);
      }),
    [rows, query, department, manager, role, status],
  );
  function open(e: Employee | null) {
    setEditing(e);
    setError("");
    setForm(
      e
        ? {
            employeeId: e.employeeId,
            firstName: e.firstName,
            lastName: e.lastName,
            email: e.email,
            password: "",
            jobTitle: e.jobTitle,
            department: e.department,
            role: e.role,
            managerId: String(e.manager?.id ?? ""),
            dateOfJoining: e.dateOfJoining,
          }
        : blank,
    );
  }
  async function save(ev: FormEvent) {
    ev.preventDefault();
    setError("");
    const body = {
      ...form,
      managerId: form.managerId ? Number(form.managerId) : null,
    };
    try {
      await api(editing ? `/api/employees/${editing.id}` : "/api/employees", {
        method: editing ? "PUT" : "POST",
        body: JSON.stringify(body),
      });
      await load();
      setEditing(undefined);
      setToast(editing ? "Employee updated" : "Employee added");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unable to save");
    }
  }
  async function toggle(e: Employee) {
    if (
      !confirm(
        `${e.active ? "Deactivate" : "Activate"} ${e.firstName} ${e.lastName}?`,
      )
    )
      return;
    await api(`/api/employees/${e.id}/status`, {
      method: "PATCH",
      body: JSON.stringify({ active: !e.active }),
    });
    await load();
    setToast(e.active ? "Employee deactivated" : "Employee activated");
  }
  return (
    <div className="space-y-7">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="eyebrow">Administration</p>
          <h1 className="page-title mt-2">Employees</h1>
          <p className="mt-2 text-sm text-slate-500">
            Manage people, roles, and reporting lines.
          </p>
        </div>
        <button className="btn-primary" onClick={() => open(null)}>
          <Plus size={18} />
          Add employee
        </button>
      </div>
      <section className="card overflow-hidden">
        <div className="grid gap-3 border-b p-4 md:grid-cols-2 xl:grid-cols-5">
          <div className="relative">
            <Search
              className="absolute left-3 top-2.5 text-slate-400"
              size={19}
            />
            <input
              className="input pl-10"
              placeholder="Search by name, email, or department…"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
            />
          </div>
          <select className="input" value={department} onChange={(e)=>setDepartment(e.target.value)}><option value="">All departments</option>{[...new Set(rows.map(e=>e.department))].sort().map(x=><option key={x}>{x}</option>)}</select>
          <select className="input" value={manager} onChange={(e)=>setManager(e.target.value)}><option value="">All managers</option>{rows.filter(e=>e.role==="MANAGER"||e.role==="ADMIN").map(e=><option key={e.id} value={e.id}>{e.firstName} {e.lastName}</option>)}</select>
          <select className="input" value={role} onChange={(e)=>setRole(e.target.value)}><option value="">All roles</option><option>EMPLOYEE</option><option>MANAGER</option><option>ADMIN</option></select>
          <select className="input" value={status} onChange={(e)=>setStatus(e.target.value)}><option value="">All statuses</option><option value="true">Active</option><option value="false">Inactive</option></select>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full min-w-[900px] text-left text-sm">
            <thead className="border-b bg-slate-50/70 text-xs uppercase tracking-wide text-slate-500">
              <tr>
                {[
                  "Employee",
                  "ID",
                  "Department",
                  "Role",
                  "Manager",
                  "Joined",
                  "Status",
                  "",
                ].map((h) => (
                  <th className="px-5 py-3.5" key={h}>
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y">
              {shown.map((e) => (
                <tr key={e.id} className="hover:bg-slate-50/60">
                  <td className="px-5 py-4">
                    <div className="font-semibold">
                      {e.firstName} {e.lastName}
                    </div>
                    <div className="text-xs text-slate-500">
                      {e.email} · {e.jobTitle}
                    </div>
                  </td>
                  <td className="px-5 py-4 text-slate-500">{e.employeeId}</td>
                  <td className="px-5 py-4">{e.department}</td>
                  <td className="px-5 py-4">
                    <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold">
                      {e.role}
                    </span>
                  </td>
                  <td className="px-5 py-4 text-slate-500">
                    {e.manager?.name ?? "—"}
                  </td>
                  <td className="px-5 py-4 whitespace-nowrap text-slate-500">
                    {fmtDate(e.dateOfJoining)}
                  </td>
                  <td className="px-5 py-4">
                    <span
                      className={`inline-flex items-center gap-1.5 text-xs font-semibold ${e.active ? "text-emerald-700" : "text-slate-400"}`}
                    >
                      <span
                        className={`h-1.5 w-1.5 rounded-full ${e.active ? "bg-emerald-500" : "bg-slate-400"}`}
                      />
                      {e.active ? "Active" : "Inactive"}
                    </span>
                  </td>
                  <td className="px-5 py-4">
                    <div className="flex gap-1">
                      <button
                        title="Edit"
                        onClick={() => open(e)}
                        className="rounded-lg p-2 text-slate-500 hover:bg-slate-100"
                      >
                        <Pencil size={16} />
                      </button>
                      <button
                        title={e.active ? "Deactivate" : "Activate"}
                        onClick={() => toggle(e)}
                        className="rounded-lg p-2 text-slate-500 hover:bg-slate-100"
                      >
                        {e.active ? (
                          <UserX size={17} />
                        ) : (
                          <UserCheck size={17} />
                        )}
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
      {editing !== undefined && (
        <div className="fixed inset-0 z-50 grid place-items-center overflow-y-auto bg-ink/40 p-4">
          <div className="card my-6 w-full max-w-2xl p-6">
            <div className="flex justify-between">
              <div>
                <p className="eyebrow">
                  {editing ? "Update record" : "New team member"}
                </p>
                <h2 className="mt-1 text-xl font-bold">
                  {editing ? "Edit employee" : "Add employee"}
                </h2>
              </div>
              <button onClick={() => setEditing(undefined)}>
                <X className="text-slate-400" />
              </button>
            </div>
            {error && (
              <div className="mt-4 rounded-xl bg-rose-50 p-3 text-sm text-rose-700">
                {error}
              </div>
            )}
            <form onSubmit={save} className="mt-6 grid gap-4 sm:grid-cols-2">
              {[
                ["firstName", "First name"],
                ["lastName", "Last name"],
                ["employeeId", "Employee ID"],
                ["email", "Work email"],
                ["jobTitle", "Job title"],
                ["department", "Department"],
              ].map(([key, label]) => (
                <div key={key}>
                  <label className="label">{label}</label>
                  <input
                    className="input"
                    type={key === "email" ? "email" : "text"}
                    required
                    value={form[key as keyof typeof form]}
                    onChange={(e) =>
                      setForm({ ...form, [key]: e.target.value })
                    }
                  />
                </div>
              ))}
              <div>
                <label className="label">Role</label>
                <select
                  className="input"
                  value={form.role}
                  onChange={(e) =>
                    setForm({ ...form, role: e.target.value as Role })
                  }
                >
                  <option>EMPLOYEE</option>
                  <option>MANAGER</option>
                  <option>ADMIN</option>
                </select>
              </div>
              <div>
                <label className="label">Manager</label>
                <select
                  className="input"
                  value={form.managerId}
                  onChange={(e) =>
                    setForm({ ...form, managerId: e.target.value })
                  }
                >
                  <option value="">No manager</option>
                  {rows
                    .filter((x) => x.id !== editing?.id && x.active)
                    .map((x) => (
                      <option key={x.id} value={x.id}>
                        {x.firstName} {x.lastName}
                      </option>
                    ))}
                </select>
              </div>
              <div>
                <label className="label">Date of joining</label>
                <input
                  className="input"
                  required
                  type="date"
                  value={form.dateOfJoining}
                  onChange={(e) =>
                    setForm({ ...form, dateOfJoining: e.target.value })
                  }
                />
              </div>
              <div>
                <label className="label">
                  {editing ? "New password (optional)" : "Temporary password"}
                </label>
                <input
                  className="input"
                  required={!editing}
                  minLength={8}
                  type="password"
                  value={form.password}
                  onChange={(e) =>
                    setForm({ ...form, password: e.target.value })
                  }
                />
              </div>
              <div className="flex justify-end gap-3 pt-3 sm:col-span-2">
                <button
                  type="button"
                  className="btn-secondary"
                  onClick={() => setEditing(undefined)}
                >
                  Cancel
                </button>
                <button className="btn-primary">
                  {editing ? "Save changes" : "Add employee"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
      {toast && <Toast message={toast} onClose={() => setToast("")} />}
    </div>
  );
}
