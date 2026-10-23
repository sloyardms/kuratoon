import type { CoverCrop } from "./types";

/**
 * Geometry of the 2:3 cover crop frame (docs/spec/06-images.md "Cover crop tool"). Frames are
 * normalized 0–1 relative to the original image; the 2:3 aspect holds in pixels, so a frame's
 * normalized height depends on the image size.
 */

const ASPECT = 2 / 3;

function clamp(v: number, min: number, max: number) {
  return Math.min(Math.max(v, min), max);
}

/** Normalized height for a normalized width, keeping 2:3 in pixels. */
export function heightFor(w: number, imageW: number, imageH: number): number {
  return (w * imageW) / ASPECT / imageH;
}

/** The largest centered 2:3 frame (the default when there is no crop). */
export function defaultFrame(imageW: number, imageH: number): CoverCrop {
  let w = 1;
  let h = heightFor(w, imageW, imageH);
  if (h > 1) {
    h = 1;
    w = (imageH * ASPECT) / imageW;
  }
  return { x: (1 - w) / 2, y: (1 - h) / 2, w, h };
}

/** Moves the frame by a normalized delta, staying inside the image. */
export function moveFrame(frame: CoverCrop, dx: number, dy: number): CoverCrop {
  return {
    ...frame,
    x: clamp(frame.x + dx, 0, 1 - frame.w),
    y: clamp(frame.y + dy, 0, 1 - frame.h),
  };
}

/**
 * Resizes to a new normalized width around the frame's top-left corner, keeping 2:3, a minimum
 * size and the image bounds.
 */
export function resizeFrame(
  frame: CoverCrop,
  w: number,
  imageW: number,
  imageH: number,
): CoverCrop {
  const minW = Math.min(0.05, (imageH * ASPECT) / imageW);
  const maxW = Math.min(
    1 - frame.x,
    ((1 - frame.y) * imageH * ASPECT) / imageW,
  );
  const width = clamp(w, minW, Math.max(minW, maxW));
  return {
    x: frame.x,
    y: frame.y,
    w: width,
    h: heightFor(width, imageW, imageH),
  };
}

/** A stored crop normalized back to exact 2:3 (older or hand-edited values). */
export function normalizeFrame(
  crop: CoverCrop | null,
  imageW: number,
  imageH: number,
): CoverCrop {
  if (!crop || imageW <= 0 || imageH <= 0) return defaultFrame(imageW, imageH);
  const resized = resizeFrame(
    { x: clamp(crop.x, 0, 1), y: clamp(crop.y, 0, 1), w: crop.w, h: crop.h },
    crop.w,
    imageW,
    imageH,
  );
  return moveFrame(resized, 0, 0);
}
