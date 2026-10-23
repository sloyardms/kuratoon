package dev.kuratoon.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.kuratoon.image.ImageProbe.ImageInfo;
import dev.kuratoon.image.ImageProbe.UnreadableImageException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImageProbeTest {

    // 5x3 images made with Pillow 12.3 (no ImageIO for WebP/AVIF).
    private static final String WEBP_LOSSY =
            "UklGRjwAAABXRUJQVlA4IDAAAADQAQCdASoFAAMAAUAmJaACdLoB+AADsAD+9hNf/izkYjs83/4E78Cd+BO/8AAAAAA=";
    private static final String WEBP_LOSSLESS = "UklGRh4AAABXRUJQVlA4TBEAAAAvBIAAAAdQhSJXof+BiOh/AAA=";
    private static final String WEBP_ALPHA =
            "UklGRmAAAABXRUJQVlA4WAoAAAAQAAAABAAAAgAAQUxQSAoAAAABB1DAiAhERP8DVlA4IDAAAADQAQCdASoFAAMAAUAmJaACdLoB"
                    + "+AADsAD+9hNf/izkYjs83/4E78Cd+BO/8AAAAAA=";
    private static final String AVIF = "AAAAIGZ0eXBhdmlmAAAAAGF2aWZtaWYxbWlhZk1BMUIAAADrbWV0YQAAAAAAAAAhaGRscgAAAAAAAAAAcGlj"
            + "dAAAAAAAAAAAAAAAAAAAAAAOcGl0bQAAAAAAAQAAAB5pbG9jAAAAAEQAAAEAAQAAAAEAAAETAAAAKAAAAChpaW5mAAAAAAABAAAAGmlu"
            + "ZmUCAAAAAAEAAGF2MDFDb2xvcgAAAABqaXBycAAAAEtpcGNvAAAAFGlzcGUAAAAAAAAABQAAAAMAAAAQcGl4aQAAAAADCAgIAAAADGF2"
            + "MUOBAAwAAAAAE2NvbHJuY2x4AAEADQAGgAAAABdpcG1hAAAAAAAAAAEAAQQBAoMEAAAAMG1kYXQSAAoIGAhk0QENBoQyGheHh4YhhJJJ"
            + "JlAAAABIVDZhrGM3dRaWWMjF";
    /** 5x3 JPEG with EXIF orientation 6 (rotate 90°): displayed as 3x5. */
    private static final String JPEG_ROTATED = "/9j/4AAQSkZJRgABAQAAAQABAAD/4QAiRXhpZgAATU0AKgAAAAgAAQESAAMAAAABAAYAAAAAAAD/"
            + "2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwL"
            + "DBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAADAAUDASIAAhEBAxEB"
            + "/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEI"
            + "I0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWW"
            + "l5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAA"
            + "AAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl"
            + "8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0"
            + "tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwDgqKKK8M/VD//Z";

    @TempDir
    Path dir;

    @Test
    void jpeg() throws Exception {
        assertThat(probe("a.jpg", encode("jpg", 40, 60))).isEqualTo(new ImageInfo("jpeg", 40, 60));
    }

    @Test
    void jpegExifOrientationSwapsDimensions() throws Exception {
        assertThat(probe("r.jpg", decode(JPEG_ROTATED))).isEqualTo(new ImageInfo("jpeg", 3, 5));
    }

    @Test
    void jpegWithTrailingBytesAfterEoiIsAccepted() throws Exception {
        byte[] jpeg = encode("jpg", 40, 60);
        byte[] padded = Arrays.copyOf(jpeg, jpeg.length + 4096);
        Arrays.fill(padded, jpeg.length, padded.length, (byte) 0x00);
        assertThat(probe("pad.jpg", padded).width()).isEqualTo(40);
    }

    @Test
    void truncatedJpegIsRejected() throws Exception {
        byte[] jpeg = encode("jpg", 400, 600);
        assertUnreadable("half.jpg", Arrays.copyOf(jpeg, jpeg.length / 2), "truncated");
        // Cut inside the header: still reported as truncated, never an exception leaking out.
        assertUnreadable("head.jpg", Arrays.copyOf(jpeg, 100), "truncated");
    }

    @Test
    void png() throws Exception {
        assertThat(probe("a.png", encode("png", 7, 9))).isEqualTo(new ImageInfo("png", 7, 9));
    }

    @Test
    void truncatedPngIsRejected() throws Exception {
        byte[] png = encode("png", 300, 300);
        assertUnreadable("t.png", Arrays.copyOf(png, png.length - 20), "truncated");
    }

    @Test
    void gif() throws Exception {
        assertThat(probe("a.gif", encode("gif", 11, 4))).isEqualTo(new ImageInfo("gif", 11, 4));
    }

    @Test
    void webpLossyLosslessAndExtended() throws Exception {
        assertThat(probe("a.webp", decode(WEBP_LOSSY))).isEqualTo(new ImageInfo("webp", 5, 3));
        assertThat(probe("b.webp", decode(WEBP_LOSSLESS))).isEqualTo(new ImageInfo("webp", 5, 3));
        assertThat(probe("c.webp", decode(WEBP_ALPHA))).isEqualTo(new ImageInfo("webp", 5, 3));
    }

    @Test
    void truncatedWebpIsRejected() throws Exception {
        byte[] webp = decode(WEBP_LOSSY);
        assertUnreadable("t.webp", Arrays.copyOf(webp, webp.length - 8), "truncated");
    }

    @Test
    void avif() throws Exception {
        assertThat(probe("a.avif", decode(AVIF))).isEqualTo(new ImageInfo("avif", 5, 3));
        byte[] avif = decode(AVIF);
        assertUnreadable("t.avif", Arrays.copyOf(avif, avif.length - 10), "truncated");
    }

    @Test
    void garbageAndEmptyFilesAreRejected() throws Exception {
        assertUnreadable("g.jpg", "this is not an image".getBytes(), "not a supported image");
        assertUnreadable("e.jpg", new byte[0], "not a supported image");
    }

    private ImageInfo probe(String name, byte[] content) throws Exception {
        Path file = dir.resolve(name);
        Files.write(file, content);
        return ImageProbe.probe(file);
    }

    private void assertUnreadable(String name, byte[] content, String message) throws IOException {
        Path file = dir.resolve(name);
        Files.write(file, content);
        assertThatThrownBy(() -> ImageProbe.probe(file))
                .isInstanceOf(UnreadableImageException.class)
                .hasMessageContaining(message);
    }

    /** Test images for formats ImageIO writes natively (never used for WebP). */
    private static byte[] encode(String format, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, (x * 31 + y * 17) * 0x010101);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    private static byte[] decode(String base64) {
        return Base64.getDecoder().decode(base64);
    }
}
