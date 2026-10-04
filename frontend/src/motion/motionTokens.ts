export const motionDurations = {
  instant: 100,
  fast: 140,
  normal: 180,
  smooth: 220,
  slow: 300,
} as const;

export const motionEasings = {
  standard: "cubic-bezier(0.2, 0.8, 0.2, 1)",
  emphasized: "cubic-bezier(0.16, 1, 0.3, 1)",
  exit: "cubic-bezier(0.4, 0, 1, 1)",
} as const;
