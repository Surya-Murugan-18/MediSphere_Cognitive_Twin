import React, { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { SearchIcon } from 'lucide-react';
import { search } from '../../api/search';

/**
 * Global search component — server-side API search.
 *
 * Per tasks.md F7.9 and FR-SCH-01 through FR-SCH-04:
 *  - useQuery with enabled: query.length >= 2
 *  - Debounced 300ms before querying
 *  - Results grouped by category, max 4 per group
 *  - Cleanup of debounce on unmount
 *  - Loading state shown while fetching
 */

export function GlobalSearch() {
  const [query,   setQuery]   = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');
  const [open,    setOpen]    = useState(false);
  const navigate     = useNavigate();
  const containerRef = useRef<HTMLDivElement>(null);
  const debounceRef  = useRef<ReturnType<typeof setTimeout> | null>(null);

  // 300ms debounce — cleanup on unmount
  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => {
      setDebouncedQuery(query.trim());
    }, 300);
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
  }, [query]);

  const { data: groups, isFetching } = useQuery({
    queryKey: ['search', debouncedQuery],
    queryFn: () => search(debouncedQuery),
    enabled: debouncedQuery.length >= 2,
    staleTime: 30_000,
  });

  const go = (to: string) => {
    navigate(to);
    setQuery('');
    setDebouncedQuery('');
    setOpen(false);
  };

  const showDropdown = open && query.trim() !== '';

  return (
    <div
      ref={containerRef}
      className="relative w-full max-w-md"
      onBlur={(e) => {
        if (!containerRef.current?.contains(e.relatedTarget as Node)) setOpen(false);
      }}
    >
      <SearchIcon
        className="pointer-events-none absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400"
        aria-hidden="true"
      />
      <input
        type="search"
        value={query}
        onChange={(e) => { setQuery(e.target.value); setOpen(true); }}
        onFocus={() => setOpen(true)}
        placeholder="Search patients, FHIR ID, alerts, care plans…"
        aria-label="Global clinical search"
        className="h-9 w-full rounded-md border border-slate-300 bg-white pl-8 pr-3 text-sm text-slate-900 placeholder:text-slate-400 transition-colors duration-150 ease-out focus:border-brand-500 focus:outline-none"
      />

      {showDropdown && (
        <div className="absolute left-0 right-0 top-11 z-40 max-h-96 overflow-y-auto rounded-lg border border-slate-200 bg-white shadow-panel">
          {isFetching ? (
            <p className="px-4 py-3 text-xs text-slate-500">Searching…</p>
          ) : debouncedQuery.length < 2 ? null
          : !groups || groups.length === 0 ? (
            <p className="px-4 py-6 text-center text-xs text-slate-500">
              No results for "{query}".
            </p>
          ) : (
            groups.map((group) => (
              <div key={group.category} className="border-b border-slate-100 last:border-0">
                <p className="bg-slate-50 px-3 py-1.5 text-2xs font-semibold uppercase tracking-wide text-slate-500">
                  {group.category}
                </p>
                <ul>
                  {group.results.slice(0, 4).map((result) => (
                    <li key={`${group.category}-${result.id}`}>
                      <button
                        type="button"
                        onMouseDown={(e) => e.preventDefault()}
                        onClick={() => go(result.to)}
                        className="flex w-full flex-col items-start px-3 py-2 text-left transition-colors duration-150 ease-out hover:bg-brand-50"
                      >
                        <span className="text-sm text-slate-800">{result.label}</span>
                        <span className="text-xs text-slate-500">{result.detail}</span>
                      </button>
                    </li>
                  ))}
                </ul>
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
}
