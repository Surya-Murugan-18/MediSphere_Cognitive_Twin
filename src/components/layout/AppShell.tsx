import React, { useEffect, useState } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { Topbar } from './Topbar';

export function AppShell({ onSignOut }: {onSignOut: () => void;}) {
  const [navOpen, setNavOpen] = useState(false);
  const { pathname } = useLocation();

  useEffect(() => {
    setNavOpen(false);
    window.scrollTo({ top: 0 });
  }, [pathname]);

  return (
    <div className="min-h-full w-full bg-slate-50">
      <Sidebar open={navOpen} onClose={() => setNavOpen(false)} />
      <div className="lg:pl-60">
        <Topbar onOpenNav={() => setNavOpen(true)} onSignOut={onSignOut} />
        <main className="mx-auto w-full max-w-[1600px] px-4 py-5 lg:px-6 lg:py-6">
          <Outlet />
        </main>
      </div>
    </div>);

}