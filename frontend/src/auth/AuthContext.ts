import { createContext } from "react";

import type { AuthUser } from "../api/auth";

export interface AuthContextValue {
  user: AuthUser | null;
  isLoading: boolean;
  sessionError: string | null;
  login: (email: string, password: string) => Promise<AuthUser>;
  refreshUser: () => Promise<AuthUser | null>;
  logout: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);
