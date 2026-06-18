import { useQuery } from '@tanstack/react-query';
import { rolesApi } from '../api/endpoints/roles';
import { useAuthStore } from './authStore';
import type { Permission } from '../types/api';

export interface CompanyPermissions {
  /** True while the roles are still being fetched. */
  loading: boolean;
  /** Current member is the founder/an owner of this company. */
  isOwner: boolean;
  /** Current member is a manager of this company. */
  isManager: boolean;
  /** Owner, manager, or platform admin — i.e. may see management UI at all. */
  canManage: boolean;
  /** Platform admin (sees everything). */
  isAdmin: boolean;
  /** Fine-grained permission check (owners + admins implicitly hold all). */
  can: (permission: Permission) => boolean;
}

/**
 * Resolves what the current member may do inside a given production company.
 *
 * Owners (and the founder) implicitly hold every permission. Managers hold only
 * the permissions granted to them. Platform admins override everything. Guests
 * and plain members get `canManage = false`, so management screens/buttons can be
 * hidden entirely rather than merely disabled.
 */
export function useCompanyPermissions(companyId?: string): CompanyPermissions {
  const session = useAuthStore((s) => s.session);
  const isAdmin = session?.roles.includes('ADMIN') ?? false;
  const memberId = session?.memberId;

  const rolesQ = useQuery({
    queryKey: ['company-roles', companyId],
    queryFn: () => rolesApi.list(companyId!),
    enabled: !!companyId && !!session,
    staleTime: 60_000,
  });

  const myRole = rolesQ.data?.find((r) => r.memberId === memberId);
  const isOwner = myRole?.roleType === 'OWNER';
  const isManager = myRole?.roleType === 'MANAGER';
  const canManage = isAdmin || isOwner || isManager;

  const can = (permission: Permission): boolean => {
    if (isAdmin || isOwner) return true;
    return myRole?.permissions.includes(permission) ?? false;
  };

  return {
    loading: rolesQ.isLoading,
    isOwner,
    isManager,
    canManage,
    isAdmin,
    can,
  };
}
