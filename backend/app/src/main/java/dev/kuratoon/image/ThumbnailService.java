package dev.kuratoon.image;

import dev.kuratoon.config.KuratoonProperties;
import dev.kuratoon.image.ImageProbe.ImageInfo;
import dev.kuratoon.image.ImageProbe.UnreadableImageException;
import dev.kuratoon.media.CoverCrop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.IntFunction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

/**
 * Generated images (docs/spec/06-images.md "Generation"): crop the original, then resize to fixed
 * widths as WebP. Outputs are written to a temp dir on the same filesystem and renamed into place,
 * so readers never see a partial file and regeneration is idempotent.
 */
@Service
public class ThumbnailService {

    public static final List<Integer> COVER_WIDTHS = List.of(240, 480, 720);
    static final int WEBP_QUALITY = 80;

    private final VipsRunner vips;
    private final Path dataDir;

    @Autowired
    public ThumbnailService(VipsRunner vips, KuratoonProperties properties) {
        this(vips, properties.paths().dataDir());
    }

    public ThumbnailService(VipsRunner vips, Path dataDir) {
        this.vips = vips;
        this.dataDir = dataDir;
    }

    /** {@code /data/thumbs/<mediaId>/} */
    public Path coverDir(UUID mediaId) {
        return dataDir.resolve("thumbs").resolve(mediaId.toString());
    }

    public Path coverThumb(UUID mediaId, int width) {
        return coverDir(mediaId).resolve("cover-" + width + ".webp");
    }

    public boolean coverThumbsExist(UUID mediaId) {
        return COVER_WIDTHS.stream().allMatch(w -> Files.isRegularFile(coverThumb(mediaId, w)));
    }

    /** Cover widths 240/480/720 at exactly 2:3 from the original and its crop (null: centered). */
    public void generateCover(UUID mediaId, Path original, CoverCrop crop) throws ImageToolException {
        ImageInfo info = probe(original);
        CropRect rect = CropRect.forAspect(info.width(), info.height(), crop, 2, 3);
        cropAndResize(original, rect, COVER_WIDTHS, w -> w * 3 / 2, w -> coverThumb(mediaId, w));
    }

    public void deleteCoverThumbs(UUID mediaId) throws IOException {
        FileSystemUtils.deleteRecursively(coverDir(mediaId));
    }

    /**
     * Crops {@code source} (after applying its EXIF orientation) and writes one WebP per width.
     *
     * @param heightFor output height for a width; the crop already has the target aspect
     * @param outputFor final path for a width
     */
    public void cropAndResize(
            Path source, CropRect rect, List<Integer> widths, IntFunction<Integer> heightFor, IntFunction<Path> outputFor)
            throws ImageToolException {
        Path firstOutput = outputFor.apply(widths.getFirst());
        Path work;
        try {
            Files.createDirectories(firstOutput.getParent());
            work = Files.createTempDirectory(firstOutput.getParent(), ".tmp-");
        } catch (IOException e) {
            throw new ImageToolException("cannot create " + firstOutput.getParent() + ": " + e.getMessage(), e);
        }
        try {
            Path rotated = work.resolve("rotated.v");
            Path cropped = work.resolve("cropped.v");
            vips.run("autorot", source.toString(), rotated.toString());
            vips.run("extract_area", rotated.toString(), cropped.toString(),
                    Integer.toString(rect.left()), Integer.toString(rect.top()),
                    Integer.toString(rect.width()), Integer.toString(rect.height()));
            for (int width : widths) {
                Path out = work.resolve("out-" + width + ".webp");
                vips.run("thumbnail", cropped.toString(),
                        out + String.format(Locale.ROOT, "[Q=%d,strip]", WEBP_QUALITY),
                        Integer.toString(width),
                        "--height", Integer.toString(heightFor.apply(width)),
                        "--size", "force");
                Path target = outputFor.apply(width);
                Files.createDirectories(target.getParent());
                Files.move(out, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
        } catch (IOException e) {
            throw new ImageToolException("cannot write thumbnails: " + e.getMessage(), e);
        } finally {
            try {
                FileSystemUtils.deleteRecursively(work);
            } catch (IOException ignored) {
                // A leftover .tmp-* dir is harmless; the next run uses a new one.
            }
        }
    }

    private static ImageInfo probe(Path file) throws ImageToolException {
        try {
            return ImageProbe.probe(file);
        } catch (IOException | UnreadableImageException e) {
            throw new ImageToolException("cannot read " + file.getFileName() + ": " + e.getMessage(), e);
        }
    }
}
