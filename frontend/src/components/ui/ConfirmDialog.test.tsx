import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { ConfirmDialog } from "./ConfirmDialog";

function DialogHarness() {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button onClick={() => setOpen(true)} type="button">Open dialog</button>
      <ConfirmDialog
        cancelLabel="Cancel"
        confirmLabel="Delete"
        description="Delete this record?"
        isOpen={open}
        onCancel={() => setOpen(false)}
        onConfirm={() => undefined}
        title="Delete record?"
      />
    </>
  );
}

const originalMatchMedia = window.matchMedia;

afterEach(() => {
  Object.defineProperty(window, "matchMedia", { configurable: true, value: originalMatchMedia });
});

describe("ConfirmDialog motion presence", () => {
  it("opens, enters a closing state, then unmounts after its transition", async () => {
    const user = userEvent.setup();
    render(<DialogHarness />);

    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Open dialog" }));

    const dialog = await screen.findByRole("alertdialog", { name: "Delete record?" });
    const overlay = dialog.parentElement;
    expect(overlay).toHaveAttribute("data-motion-state", "open");

    await user.click(screen.getByRole("button", { name: "Cancel" }));
    expect(dialog).toBeInTheDocument();
    expect(overlay).toHaveAttribute("data-motion-state", "closed");
    expect(overlay).toHaveAttribute("inert");

    fireEvent.transitionEnd(overlay!);
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
  });


  it("closes with Escape and returns focus after the exit finishes", async () => {
    const user = userEvent.setup();
    render(<DialogHarness />);

    const trigger = screen.getByRole("button", { name: "Open dialog" });
    await user.click(trigger);
    const dialog = await screen.findByRole("alertdialog", { name: "Delete record?" });
    expect(screen.getByRole("button", { name: "Cancel" })).toHaveFocus();

    await user.keyboard("{Escape}");
    const overlay = dialog.parentElement;
    expect(overlay).toHaveAttribute("data-motion-state", "closed");
    expect(overlay).toHaveAttribute("inert");

    fireEvent.transitionEnd(overlay!);
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
  });

  it("does not delay unmounting for reduced-motion users", async () => {
    Object.defineProperty(window, "matchMedia", {
      configurable: true,
      value: vi.fn().mockReturnValue({
        addEventListener: vi.fn(),
        matches: true,
        removeEventListener: vi.fn(),
      }),
    });
    const user = userEvent.setup();
    render(<DialogHarness />);

    await user.click(screen.getByRole("button", { name: "Open dialog" }));
    expect(await screen.findByRole("alertdialog")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Cancel" }));

    await waitFor(() => expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument());
  });
});
