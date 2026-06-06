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
    api.post<CompanyDto>('/companies', body).then((r) => r.data),

  get: (companyId: string) =>
    api.get<CompanyDto>(`/companies/${companyId}`).then((r) => r.data),

  updateStatus: (companyId: string, body: CompanyStatusUpdate) =>
    api.patch<CompanyDto>(`/companies/${companyId}/status`, body).then((r) => r.data),

  salesReport: (companyId: string) =>
    api.get<SalesReportRow[]>(`/companies/${companyId}/reports/sales`).then((r) => r.data),
};
