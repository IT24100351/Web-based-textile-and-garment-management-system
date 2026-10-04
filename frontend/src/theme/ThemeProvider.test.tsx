import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ThemeToggle } from "../components/ui/ThemeToggle";
import { ThemeProvider } from "./ThemeProvider";
import { THEME_STORAGE_KEY } from "./theme";

function mockSystemTheme(prefersDark: boolean) {
  Object.defineProperty(window, "matchMedia", {
    configurable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      addEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
      matches: query === "(prefers-color-scheme: dark)" && prefersDark,
      media: query,
      onchange: null,
      removeEventListener: vi.fn(),
    })),
    writable: true,
  });
}

function renderThemeToggle() {
  return render(<ThemeProvider><ThemeToggle /></ThemeProvider>);
}

describe("ThemeProvider", () => {
  beforeEach(() => {
    window.localStorage.clear();
    delete document.documentElement.dataset.theme;
    mockSystemTheme(false);
  });

  it("uses the operating-system preference on a first visit", async () => {
    mockSystemTheme(true);
    renderThemeToggle();

    await waitFor(() => expect(document.documentElement.dataset.theme).toBe("dark"));
    expect(screen.getByRole("button", { name: "Use system theme" })).toHaveAttribute("aria-pressed", "true");
  });

  it("switches theme accessibly and persists the manual preference", async () => {
    const user = userEvent.setup();
    renderThemeToggle();

    await user.click(screen.getByRole("button", { name: "Use dark theme" }));

    expect(document.documentElement.dataset.theme).toBe("dark");
    expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe("dark");
    expect(screen.getByRole("button", { name: "Use dark theme" })).toHaveAttribute("aria-pressed", "true");
  });

  it("restores a saved preference", async () => {
    window.localStorage.setItem(THEME_STORAGE_KEY, "light");
    mockSystemTheme(true);
    renderThemeToggle();

    await waitFor(() => expect(document.documentElement.dataset.theme).toBe("light"));
    expect(screen.getByRole("button", { name: "Use light theme" })).toHaveAttribute("aria-pressed", "true");
  });
});
