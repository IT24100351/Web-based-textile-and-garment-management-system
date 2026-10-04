import { useEffect, useState } from "react";

const reducedMotionQuery = "(prefers-reduced-motion: reduce)";

function getInitialPreference() {
  return typeof window !== "undefined"
    && typeof window.matchMedia === "function"
    && window.matchMedia(reducedMotionQuery).matches;
}

export function useReducedMotion() {
  const [prefersReducedMotion, setPrefersReducedMotion] = useState(getInitialPreference);

  useEffect(() => {
    if (typeof window.matchMedia !== "function") return undefined;
    const media = window.matchMedia(reducedMotionQuery);
    const updatePreference = (event: MediaQueryListEvent) => setPrefersReducedMotion(event.matches);
    media.addEventListener("change", updatePreference);
    return () => media.removeEventListener("change", updatePreference);
  }, []);

  return prefersReducedMotion;
}
