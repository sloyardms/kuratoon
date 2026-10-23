import { describe, expect, it } from "vitest";

import {
  EMPTY_FIELDS,
  changedFields,
  fromFormState,
  parseCharacters,
  parseList,
  toFormState,
  validateForCreate,
} from "./metadata";

describe("metadata form", () => {
  it("parses one entry per line, trimmed and de-duplicated", () => {
    expect(parseList(" A \n\nB\r\nA\n  ")).toEqual(["A", "B"]);
  });

  it("parses characters with an optional parody", () => {
    expect(parseCharacters("Hero | Saga\nSidekick\n  Villain|  ")).toEqual([
      { name: "Hero", parody: "Saga" },
      { name: "Sidekick", parody: null },
      { name: "Villain", parody: null },
    ]);
  });

  it("round-trips fields through the form state", () => {
    const fields = {
      ...EMPTY_FIELDS,
      title: "Foo",
      titles: ["Foo (Alt)"],
      type: "manhwa",
      status: "ONGOING" as const,
      year: 2023,
      adult: true,
      authors: ["A", "B"],
      characters: [{ name: "C", parody: "P" }],
    };
    expect(fromFormState(toFormState(fields))).toEqual(fields);
  });

  it("reports only the fields that changed", () => {
    const before = toFormState({
      ...EMPTY_FIELDS,
      title: "Foo",
      authors: ["A"],
    });
    const after = { ...before, title: "Foo ", authors: "A\nB", year: "2001" };
    expect(changedFields(before, after)).toEqual(["year", "authors"]);
  });

  it("requires title, type and an author or artist to create a media", () => {
    expect(validateForCreate(EMPTY_FIELDS)).toEqual({
      title: "A title is required.",
      type: "Choose a type.",
      authors: "At least one author or artist is required.",
    });
    expect(
      validateForCreate({
        ...EMPTY_FIELDS,
        title: "T",
        type: "manga",
        artists: ["X"],
      }),
    ).toEqual({});
    expect(
      validateForCreate({
        ...EMPTY_FIELDS,
        title: "T",
        type: "manga",
        authors: ["X"],
        year: 99,
      }),
    ).toEqual({ year: "Four digits, e.g. 2023." });
  });
});
