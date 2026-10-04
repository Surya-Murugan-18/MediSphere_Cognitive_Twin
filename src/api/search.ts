import api from './client';
import { z } from 'zod';
import {
  SearchResultGroupSchema,
  type SearchResultGroup,
} from '../schemas/search.schema';

/**
 * Global search API service.
 * Per design.md §10 and tasks.md F7.9.
 *
 * GET /api/search?q=&categories=&limit=
 *
 * FR-SCH-02: server-side only — no in-memory static data
 * Min query length enforced by the caller (enabled: query.length >= 2)
 */

export async function search(
  query: string,
  categories?: string[],
  limit = 4,
): Promise<SearchResultGroup[]> {
  const params = new URLSearchParams();
  params.set('q', query);
  if (categories && categories.length > 0) {
    categories.forEach(c => params.append('categories', c));
  }
  params.set('limit', String(limit));

  const response = await api.get(`/api/search?${params.toString()}`);
  return z.array(SearchResultGroupSchema).parse(response.data);
}
