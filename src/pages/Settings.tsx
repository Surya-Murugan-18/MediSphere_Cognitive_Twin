import React, { useEffect, useState } from 'react';
import { toast } from 'sonner';
import { useQuery, useMutation } from '@tanstack/react-query';
import { PageHeader } from '../components/ui/PageHeader';
import { Card, CardBody, CardHeader, DefinitionRow } from '../components/ui/Card';
import { Badge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { Field, Select, TextInput, Toggle } from '../components/ui/Field';
import { Tabs } from '../components/ui/Tabs';
import { PermissionDenied, SkeletonBlock } from '../components/ui/States';
import {
  getProviderProfile,
  updateProviderProfile,
  getNotificationPrefs,
  updateNotificationPrefs,
  getFhirConfig,
  updateFhirConfig,
} from '../api/settings';
import { getSystemServices } from '../api/system';
import { useAuth } from '../hooks/useAuth';

const tabs = [
  { id: 'profile',       label: 'Profile' },
  { id: 'notifications', label: 'Notifications' },
  { id: 'security',      label: 'Security' },
  { id: 'roles',         label: 'Role & Permissions' },
  { id: 'integration',   label: 'Integration' },
  { id: 'fhir',          label: 'FHIR Configuration' },
  { id: 'wearables',     label: 'Wearable Connections' },
  { id: 'audit',         label: 'Audit Settings' },
];

export function Settings() {
  const [tab, setTab] = useState('profile');
  const { currentUser } = useAuth();
  const isAdmin = currentUser?.role === 'ADMIN';

  // ── Profile ───────────────────────────────────────────────────────────
  const { data: profile, isLoading: profileLoading } = useQuery({
    queryKey: ['provider-profile'],
    queryFn: getProviderProfile,
    staleTime: 60_000,
  });

  const [profileForm, setProfileForm] = useState({ name: '', specialty: '', facility: '' });
  useEffect(() => {
    if (profile) {
      setProfileForm({
        name:      profile.name ?? '',
        specialty: profile.specialty ?? '',
        facility:  profile.facility ?? '',
      });
    }
  }, [profile]);

  const { mutate: saveProfile, isPending: savingProfile } = useMutation({
    mutationFn: () => updateProviderProfile(profileForm),
    onSuccess: () => toast.success('Profile updated'),
    onError: ()  => toast.error('Failed to update profile'),
  });

  // ── Notification prefs ────────────────────────────────────────────────
  const { data: notifPrefs } = useQuery({
    queryKey: ['notification-prefs'],
    queryFn: getNotificationPrefs,
    enabled: tab === 'notifications',
    staleTime: 60_000,
  });

  const [notifications, setNotifications] = useState({
    critical: true, risk: true, approvals: true, system: false,
  });
  useEffect(() => {
    if (notifPrefs) setNotifications(notifPrefs);
  }, [notifPrefs]);

  const { mutate: saveNotif } = useMutation({
    mutationFn: (data: typeof notifications) => updateNotificationPrefs(data),
    onSuccess: () => toast.success('Notification preferences saved'),
    onError:   () => toast.error('Failed to save notification preferences'),
  });

  // ── FHIR config (ADMIN only) ──────────────────────────────────────────
  const { data: fhirConfig } = useQuery({
    queryKey: ['fhir-config'],
    queryFn: getFhirConfig,
    enabled: tab === 'fhir' && isAdmin,
    staleTime: 60_000,
  });

  const [fhirForm, setFhirForm] = useState({
    mode: 'mock', baseUrl: '', version: 'R4',
    authType: 'SMART on FHIR', syncIntervalMinutes: 5,
  });
  useEffect(() => {
    if (fhirConfig) {
      setFhirForm({
        mode: fhirConfig.mode,
        baseUrl: fhirConfig.baseUrl,
        version: fhirConfig.version,
        authType: fhirConfig.authType,
        syncIntervalMinutes: fhirConfig.syncIntervalMinutes,
      });
    }
  }, [fhirConfig]);

  const { mutate: saveFhir, isPending: savingFhir } = useMutation({
    mutationFn: () => updateFhirConfig(fhirForm),
    onSuccess: () => toast.success('FHIR configuration saved'),
    onError:   () => toast.error('Failed to save FHIR configuration'),
  });

  // ── Integration tab: reuse system services ────────────────────────────
  const { data: systemServices } = useQuery({
    queryKey: ['system-services'],
    queryFn: getSystemServices,
    enabled: tab === 'integration',
    staleTime: 30_000,
  });

  return (
    <div>
      <PageHeader
        title="Settings"
        subtitle="Manage your clinician profile, notification routing and platform integrations"
      />

      <Card>
        <Tabs tabs={tabs} active={tab} onChange={setTab} className="px-2" />

        {tab === 'profile' && (
          profileLoading ? (
            <CardBody><SkeletonBlock className="h-40" /></CardBody>
          ) : (
            <CardBody className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <Field label="Full name" htmlFor="s-name">
                <TextInput
                  id="s-name"
                  value={profileForm.name}
                  onChange={(e) => setProfileForm(f => ({ ...f, name: e.target.value }))}
                />
              </Field>
              <Field label="Provider ID" htmlFor="s-provider">
                <TextInput
                  id="s-provider"
                  value={profile?.id ?? ''}
                  readOnly
                  className="bg-slate-50 text-slate-500"
                />
              </Field>
              <Field label="Specialty" htmlFor="s-specialty">
                <Select
                  id="s-specialty"
                  value={profileForm.specialty}
                  onChange={(e) => setProfileForm(f => ({ ...f, specialty: e.target.value }))}
                >
                  <option value="Cardiology">Cardiology</option>
                  <option value="Endocrinology">Endocrinology</option>
                  <option value="Internal Medicine">Internal Medicine</option>
                  <option value="Nephrology">Nephrology</option>
                  <option value="Pulmonology">Pulmonology</option>
                </Select>
              </Field>
              <Field label="Primary facility" htmlFor="s-facility">
                <Select
                  id="s-facility"
                  value={profileForm.facility}
                  onChange={(e) => setProfileForm(f => ({ ...f, facility: e.target.value }))}
                >
                  <option value="Hospital A — Northside General">Hospital A — Northside General</option>
                  <option value="Hospital B — Lakeview Medical">Hospital B — Lakeview Medical</option>
                  <option value="Hospital C — Riverbend Clinic">Hospital C — Riverbend Clinic</option>
                </Select>
              </Field>
              <div className="sm:col-span-2">
                <Button
                  variant="primary"
                  disabled={savingProfile}
                  onClick={() => saveProfile()}
                >
                  {savingProfile ? 'Saving…' : 'Save changes'}
                </Button>
              </div>
            </CardBody>
          )
        )}

        {tab === 'notifications' && (
          <CardBody>
            <Toggle
              id="n-critical"
              checked={notifications.critical}
              onChange={(v) => { const n = { ...notifications, critical: v }; setNotifications(n); saveNotif(n); }}
              label="Critical alerts"
              description="Immediate push for HIGH severity patient-safety events."
            />
            <Toggle
              id="n-risk"
              checked={notifications.risk}
              onChange={(v) => { const n = { ...notifications, risk: v }; setNotifications(n); saveNotif(n); }}
              label="Risk updates"
              description="Notify when a model output moves a patient into a new risk band."
            />
            <Toggle
              id="n-approvals"
              checked={notifications.approvals}
              onChange={(v) => { const n = { ...notifications, approvals: v }; setNotifications(n); saveNotif(n); }}
              label="Care plan approvals"
              description="Plans awaiting your provider signature."
            />
            <Toggle
              id="n-system"
              checked={notifications.system}
              onChange={(v) => { const n = { ...notifications, system: v }; setNotifications(n); saveNotif(n); }}
              label="System notifications"
              description="Federated rounds, integration and device status changes."
            />
          </CardBody>
        )}

        {tab === 'security' && (
          <CardBody className="max-w-xl space-y-4">
            <dl>
              <DefinitionRow label="Multi-factor authentication" value={<Badge tone="healthy">Enabled</Badge>} />
              <DefinitionRow label="Session timeout" value="15 minutes of inactivity" />
              <DefinitionRow label="Last sign-in" value={profile?.lastSignIn ? new Date(profile.lastSignIn).toLocaleString('en-GB') : '—'} />
              <DefinitionRow label="Patient data encryption" value={<Badge tone="healthy">AES-256 at rest · TLS 1.3 in transit</Badge>} />
            </dl>
            <Button onClick={() => toast.success('Password reset email sent')}>Reset password</Button>
          </CardBody>
        )}

        {tab === 'roles' && (
          <CardBody className="max-w-2xl space-y-4">
            <dl>
              <DefinitionRow label="Assigned role"       value={<Badge tone="info">{currentUser?.role ?? '—'}</Badge>} />
              <DefinitionRow label="Patient records"     value="Read / write for assigned panel" />
              <DefinitionRow label="Care plan approval"  value={<Badge tone="healthy">Permitted</Badge>} />
              <DefinitionRow label="Model configuration" value={<Badge tone="neutral">Not permitted</Badge>} />
              <DefinitionRow label="Bulk data export"    value={<Badge tone="neutral">Not permitted</Badge>} />
            </dl>
            <div className="rounded-lg border border-slate-200">
              <PermissionDenied detail="Editing role definitions requires the Healthcare Administrator role. Contact your system administrator to request a change." />
            </div>
          </CardBody>
        )}

        {tab === 'integration' && (
          <CardBody className="grid grid-cols-1 gap-3 md:grid-cols-2">
            {(systemServices ?? []).map((svc) => (
              <div
                key={svc.name}
                className="flex items-center justify-between rounded-md border border-slate-200 px-3 py-2.5"
              >
                <span className="text-sm text-slate-800">{svc.name}</span>
                <Badge tone={svc.tone as any} dot>{svc.state}</Badge>
              </div>
            ))}
          </CardBody>
        )}

        {tab === 'fhir' && (
          isAdmin ? (
            <CardBody className="grid max-w-2xl grid-cols-1 gap-4 sm:grid-cols-2">
              <Field label="FHIR base URL" htmlFor="fhir-url">
                <TextInput
                  id="fhir-url"
                  value={fhirForm.baseUrl}
                  onChange={(e) => setFhirForm(f => ({ ...f, baseUrl: e.target.value }))}
                />
              </Field>
              <Field label="FHIR version" htmlFor="fhir-version">
                <Select
                  id="fhir-version"
                  value={fhirForm.version}
                  onChange={(e) => setFhirForm(f => ({ ...f, version: e.target.value }))}
                >
                  <option value="R4">R4</option>
                  <option value="R4B">R4B</option>
                </Select>
              </Field>
              <Field label="Authorization" htmlFor="fhir-auth">
                <Select
                  id="fhir-auth"
                  value={fhirForm.authType}
                  onChange={(e) => setFhirForm(f => ({ ...f, authType: e.target.value }))}
                >
                  <option value="SMART on FHIR">SMART on FHIR</option>
                  <option value="OAuth 2.0 client credentials">OAuth 2.0 client credentials</option>
                </Select>
              </Field>
              <Field label="Sync interval" htmlFor="fhir-sync">
                <Select
                  id="fhir-sync"
                  value={String(fhirForm.syncIntervalMinutes)}
                  onChange={(e) => setFhirForm(f => ({ ...f, syncIntervalMinutes: Number(e.target.value) }))}
                >
                  <option value="0">Real time</option>
                  <option value="5">Every 5 minutes</option>
                  <option value="60">Hourly</option>
                </Select>
              </Field>
              <div className="sm:col-span-2">
                <Button
                  variant="primary"
                  disabled={savingFhir}
                  onClick={() => saveFhir()}
                >
                  {savingFhir ? 'Saving…' : 'Save configuration'}
                </Button>
              </div>
            </CardBody>
          ) : (
            <CardBody>
              <PermissionDenied detail="FHIR configuration requires the Administrator role." />
            </CardBody>
          )
        )}

        {tab === 'wearables' && (
          <CardBody className="space-y-2">
            {[
              { name: 'Smart Watch gateway',          status: 'Online', tone: 'healthy' as const },
              { name: 'Continuous glucose monitors',  status: 'Online', tone: 'healthy' as const },
              { name: 'Home BP cuffs',                status: '14 offline', tone: 'warning' as const },
            ].map((device) => (
              <div
                key={device.name}
                className="flex items-center justify-between rounded-md border border-slate-200 px-3 py-2.5"
              >
                <span className="text-sm text-slate-800">{device.name}</span>
                <Badge tone={device.tone} dot>{device.status}</Badge>
              </div>
            ))}
          </CardBody>
        )}

        {tab === 'audit' && (
          <CardBody className="max-w-xl">
            <dl>
              <DefinitionRow label="Audit logging"    value={<Badge tone="healthy" dot>Active</Badge>} />
              <DefinitionRow label="Retention period" value="7 years (regulatory minimum)" />
              <DefinitionRow label="Export access"    value="Healthcare Administrator only" />
              <DefinitionRow label="Tamper protection" value={<Badge tone="healthy">Write-ahead, append-only</Badge>} />
            </dl>
            <p className="mt-3 text-xs text-slate-500">
              Audit settings are governed by institutional policy and cannot be reduced from this screen.
            </p>
          </CardBody>
        )}
      </Card>
    </div>
  );
}
