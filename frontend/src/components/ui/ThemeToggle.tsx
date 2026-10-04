import { Monitor, Moon, Sun } from "lucide-react";

import type { ThemePreference } from "../../theme/theme";
import { useTheme } from "../../theme/useTheme";
import { cn } from "./cn";

const options: Array<{
  label: string;
  preference: ThemePreference;
  Icon: typeof Sun;
}> = [
  { label: "Use light theme", preference: "light", Icon: Sun },
  { label: "Use system theme", preference: "system", Icon: Monitor },
  { label: "Use dark theme", preference: "dark", Icon: Moon },
];

export function ThemeToggle({ compact = false }: { compact?: boolean }) {
  const { preference, setPreference } = useTheme();

  return (
    <div
      aria-label="Theme preference"
      className="inline-flex rounded-xl border border-border bg-surface-muted p-1"
      role="group"
    >
      {options.map(({ Icon, label, preference: option }) => (
        <button
          aria-label={label}
          aria-pressed={preference === option}
          className={cn(
            "group grid h-8 place-items-center rounded-lg text-muted transition duration-[var(--motion-fast)] ease-[var(--ease-standard)] hover:text-foreground active:scale-[0.96]",
            compact ? "w-8" : "w-9",
            preference === option && "bg-surface-elevated text-primary shadow-sm",
          )}
          key={option}
          onClick={() => setPreference(option)}
          title={label}
          type="button"
        >
          <Icon aria-hidden="true" className={cn("theme-toggle-icon h-4 w-4 transition-transform duration-[var(--motion-normal)] ease-[var(--ease-emphasized)]", preference === option && "rotate-0 scale-105", preference !== option && "-rotate-12 scale-95")} />
        </button>
      ))}
    </div>
  );
}
