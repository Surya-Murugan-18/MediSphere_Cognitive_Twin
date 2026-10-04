export interface NavItem {
  label: string;
  to: string;
  icon: string;
  match?: string[];
  badge?: string;
}

export const primaryNav: NavItem[] = [
{ label: 'Dashboard', to: '/', icon: 'LayoutDashboard' },
{ label: 'Patients', to: '/patients', icon: 'Users' },
{ label: 'Health Twins', to: '/twins', icon: 'PersonStanding' },
{ label: 'Vitals', to: '/vitals', icon: 'Activity' },
{ label: 'Lab Results', to: '/labs', icon: 'FlaskConical' },
{ label: 'Predictions', to: '/predictions', icon: 'Brain' },
{ label: 'Federated Learning', to: '/federated', icon: 'Network' },
{ label: 'Monitoring', to: '/monitoring', icon: 'Radio' },
{ label: 'Alerts', to: '/alerts', icon: 'BellRing', badge: '1' },
{ label: 'Care Plans', to: '/care-plans', icon: 'ClipboardList' },
{ label: 'Population Health', to: '/population', icon: 'PieChart' },
{ label: 'Reports', to: '/reports', icon: 'FileText' },
{ label: 'Consent', to: '/consent', icon: 'ShieldCheck' },
{ label: 'Audit Logs', to: '/audit', icon: 'ScrollText' }];


export const secondaryNav: NavItem[] = [
{ label: 'System Status', to: '/status', icon: 'ServerCog' },
{ label: 'Settings', to: '/settings', icon: 'Settings' }];


export interface RouteMeta {
  title: string;
  crumbs: {label: string;to?: string;}[];
}

export const routeMeta: Record<string, RouteMeta> = {
  '/': { title: 'Clinical Operations Dashboard', crumbs: [{ label: 'MediSphere' }, { label: 'Dashboard' }] },
  '/patients': { title: 'Patients', crumbs: [{ label: 'MediSphere' }, { label: 'Patients' }] },
  '/patients/new': { title: 'Add Patient', crumbs: [{ label: 'MediSphere' }, { label: 'Patients', to: '/patients' }, { label: 'Add Patient' }] },
  '/twins': { title: 'Digital Health Twin', crumbs: [{ label: 'MediSphere' }, { label: 'Health Twins' }] },
  '/vitals': { title: 'Patient Vitals', crumbs: [{ label: 'MediSphere' }, { label: 'Vitals' }] },
  '/labs': { title: 'Laboratory Results', crumbs: [{ label: 'MediSphere' }, { label: 'Lab Results' }] },
  '/predictions': { title: 'AI Risk Prediction Engine', crumbs: [{ label: 'MediSphere' }, { label: 'Predictions' }] },
  '/explain': { title: 'Prediction Explainability', crumbs: [{ label: 'MediSphere' }, { label: 'Predictions', to: '/predictions' }, { label: 'Explainability' }] },
  '/federated': { title: 'Federated Learning', crumbs: [{ label: 'MediSphere' }, { label: 'Federated Learning' }] },
  '/monitoring': { title: 'Real-Time Health Surveillance', crumbs: [{ label: 'MediSphere' }, { label: 'Monitoring' }] },
  '/alerts': { title: 'Clinical Alerts', crumbs: [{ label: 'MediSphere' }, { label: 'Alerts' }] },
  '/care-plans': { title: 'Precision Care Management', crumbs: [{ label: 'MediSphere' }, { label: 'Care Plans' }] },
  '/care-plans/new': { title: 'Generate Personalized Care Plan', crumbs: [{ label: 'MediSphere' }, { label: 'Care Plans', to: '/care-plans' }, { label: 'AI Generator' }] },
  '/population': { title: 'Population Health', crumbs: [{ label: 'MediSphere' }, { label: 'Population Health' }] },
  '/reports': { title: 'Clinical Reports', crumbs: [{ label: 'MediSphere' }, { label: 'Reports' }] },
  '/consent': { title: 'Patient Consent', crumbs: [{ label: 'MediSphere' }, { label: 'Consent' }] },
  '/audit': { title: 'Clinical Audit Trail', crumbs: [{ label: 'MediSphere' }, { label: 'Audit Logs' }] },
  '/status': { title: 'MediSphere System Status', crumbs: [{ label: 'MediSphere' }, { label: 'System Status' }] },
  '/settings': { title: 'Settings', crumbs: [{ label: 'MediSphere' }, { label: 'Settings' }] }
};

export function metaForPath(pathname: string): RouteMeta {
  if (routeMeta[pathname]) return routeMeta[pathname];
  if (pathname.startsWith('/patients/')) {
    return { title: 'Patient 360', crumbs: [{ label: 'MediSphere' }, { label: 'Patients', to: '/patients' }, { label: 'Patient 360' }] };
  }
  if (pathname.startsWith('/twins/')) {
    return { title: 'Digital Health Twin', crumbs: [{ label: 'MediSphere' }, { label: 'Health Twins', to: '/twins' }, { label: 'Twin Detail' }] };
  }
  if (pathname.startsWith('/alerts/')) {
    return { title: 'Alert Details', crumbs: [{ label: 'MediSphere' }, { label: 'Alerts', to: '/alerts' }, { label: 'Alert Details' }] };
  }
  if (pathname.endsWith('/review')) {
    return { title: 'Care Plan Review', crumbs: [{ label: 'MediSphere' }, { label: 'Care Plans', to: '/care-plans' }, { label: 'Provider Approval' }] };
  }
  if (pathname.endsWith('/adherence')) {
    return { title: 'Patient Adherence', crumbs: [{ label: 'MediSphere' }, { label: 'Care Plans', to: '/care-plans' }, { label: 'Adherence' }] };
  }
  return { title: 'MediSphere', crumbs: [{ label: 'MediSphere' }] };
}