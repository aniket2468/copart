import {useEffect, useRef, useState} from 'react';
import {Link, NavLink, Outlet} from 'react-router-dom';
import {
  BarChart3,
  CalendarDays,
  ChevronDown,
  ClipboardCheck,
  LayoutDashboard,
  LogOut,
  Menu,
  PanelLeftClose,
  UserRound,
  X,
} from 'lucide-react';
import {useAuth} from '../context/AuthContext';
import {initials} from '../lib/api';

const base = [
  {to: '/', label: 'Overview', icon: BarChart3},
  {to: '/leave', label: 'My leave', icon: CalendarDays},
  {to: '/profile', label: 'My profile', icon: UserRound},
];

export function Layout() {
  const {user, logout} = useAuth();
  const [open, setOpen] = useState(false);
  const [accountMenuOpen, setAccountMenuOpen] = useState(false);
  const accountMenuRef = useRef<HTMLDivElement>(null);

  const nav = [
    ...base,
    ...(user?.role === 'MANAGER' || user?.role === 'ADMIN'
      ? [
          {to: '/approvals', label: 'Approvals', icon: ClipboardCheck},
          {to: '/team-calendar', label: 'Team calendar', icon: CalendarDays},
        ]
      : []),
    ...(user?.role === 'ADMIN'
      ? [{to: '/admin', label: 'Admin portal', icon: LayoutDashboard}]
      : []),
  ];

  useEffect(() => {
    function closeOnOutsideClick(event: MouseEvent) {
      if (accountMenuRef.current && !accountMenuRef.current.contains(event.target as Node)) {
        setAccountMenuOpen(false);
      }
    }

    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') setAccountMenuOpen(false);
    }

    document.addEventListener('mousedown', closeOnOutsideClick);
    document.addEventListener('keydown', closeOnEscape);
    return () => {
      document.removeEventListener('mousedown', closeOnOutsideClick);
      document.removeEventListener('keydown', closeOnEscape);
    };
  }, []);

  return (
    <div className="min-h-screen bg-[#f7f8f5]">
      <aside className={`fixed inset-y-0 left-0 z-40 w-64 transform bg-forest-900 text-white transition md:translate-x-0 ${open ? 'translate-x-0' : '-translate-x-full'}`}>
        <div className="flex h-20 items-center justify-between px-6">
          <div className="flex items-center gap-3">
            <div className="grid h-9 w-9 place-items-center rounded-xl bg-white text-sm font-bold text-forest-900">PF</div>
            <div>
              <div className="font-bold">PeopleFlow</div>
              <div className="text-[10px] uppercase tracking-[.2em] text-emerald-200/70">Workspace</div>
            </div>
          </div>
          <button type="button" aria-label="Close navigation" className="md:hidden" onClick={() => setOpen(false)}>
            <X />
          </button>
        </div>

        <nav className="space-y-1 px-3 pt-5">
          {nav.map(({to, label, icon: Icon}) => (
            <NavLink
              key={to}
              to={to}
              end={to === '/' || to === '/admin'}
              onClick={() => setOpen(false)}
              className={({isActive}) => `flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition ${isActive ? 'bg-white/12 text-white' : 'text-emerald-50/70 hover:bg-white/5 hover:text-white'}`}
            >
              <Icon size={19} />{label}
            </NavLink>
          ))}
        </nav>

        <div className="absolute inset-x-3 bottom-4 rounded-2xl bg-white/[.07] p-3">
          <div className="flex items-center gap-3">
            <div className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-emerald-200 font-semibold text-forest-900">
              {user && initials(user.firstName, user.lastName)}
            </div>
            <div className="min-w-0 flex-1">
              <div className="truncate text-sm font-semibold">{user?.firstName} {user?.lastName}</div>
              <div className="truncate text-xs text-emerald-100/60">{user?.role.toLowerCase()}</div>
            </div>
            <button type="button" onClick={logout} aria-label="Sign out" title="Sign out" className="text-emerald-100/60 hover:text-white">
              <LogOut size={18} />
            </button>
          </div>
        </div>
      </aside>

      {open && <button type="button" aria-label="Close menu" className="fixed inset-0 z-30 bg-black/30 md:hidden" onClick={() => setOpen(false)} />}

      <div className="md:pl-64">
        <header className="sticky top-0 z-20 flex h-20 items-center justify-between border-b bg-[#f7f8f5]/90 px-5 backdrop-blur md:px-8">
          <button type="button" aria-label="Open navigation" className="md:hidden" onClick={() => setOpen(true)}>
            <Menu />
          </button>
          <div className="hidden items-center gap-2 text-sm text-slate-500 md:flex">
            <PanelLeftClose size={18} /> Employee workspace
          </div>

          <div ref={accountMenuRef} className="relative">
            <button
              type="button"
              aria-haspopup="menu"
              aria-expanded={accountMenuOpen}
              aria-controls="account-menu"
              onClick={() => setAccountMenuOpen((current) => !current)}
              className="flex min-w-28 items-center justify-between gap-2 rounded-xl border bg-white px-3 py-2 text-sm transition hover:border-slate-300 hover:bg-slate-50"
            >
              <span className="font-medium">{user?.department}</span>
              <ChevronDown size={14} className={`text-slate-400 transition-transform ${accountMenuOpen ? 'rotate-180' : ''}`} />
            </button>

            {accountMenuOpen && (
              <div id="account-menu" role="menu" className="absolute right-0 top-[calc(100%+.5rem)] w-64 overflow-hidden rounded-2xl border bg-white p-2 shadow-xl">
                <div className="flex items-center gap-3 border-b px-3 py-3">
                  <div className="grid h-10 w-10 shrink-0 place-items-center rounded-full bg-emerald-100 text-sm font-bold text-forest-800">
                    {user && initials(user.firstName, user.lastName)}
                  </div>
                  <div className="min-w-0">
                    <div className="truncate text-sm font-semibold text-slate-900">{user?.firstName} {user?.lastName}</div>
                    <div className="truncate text-xs text-slate-500">{user?.email}</div>
                  </div>
                </div>

                <Link to="/profile" role="menuitem" onClick={() => setAccountMenuOpen(false)} className="mt-2 flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium text-slate-700 hover:bg-slate-50">
                  <UserRound size={17} /> My profile
                </Link>
                {user?.role === 'ADMIN' && (
                  <Link to="/admin" role="menuitem" onClick={() => setAccountMenuOpen(false)} className="flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium text-slate-700 hover:bg-slate-50">
                    <LayoutDashboard size={17} /> Admin portal
                  </Link>
                )}
                <button
                  type="button"
                  role="menuitem"
                  onClick={() => {setAccountMenuOpen(false); logout();}}
                  className="flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium text-rose-600 hover:bg-rose-50"
                >
                  <LogOut size={17} /> Sign out
                </button>
              </div>
            )}
          </div>
        </header>
        <main className="mx-auto max-w-7xl p-5 md:p-8"><Outlet /></main>
      </div>
    </div>
  );
}
