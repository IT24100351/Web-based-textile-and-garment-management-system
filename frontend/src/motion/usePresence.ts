import { useCallback, useEffect, useRef, useState, type TransitionEvent } from "react";

import { motionDurations } from "./motionTokens";
import { useReducedMotion } from "./useReducedMotion";

interface PresenceOptions {
  exitDuration?: number;
  onExitComplete?: () => void;
}

export function usePresence(
  isPresent: boolean,
  { exitDuration = motionDurations.normal, onExitComplete }: PresenceOptions = {},
) {
  const prefersReducedMotion = useReducedMotion();
  const [isMounted, setIsMounted] = useState(isPresent);
  const [isVisible, setIsVisible] = useState(isPresent);
  const isPresentRef = useRef(isPresent);
  const onExitCompleteRef = useRef(onExitComplete);
  const exitCompletedRef = useRef(!isPresent);

  isPresentRef.current = isPresent;
  onExitCompleteRef.current = onExitComplete;

  const completeExit = useCallback(() => {
    if (isPresentRef.current || exitCompletedRef.current) return;
    exitCompletedRef.current = true;
    setIsMounted(false);
    onExitCompleteRef.current?.();
  }, []);

  useEffect(() => {
    if (isPresent) {
      exitCompletedRef.current = false;
      setIsMounted(true);
      if (prefersReducedMotion) setIsVisible(true);
      return;
    }

    setIsVisible(false);
    if (prefersReducedMotion && isMounted) completeExit();
  }, [completeExit, isMounted, isPresent, prefersReducedMotion]);

  useEffect(() => {
    if (!isMounted || !isPresent || prefersReducedMotion) return undefined;
    const frame = window.requestAnimationFrame(() => setIsVisible(true));
    return () => window.cancelAnimationFrame(frame);
  }, [isMounted, isPresent, prefersReducedMotion]);

  useEffect(() => {
    if (!isMounted || isPresent || prefersReducedMotion) return undefined;
    const fallback = window.setTimeout(completeExit, exitDuration + 40);
    return () => window.clearTimeout(fallback);
  }, [completeExit, exitDuration, isMounted, isPresent, prefersReducedMotion]);

  const handleTransitionEnd = useCallback((event: TransitionEvent<HTMLElement>) => {
    if (event.currentTarget === event.target && !isPresentRef.current) completeExit();
  }, [completeExit]);

  return {
    completeExit,
    isMounted,
    motionState: isPresent && isVisible ? "open" as const : "closed" as const,
    onTransitionEnd: handleTransitionEnd,
    prefersReducedMotion,
  };
}
