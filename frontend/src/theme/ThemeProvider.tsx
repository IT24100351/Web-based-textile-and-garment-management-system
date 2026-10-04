import { useEffect, useMemo, useState } from "react";

import { ThemeContext } from "./ThemeContext";
import {
  isThemePreference,
  resolveTheme,
  THEME_STORAGE_KEY,
  type ThemePreference,
} from "./theme";

function getInitialPreference(): ThemePreference {
  if (typeof window === "undefined") return "system";
  try {
    const stored = window.localStorage?.getItem(THEME_STORAGE_KEY);
    return isThemePreference(stored) ? stored : "system";
  } catch {
    return "system";
  }
}

function getSystemPreference() {
  return typeof window !== "undefined"
    && typeof window.matchMedia === "function"
    && window.matchMedia("(prefers-color-scheme: dark)").matches;
}

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const [preference, setPreference] = useState<ThemePreference>(getInitialPreference);
  const [systemPrefersDark, setSystemPrefersDark] = useState(getSystemPreference);
  const resolvedTheme = resolveTheme(preference, systemPrefersDark);

  useEffect(() => {
    if (typeof window.matchMedia !== "function") return undefined;
    const media = window.matchMedia("(prefers-color-scheme: dark)");
    const updateSystemPreference = (event: MediaQueryListEvent) => {
      setSystemPrefersDark(event.matches);
    };
    media.addEventListener("change", updateSystemPreference);
    return () => media.removeEventListener("change", updateSystemPreference);
  }, []);

  useEffect(() => {
    const root = document.documentElement;
    root.dataset.theme = resolvedTheme;
    root.style.colorScheme = resolvedTheme;
    document.querySelector('meta[name="theme-color"]')
      ?.setAttribute("content", resolvedTheme === "dark" ? "#000000" : "#0f766e");
    try {
      window.localStorage?.setItem(THEME_STORAGE_KEY, preference);
    } catch {
      // Theme still applies when storage is blocked by browser privacy settings.
    }
  }, [preference, resolvedTheme]);

  useEffect(() => {
    const frame = window.requestAnimationFrame(() => {
      document.documentElement.dataset.themeReady = "true";
    });
    return () => window.cancelAnimationFrame(frame);
  }, []);

  const value = useMemo(
    () => ({ preference, resolvedTheme, setPreference }),
    [preference, resolvedTheme],
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}
