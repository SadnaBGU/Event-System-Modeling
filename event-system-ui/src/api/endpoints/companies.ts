import { api } from '../client';
import type {
  CompanyDto,
  CompanyStatusUpdate,
  CreateCompanyRequest,
  SalesReportRow,
} from '../../types/api';

export const companiesApi = {
  list: () => api.get<CompanyDto[]>('/companies').then((r) => r.data),

  create: (body: CreateCompanyRequest) =>
    api
      .post<void>('/companies', body)
      .then((r) => {
        const loc = r.headers['location'] ?? r.headers.location ?? '';
        const companyId = String(loc).split('/').pop() ?? '';
        return { companyId, companyName: body.companyName, status: 'ACTIVE', contactDetails: body.contactDetails } satisfies CompanyDto;
      }),

  get: (companyId: string) =>
    api.get<CompanyDto>(`/companies/${companyId}`).then((r) => r.data),

  updateStatus: (companyId: string, body: CompanyStatusUpdate) =>
    api.patch<void>(`/companies/${companyId}/status`, body).then(() => undefined),

  salesReport: (companyId: string) =>
    api
      .get<{
        companyId: string;
        reportGeneratedAt: string;
        grandTotalRevenue: number;
        events: Array<{ eventId: string; eventName: string; ticketsSold: number; revenue: number }>;
      }>(`/companies/${companyId}/reports/sales`)
      .then((r) =>
        (r.data.events ?? []).map<SalesReportRow>((e) => ({
          eventId: e.eventId,
          eventName: e.eventName,
          ticketsSold: e.ticketsSold,
          grossRevenue: e.revenue,
        })),
      ),
};
