import { useCallback, useEffect, useMemo, useRef, useState } from "react";

import {
  getCurrentUser,
  loginUser,
  logoutUser,
  type AuthUser,
} from "../api/auth";
import { AuthContext } from "./AuthContext";

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [sessionError, setSessionError] = useState<string | null>(null);
  const initialSessionRequest = useRef<Promise<AuthUser | null> | null>(null);

  useEffect(() => {
    let isActive = true;
    const sessionRequest = initialSessionRequest.current ?? getCurrentUser();
    initialSessionRequest.current = sessionRequest;

    void sessionRequest
      .then((currentUser) => {
        if (isActive) {
          setUser(currentUser);
        }
      })
      .catch(() => {
        if (isActive) {
          setSessionError("Session status could not be checked. Public pages are still available.");
        }
      })
      .finally(() => {
        if (isActive) {
          setIsLoading(false);
        }
      });

    return () => {
      isActive = false;
    };
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const response = await loginUser({ email, password });
    setUser(response.user);
    setSessionError(null);
    return response.user;
  }, []);

  const refreshUser = useCallback(async () => {
    const currentUser = await getCurrentUser();
    setUser(currentUser);
    setSessionError(null);
    return currentUser;
  }, []);

  const logout = useCallback(async () => {
    try {
      await logoutUser();
    } finally {
      setUser(null);
      setSessionError(null);
    }
  }, []);

  const value = useMemo(
    () => ({ user, isLoading, sessionError, login, refreshUser, logout }),
    [user, isLoading, sessionError, login, refreshUser, logout],
  );

  return <AuthContext value={value}>{children}</AuthContext>;
}
