import { describe, expect, it } from "vitest";

import {
  defaultFrame,
  heightFor,
  moveFrame,
  normalizeFrame,
  resizeFrame,
} from "./crop";

/** Pixel aspect of a normalized frame on an image. */
function aspect(f: { w: number; h: number }, imageW: number, imageH: number) {
  return (f.w * imageW) / (f.h * imageH);
}

describe("cover crop frame", () => {
  it("defaults to the largest centered 2:3 frame", () => {
    // Landscape 1000x600: full height, 400 px wide, centered.
    const landscape = defaultFrame(1000, 600);
    expect(landscape.h).toBeCloseTo(1);
    expect(landscape.w).toBeCloseTo(0.4);
    expect(landscape.x).toBeCloseTo(0.3);
    // Tall strip 800x12000: full width, 1200 px high, centered.
    const tall = defaultFrame(800, 12000);
    expect(tall.w).toBeCloseTo(1);
    expect(tall.h).toBeCloseTo(0.1);
    expect(tall.y).toBeCloseTo(0.45);
  });

  it("keeps 2:3 in pixels when resizing", () => {
    const frame = resizeFrame(defaultFrame(800, 1200), 0.5, 800, 1200);
    expect(aspect(frame, 800, 1200)).toBeCloseTo(2 / 3);
    expect(heightFor(0.5, 800, 1200)).toBeCloseTo(0.5);
  });

  it("never leaves the image", () => {
    const frame = resizeFrame(
      { x: 0.6, y: 0.6, w: 0.2, h: 0.2 },
      0.9,
      800,
      1200,
    );
    expect(frame.x + frame.w).toBeLessThanOrEqual(1 + 1e-9);
    expect(frame.y + frame.h).toBeLessThanOrEqual(1 + 1e-9);
    const moved = moveFrame(frame, 5, -5);
    expect(moved.x).toBeCloseTo(1 - frame.w);
    expect(moved.y).toBe(0);
  });

  it("normalizes stored crops to exact 2:3", () => {
    const frame = normalizeFrame({ x: 0.1, y: 0, w: 0.6, h: 1 }, 800, 1200);
    expect(aspect(frame, 800, 1200)).toBeCloseTo(2 / 3);
    expect(normalizeFrame(null, 800, 1200)).toEqual(defaultFrame(800, 1200));
  });
});
