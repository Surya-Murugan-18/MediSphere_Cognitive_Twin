import api from './client';
import {
  ExplanationSchema,
  type Explanation,
} from '../schemas/prediction.schema';

/**
 * Explainability API service — F4.1
 *
 * Fetches the full SHAP explanation for a prediction from the backend.
 * The frontend never computes or fabricates SHAP values.
 */

// ── Full SHAP explanation for a prediction ────────────────────────────────

export async function getExplanation(predictionId: string): Promise<Explanation> {
  const response = await api.get(`/api/explainability/${predictionId}`);
  return ExplanationSchema.parse(response.data);
}
