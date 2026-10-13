import type {LeaveStatus} from '../types';
const styles:Record<LeaveStatus,string>={PENDING:'bg-amber-50 text-amber-700 ring-amber-600/20',APPROVED:'bg-emerald-50 text-emerald-700 ring-emerald-600/20',REJECTED:'bg-rose-50 text-rose-700 ring-rose-600/20',CANCELLED:'bg-slate-100 text-slate-600 ring-slate-500/20'};
export function StatusBadge({status}:{status:LeaveStatus}){return <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ring-1 ring-inset ${styles[status]}`}>{status[0]+status.slice(1).toLowerCase()}</span>}

