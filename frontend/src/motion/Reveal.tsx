import { useEffect, useRef, useState, type HTMLAttributes } from "react";

import { cn } from "../components/ui/cn";
import { useReducedMotion } from "./useReducedMotion";

export function Reveal({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  const elementRef = useRef<HTMLDivElement>(null);
  const prefersReducedMotion = useReducedMotion();
  const canObserve = typeof IntersectionObserver !== "undefined";
  const [isVisible, setIsVisible] = useState(prefersReducedMotion || !canObserve);

  useEffect(() => {
    if (prefersReducedMotion || !canObserve) {
      setIsVisible(true);
      return undefined;
    }

    const element = elementRef.current;
    if (!element) return undefined;

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (!entry?.isIntersecting) return;
        setIsVisible(true);
        observer.disconnect();
      },
      { rootMargin: "0px 0px -8%", threshold: 0.12 },
    );
    observer.observe(element);
    return () => observer.disconnect();
  }, [canObserve, prefersReducedMotion]);

  return (
    <div
      className={cn("motion-reveal", isVisible && "is-visible", className)}
      data-motion-state={isVisible ? "visible" : "pending"}
      ref={elementRef}
      {...props}
    />
  );
}
