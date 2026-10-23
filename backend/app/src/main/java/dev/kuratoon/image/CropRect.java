package dev.kuratoon.image;

import dev.kuratoon.media.CoverCrop;

/** A crop in pixels of the (orientation-corrected) source image. */
public record CropRect(int left, int top, int width, int height) {

    /**
     * The exact-aspect pixel rect for a normalized crop: the crop scaled to the image, then shrunk
     * around its center to {@code aspectW:aspectH} and kept inside the image. A null crop means the
     * largest centered rect of that aspect (docs/spec/06-images.md: "centered 2:3 crop if none").
     */
    public static CropRect forAspect(int imageWidth, int imageHeight, CoverCrop crop, int aspectW, int aspectH) {
        double x = 0;
        double y = 0;
        double w = imageWidth;
        double h = imageHeight;
        if (crop != null) {
            x = clamp01(crop.x()) * imageWidth;
            y = clamp01(crop.y()) * imageHeight;
            w = Math.min(clamp01(crop.w()) * imageWidth, imageWidth - x);
            h = Math.min(clamp01(crop.h()) * imageHeight, imageHeight - y);
            if (w < 1 || h < 1) {
                x = 0;
                y = 0;
                w = imageWidth;
                h = imageHeight;
            }
        }
        double targetW = w;
        double targetH = w * aspectH / aspectW;
        if (targetH > h) {
            targetH = h;
            targetW = h * aspectW / aspectH;
        }
        int width = Math.max(1, (int) Math.floor(targetW));
        int height = Math.max(1, (int) Math.floor(targetH));
        int left = (int) Math.round(x + (w - width) / 2);
        int top = (int) Math.round(y + (h - height) / 2);
        left = Math.clamp(left, 0, imageWidth - width);
        top = Math.clamp(top, 0, imageHeight - height);
        return new CropRect(left, top, width, height);
    }

    private static double clamp01(double v) {
        return Double.isNaN(v) ? 0 : Math.clamp(v, 0, 1);
    }
}
