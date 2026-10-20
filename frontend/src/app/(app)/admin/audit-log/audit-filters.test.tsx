import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import {
  AuditFilters,
  EMPTY_FILTERS,
  filtersFromParams,
} from "./audit-filters";

const actors = [
  { id: "a1", username: "admin" },
  { id: "a2", username: "mod" },
];

describe("audit log filters", () => {
  it("reads filters from the URL query", () => {
    expect(
      filtersFromParams(
        new URLSearchParams(
          "action=USER_CREATED&targetType=user&actorId=a2&page=3",
        ),
      ),
    ).toEqual({
      action: "USER_CREATED",
      targetType: "user",
      actorId: "a2",
    });
    expect(filtersFromParams(new URLSearchParams(""))).toEqual(EMPTY_FILTERS);
  });

  it("shows the applied filters", () => {
    render(
      <AuditFilters
        value={{ action: "TAG_DELETED", targetType: "tag", actorId: "a2" }}
        actors={actors}
        onApply={() => {}}
      />,
    );

    expect(screen.getByLabelText("Action")).toHaveValue("TAG_DELETED");
    expect(screen.getByLabelText("Target type")).toHaveValue("tag");
    expect(screen.getByLabelText("Actor")).toHaveValue("a2");
  });

  it("applies edited filters, normalizing the action", () => {
    const onApply = vi.fn();
    render(
      <AuditFilters value={EMPTY_FILTERS} actors={actors} onApply={onApply} />,
    );

    fireEvent.change(screen.getByLabelText("Action"), {
      target: { value: " user_created " },
    });
    fireEvent.change(screen.getByLabelText("Target type"), {
      target: { value: "user" },
    });
    fireEvent.change(screen.getByLabelText("Actor"), {
      target: { value: "a1" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Apply" }));

    expect(onApply).toHaveBeenCalledWith({
      action: "USER_CREATED",
      targetType: "user",
      actorId: "a1",
    });
  });

  it("clears all filters", () => {
    const onApply = vi.fn();
    render(
      <AuditFilters
        value={{ action: "X", targetType: "tag", actorId: "a1" }}
        actors={actors}
        onApply={onApply}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Clear" }));

    expect(onApply).toHaveBeenCalledWith(EMPTY_FILTERS);
    expect(screen.getByLabelText("Action")).toHaveValue("");
  });
});
