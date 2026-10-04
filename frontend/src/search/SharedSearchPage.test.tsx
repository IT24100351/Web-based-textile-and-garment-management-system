import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { searchCoreRecords } from "../api/search";
import { SharedSearchPage } from "./SharedSearchPage";

vi.mock("../api/search", () => ({ searchCoreRecords: vi.fn() }));
const mockedSearch = vi.mocked(searchCoreRecords);

describe("SharedSearchPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedSearch.mockResolvedValue({
      search: "ORD",
      page: 0,
      size: 10,
      totalResults: 1,
      totalPages: 1,
      results: [{
        module: "ORDER",
        recordId: 91,
        title: "ORD-91",
        subtitle: "Your order",
        status: "CONFIRMED",
        path: "/orders/history/91",
      }],
    });
  });

  it("submits a normalized query and renders only server-returned permitted results", async () => {
    const user = userEvent.setup();
    render(<MemoryRouter initialEntries={["/search"]}><SharedSearchPage /></MemoryRouter>);

    await user.type(screen.getByLabelText("Search records"), "  ORD   ");
    await user.click(screen.getByRole("button", { name: "Search" }));

    expect(await screen.findByText("ORD-91")).toBeInTheDocument();
    expect(mockedSearch).toHaveBeenCalledWith("ORD", 0, 10);
    expect(screen.getByRole("link", { name: /ORD-91/i })).toHaveAttribute("href", "/orders/history/91");
  });

  it("does not call the API for a one-character query", async () => {
    const user = userEvent.setup();
    render(<MemoryRouter initialEntries={["/search"]}><SharedSearchPage /></MemoryRouter>);

    await user.type(screen.getByLabelText("Search records"), "x");
    await user.click(screen.getByRole("button", { name: "Search" }));

    expect(screen.getByRole("alert")).toHaveTextContent("Enter at least 2 characters");
    expect(mockedSearch).not.toHaveBeenCalled();
  });
});
