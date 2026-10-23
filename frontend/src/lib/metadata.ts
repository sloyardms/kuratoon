import type { MediaField, MediaFields, PublicationStatus } from "./types";

/** Field order of the metadata form (backend `MediaFields.FIELD_NAMES`). */
export const FIELD_NAMES: MediaField[] = [
  "title",
  "titles",
  "description",
  "type",
  "language",
  "status",
  "year",
  "adult",
  "authors",
  "artists",
  "genres",
  "tags",
  "parodies",
  "characters",
];

export const FIELD_LABELS: Record<MediaField, string> = {
  title: "Title",
  titles: "Alternative titles",
  description: "Description",
  type: "Type",
  language: "Language",
  status: "Status",
  year: "Year",
  adult: "Adult",
  authors: "Authors",
  artists: "Artists",
  genres: "Genres",
  tags: "Library tags",
  parodies: "Parodies",
  characters: "Characters",
};

export const STATUSES: PublicationStatus[] = [
  "ONGOING",
  "COMPLETED",
  "HIATUS",
  "CANCELLED",
  "UNKNOWN",
];

/**
 * The form keeps every field as text: lists one entry per line, characters as
 * `Name | Parody` per line.
 */
export type MetadataFormState = Record<MediaField, string>;

export const EMPTY_FIELDS: MediaFields = {
  title: null,
  titles: [],
  description: null,
  type: null,
  language: null,
  status: null,
  year: null,
  adult: null,
  authors: [],
  artists: [],
  genres: [],
  tags: [],
  parodies: [],
  characters: [],
};

export function toFormState(fields: MediaFields | null): MetadataFormState {
  const f = fields ?? EMPTY_FIELDS;
  return {
    title: f.title ?? "",
    titles: f.titles.join("\n"),
    description: f.description ?? "",
    type: f.type ?? "",
    language: f.language ?? "",
    status: f.status ?? "",
    year: f.year === null ? "" : String(f.year),
    adult: f.adult ? "true" : "false",
    authors: f.authors.join("\n"),
    artists: f.artists.join("\n"),
    genres: f.genres.join("\n"),
    tags: f.tags.join("\n"),
    parodies: f.parodies.join("\n"),
    characters: f.characters
      .map((c) => (c.parody ? `${c.name} | ${c.parody}` : c.name))
      .join("\n"),
  };
}

/** One entry per line, trimmed, blanks and duplicates dropped. */
export function parseList(text: string): string[] {
  const seen = new Set<string>();
  for (const line of text.split(/\r?\n/)) {
    const value = line.trim();
    if (value) seen.add(value);
  }
  return [...seen];
}

/** `Name | Parody` per line; the parody is optional. */
export function parseCharacters(
  text: string,
): { name: string; parody: string | null }[] {
  return parseList(text).map((line) => {
    const [name, ...rest] = line.split("|");
    const parody = rest.join("|").trim();
    return { name: name.trim(), parody: parody || null };
  });
}

export function fromFormState(state: MetadataFormState): MediaFields {
  const year = state.year.trim();
  return {
    title: state.title.trim() || null,
    titles: parseList(state.titles),
    description: state.description.trim() || null,
    type: state.type.trim() || null,
    language: state.language.trim() || null,
    status: (state.status || null) as PublicationStatus | null,
    year: year === "" ? null : Number(year),
    adult: state.adult === "true",
    authors: parseList(state.authors),
    artists: parseList(state.artists),
    genres: parseList(state.genres),
    tags: parseList(state.tags),
    parodies: parseList(state.parodies),
    characters: parseCharacters(state.characters),
  };
}

/** Fields whose value differs between two form states (normalized). */
export function changedFields(
  before: MetadataFormState,
  after: MetadataFormState,
): MediaField[] {
  const a = fromFormState(before);
  const b = fromFormState(after);
  return FIELD_NAMES.filter(
    (name) => JSON.stringify(a[name]) !== JSON.stringify(b[name]),
  );
}

/**
 * Client-side checks matching the backend rules for creating a media: title, type and at
 * least one author or artist (docs/spec/01-data-model.md). The server checks again.
 */
export function validateForCreate(
  fields: MediaFields,
): Partial<Record<MediaField, string>> {
  const errors: Partial<Record<MediaField, string>> = {};
  if (!fields.title) errors.title = "A title is required.";
  if (!fields.type) errors.type = "Choose a type.";
  if (fields.authors.length === 0 && fields.artists.length === 0)
    errors.authors = "At least one author or artist is required.";
  if (
    fields.year !== null &&
    (!Number.isInteger(fields.year) || fields.year < 1000 || fields.year > 9999)
  )
    errors.year = "Four digits, e.g. 2023.";
  return errors;
}
