import React, { useState } from 'react';
import { toast } from 'sonner';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { PageHeader } from '../components/ui/PageHeader';
import { Card, CardBody, CardHeader } from '../components/ui/Card';
import { Badge } from '../components/ui/Badge';
import { Button, LinkButton } from '../components/ui/Button';
import { Select, Toggle } from '../components/ui/Field';
import { TableShell, Td, Th, Tr } from '../components/ui/Table';
import { Notice, SkeletonBlock } from '../components/ui/States';
import { getPatientConsent, updateConsent, getConsentHistory } from '../api/consent';
import { getPatients } from '../api/patients';

export function Consent() {
  const [patientId, setPatientId] = useState('P001');
  const [showHistory, setShowHistory] = useState(false);
  const queryClient = useQueryClient();

  // Load the patient list for the selector dropdown
  const { data: patientsPage } = useQuery({
    queryKey: ['patients-consent-list'],
    queryFn: () => getPatients({}, 0, 100),
    staleTime: 60_000,
  });
  const patients = patientsPage?.content ?? [];

  // Load consent for the selected patient
  const { data: consent, isLoading } = useQuery({
    queryKey: ['consent', patientId],
    queryFn: () => getPatientConsent(patientId),
    enabled: !!patientId,
  });

  // Local toggle state initialised from API response
  const [consents, setConsents] = useState({ ehr: true, wearable: true, ai: true });

  // Sync local state when API data arrives
  React.useEffect(() => {
    if (consent) {
      setConsents({ ehr: consent.ehr, wearable: consent.wearable, ai: consent.ai });
    }
  }, [consent]);

  // Load history lazily — only when "View history" is toggled on
  const { data: history, isLoading: historyLoading } = useQuery({
    queryKey: ['consent-history', patientId],
    queryFn: () => getConsentHistory(patientId),
    enabled: showHistory && !!patientId,
    staleTime: 30_000,
  });

  // Update consent mutation
  const { mutate: saveConsent, isPending: saving } = useMutation({
    mutationFn: () => updateConsent(patientId, consents),
    onSuccess: () => {
      toast.success('Consent updated', {
        description: `Patient ${patientId} · change recorded in the clinical audit trail.`,
      });
      queryClient.invalidateQueries({ queryKey: ['consent', patientId] });
      queryClient.invalidateQueries({ queryKey: ['consent-history', patientId] });
    },
    onError: () => {
      toast.error('Failed to update consent. Please try again.');
    },
  });

  const selectedPatient = patients.find(p => p.id === patientId);
  const patientName = selectedPatient?.name ?? patientId;
  const consentComplete = consent ? (consent.ehr && consent.ai) : false;

  return (
    <div>
      <PageHeader
        title="Patient consent"
        subtitle={`${patientName} · ${patientId} · consent governs which data may be ingested and analysed`}
        actions={
          <div className="flex items-center gap-2">
            <label htmlFor="consent-patient" className="text-xs text-slate-500">
              Patient
            </label>
            <Select
              id="consent-patient"
              value={patientId}
              onChange={(e) => setPatientId(e.target.value)}
              className="h-9 w-48"
            >
              {patients.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name} ({p.id})
                </option>
              ))}
            </Select>
          </div>
        }
      />

      {!consentComplete && consent && (
        <div className="mb-4">
          <Notice tone="warning" title="Patient consent missing">
            No consent record exists for this patient. EHR ingestion, wearable streaming and AI
            risk analysis are all disabled until consent is captured.
          </Notice>
        </div>
      )}

      {isLoading ? (
        <SkeletonBlock className="h-48" />
      ) : (
        <div className="grid grid-cols-1 gap-4 xl:grid-cols-3">
          <Card className="xl:col-span-2">
            <CardHeader
              title="Current consent"
              description="Each item can be granted or withdrawn by the patient at any time"
            />
            <CardBody className="pt-0">
              <Toggle
                id="c-ehr"
                checked={consents.ehr}
                onChange={(v) => setConsents((c) => ({ ...c, ehr: v }))}
                label="EHR data access"
                description="Conditions, medications, encounters and laboratory results via FHIR R4."
              />
              <Toggle
                id="c-wearable"
                checked={consents.wearable}
                onChange={(v) => setConsents((c) => ({ ...c, wearable: v }))}
                label="Wearable data access"
                description="Continuous vitals streaming into the Kafka monitoring pipeline."
              />
              <Toggle
                id="c-ai"
                checked={consents.ai}
                onChange={(v) => setConsents((c) => ({ ...c, ai: v }))}
                label="AI risk analysis"
                description="Federated risk prediction and AI care-plan recommendations."
              />
              <div className="mt-4 flex flex-wrap gap-2">
                <Button variant="primary" onClick={() => saveConsent()} disabled={saving}>
                  {saving ? 'Saving…' : 'Update consent'}
                </Button>
                <Button onClick={() => setShowHistory((v) => !v)}>
                  {showHistory ? 'Hide history' : 'View history'}
                </Button>
                <LinkButton to="/audit">Open audit trail</LinkButton>
              </div>
            </CardBody>
          </Card>

          <Card>
            <CardHeader title="Consent status" />
            <CardBody className="space-y-2.5 pt-0">
              {[
                { label: 'EHR data access',    granted: consents.ehr },
                { label: 'Wearable data access', granted: consents.wearable },
                { label: 'AI risk analysis',   granted: consents.ai },
              ].map((item) => (
                <div
                  key={item.label}
                  className="flex items-center justify-between gap-3 border-b border-slate-100 py-2 last:border-0"
                >
                  <span className="text-sm text-slate-700">{item.label}</span>
                  <Badge tone={item.granted ? 'healthy' : 'warning'} dot>
                    {item.granted ? 'Granted' : 'Not granted'}
                  </Badge>
                </div>
              ))}
              <p className="pt-2 text-2xs text-slate-500">
                Withdrawing consent halts the corresponding data flow immediately and is propagated
                to the twin service.
              </p>
            </CardBody>
          </Card>
        </div>
      )}

      {showHistory && (
        <Card className="mt-4">
          <CardHeader
            title="Consent history"
            description="Immutable record of every consent decision"
          />
          {historyLoading ? (
            <SkeletonBlock className="h-32 m-4" />
          ) : (
            <TableShell>
              <thead>
                <tr>
                  <Th>Date</Th>
                  <Th>Consent type</Th>
                  <Th>Status</Th>
                  <Th>Updated by</Th>
                </tr>
              </thead>
              <tbody>
                {(history ?? []).map((entry) => (
                  <Tr key={entry.id}>
                    <Td className="font-mono-clinical text-xs text-slate-500">
                      {entry.date ? new Date(entry.date).toLocaleString('en-GB') : '—'}
                    </Td>
                    <Td className="text-slate-800">{entry.type}</Td>
                    <Td>
                      <Badge tone={entry.status === 'Granted' ? 'healthy' : 'neutral'}>
                        {entry.status}
                      </Badge>
                    </Td>
                    <Td className="text-slate-600">{entry.updatedBy ?? '—'}</Td>
                  </Tr>
                ))}
              </tbody>
            </TableShell>
          )}
        </Card>
      )}
    </div>
  );
}
