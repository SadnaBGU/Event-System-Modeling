import { api } from '../client';
import type {
  CompanyAppointmentTreeDto,
  CompanyDto,
  CompanyStatusUpdate,
  CreateCompanyRequest,
  SalesReportRow,
} from '../../types/api';

export interface CompanyEventListItem {
  eventId: string;
  eventName: string;
  status: string;
  salesMethod: string;
  category?: string;
  location?: string;
  dates: string[];
}

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

  events: (companyId: string) =>
    api.get<CompanyEventListItem[]>(`/companies/${companyId}/events`).then((r) => r.data),

  appointmentTree: (companyId: string) =>
    api.get<CompanyAppointmentTreeDto>(`/companies/${companyId}/appointments/tree`).then((r) => r.data),

  updateStatus: (companyId: string, body: CompanyStatusUpdate) =>
    api.patch<void>(`/companies/${companyId}/status`, body).then(() => undefined),

  invitations: () =>
    api
      .get<{ companyId: string; companyName: string; roleType: string }[]>('/companies/mine/invitations')
      .then((r) => r.data),

  acceptInvitation: (companyId: string, memberId: string) =>
    api
      .post<void>(`/companies/${companyId}/roles/${memberId}/accept`)
      .then(() => undefined),

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
