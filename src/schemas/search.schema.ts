import { z } from 'zod';

export const SearchResultItemSchema = z.object({
  id: z.string(),
  label: z.string(),
  detail: z.string(),
  to: z.string(),
});

export const SearchResultGroupSchema = z.object({
  category: z.string(),
  results: z.array(SearchResultItemSchema),
});

export type SearchResultItem = z.infer<typeof SearchResultItemSchema>;
export type SearchResultGroup = z.infer<typeof SearchResultGroupSchema>;
