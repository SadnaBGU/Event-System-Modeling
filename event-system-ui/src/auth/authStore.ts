import { create } from 'zustand';
import { jwtDecode } from 'jwt-decode';

export type Role = 'MEMBER' | 'COMPANY_OWNER' | 'COMPANY_MANAGER' | 'ADMIN';

interface JwtClaims {
  sub?: string;
  memberId?: string;
  roles?: Role[];
  role?: Role; // some backends use singular
  exp?: number;
}

export interface Session {
  token: string;
  memberId: string;
  roles: Role[];
  expiresAt: string;
}

interface AuthState {
  session: Session | null;
  hydrated: boolean;
  setSession: (s: Session) => void;
  clear: () => void;
  hydrateFromStorage: () => void;
  hasRole: (role: Role) => boolean;
}

const STORAGE_KEY = 'eventsystem.session';

function decodeRoles(token: string): Role[] {
  try {
    const claims = jwtDecode<JwtClaims>(token);
    if (claims.roles && claims.roles.length > 0) return claims.roles;
    if (claims.role) return [claims.role];
  } catch {
    // unparseable token — treat as no roles. Backend remains source of truth.
  }
  return ['MEMBER'];
}

export const useAuthStore = create<AuthState>((set, get) => ({
  session: null,
  hydrated: false,
  setSession: (s) => {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(s));
    set({ session: s });
  },
  clear: () => {
    sessionStorage.removeItem(STORAGE_KEY);
    set({ session: null });
  },
  hydrateFromStorage: () => {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) {
      set({ hydrated: true });
      return;
    }
    try {
      const parsed = JSON.parse(raw) as Session;
      if (new Date(parsed.expiresAt).getTime() > Date.now()) {
        set({ session: parsed, hydrated: true });
        return;
      }
    } catch {
      // fall through to clear
    }
    sessionStorage.removeItem(STORAGE_KEY);
    set({ hydrated: true });
  },
  hasRole: (role) => get().session?.roles.includes(role) ?? false,
}));

export function sessionFromLogin(token: string, memberId: string, expiresAt: string): Session {
  return { token, memberId, roles: decodeRoles(token), expiresAt };
}
