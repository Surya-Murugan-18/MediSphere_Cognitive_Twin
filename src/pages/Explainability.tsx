import { useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { PageHeader } from '../components/ui/PageHeader';
import { Card, CardBody, CardHeader, DefinitionRow } from '../components/ui/Card';
import { Badge } from '../components/ui/Badge';
import { LinkButton } from '../components/ui/Button';
import { AiNotice } from '../components/ui/AiNotice';
import { EmptyState, SkeletonBlock } from '../components/ui/States';
import { getExplanation } from '../api/explainability';

/**
 * Explainability page — Phase 4 API-wired.
 *
 * Reads ?predictionId= from URL (Phase 1 routing fix already in place).
 * When present: calls getExplanation(predictionId) and populates all panels.
 * When absent: shows EmptyState (Phase 1 behaviour preserved).
 *
 * No hardcoded patient or prediction data remains.
 * SHAP values come from the backend — never computed in React.
 * UI layout, card design, colors, and chart structure are unchanged.
 */
export function Explainability() {
  const [searchParams] = useSearchParams();
  const predictionId = searchParams.get('predictionId');

  // ── No predictionId: show empty state (Phase 1 behaviour) ─────────────────
  if (!predictionId) {
    return (
      <div>
        <PageHeader
          title="Prediction explainability"
          subtitle="SHAP feature attribution for AI risk predictions"
          backTo={{ to: '/predictions', label: 'Back to predictions' }}
        />
        <EmptyState
          title="No prediction selected"
          description="Select a prediction from the Predictions page to view its SHAP explanation."
          action={<LinkButton to="/predictions" variant="primary">Go to predictions</LinkButton>}
        />
      </div>
    );
  }

  return <ExplainabilityContent predictionId={predictionId} />;
}

// ── Inner component — only mounted when predictionId is present ───────────

function ExplainabilityContent({ predictionId }: { predictionId: string }) {
  const { data: explanation, isLoading, isError } = useQuery({
    queryKey: ['explanation', predictionId],
    queryFn:  () => getExplanation(predictionId),
    staleTime: 60_000,
    retry: (count, err: any) => err?.response?.status !== 404 && count < 2,
  });

  // ── Loading ───────────────────────────────────────────────────────────────
  if (isLoading) {
    return (
      <div>
        <PageHeader
          title="Prediction explainability"
          subtitle={`Prediction ${predictionId} · Loading explanation…`}
          backTo={{ to: '/predictions', label: 'Back to predictions' }}
        />
        <div className="space-y-4">
          <SkeletonBlock className="h-8 w-1/3" />
          <SkeletonBlock className="h-64" />
        </div>
      </div>
    );
  }

  // ── Error / Not found ─────────────────────────────────────────────────────
  if (isError || !explanation) {
    return (
      <div>
        <PageHeader
          title="Prediction explainability"
          subtitle="SHAP feature attribution"
          backTo={{ to: '/predictions', label: 'Back to predictions' }}
        />
        <EmptyState
          title="Explanation not found"
          description={`No explanation found for prediction ${predictionId}. The prediction may have been removed.`}
          action={<LinkButton to="/predictions" variant="primary">Back to predictions</LinkButton>}
        />
      </div>
    );
  }

  // ── Derived values ────────────────────────────────────────────────────────
  const factors       = explanation.shapFactors ?? [];
  const maxMagnitude  = factors.length > 0
    ? Math.max(...factors.map((f) => Math.abs(f.contribution)))
    : 1;
  const positives     = factors.filter((f) => f.contribution > 0);
  const negatives     = factors.filter((f) => f.contribution < 0);

  // ── Render ────────────────────────────────────────────────────────────────
  return (
    <div>
      <PageHeader
        title="Prediction explainability"
        subtitle={`Prediction ${predictionId} · SHAP feature attribution`}
        backTo={{ to: '/predictions', label: 'Back to prediction' }}
        meta={
          <>
            <Badge tone="info">Federated round {explanation.federatedRound}</Badge>
            <Badge tone={explanation.calibration === 'Calibrated' ? 'healthy' : 'warning'}>
              {explanation.calibration}
            </Badge>
          </>
        }
        actions={
          <>
            {/* patientId from API — never hardcoded */}
            <LinkButton to={`/care-plans/new?patientId=${explanation.patientId}`}>
              Create care plan
            </LinkButton>
            <LinkButton to="/predictions" variant="primary">
              Back to predictions
            </LinkButton>
          </>
        }
      />

      <div className="mb-4">
        <AiNotice kind="prediction" />
      </div>

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-3">
        {/* ── SHAP chart ── */}
        <Card className="xl:col-span-2">
          <CardHeader
            title="Feature contributions"
            description="Bars to the right increase predicted risk; bars to the left reduce it"
          />
          <CardBody>
            {factors.length === 0 ? (
              <p className="text-sm text-slate-500">No SHAP factors available for this prediction.</p>
            ) : (
              <div className="space-y-2.5">
                {factors.map((factor) => {
                  const width    = (Math.abs(factor.contribution) / maxMagnitude) * 50;
                  const positive = factor.contribution > 0;
                  return (
                    <div
                      key={factor.feature}
                      className="grid grid-cols-[140px_1fr_64px] items-center gap-3"
                    >
                      <div className="min-w-0">
                        <p className="truncate text-sm text-slate-800">{factor.feature}</p>
                        <p className="truncate text-2xs text-slate-400">{factor.value}</p>
                      </div>
                      <div className="relative h-5 rounded bg-slate-50">
                        <span className="absolute inset-y-0 left-1/2 w-px bg-slate-300" aria-hidden="true" />
                        <span
                          className={`absolute top-1/2 h-3.5 -translate-y-1/2 rounded-sm ${
                            positive ? 'bg-critical-500' : 'bg-teal-500'
                          }`}
                          style={
                            positive
                              ? { left: '50%', width: `${width}%` }
                              : { right: '50%', width: `${width}%` }
                          }
                          aria-hidden="true"
                        />
                      </div>
                      <span
                        className={`text-right text-sm font-semibold tabular ${
                          positive ? 'text-critical-600' : 'text-teal-700'
                        }`}
                      >
                        {positive ? '+' : ''}{factor.contribution}%
                      </span>
                    </div>
                  );
                })}
              </div>
            )}
            <div className="mt-4 flex items-center gap-4 text-2xs text-slate-500">
              <span className="flex items-center gap-1.5">
                <span className="h-2 w-2 rounded-sm bg-critical-500" aria-hidden="true" />
                Increases risk
              </span>
              <span className="flex items-center gap-1.5">
                <span className="h-2 w-2 rounded-sm bg-teal-500" aria-hidden="true" />
                Reduces risk
              </span>
            </div>
          </CardBody>
        </Card>

        {/* ── Right-hand info panels ── */}
        <div className="space-y-4">
          <Card>
            <CardHeader title="Model information" />
            <CardBody className="pt-0">
              <dl>
                <DefinitionRow label="Prediction ID"         value={predictionId} />
                <DefinitionRow label="Patient"               value={explanation.patientName} />
                <DefinitionRow label="Model version"         value={explanation.model} />
                <DefinitionRow label="Training round"        value={`${explanation.federatedRound} (federated)`} />
                <DefinitionRow label="Prediction"            value={`${explanation.value}%`} />
                <DefinitionRow label="Confidence"            value={`${explanation.confidence}%`} />
                <DefinitionRow label="Calibration status"    value={
                  <Badge tone={explanation.calibration === 'Calibrated' ? 'healthy' : 'warning'}>
                    {explanation.calibration}
                  </Badge>
                } />
                <DefinitionRow label="Explainability method" value="SHAP (TreeExplainer)" />
              </dl>
            </CardBody>
          </Card>

          <Card>
            <CardHeader title="Clinical evidence" />
            <CardBody className="space-y-2.5 pt-0">
              {(explanation.clinicalEvidence ?? []).length === 0 ? (
                <p className="text-sm text-slate-500">No evidence citations available.</p>
              ) : (
                explanation.clinicalEvidence.map((item) => (
                  <div key={item.guideline} className="border-b border-slate-100 py-2 last:border-0">
                    <p className="text-sm font-medium text-slate-800">{item.guideline}</p>
                    <p className="mt-0.5 text-xs text-slate-500">{item.detail}</p>
                  </div>
                ))
              )}
            </CardBody>
          </Card>
        </div>
      </div>

      {/* ── Natural-language summary ── */}
      <Card className="mt-4">
        <CardHeader title="Why did the model make this prediction?" />
        <CardBody className="space-y-3 text-sm leading-relaxed text-slate-700">
          {explanation.naturalLanguageSummary ? (
            <p>{explanation.naturalLanguageSummary}</p>
          ) : (
            <>
              <p>
                The model estimated a{' '}
                <strong className="tabular">{explanation.value}%</strong>{' '}
                {explanation.label.toLowerCase()} for this patient.
              </p>
              {positives.length > 0 && (
                <p>
                  Risk was driven upward mainly by{' '}
                  {positives.slice(0, 3).map((f) => `${f.feature} (${f.value})`).join(', ')}.
                </p>
              )}
              {negatives.length > 0 && (
                <p>
                  Contributions of{' '}
                  {negatives.map((f) => f.feature.toLowerCase()).join(' and ')}{' '}
                  partly offset the prediction.
                </p>
              )}
            </>
          )}
          <p className="text-xs text-slate-500">
            All contributions are computed locally on hospital data. No patient-level features
            leave this institution during federated training.
          </p>
        </CardBody>
      </Card>
    </div>
  );
}
