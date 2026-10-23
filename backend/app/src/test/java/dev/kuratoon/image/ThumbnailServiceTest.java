package dev.kuratoon.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.kuratoon.media.CoverCrop;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Real libvips: exact output sizes and crop placement (docs/spec/06-images.md). */
@RequiresVips
class ThumbnailServiceTest {

    private static final int RED = 0xFF0000;
    private static final int BLUE = 0x0000FF;

    @TempDir
    Path dir;

    private VipsRunner vips;
    private ThumbnailService thumbnails;
    private Path original;
    private final UUID mediaId = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        vips = new VipsRunner(List.of("vips"), Duration.ofSeconds(60));
        thumbnails = new ThumbnailService(vips, dir.resolve("data"));
        // 400x900: left half red, right half blue.
        BufferedImage image = new BufferedImage(400, 900, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 400; x++) {
            for (int y = 0; y < 900; y++) {
                image.setRGB(x, y, x < 200 ? RED : BLUE);
            }
        }
        original = dir.resolve("cover.png");
        ImageIO.write(image, "png", original.toFile());
    }

    @Test
    void coverWidthsAreExactTwoByThreeWebp() throws Exception {
        thumbnails.generateCover(mediaId, original, null);
        for (int width : ThumbnailService.COVER_WIDTHS) {
            Path thumb = thumbnails.coverThumb(mediaId, width);
            assertThat(ImageProbe.probe(thumb)).isEqualTo(new ImageProbe.ImageInfo("webp", width, width * 3 / 2));
        }
        assertThat(thumbnails.coverThumbsExist(mediaId)).isTrue();
        // No temp files are left next to the thumbnails.
        try (Stream<Path> files = Files.list(thumbnails.coverDir(mediaId))) {
            assertThat(files.map(p -> p.getFileName().toString()))
                    .containsExactlyInAnyOrder("cover-240.webp", "cover-480.webp", "cover-720.webp");
        }
    }

    @Test
    void centeredCropShowsBothHalves() throws Exception {
        thumbnails.generateCover(mediaId, original, null);
        Path thumb = thumbnails.coverThumb(mediaId, 240);
        assertThat(pixel(thumb, 30, 180)).isEqualTo(RED);
        assertThat(pixel(thumb, 210, 180)).isEqualTo(BLUE);
    }

    @Test
    void cropSelectsTheRegion() throws Exception {
        thumbnails.generateCover(mediaId, original, new CoverCrop(0.5, 0, 0.5, 1));
        Path thumb = thumbnails.coverThumb(mediaId, 240);
        assertThat(pixel(thumb, 10, 10)).isEqualTo(BLUE);
        assertThat(pixel(thumb, 230, 350)).isEqualTo(BLUE);
    }

    @Test
    void regenerationOverwritesInPlace() throws Exception {
        thumbnails.generateCover(mediaId, original, new CoverCrop(0, 0, 0.5, 1));
        assertThat(pixel(thumbnails.coverThumb(mediaId, 480), 240, 360)).isEqualTo(RED);
        thumbnails.generateCover(mediaId, original, new CoverCrop(0.5, 0, 0.5, 1));
        assertThat(pixel(thumbnails.coverThumb(mediaId, 480), 240, 360)).isEqualTo(BLUE);
    }

    @Test
    void unreadableOriginalIsAnErrorAndWritesNothing() throws Exception {
        Path broken = dir.resolve("broken.jpg");
        Files.write(broken, Arrays.copyOf(Files.readAllBytes(original), 10));
        assertThatThrownBy(() -> thumbnails.generateCover(mediaId, broken, null))
                .isInstanceOf(ImageToolException.class);
        assertThat(thumbnails.coverThumbsExist(mediaId)).isFalse();
    }

    /** Pixel color (rgb, rounded to the nearest primary) read with {@code vips getpoint}. */
    private int pixel(Path image, int x, int y) throws Exception {
        String[] bands = vips.run("getpoint", image.toString(), Integer.toString(x), Integer.toString(y))
                .strip().split("\\s+");
        int r = (int) Double.parseDouble(bands[0]);
        int g = (int) Double.parseDouble(bands[1]);
        int b = (int) Double.parseDouble(bands[2]);
        return (r > 128 ? 0xFF0000 : 0) | (g > 128 ? 0x00FF00 : 0) | (b > 128 ? 0x0000FF : 0);
    }
}
