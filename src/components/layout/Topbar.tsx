import React, { useRef, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { BellIcon, ChevronRightIcon, LockIcon, MenuIcon } from 'lucide-react';
import { GlobalSearch } from './GlobalSearch';
import { NotificationPanel } from './NotificationPanel';
import { metaForPath } from '../../data/navigation';
export function Topbar({
  onOpenNav,
  onSignOut



}: {onOpenNav: () => void;onSignOut: () => void;}) {
  const {
    pathname
  } = useLocation();
  const meta = metaForPath(pathname);
  const [notificationsOpen, setNotificationsOpen] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);
  const notifRef = useRef<HTMLDivElement>(null);
  const profileRef = useRef<HTMLDivElement>(null);
  return <header className="sticky top-0 z-20 border-b border-slate-200 bg-white">
      <div className="flex items-center gap-3 px-4 py-2.5 lg:px-6">
        <button type="button" onClick={onOpenNav} className="rounded p-1.5 text-slate-500 transition-colors duration-150 ease-out hover:bg-slate-100 lg:hidden" aria-label="Open navigation">
          <MenuIcon className="h-5 w-5" aria-hidden="true" />
        </button>

        <div className="min-w-0 flex-1">
          <nav aria-label="Breadcrumb" className="flex items-center gap-1 text-2xs text-slate-400">
            {meta.crumbs.map((crumb, index) => <span key={`${crumb.label}-${index}`} className="flex items-center gap-1">
                {index > 0 && <ChevronRightIcon className="h-3 w-3" aria-hidden="true" />}
                {crumb.to ? <Link to={crumb.to} className="transition-colors duration-150 ease-out hover:text-brand-600">
                    {crumb.label}
                  </Link> : <span className={index === meta.crumbs.length - 1 ? 'text-slate-600' : undefined}>{crumb.label}</span>}
              </span>)}
          </nav>
          <h1 className="truncate text-sm font-semibold text-slate-900">{meta.title}</h1>
        </div>

        <div className="hidden flex-1 justify-center md:flex">
          <GlobalSearch />
        </div>

        <div className="flex items-center gap-1.5">
          <span className="hidden items-center gap-1.5 rounded-md border border-teal-100 bg-teal-50 px-2 py-1 text-2xs font-medium text-teal-700 xl:inline-flex">
            <LockIcon className="h-3 w-3" aria-hidden="true" />
            Secure clinical data
          </span>

          <div className="relative" ref={notifRef}>
            <button type="button" onClick={() => {
            setNotificationsOpen((v) => !v);
            setProfileOpen(false);
          }} aria-label="Notifications" aria-expanded={notificationsOpen} className="relative rounded p-1.5 text-slate-500 transition-colors duration-150 ease-out hover:bg-slate-100">
              <BellIcon className="h-5 w-5" aria-hidden="true" />
              <span className="absolute right-1 top-1 h-1.5 w-1.5 rounded-full bg-critical-500" aria-hidden="true" />
            </button>
            {notificationsOpen && <NotificationPanel onNavigate={() => setNotificationsOpen(false)} />}
          </div>

          <Link to="/settings" aria-label="Help and documentation" className="rounded p-1.5 text-slate-500 transition-colors duration-150 ease-out hover:bg-slate-100">
            <div className="h-5 w-5" aria-hidden="true" />
          </Link>

          <div className="relative" ref={profileRef}>
            <button type="button" onClick={() => {
            setProfileOpen((v) => !v);
            setNotificationsOpen(false);
          }} aria-expanded={profileOpen} className="flex items-center gap-2 rounded-md border border-slate-200 py-1 pl-1 pr-2 transition-colors duration-150 ease-out hover:bg-slate-50">
              <span className="flex h-6 w-6 items-center justify-center rounded-full bg-navy-900 text-2xs font-semibold text-white">AM</span>
              <span className="hidden text-left sm:block">
                <span className="block text-xs font-medium leading-tight text-slate-800">Dr. A. Mehta</span>
                <span className="block text-2xs leading-tight text-slate-500">Clinician</span>
              </span>
            </button>
            {profileOpen && <div className="absolute right-0 top-11 z-40 w-56 overflow-hidden rounded-lg border border-slate-200 bg-white shadow-panel">
                <div className="border-b border-slate-100 px-3 py-2.5">
                  <p className="text-sm font-medium text-slate-900">Dr. Anika Mehta</p>
                  <p className="text-xs text-slate-500">Provider ID: PRV-4471</p>
                  <p className="mt-1 text-2xs text-teal-700">Role: Clinician · Cardiology</p>
                </div>
                <Link to="/settings" onClick={() => setProfileOpen(false)} className="block px-3 py-2 text-sm text-slate-700 transition-colors duration-150 ease-out hover:bg-slate-50">
                  Profile & settings
                </Link>
                <Link to="/audit" onClick={() => setProfileOpen(false)} className="block px-3 py-2 text-sm text-slate-700 transition-colors duration-150 ease-out hover:bg-slate-50">
                  My audit activity
                </Link>
                <button type="button" onClick={onSignOut} className="block w-full border-t border-slate-100 px-3 py-2 text-left text-sm text-critical-600 transition-colors duration-150 ease-out hover:bg-critical-50">
                  Sign out
                </button>
              </div>}
          </div>
        </div>
      </div>

      <div className="border-t border-slate-100 px-4 py-2 md:hidden">
        <GlobalSearch />
      </div>
    </header>;
}