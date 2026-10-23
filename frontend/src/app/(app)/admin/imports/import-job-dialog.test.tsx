import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { ImportJobDetail } from "@/lib/types";

import { ImportJobDialog } from "./import-job-dialog";

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}));

const detail: ImportJobDetail = {
  job: {
    id: "j1",
    sourceFolder: "oneshot",
    kind: "NEW",
    status: "NEEDS_ATTENTION",
    message: "No metadata plugin supports http://fake-manga.test/oneshot/one.",
    attention: "NO_PLUGIN",
    mediaId: null,
    mediaTitle: null,
    chapterCount: 1,
    attempts: 1,
    createdAt: "2026-10-22T00:00:00Z",
    updatedAt: "2026-10-22T00:00:00Z",
    finishedAt: null,
    actions: ["RETRY", "FILL_METADATA", "DISMISS"],
  },
  url: "http://fake-manga.test/oneshot/one",
  chapters: [
    {
      sourceFolder: "",
      number: 1,
      title: null,
      folder: "1",
      pageCount: 5,
      warnings: [],
    },
  ],
  warnings: [],
  missing: ["type", "authors or artists"],
  prefill: null,
  conflicts: [],
  duplicate: null,
  committed: false,
};

function json(body: unknown) {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  });
}

describe("import job dialog", () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => {
    fetchMock.mockReset();
    fetchMock.mockImplementation(async (input) => {
      const url = String(input);
      if (url.startsWith("/api/v1/admin/media-types"))
        return json({
          items: [
            {
              id: "t1",
              name: "One-shot",
              slug: "one-shot",
              color: "#000",
              sortOrder: 0,
            },
          ],
          page: 0,
          size: 100,
          totalItems: 1,
          totalPages: 1,
        });
      return json(detail);
    });
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => vi.unstubAllGlobals());

  function renderDialog() {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    render(
      <QueryClientProvider client={client}>
        <ImportJobDialog jobId="j1" onClose={() => {}} />
      </QueryClientProvider>,
    );
  }

  it("shows the message, chapter table and only the allowed actions", async () => {
    renderDialog();
    expect(
      await screen.findByText(/No metadata plugin supports/),
    ).toBeInTheDocument();
    expect(screen.getByText("(folder root)")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Retry" })).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Fill metadata manually" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Dismiss" })).toBeInTheDocument();
    expect(
      screen.queryByText("Chapters that already exist"),
    ).not.toBeInTheDocument();
  });

  it("checks the metadata before sending it", async () => {
    renderDialog();
    fireEvent.click(
      await screen.findByRole("button", { name: "Fill metadata manually" }),
    );
    fireEvent.click(screen.getByRole("button", { name: "Save and import" }));
    expect(await screen.findByText("A title is required.")).toBeInTheDocument();
    expect(
      screen.getByText("At least one author or artist is required."),
    ).toBeInTheDocument();
    expect(
      fetchMock.mock.calls.some(([, init]) => init?.method === "PUT"),
    ).toBe(false);

    fireEvent.change(screen.getByLabelText("Title"), {
      target: { value: "One Shot" },
    });
    await screen.findByRole("option", { name: "One-shot" });
    fireEvent.change(screen.getByLabelText("Type"), {
      target: { value: "one-shot" },
    });
    fireEvent.change(screen.getByLabelText("Authors"), {
      target: { value: "Someone" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Save and import" }));

    await waitFor(() =>
      expect(
        fetchMock.mock.calls.some(([, init]) => init?.method === "PUT"),
      ).toBe(true),
    );
    const [url, init] = fetchMock.mock.calls.find(
      ([, i]) => i?.method === "PUT",
    )!;
    expect(url).toBe("/api/v1/admin/imports/j1/metadata");
    expect(JSON.parse(String(init?.body))).toMatchObject({
      title: "One Shot",
      type: "one-shot",
      authors: ["Someone"],
    });
  });
});
