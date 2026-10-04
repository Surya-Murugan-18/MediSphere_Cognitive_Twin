import React, { useEffect, useState } from 'react';
import { toast } from 'sonner';
import { DownloadIcon, FileTextIcon } from 'lucide-react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { PageHeader } from '../components/ui/PageHeader';
import { Card, CardBody, CardHeader } from '../components/ui/Card';
import { Badge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { Modal } from '../components/ui/Modal';
import { EmptyState, SkeletonBlock } from '../components/ui/States';
import { getReports, generateReport, downloadReport } from '../api/reports';
import { useWebSocket } from '../hooks/useWebSocket';
import type { ReportCard } from '../schemas/report.schema';

export function Reports() {
  const [preview, setPreview] = useState<ReportCard | null>(null);
  const queryClient = useQueryClient();
  const { subscribe, unsubscribe } = useWebSocket();

  // ── Load report cards ─────────────────────────────────────────────────
  const { data: reportCards, isLoading } = useQuery({
    queryKey: ['reports'],
    queryFn: getReports,
    staleTime: 30_000,
  });

  // ── Generate report mutation ──────────────────────────────────────────
  const { mutate: triggerGenerate, isPending: generating } = useMutation({
    mutationFn: (reportId: string) => generateReport(reportId),
    onSuccess: (result) => {
      toast.success('Report queued', {
        description: `Generation started · job ${result.jobId}`,
      });
      // Subscribe to WebSocket notification for this report
      const subId = subscribe(`/topic/reports.${result.reportId}`, (msg: any) => {
        if (msg?.status === 'COMPLETED') {
          toast.success('Report ready', { description: 'Export is now available.' });
          queryClient.invalidateQueries({ queryKey: ['reports'] });
        } else if (msg?.status === 'FAILED') {
          toast.error('Report generation failed', { description: msg.errorMessage ?? '' });
        }
        unsubscribe(subId);
      });
    },
    onError: () => {
      toast.error('Failed to queue report. Please try again.');
    },
  });

  if (isLoading) {
    return (
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
        {Array.from({ length: 6 }).map((_, i) => (
          <SkeletonBlock key={i} className="h-48" />
        ))}
      </div>
    );
  }

  const cards = reportCards ?? [];

  return (
    <div>
      <PageHeader
        title="Clinical reports"
        subtitle="Generate, preview and export governed clinical and model reports"
        meta={<Badge tone="neutral">Exports are audited and role-restricted</Badge>}
      />

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
        {cards.map((report) => (
          <Card key={report.id} className="flex flex-col">
            <CardHeader
              title={report.title}
              description={report.detail}
              icon={<FileTextIcon className="h-4 w-4" />}
            />
            <CardBody className="flex flex-1 flex-col">
              <div className="flex-1 rounded-md border border-slate-200 bg-slate-50 p-3">
                {report.ready ? (
                  <div className="space-y-1.5" aria-hidden="true">
                    <div className="h-2 w-1/3 rounded bg-slate-300" />
                    <div className="h-1.5 w-full rounded bg-slate-200" />
                    <div className="h-1.5 w-11/12 rounded bg-slate-200" />
                    <div className="mt-2 grid grid-cols-4 gap-1.5">
                      {Array.from({ length: 8 }).map((_, i) => (
                        <div key={i} className="h-3 rounded bg-white" />
                      ))}
                    </div>
                    <div className="h-1.5 w-2/3 rounded bg-slate-200" />
                  </div>
                ) : (
                  <p className="py-4 text-center text-xs text-slate-500">
                    No data — this report has not been generated yet.
                  </p>
                )}
              </div>
              <div className="mt-3 flex items-center justify-between gap-2">
                <p className="text-2xs text-slate-500">{report.lastRun} · {report.rows}</p>
                <Badge tone={report.ready ? 'healthy' : 'neutral'}>
                  {report.ready ? 'Ready' : 'No data'}
                </Badge>
              </div>
              <div className="mt-3 grid grid-cols-3 gap-2">
                <Button size="sm" onClick={() => setPreview(report)} disabled={!report.ready}>
                  View
                </Button>
                <Button
                  size="sm"
                  disabled={generating}
                  onClick={() => triggerGenerate(report.id)}
                >
                  Generate
                </Button>
                <Button
                  size="sm"
                  disabled={!report.ready}
                  onClick={() => {
                    downloadReport(report.id, 'pdf');
                    toast.success('Export started', {
                      description: `${report.title} · PDF export recorded in the audit log.`,
                    });
                  }}
                >
                  <DownloadIcon className="h-3.5 w-3.5" aria-hidden="true" />
                  Export
                </Button>
              </div>
            </CardBody>
          </Card>
        ))}
      </div>

      <Modal
        open={preview !== null}
        onClose={() => setPreview(null)}
        title={preview?.title ?? ''}
        description={preview ? `${preview.lastRun} · ${preview.rows}` : undefined}
        width="max-w-2xl"
        footer={
          <>
            <Button onClick={() => setPreview(null)}>Close</Button>
            <Button
              variant="primary"
              onClick={() => {
                if (preview) {
                  downloadReport(preview.id, 'pdf');
                  toast.success('Export started', {
                    description: `${preview.title} · PDF export recorded in the audit log.`,
                  });
                }
                setPreview(null);
              }}
            >
              Export PDF
            </Button>
          </>
        }
      >
        {preview ? (
          <div className="space-y-3">
            <p className="text-sm text-slate-600">{preview.detail}</p>
            <div className="rounded-md border border-slate-200 p-4">
              <p className="text-sm font-semibold text-slate-900">{preview.title}</p>
              <p className="text-2xs text-slate-500">
                MediSphere Cognitive Twin · generated {preview.lastRun}
              </p>
              <div className="mt-3 space-y-1.5" aria-hidden="true">
                {Array.from({ length: 6 }).map((_, i) => (
                  <div key={i} className="flex gap-2">
                    <div className="h-2 w-1/4 rounded bg-slate-200" />
                    <div className="h-2 flex-1 rounded bg-slate-100" />
                  </div>
                ))}
              </div>
            </div>
          </div>
        ) : (
          <EmptyState title="No report selected" description="Choose a report to preview." />
        )}
      </Modal>
    </div>
  );
}
