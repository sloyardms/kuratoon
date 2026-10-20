import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import Home from "./page";

describe("Home", () => {
  it("renders the placeholder", () => {
    render(<Home />);

    expect(
      screen.getByRole("heading", { level: 1, name: "Kuratoon" }),
    ).toBeInTheDocument();
    expect(screen.getByText(/coming soon/i)).toBeInTheDocument();
  });
});
