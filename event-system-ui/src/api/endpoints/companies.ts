import { api } from '../client';
import type {
  CompanyDto,
  CompanyStatusUpdate,
  CreateCompanyRequest,
  SalesReportRow,
} from '../../types/api';

// NOTE: backend currently exposes no GET /companies and no GET /companies/{id}.
// The list() and get() functions below resolve to empty/null so company screens
// render their empty states instead of crashing. Wire to real endpoints once the
// backend grows them.
export const companiesApi = {
  list: async (): Promise<CompanyDto[]> => [],

  create: (body: CreateCompanyRequest) =>
    api
      .post<void>('/companies', body)
      .then((r) => {
        const loc = r.headers['location'] ?? r.headers.location ?? '';
        const companyId = String(loc).split('/').pop() ?? '';
        return { companyId, companyName: body.companyName, status: 'ACTIVE', contactDetails: body.contactDetails } satisfies CompanyDto;
      }),

  get: async (_companyId: string): Promise<CompanyDto | null> => {
    void _companyId;
    return null;
  },

  updateStatus: (companyId: string, body: CompanyStatusUpdate) =>
    api.patch<void>(`/companies/${companyId}/status`, body).then(() => undefined),

  salesReport: (companyId: string) =>
    api
      .get<{ companyId: string; reportGeneratedAt: string; grandTotalRevenue: number; events: SalesReportRow[] }>(
        `/companies/${companyId}/reports/sales`,
      )
      .then((r) => r.data.events ?? []),
};
