import {
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
  type Key,
  type ReactNode,
} from "react";

import { cn } from "../components/ui/cn";
import { motionDurations } from "./motionTokens";
import { useReducedMotion } from "./useReducedMotion";

interface MotionLayer {
  key: Key;
  node: ReactNode;
}

export function MotionSwap({
  children,
  className,
  stateKey,
}: {
  children: ReactNode;
  className?: string;
  stateKey: Key;
}) {
  const prefersReducedMotion = useReducedMotion();
  const previousLayerRef = useRef<MotionLayer>({ key: stateKey, node: children });
  const [exitingLayer, setExitingLayer] = useState<MotionLayer | null>(null);

  useLayoutEffect(() => {
    const previousLayer = previousLayerRef.current;
    if (previousLayer.key !== stateKey) {
      setExitingLayer(prefersReducedMotion ? null : previousLayer);
    }
    previousLayerRef.current = { key: stateKey, node: children };
  }, [children, prefersReducedMotion, stateKey]);

  useEffect(() => {
    if (!exitingLayer || prefersReducedMotion) return undefined;
    const fallback = window.setTimeout(
      () => setExitingLayer((current) => current?.key === exitingLayer.key ? null : current),
      motionDurations.normal + 40,
    );
    return () => window.clearTimeout(fallback);
  }, [exitingLayer, prefersReducedMotion]);

  return (
    <div className={cn("motion-swap", className)}>
      {exitingLayer ? (
        <div
          className="motion-swap-layer motion-swap-exiting"
          inert
          onAnimationEnd={(event) => {
            if (event.currentTarget === event.target) setExitingLayer(null);
          }}
        >
          {exitingLayer.node}
        </div>
      ) : null}
      <div className="motion-swap-layer motion-swap-entering" key={stateKey}>
        {children}
      </div>
    </div>
  );
}
