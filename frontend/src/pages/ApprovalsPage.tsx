import { useState } from "react";
import { useApiQuery } from "../lib/useApiQuery";
import { Check, MessageSquareText, X } from "lucide-react";
import { api, fmtDate } from "../lib/api";
import type { Leave } from "../types";
import { StatusBadge } from "../components/StatusBadge";
import { EmptyState } from "../components/EmptyState";
import { Toast } from "../components/Toast";
import { ConflictWarning } from "../components/ConflictWarning";
import { RequestError } from "../components/RequestError";

export function ApprovalsPage() {
  const {data, loading, error: loadError, reload: load} = useApiQuery<Leave[]>("/api/manager/leave-requests");
  const rows = data ?? [];
  const [reject, setReject] = useState<Leave | null>(null);
  const [comment, setComment] = useState("");
  const [toast, setToast] = useState("");
  const [busyId, setBusyId] = useState<number | null>(null);

  async function decide(leave: Leave, decision: "approve" | "reject", note = "") {
    if (busyId !== null) return;
    if (decision === "reject" && !note.trim()) {
      setToast("Add a reason before rejecting this request");
      return;
    }
    setBusyId(leave.id);
    try {
      await api(`/api/manager/leave-requests/${leave.id}/${decision}`, {
        method: "PATCH",
        body: JSON.stringify({ comment: note }),
      });
      await load();
      setReject(null);
      setComment("");
      setToast(`Request ${decision === "approve" ? "approved" : "rejected"}`);
    } catch (error) {
      setToast(error instanceof Error ? error.message : "Unable to update request");
    } finally {
      setBusyId(null);
    }
  }

  const pending = rows.filter((row) => row.status === "PENDING");
  const decided = rows.filter((row) => row.status !== "PENDING");
  return <div className="space-y-7">
    <div><p className="eyebrow">Manager workspace</p><h1 className="page-title mt-2">Leave approvals</h1><p className="mt-2 text-sm text-slate-500">Review requests and team availability before deciding.</p></div>
    {loadError && <RequestError message={loadError} onRetry={load} />}
    {loading && <div className="card p-6 text-sm text-slate-500">Loading approvals…</div>}
    <div className="grid gap-4 sm:grid-cols-3">{[["Awaiting your decision", pending.length], ["Approved", rows.filter(x => x.status === "APPROVED").length], ["Rejected", rows.filter(x => x.status === "REJECTED").length]].map(([label, value]) => <div className="card p-5" key={String(label)}><div className="text-3xl font-bold">{loading||loadError?'–':value}</div><p className="mt-1 text-sm text-slate-500">{label}</p></div>)}</div>
    {!loading&&!loadError&&<><section className="card overflow-hidden"><div className="border-b px-6 py-5"><h2 className="font-semibold">Pending approvals</h2></div>{pending.length === 0 ? <EmptyState title="You’re all caught up" description="There are no pending leave requests for your team." /> : <div className="divide-y">{pending.map(leave => <div key={leave.id} className="p-5 md:p-6"><div className="flex flex-wrap items-start gap-4"><div className="grid h-11 w-11 place-items-center rounded-full bg-emerald-100 font-bold text-forest-800">{leave.employee.name.split(" ").map(name => name[0]).join("")}</div><div className="min-w-[220px] flex-1"><div className="font-semibold">{leave.employee.name}</div><div className="text-xs text-slate-500">{leave.employee.jobTitle} · {leave.employee.department}</div><div className="mt-4 text-sm"><b>{leave.leaveType[0] + leave.leaveType.slice(1).toLowerCase()}</b> · {fmtDate(leave.startDate)} – {fmtDate(leave.endDate)} <span className="text-slate-400">({leave.days} working days)</span></div><div className="mt-2 flex gap-2 text-sm text-slate-600"><MessageSquareText size={17} className="mt-0.5 shrink-0 text-slate-400" />{leave.reason}</div><ConflictWarning requestId={leave.id} /></div><div className="flex gap-2"><button className="btn-secondary !text-rose-600" disabled={busyId !== null} onClick={() => setReject(leave)}><X size={17} />Reject</button><button className="btn-primary" disabled={busyId !== null} onClick={() => decide(leave, "approve")}><Check size={17} />Approve</button></div></div></div>)}</div>}</section>
    <section className="card overflow-hidden"><div className="border-b px-6 py-5"><h2 className="font-semibold">Recent decisions</h2></div>{decided.length === 0 ? <EmptyState /> : <div className="divide-y">{decided.slice(0, 8).map(leave => <div className="flex items-center gap-4 px-6 py-4" key={leave.id}><div className="flex-1"><b className="text-sm">{leave.employee.name}</b><p className="text-xs text-slate-500">{leave.leaveType} · {fmtDate(leave.startDate)} – {fmtDate(leave.endDate)}</p></div><StatusBadge status={leave.status} /></div>)}</div>}</section>
    </>}
    {reject && <div className="fixed inset-0 z-50 grid place-items-center bg-ink/40 p-4"><div className="card w-full max-w-md p-6"><h2 className="text-xl font-bold">Reject request?</h2><p className="mt-2 text-sm text-slate-500">Add context for {reject.employee.name}.</p><textarea autoFocus required maxLength={1000} className="input mt-5 resize-none" rows={4} value={comment} onChange={event => setComment(event.target.value)} /><div className="mt-5 flex justify-end gap-3"><button className="btn-secondary" disabled={busyId !== null} onClick={() => setReject(null)}>Go back</button><button className="btn !bg-rose-600 text-white" disabled={busyId !== null || !comment.trim()} onClick={() => decide(reject, "reject", comment)}>Reject request</button></div></div></div>}
    {toast && <Toast message={toast} onClose={() => setToast("")} />}
  </div>;
}
