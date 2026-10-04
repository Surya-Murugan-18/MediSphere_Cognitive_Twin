import React, { useEffect, useMemo, useState } from 'react';

import { useNavigate, useSearchParams } from 'react-router-dom';

import { useQuery } from '@tanstack/react-query';

import { PageHeader } from '../components/ui/PageHeader';

import { Card, CardHeader } from '../components/ui/Card';

import { Badge } from '../components/ui/Badge';

import { Button, LinkButton } from '../components/ui/Button';

import { FilterSelect } from '../components/ui/Field';

import { TableShell, Td, Th, Tr } from '../components/ui/Table';

import { EmptyState, SkeletonRows } from '../components/ui/States';

import { AiNotice } from '../components/ui/AiNotice';

import { getAlerts, getAlertCount } from '../api/alerts';

import { getPatients } from '../api/patients';

import { useAlertStream } from '../hooks/useAlertStream';


const severityTone = {
  HIGH: 'critical',
  MEDIUM: 'warning',
  LOW: 'info',
} as const;


const statusTone = {
  Unacknowledged: 'critical',
  Acknowledged: 'info',
  Escalated: 'warning',
  Resolved: 'healthy',
} as const;


export function Alerts() {
  const navigate = useNavigate();

  const [searchParams] = useSearchParams();


  // -----------------------------------------------------------------------
  // Filter state
  // -----------------------------------------------------------------------

  const [severity, setSeverity] = useState('All severities');

  const [status, setStatus] = useState('All statuses');

  const [patientId, setPatientId] = useState(
    searchParams.get('patientId') ?? ''
  );

  const [type, setType] = useState('All types');


  // -----------------------------------------------------------------------
  // Real-time alert stream
  // -----------------------------------------------------------------------

  useAlertStream();


  // -----------------------------------------------------------------------
  // Load patients separately.
  //
  // IMPORTANT:
  // We do NOT derive patient IDs from the filtered alerts response.
  // This prevents the patient dropdown from changing after a filter
  // has already been applied.
  // -----------------------------------------------------------------------

  const { data: patientsPage } = useQuery({
    queryKey: ['alerts-patient-selector'],
    queryFn: () => getPatients({}, 0, 100),
    staleTime: 2 * 60 * 1000,
  });


  const patients = patientsPage?.content ?? [];


  // -----------------------------------------------------------------------
  // Patient dropdown
  // -----------------------------------------------------------------------

  const patientOptions = useMemo(() => {
    return [
      'All patients',
      ...patients.map((patient) => patient.name),
    ];
  }, [patients]);


  // -----------------------------------------------------------------------
  // Patient name -> patient ID
  //
  // Example:
  // kishore -> P005
  // Aaron   -> P003
  // -----------------------------------------------------------------------

  const patientIdByName = useMemo(() => {
    const map = new Map<string, string>();

    patients.forEach((patient) => {
      map.set(patient.name, patient.id);
    });

    return map;
  }, [patients]);


  // -----------------------------------------------------------------------
  // Convert URL patientId into the correct dropdown value.
  // -----------------------------------------------------------------------

  useEffect(() => {
    const urlPatientId = searchParams.get('patientId');

    if (!urlPatientId) {
      return;
    }

    const patient = patients.find(
      (item) => item.id === urlPatientId
    );

    if (patient) {
      setPatientId(patient.id);
    }
  }, [patients, searchParams]);


  // -----------------------------------------------------------------------
  // Server-side filters
  // -----------------------------------------------------------------------

  const apiFilters = useMemo(() => {
    return {
      severity:
        severity !== 'All severities'
          ? severity
          : undefined,

      status:
        status !== 'All statuses'
          ? status
          : undefined,

      patientId:
        patientId !== ''
          ? patientId
          : undefined,

      type:
        type !== 'All types'
          ? type
          : undefined,

      page: 0,

      size: 50,
    };
  }, [
    severity,
    status,
    patientId,
    type,
  ]);


  // -----------------------------------------------------------------------
  // Alerts query
  // -----------------------------------------------------------------------

  const {
    data: alertsPage,
    isLoading,
    isFetching,
  } = useQuery({
    queryKey: ['alerts', apiFilters],

    queryFn: () => getAlerts(apiFilters),

    staleTime: 15_000,
  });


  // -----------------------------------------------------------------------
  // Unacknowledged count
  // -----------------------------------------------------------------------

  const { data: countData } = useQuery({
    queryKey: ['alert-count', 'Unacknowledged'],

    queryFn: () => getAlertCount('Unacknowledged'),

    staleTime: 30_000,

    refetchInterval: 30_000,
  });


  const rows = alertsPage?.content ?? [];

  const unackCount = countData?.count ?? 0;


  // -----------------------------------------------------------------------
  // Alert type options
  //
  // Alert types are derived from the current backend response.
  //
  // Example:
  // Vitals anomaly
  // Device
  // Risk change
  // -----------------------------------------------------------------------

  const typeOptions = useMemo(() => {
    const types = rows
      .map((alert) => alert.type)
      .filter(
        (value): value is string =>
          Boolean(value && value.trim())
      );

    return [
      'All types',
      ...Array.from(new Set(types)),
    ];
  }, [rows]);


  // -----------------------------------------------------------------------
  // Clear filters
  // -----------------------------------------------------------------------

  const clear = () => {
    setSeverity('All severities');

    setStatus('All statuses');

    setPatientId('');

    setType('All types');
  };


  // -----------------------------------------------------------------------
  // Patient dropdown display value
  //
  // FilterSelect displays patient NAME, while API uses patient ID.
  // -----------------------------------------------------------------------

  const selectedPatientName = useMemo(() => {
    if (!patientId) {
      return 'All patients';
    }

    const patient = patients.find(
      (item) => item.id === patientId
    );

    return patient?.name ?? 'All patients';
  }, [patientId, patients]);


  // -----------------------------------------------------------------------
  // Patient selection handler
  // -----------------------------------------------------------------------

  const handlePatientChange = (selectedName: string) => {
    if (selectedName === 'All patients') {
      setPatientId('');
      return;
    }

    const selectedId = patientIdByName.get(selectedName);

    setPatientId(selectedId ?? '');
  };


  return (
    <div>

      {/* ================================================================
          PAGE HEADER
          ================================================================ */}

      <PageHeader
        title="Clinical alerts"

        subtitle="Anomalies raised by the streaming detection service and routed to the responsible clinician"

        meta={
          <>
            <Badge tone="critical">
              {unackCount} unacknowledged
            </Badge>

            <Badge tone="neutral">
              {alertsPage?.totalElements ?? 0} total
            </Badge>
          </>
        }

        actions={
          <LinkButton to="/monitoring">
            Live monitoring
          </LinkButton>
        }
      />


      {/* ================================================================
          AI NOTICE
          ================================================================ */}

      <div className="mb-4">
        <AiNotice kind="alert" />
      </div>


      {/* ================================================================
          ALERT QUEUE
          ================================================================ */}

      <Card>

        <CardHeader
          title="Alert queue"
          description="Red is reserved for critical patient-safety events"
        />


        {/* ==============================================================
            FILTERS
            ============================================================== */}

        <div className="flex flex-wrap gap-3 border-b border-slate-200 p-4">

          {/* Severity */}

          <FilterSelect
            label="Severity"

            value={severity}

            onChange={setSeverity}

            options={[
              'All severities',
              'HIGH',
              'MEDIUM',
              'LOW',
            ]}
          />


          {/* Status */}

          <FilterSelect
            label="Status"

            value={status}

            onChange={setStatus}

            options={[
              'All statuses',
              'Unacknowledged',
              'Acknowledged',
              'Escalated',
              'Resolved',
            ]}
          />


          {/* Patient */}

          <FilterSelect
            label="Patient"

            value={selectedPatientName}

            onChange={handlePatientChange}

            options={patientOptions}
          />


          {/* Alert Type */}

          <FilterSelect
            label="Alert type"

            value={type}

            onChange={setType}

            options={typeOptions}
          />

        </div>


        {/* ==============================================================
            LOADING
            ============================================================== */}

        {isLoading ? (

          <SkeletonRows
            rows={5}
            cols={7}
          />

        ) : rows.length === 0 ? (

          /* ============================================================
             EMPTY STATE
             ============================================================ */

          <EmptyState
            title="No active alerts"

            description="No alerts match the selected filters. The monitoring stream remains active and new anomalies will appear here immediately."

            action={
              <Button
                size="sm"
                onClick={clear}
              >
                Clear filters
              </Button>
            }
          />

        ) : (

          /* ============================================================
             ALERT TABLE
             ============================================================ */

          <TableShell>

            <thead>

              <tr>

                <Th>
                  Severity
                </Th>

                <Th>
                  Patient
                </Th>

                <Th>
                  Event
                </Th>

                <Th>
                  AI analysis
                </Th>

                <Th>
                  Time
                </Th>

                <Th>
                  Status
                </Th>

                <Th>
                  Assigned provider
                </Th>

              </tr>

            </thead>


            <tbody>

              {rows.map((alert) => (

                <Tr
                  key={alert.id}

                  onClick={() =>
                    navigate(`/alerts/${alert.id}`)
                  }
                >

                  {/* Severity */}

                  <Td>

                    <Badge
                      tone={severityTone[alert.severity]}
                    >
                      {alert.severity}
                    </Badge>

                  </Td>


                  {/* Patient */}

                  <Td className="whitespace-nowrap font-medium text-slate-900">
                    {alert.patientName}
                  </Td>


                  {/* Event */}

                  <Td className="text-slate-700">
                    {alert.event}
                  </Td>


                  {/* AI analysis */}

                  <Td className="max-w-xs text-xs text-slate-500">
                    {alert.analysis}
                  </Td>


                  {/* Time */}

                  <Td className="whitespace-nowrap text-slate-500">

                    {alert.detectedAt
                      ? new Date(
                          alert.detectedAt
                        ).toLocaleTimeString(
                          'en-GB',
                          {
                            hour12: false,
                            hour: '2-digit',
                            minute: '2-digit',
                            second: '2-digit',
                          }
                        )
                      : '—'}

                  </Td>


                  {/* Status */}

                  <Td>

                    <Badge
                      tone={statusTone[alert.status]}
                    >
                      {alert.status}
                    </Badge>

                  </Td>


                  {/* Provider */}

                  <Td className="whitespace-nowrap text-slate-600">
                    {alert.assignedProvider ?? '—'}
                  </Td>

                </Tr>

              ))}

            </tbody>

          </TableShell>

        )}

      </Card>


      {/* ================================================================
          OPTIONAL FETCHING INDICATOR
          ================================================================ */}

      {isFetching && !isLoading && (
        <div className="mt-2 text-xs text-slate-400">
          Updating alerts…
        </div>
      )}

    </div>
  );
}