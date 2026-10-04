import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";

import { MotionPage } from "./MotionPage";
import { MotionSwap } from "./MotionSwap";
import { Reveal } from "./Reveal";

describe("motion infrastructure", () => {
  it("renders page content immediately", () => {
    render(<MotionPage>Operational content</MotionPage>);
    expect(screen.getByText("Operational content")).toBeInTheDocument();
  });

  it("keeps reveal content immediately visible when IntersectionObserver is unavailable", () => {
    const originalObserver = globalThis.IntersectionObserver;
    Object.defineProperty(globalThis, "IntersectionObserver", { configurable: true, value: undefined });

    try {
      render(<Reveal>Revealed content</Reveal>);
      expect(screen.getByText("Revealed content")).toHaveAttribute("data-motion-state", "visible");
    } finally {
      Object.defineProperty(globalThis, "IntersectionObserver", { configurable: true, value: originalObserver });
    }
  });

  it("renders reveal content without delay when reduced motion is requested", () => {
    Object.defineProperty(window, "matchMedia", {
      configurable: true,
      value: vi.fn().mockReturnValue({
        addEventListener: vi.fn(),
        matches: true,
        removeEventListener: vi.fn(),
      }),
    });

    render(<Reveal>Reduced motion content</Reveal>);
    expect(screen.getByText("Reduced motion content")).toHaveAttribute("data-motion-state", "visible");
  });
  it("keeps outgoing swap content non-interactive while its exit animation finishes", async () => {
    function SwapHarness() {
      const [state, setState] = useState<"first" | "second">("first");
      return (
        <>
          <button onClick={() => setState("second")} type="button">Change state</button>
          <MotionSwap stateKey={state}>
            <button type="button">{state === "first" ? "Old action" : "New action"}</button>
          </MotionSwap>
        </>
      );
    }

    const user = userEvent.setup();
    render(<SwapHarness />);
    await user.click(screen.getByRole("button", { name: "Change state" }));

    const outgoing = screen.getByRole("button", { name: "Old action", hidden: true }).parentElement;
    expect(outgoing).toHaveAttribute("inert");
    expect(screen.getByRole("button", { name: "New action" })).toBeInTheDocument();
  });

});
