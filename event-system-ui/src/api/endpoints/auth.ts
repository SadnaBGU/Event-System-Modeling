import { api } from '../client';
import type {
  LoginRequest,
  LoginResponse,
  MemberDto,
  RegisterRequest,
} from '../../types/api';

export const authApi = {
  login: (body: LoginRequest) =>
    api.post<LoginResponse>('/auth/login', body).then((r) => r.data),

  register: (body: RegisterRequest) =>
    api.post<MemberDto>('/auth/register', body).then((r) => r.data),

  getMember: (memberId: string) =>
    api.get<MemberDto>(`/members/${memberId}`).then((r) => r.data),

  deleteMember: (memberId: string) =>
    api.delete<void>(`/members/${memberId}`).then((r) => r.data),
};
