import { useReducedMotion } from "../motion/useReducedMotion";
import { useTheme } from "../theme/useTheme";

const videoSources = {
  light: "/media/lankawear-sewing-light.mp4",
  dark: "/media/lankawear-sewing-dark.mp4",
} as const;

export function HomeLowerVideo() {
  const prefersReducedMotion = useReducedMotion();
  const { resolvedTheme } = useTheme();
  const source = videoSources[resolvedTheme];

  return (
    <div aria-hidden="true" className="home-lower-video-frame">
      <div className="home-lower-video-stage">
        <video
          autoPlay={!prefersReducedMotion}
          className="home-lower-video"
          key={source}
          loop
          muted
          playsInline
          preload={prefersReducedMotion ? "metadata" : "auto"}
        >
          <source src={source} type="video/mp4" />
        </video>
        <div className="home-lower-video-overlay" />
      </div>
    </div>
  );
}
