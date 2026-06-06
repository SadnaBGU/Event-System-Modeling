import { http, HttpResponse } from 'msw';
import type { PolicyBundle } from '../../types/policies';

// Per-scope stores. Reset on page reload.
const companyPolicies = new Map<string, PolicyBundle>();
const eventPolicies = new Map<string, PolicyBundle>();

const empty: PolicyBundle = { discount: null, purchase: null };

export const policyHandlers = [
  http.get('/api/companies/:companyId/policies', ({ params }) => {
    const b = companyPolicies.get(String(params.companyId)) ?? empty;
    return HttpResponse.json(b);
  }),
  http.put('/api/companies/:companyId/policies', async ({ request, params }) => {
    const body = (await request.json()) as PolicyBundle;
    companyPolicies.set(String(params.companyId), body);
    return HttpResponse.json(body);
  }),

  http.get('/api/events/:eventId/policies', ({ params }) => {
    const b = eventPolicies.get(String(params.eventId)) ?? empty;
    return HttpResponse.json(b);
  }),
  http.put('/api/events/:eventId/policies', async ({ request, params }) => {
    const body = (await request.json()) as PolicyBundle;
    eventPolicies.set(String(params.eventId), body);
    return HttpResponse.json(body);
  }),
];