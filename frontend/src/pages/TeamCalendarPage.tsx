import {useMemo, useState} from 'react';
import {useApiQuery} from '../lib/useApiQuery';
import {RequestError} from '../components/RequestError';
import {CalendarX2, ChevronLeft, ChevronRight, Loader2} from 'lucide-react';
import {api} from '../lib/api';
import {useAuth} from '../context/AuthContext';
import type {CalendarEvent} from '../types';

const iso = (date: Date) =>
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;

export function TeamCalendarPage() {
  const {user} = useAuth();
  const [month, setMonth] = useState(() => {
    const today = new Date();
    return new Date(today.getFullYear(), today.getMonth(), 1);
  });
  const start = iso(new Date(month.getFullYear(), month.getMonth(), 1));
  const end = iso(new Date(month.getFullYear(), month.getMonth() + 1, 0));

  const {data,loading,error,reload}=useApiQuery<CalendarEvent[]>(`/api/manager/leave-requests/calendar?start=${start}&end=${end}`);
  const events=data??[];

  const cells = useMemo(() => {
    const first = new Date(month.getFullYear(), month.getMonth(), 1);
    const last = new Date(month.getFullYear(), month.getMonth() + 1, 0);
    return [
      ...Array(first.getDay()).fill(null),
      ...Array.from({length: last.getDate()}, (_, index) => index + 1),
    ];
  }, [month]);

  const change = (amount: number) => {
    setMonth(new Date(month.getFullYear(), month.getMonth() + amount, 1));
  };

  return (
    <div className="space-y-7">
      <div>
        <p className="eyebrow">Manager workspace</p>
        <h1 className="page-title mt-2">Team calendar</h1>
        <p className="mt-2 text-sm text-slate-500">
          {user?.role === 'ADMIN'
            ? 'Approved and pending leave across the organization. Reasons remain private.'
            : 'Approved and pending leave for your direct reports. Reasons remain private.'}
        </p>
      </div>

      <section className="card overflow-hidden">
        <div className="flex items-center justify-between border-b px-5 py-4">
          <button type="button" aria-label="Previous month" className="btn-secondary !p-2" onClick={() => change(-1)}>
            <ChevronLeft size={18} />
          </button>
          <h2 className="text-lg font-bold">
            {month.toLocaleDateString('en-US', {month: 'long', year: 'numeric'})}
          </h2>
          <button type="button" aria-label="Next month" className="btn-secondary !p-2" onClick={() => change(1)}>
            <ChevronRight size={18} />
          </button>
        </div>

        {error && (
          <RequestError message={`Calendar could not be loaded: ${error}`} onRetry={reload}/>
        )}
        {!loading && !error && events.length === 0 && (
          <div className="flex items-center justify-center gap-2 border-b bg-slate-50 px-5 py-3 text-sm text-slate-500">
            <CalendarX2 size={17} /> No approved or pending leave this month.
          </div>
        )}
        {loading && (
          <div className="flex items-center justify-center gap-2 border-b bg-slate-50 px-5 py-3 text-sm text-slate-500">
            <Loader2 className="animate-spin" size={17} /> Loading calendar…
          </div>
        )}

        <div className="grid grid-cols-7 border-b bg-slate-50 text-center text-xs font-semibold uppercase tracking-wide text-slate-500">
          {['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'].map((day) => <div className="p-3" key={day}>{day}</div>)}
        </div>
        <div className="grid grid-cols-7">
          {cells.map((day, index) => {
            const date = day ? `${start.slice(0, 8)}${String(day).padStart(2, '0')}` : '';
            const items = events.filter((event) => date >= event.startDate && date <= event.endDate);
            return (
              <div key={index} className="min-h-28 border-b border-r p-2">
                <div className={`text-xs font-semibold ${day ? 'text-slate-600' : 'text-slate-200'}`}>{day ?? ''}</div>
                <div className="mt-2 space-y-1">
                  {items.slice(0, 3).map((event) => (
                    <div
                      key={event.id}
                      title={`${event.employeeName} · ${event.leaveType} · ${event.status}`}
                      className={`rounded-md px-2 py-1 text-[11px] font-medium ${event.status === 'APPROVED' ? 'bg-emerald-100 text-emerald-800' : 'border border-dashed border-amber-300 bg-amber-50 text-amber-800'}`}
                    >
                      <div className="truncate">{event.employeeName.split(' ')[0]} · {event.leaveType[0] + event.leaveType.slice(1).toLowerCase()}</div>
                      <div className="mt-0.5 text-[9px] font-bold uppercase tracking-wide opacity-75">{event.status}</div>
                    </div>
                  ))}
                  {items.length > 3 && <div className="text-[10px] text-slate-400">+{items.length - 3} more</div>}
                </div>
              </div>
            );
          })}
        </div>
        <div className="flex gap-5 px-5 py-4 text-xs text-slate-500">
          <span className="flex items-center gap-2"><i className="h-2.5 w-2.5 rounded-full bg-emerald-400" />Approved</span>
          <span className="flex items-center gap-2"><i className="h-2.5 w-2.5 rounded-full border border-amber-400 bg-amber-100" />Pending</span>
        </div>
      </section>
    </div>
  );
}
