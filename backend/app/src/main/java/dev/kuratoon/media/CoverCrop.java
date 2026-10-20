package dev.kuratoon.media;

/** Cover crop, normalized 0–1 relative to the original cover image. */
public record CoverCrop(double x, double y, double w, double h) {}
