"use client";

import { useState, type PointerEvent } from "react";

import { moveFrame, normalizeFrame, resizeFrame } from "@/lib/crop";
import type { CoverCrop } from "@/lib/types";

type Drag =
  | { kind: "move"; startX: number; startY: number; frame: CoverCrop }
  | { kind: "resize"; startX: number; frame: CoverCrop };

const KEY_STEP = 0.01;

/**
 * Admin cover crop (docs/spec/06-images.md): a 2:3 frame dragged over the original cover. Drag
 * the frame to move it, the corner handle to resize; arrow keys move, +/- resize.
 */
export function CoverCropTool({
  src,
  imageWidth,
  imageHeight,
  initial,
  onChange,
}: {
  src: string;
  imageWidth: number;
  imageHeight: number;
  initial: CoverCrop | null;
  onChange: (frame: CoverCrop) => void;
}) {
  const [frame, setFrame] = useState(() =>
    normalizeFrame(initial, imageWidth, imageHeight),
  );
  const [drag, setDrag] = useState<Drag | null>(null);

  const update = (next: CoverCrop) => {
    setFrame(next);
    onChange(next);
  };

  /** Pointer position normalized to the image box (the element marked data-crop-box). */
  const normalized = (e: PointerEvent) => {
    const el = (e.currentTarget as HTMLElement).closest("[data-crop-box]");
    const rect = el?.getBoundingClientRect();
    return rect
      ? {
          x: (e.clientX - rect.left) / rect.width,
          y: (e.clientY - rect.top) / rect.height,
        }
      : { x: 0, y: 0 };
  };

  const onPointerDown = (kind: Drag["kind"]) => (e: PointerEvent) => {
    e.preventDefault();
    e.stopPropagation();
    (e.target as Element).setPointerCapture(e.pointerId);
    const p = normalized(e);
    setDrag(
      kind === "move"
        ? { kind, startX: p.x, startY: p.y, frame }
        : { kind, startX: p.x, frame },
    );
  };

  const onPointerMove = (e: PointerEvent) => {
    if (!drag) return;
    const p = normalized(e);
    if (drag.kind === "move") {
      update(moveFrame(drag.frame, p.x - drag.startX, p.y - drag.startY));
    } else {
      update(
        resizeFrame(
          drag.frame,
          drag.frame.w + (p.x - drag.startX),
          imageWidth,
          imageHeight,
        ),
      );
    }
  };

  const onKeyDown = (e: React.KeyboardEvent) => {
    const moves: Record<string, [number, number]> = {
      ArrowLeft: [-KEY_STEP, 0],
      ArrowRight: [KEY_STEP, 0],
      ArrowUp: [0, -KEY_STEP],
      ArrowDown: [0, KEY_STEP],
    };
    if (moves[e.key]) {
      e.preventDefault();
      update(moveFrame(frame, ...moves[e.key]));
    } else if (e.key === "+" || e.key === "=" || e.key === "-") {
      e.preventDefault();
      const delta = e.key === "-" ? -KEY_STEP : KEY_STEP;
      update(resizeFrame(frame, frame.w + delta, imageWidth, imageHeight));
    }
  };

  const pct = (v: number) => `${v * 100}%`;

  return (
    <div
      data-crop-box
      className="relative w-full max-w-md touch-none select-none"
      style={{ aspectRatio: `${imageWidth} / ${imageHeight}` }}
      onPointerMove={onPointerMove}
      onPointerUp={() => setDrag(null)}
      onPointerCancel={() => setDrag(null)}
    >
      {/* eslint-disable-next-line @next/next/no-img-element -- admin-only original, not optimized */}
      <img
        src={src}
        alt="Original cover"
        width={imageWidth}
        height={imageHeight}
        className="absolute inset-0 size-full"
        draggable={false}
      />
      <div
        role="slider"
        tabIndex={0}
        aria-label="Crop frame (arrow keys move, plus and minus resize)"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={Math.round(frame.w * 100)}
        aria-valuetext={`x ${Math.round(frame.x * 100)}%, y ${Math.round(frame.y * 100)}%, width ${Math.round(frame.w * 100)}%`}
        onKeyDown={onKeyDown}
        onPointerDown={onPointerDown("move")}
        className="absolute cursor-move shadow-[0_0_0_9999px_rgba(0,0,0,0.55)] outline-2 outline-white focus-visible:outline-primary"
        style={{
          left: pct(frame.x),
          top: pct(frame.y),
          width: pct(frame.w),
          height: pct(frame.h),
        }}
      >
        <span
          aria-hidden
          onPointerDown={onPointerDown("resize")}
          className="absolute -right-2 -bottom-2 size-4 cursor-se-resize rounded-full border-2 border-white bg-primary"
        />
      </div>
    </div>
  );
}
