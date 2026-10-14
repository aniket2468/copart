import {useState} from 'react';
import {useApiQuery} from '../../lib/useApiQuery';
import {Search,X} from 'lucide-react';
import {api} from '../../lib/api';
import type {Employee,LeaveBalance} from '../../types';
import {Toast} from '../../components/Toast';
import {RequestError} from '../../components/RequestError';

export function AdminBalancesPage(){
  const employeeQuery=useApiQuery<Employee[]>('/api/admin/employees');
  const employees=employeeQuery.data??[];
  const [selected,setSelected]=useState<Employee|null>(null);
  const balanceQuery=useApiQuery<LeaveBalance[]>(selected?`/api/admin/employees/${selected.id}/leave-balances`:null);
  const balances=balanceQuery.data??[];
  const [query,setQuery]=useState('');
  const [edit,setEdit]=useState<LeaveBalance|null>(null);
  const [amount,setAmount]=useState(0);
  const [reason,setReason]=useState('');
  const [error,setError]=useState('');
  const pageError=employeeQuery.error||balanceQuery.error;
  const [busy,setBusy]=useState(false);
  const [toast,setToast]=useState('');
  const loadEmployees=employeeQuery.reload;
  function choose(employee:Employee){if(busy)return;setSelected(employee);setEdit(null)}
  async function save(){if(!selected||!edit||!reason.trim())return setError('A reason is required.');if(busy)return;setBusy(true);try{await api(`/api/admin/employees/${selected.id}/leave-balances/${edit.leaveType}`,{method:'PATCH',body:JSON.stringify({totalAllocated:amount,reason})});balanceQuery.reload();setEdit(null);setReason('');setToast('Leave allocation updated and audited')}catch(e){setError(e instanceof Error?e.message:'Unable to update')}finally{setBusy(false)}}
  const shown=employees.filter(e=>`${e.firstName} ${e.lastName} ${e.email} ${e.employeeId}`.toLowerCase().includes(query.toLowerCase()));
  return <div className="space-y-7">
    <div><p className="eyebrow">Admin portal</p><h1 className="page-title mt-2">Leave balances</h1><p className="mt-2 text-sm text-slate-500">Review annual balances and make accountable adjustments.</p></div>
    {pageError&&<RequestError message={pageError} onRetry={()=>{loadEmployees();balanceQuery.reload()}}/>}
    <div className="grid gap-6 lg:grid-cols-[340px_1fr]"><section className="card overflow-hidden"><div className="relative border-b p-4"><Search className="absolute left-7 top-7 text-slate-400" size={17}/><input className="input pl-9" placeholder="Find an employee…" value={query} onChange={e=>setQuery(e.target.value)}/></div><div className="max-h-[560px] divide-y overflow-y-auto">{shown.map(e=><button key={e.id} onClick={()=>choose(e)} className={`w-full px-5 py-4 text-left hover:bg-slate-50 ${selected?.id===e.id?'bg-forest-50':''}`}><div className="text-sm font-semibold">{e.firstName} {e.lastName}</div><div className="text-xs text-slate-500">{e.employeeId} · {e.department}</div></button>)}</div></section><section className="card p-6">{!selected?<div className="grid min-h-72 place-items-center text-sm text-slate-400">Select an employee to view balances.</div>:<><div className="border-b pb-5"><h2 className="text-xl font-bold">{selected.firstName} {selected.lastName}</h2><p className="mt-1 text-sm text-slate-500">{selected.jobTitle} · {selected.department}</p></div>{balanceQuery.loading&&<p className="mt-6 text-sm text-slate-500">Loading balances...</p>}<div className="mt-6 grid gap-4 md:grid-cols-3">{balances.map(balance=><div className="rounded-2xl border p-5" key={balance.leaveType}><div className="text-sm font-semibold">{balance.leaveType[0]+balance.leaveType.slice(1).toLowerCase()}</div><div className="mt-4 text-2xl font-bold">{balance.remaining}<span className="text-sm font-normal text-slate-400"> / {balance.totalAllocated}</span></div><div className="mt-1 text-xs text-slate-500">{balance.used} used · {balance.remaining} remaining</div><button className="mt-5 text-xs font-semibold text-forest-600" onClick={()=>{setEdit(balance);setAmount(balance.totalAllocated);setError('')}}>Adjust allocation</button></div>)}</div></>}</section></div>
    {edit&&<div className="fixed inset-0 z-50 grid place-items-center bg-ink/40 p-4"><div className="card w-full max-w-md p-6"><div className="flex justify-between"><h2 className="text-xl font-bold">Change {edit.leaveType.toLowerCase()} allocation</h2><button disabled={busy} onClick={()=>setEdit(null)}><X/></button></div><p className="mt-2 text-sm text-slate-500">Current allocation: {edit.totalAllocated} days · {edit.used} used</p>{error&&<div className="mt-4 rounded-xl bg-rose-50 p-3 text-sm text-rose-700">{error}</div>}<div className="mt-5"><label className="label">New allocation</label><input type="number" min={edit.used} max={366} className="input" value={amount} onChange={e=>setAmount(Number(e.target.value))}/></div><div className="mt-4"><label className="label">Reason for change</label><textarea className="input resize-none" rows={3} maxLength={500} value={reason} onChange={e=>setReason(e.target.value)}/></div><div className="mt-5 flex justify-end gap-3"><button className="btn-secondary" disabled={busy} onClick={()=>setEdit(null)}>Cancel</button><button className="btn-primary" disabled={busy} onClick={save}>{busy?'Saving…':'Save adjustment'}</button></div></div></div>}
    {toast&&<Toast message={toast} onClose={()=>setToast('')}/>}
  </div>;
}
