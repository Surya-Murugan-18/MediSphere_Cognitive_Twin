import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { Toaster } from 'sonner';
import { QueryProvider } from './context/QueryProvider';
import { AuthContextProvider } from './context/AuthContext';
import { useAuth } from './hooks/useAuth';
import { AppShell } from './components/layout/AppShell';
import { Login } from './pages/Login';
import { Dashboard } from './pages/Dashboard';
import { Patients } from './pages/Patients';
import { AddPatient } from './pages/AddPatient';
import { Patient360 } from './pages/Patient360';
import { HealthTwin } from './pages/HealthTwin';
import { Vitals } from './pages/Vitals';
import { LabResults } from './pages/LabResults';
import { Predictions } from './pages/Predictions';
import { Explainability } from './pages/Explainability';
import { FederatedLearning } from './pages/FederatedLearning';
import { Monitoring } from './pages/Monitoring';
import { Alerts } from './pages/Alerts';
import { AlertDetails } from './pages/AlertDetails';
import { CarePlans } from './pages/CarePlans';
import { CarePlanGenerator } from './pages/CarePlanGenerator';
import { CarePlanReview } from './pages/CarePlanReview';
import { Adherence } from './pages/Adherence';
import { PopulationHealth } from './pages/PopulationHealth';
import { Reports } from './pages/Reports';
import { Consent } from './pages/Consent';
import { AuditLogs } from './pages/AuditLogs';
import { SystemStatus } from './pages/SystemStatus';
import { Settings } from './pages/Settings';
import { SkeletonBlock } from './components/ui/States';

// ── Inner app — reads AuthContext ─────────────────────────────────────────

function AppInner() {
  const { isAuthenticated, isLoading, logout } = useAuth();

  // Show a minimal full-screen loader while session restoration is in progress.
  // This prevents a flash of the login page on page refresh when the user has
  // a valid refresh token.
  if (isLoading) {
    return (
      <div className="flex min-h-full w-full items-center justify-center bg-slate-50">
        <div className="w-64 space-y-3">
          <div className="flex items-center gap-3">
            <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-md bg-navy-900 text-sm font-bold text-white">
              M
            </span>
            <SkeletonBlock className="h-5 flex-1" />
          </div>
          <SkeletonBlock className="h-3 w-3/4" />
          <SkeletonBlock className="h-3 w-1/2" />
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Login />;
  }

  return (
    <Routes>
      <Route element={<AppShell onSignOut={logout} />}>
        <Route path="/" element={<Dashboard />} />
        <Route path="/patients" element={<Patients />} />
        <Route path="/patients/new" element={<AddPatient />} />
        <Route path="/patients/:patientId" element={<Patient360 />} />
        <Route path="/twins" element={<HealthTwin />} />
        <Route path="/twins/:patientId" element={<HealthTwin />} />
        <Route path="/vitals" element={<Vitals />} />
        <Route path="/vitals/:patientId" element={<Vitals />} />
        <Route path="/labs" element={<LabResults />} />
        <Route path="/predictions" element={<Predictions />} />
        <Route path="/explain" element={<Explainability />} />
        <Route path="/federated" element={<FederatedLearning />} />
        <Route path="/monitoring" element={<Monitoring />} />
        <Route path="/alerts" element={<Alerts />} />
        <Route path="/alerts/:alertId" element={<AlertDetails />} />
        <Route path="/care-plans" element={<CarePlans />} />
        <Route path="/care-plans/new" element={<CarePlanGenerator />} />
        {/* Phase 1 routing fix: edit existing care plan (previously incorrectly went to /new) */}
        <Route path="/care-plans/:planId/edit" element={<CarePlanGenerator />} />
        <Route path="/care-plans/:planId/review" element={<CarePlanReview />} />
        <Route path="/care-plans/:planId/adherence" element={<Adherence />} />
        <Route path="/population" element={<PopulationHealth />} />
        <Route path="/reports" element={<Reports />} />
        <Route path="/consent" element={<Consent />} />
        <Route path="/audit" element={<AuditLogs />} />
        <Route path="/status" element={<SystemStatus />} />
        <Route path="/settings" element={<Settings />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}

// ── Root app — providers ──────────────────────────────────────────────────

export function App() {
  return (
    <BrowserRouter>
      <QueryProvider>
        <AuthContextProvider>
          <Toaster position="bottom-right" richColors closeButton />
          <AppInner />
        </AuthContextProvider>
      </QueryProvider>
    </BrowserRouter>
  );
}
