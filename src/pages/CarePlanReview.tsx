import React, { useState } from 'react';
import { Navigate, useNavigate, useParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { CheckCircle2Icon, ShieldCheckIcon } from 'lucide-react';
import { PageHeader } from '../components/ui/PageHeader';
import { Card, CardBody, CardHeader, DefinitionRow } from '../components/ui/Card';
import { Badge, StatusDot } from '../components/ui/Badge';
import { Button, LinkButton } from '../components/ui/Button';
import { Modal } from '../components/ui/Modal';
import { Field, TextInput } from '../components/ui/Field';
import { AiNotice } from '../components/ui/AiNotice';
import { Notice, SkeletonBlock } from '../components/ui/States';
import { useAuth } from '../hooks/useAuth';
import { getCarePlan, approveCarePlan, rejectCarePlan } from '../api/carePlans';

/**
 * CarePlanReview — Phase 6 wiring.
 *
 * - Loads plan via useQuery(['care-plan', planId], () => getCarePlan(planId))
 * - Provider name/ID come from useAuth().currentUser — NOT hardcoded
 * - Approve: useMutation(approveCarePlan) → invalidate queries → navigate to adherence
 * - Reject:  useMutation(rejectCarePlan)  → invalidate queries → navigate to /care-plans
 * - "Modify plan" navigates to /care-plans/:planId/edit (not /new)
 *
 * UI layout, colors, typography and component structure unchanged.
 */
export function CarePlanReview() {
  const { planId } = useParams<{ planId: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { currentUser } = useAuth();

  const [confirmOpen, setConfirmOpen] = useState(false);
  const [rejectOpen,  setRejectOpen]  = useState(false);
  const [rejectReason, setRejectReason] = useState('');

  // ── Load plan ─────────────────────────────────────────────────────────
  const { data: plan, isLoading, isError } = useQuery({
    queryKey: ['care-plan', planId],
    queryFn:  () => getCarePlan(planId!),
    enabled:  Boolean(planId),
    retry:    (count, err: any) => err?.response?.status !== 404 && count < 2,
  });

  // ── Approve mutation ──────────────────────────────────────────────────
  const { mutate: approve, isPending: approving } = useMutation({
    mutationFn: () => approveCarePlan(planId!),
    onSuccess: (updated) => {
      setConfirmOpen(false);
      queryClient.invalidateQueries({ queryKey: ['care-plan', planId] });
      queryClient.invalidateQueries({ queryKey: ['care-plans'] });
      queryClient.invalidateQueries({ queryKey: ['care-plan-stats'] });
      queryClient.invalidateQueries({ queryKey: ['active-care-plan'] });
      toast.success('Care plan approved', {
        description: `${updated.patientName} · ${updated.goal} · signed by ${currentUser?.name}.`,
      });
      navigate(`/care-plans/${planId}/adherence`);
    },
    onError: (err: any) => {
      setConfirmOpen(false);
      const message = err?.response?.data?.message ?? 'Approval failed. Please try again.';
      toast.error('Approval failed', { description: message });
    },
  });

  // ── Reject mutation ───────────────────────────────────────────────────
  const { mutate: reject, isPending: rejecting } = useMutation({
    mutationFn: () => rejectCarePlan(planId!, rejectReason),
    onSuccess: () => {
      setRejectOpen(false);
      queryClient.invalidateQueries({ queryKey: ['care-plan', planId] });
      queryClient.invalidateQueries({ queryKey: ['care-plans'] });
      queryClient.invalidateQueries({ queryKey: ['care-plan-stats'] });
      toast.error('Care plan rejected', {
        description: 'The AI draft was returned for revision and the decision was audited.',
      });
      navigate('/care-plans');
    },
    onError: (err: any) => {
      setRejectOpen(false);
      const message = err?.response?.data?.message ?? 'Rejection failed. Please try again.';
      toast.error('Rejection failed', { description: message });
    },
  });

  // ── Loading / error states ────────────────────────────────────────────
  if (isLoading) {
    return (
      <div className="space-y-4 p-6">
        <SkeletonBlock className="h-12 w-2/3" />
        <SkeletonBlock className="h-64" />
        <SkeletonBlock className="h-48" />
      </div>
    );
  }

  if (isError || !plan) {
    return <Navigate to="/care-plans" replace />;
  }

  // ── Derive decision state from plan status ────────────────────────────
  const decision: 'pending' | 'approved' | 'rejected' =
    plan.status === 'ACTIVE'   ? 'approved' :
    plan.status === 'REJECTED' ? 'rejected' :
    'pending';

  const approvedAt = plan.approvedAt
    ? new Date(plan.approvedAt).toLocaleString('en-US', { hour12: false })
    : new Date().toLocaleString('en-US', { hour12: false });

  // Provider identity from authenticated user — NOT hardcoded
  const providerName = currentUser?.name ?? '—';
  const providerId   = currentUser?.id   ?? '—';

  return (
    <div>
      <PageHeader
        title="Care plan review"
        subtitle={`${plan.patientName} · Plan ${plan.id} · provider approval required before any intervention starts`}
        backTo={{ to: '/care-plans', label: 'Back to care plans' }}
        meta={
          <>
            <Badge tone={decision === 'approved' ? 'healthy' : decision === 'rejected' ? 'critical' : 'warning'}>
              {decision === 'approved' ? 'Approved' : decision === 'rejected' ? 'Rejected' : 'Awaiting provider approval'}
            </Badge>
            <Badge tone="info">Goal: {plan.goal}</Badge>
          </>
        }
      />

      <div className="mb-4">
        <AiNotice kind="carePlan" />
      </div>

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-3">
        {/* Left column — recommendations + safety checks */}
        <div className="space-y-4 xl:col-span-2">
          <Card>
            <CardHeader
              title="AI recommendation summary"
              description="Generated from the patient's twin state and institutional guidelines"
            />
            <CardBody className="space-y-4">
              <dl>
                <DefinitionRow label="Patient"    value={`${plan.patientName} (${plan.patientId})`} />
                <DefinitionRow label="Goal"       value={plan.goal} />
                {plan.predictedOutcome && (
                  <>
                    <DefinitionRow
                      label="Current risk"
                      value={<span className="text-critical-600">{plan.predictedOutcome.before}{plan.predictedOutcome.unit}</span>}
                    />
                    <DefinitionRow
                      label="Predicted outcome after intervention"
                      value={<span className="text-teal-700">{plan.predictedOutcome.metric} → {plan.predictedOutcome.after}{plan.predictedOutcome.unit}</span>}
                    />
                  </>
                )}
              </dl>
              {plan.recommendations.length > 0 && (
                <div>
                  <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Interventions</p>
                  <ul className="space-y-2">
                    {plan.recommendations.slice(0, 3).map((rec) => (
                      <li key={rec.id} className="rounded-md border border-slate-200 p-3">
                        <p className="text-sm font-medium text-slate-900">{rec.title}</p>
                        <p className="mt-0.5 text-xs leading-relaxed text-slate-600">{rec.intervention}</p>
                        <p className="mt-1 text-2xs text-slate-400">{rec.evidence}</p>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </CardBody>
          </Card>

          {plan.safetyChecks.length > 0 && (
            <Card>
              <CardHeader
                title="Safety checks"
                description="Automated validation run before approval"
                icon={<ShieldCheckIcon className="h-4 w-4" />}
              />
              <CardBody className="pt-0">
                {plan.safetyChecks.map((check) => (
                  <div key={check.id} className="flex items-start justify-between gap-4 border-b border-slate-100 py-2.5 last:border-0">
                    <div className="flex items-start gap-2.5">
                      <span className="mt-1.5">
                        <StatusDot tone={check.tone as any} />
                      </span>
                      <div>
                        <p className="text-sm font-medium text-slate-800">{check.label}</p>
                        <p className="text-xs text-slate-500">{check.detail}</p>
                      </div>
                    </div>
                    <Badge tone={check.tone as any}>
                      {check.tone === 'healthy' ? 'Pass' : 'Review'}
                    </Badge>
                  </div>
                ))}
              </CardBody>
            </Card>
          )}

          <Card>
            <CardHeader title="Clinical guidelines applied" />
            <CardBody className="space-y-2 pt-0 text-sm text-slate-700">
              {['ADA Standards of Care 2026 — glycemic targets',
                'ACC/AHA hypertension management pathway',
                'Northside General chronic-care protocol v4'].map((g) => (
                <p key={g} className="flex items-center gap-2 border-b border-slate-100 py-2 last:border-0">
                  <CheckCircle2Icon className="h-4 w-4 text-teal-600" aria-hidden="true" />
                  {g}
                </p>
              ))}
            </CardBody>
          </Card>
        </div>

        {/* Right column — approval panel */}
        <div className="space-y-4">
          <Card className={decision === 'approved' ? 'border-teal-300' : 'border-amber-300'}>
            <CardHeader
              title="Provider authorization"
              description="A licensed provider must sign before this plan becomes active"
            />
            <CardBody className="space-y-3">
              {decision === 'approved' ? (
                <Notice tone="healthy" title="Plan approved">
                  Signed by {plan.approvedByName ?? providerName} at {approvedAt}. The intervention is now active and adherence tracking has started.
                </Notice>
              ) : decision === 'rejected' ? (
                <Notice tone="critical" title="Plan rejected">
                  The draft was returned to the AI care-plan generator for revision.
                  {plan.rejectionReason && <> Reason: {plan.rejectionReason}</>}
                </Notice>
              ) : (
                <Notice tone="warning" title="Awaiting provider approval">
                  Critical care-plan and medication actions require provider review and approval.
                </Notice>
              )}

              {/* Provider identity from useAuth() — not hardcoded */}
              <Field label="Provider name" htmlFor="providerName">
                <TextInput id="providerName" value={providerName} readOnly className="bg-slate-50 text-slate-600" />
              </Field>
              <Field label="Provider ID" htmlFor="providerIdField">
                <TextInput id="providerIdField" value={providerId} readOnly className="bg-slate-50 text-slate-600" />
              </Field>
              <Field label="Approval timestamp" htmlFor="approvalTime">
                <TextInput
                  id="approvalTime"
                  value={decision === 'approved' ? approvedAt : 'Pending'}
                  readOnly
                  className="bg-slate-50 text-slate-600"
                />
              </Field>

              <Button
                variant="approve"
                size="lg"
                className="w-full"
                disabled={decision !== 'pending' || approving}
                onClick={() => setConfirmOpen(true)}
              >
                {decision === 'approved' ? 'Approval confirmed' : 'Confirm approval'}
              </Button>

              <div className="grid grid-cols-2 gap-2">
                <Button
                  onClick={() => navigate(`/care-plans/${plan.id}/edit`)}
                  disabled={decision !== 'pending'}
                >
                  Modify plan
                </Button>
                <Button
                  variant="danger"
                  onClick={() => setRejectOpen(true)}
                  disabled={decision !== 'pending' || rejecting}
                >
                  Reject
                </Button>
              </div>

              {decision === 'approved' && (
                <LinkButton to={`/care-plans/${plan.id}/adherence`} className="w-full">
                  Track adherence
                </LinkButton>
              )}
            </CardBody>
          </Card>
        </div>
      </div>

      {/* Approval confirmation modal */}
      <Modal
        open={confirmOpen}
        onClose={() => setConfirmOpen(false)}
        title="Confirm provider approval"
        description="You are approving an AI-assisted care plan as the responsible clinician."
        footer={
          <>
            <Button onClick={() => setConfirmOpen(false)} disabled={approving}>Cancel</Button>
            <Button variant="approve" onClick={() => approve()} disabled={approving}>
              {approving ? 'Approving…' : 'Approve plan'}
            </Button>
          </>
        }
      >
        <p>
          Approving activates the interventions for {plan.patientName} and records your provider ID, name and
          timestamp in the clinical audit trail.
        </p>
      </Modal>

      {/* Reject modal */}
      <Modal
        open={rejectOpen}
        onClose={() => setRejectOpen(false)}
        title="Reject this care plan?"
        description="The plan returns to draft and no intervention is started."
        footer={
          <>
            <Button onClick={() => setRejectOpen(false)} disabled={rejecting}>Cancel</Button>
            <Button
              variant="danger"
              onClick={() => reject()}
              disabled={rejecting || !rejectReason.trim()}
            >
              {rejecting ? 'Rejecting…' : 'Reject plan'}
            </Button>
          </>
        }
      >
        <div className="space-y-3">
          <p>The rejection and your provider identity will be recorded in the audit trail.</p>
          <Field label="Reason for rejection (required)" htmlFor="rejectReason">
            <textarea
              id="rejectReason"
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand-500"
              rows={3}
              placeholder="Describe why this plan is being rejected…"
              value={rejectReason}
              onChange={(e) => setRejectReason(e.target.value)}
            />
          </Field>
        </div>
      </Modal>
    </div>
  );
}
