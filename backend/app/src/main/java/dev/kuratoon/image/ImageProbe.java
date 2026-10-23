package dev.kuratoon.image;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Reads image dimensions from the file header and checks the file is not truncated, without
 * decoding pixels (jpg, png, gif, webp, avif). Not a full decode: a file with a valid header and
 * end marker but damaged pixel data passes.
 *
 * <p>JPEG dimensions are reported as displayed: EXIF orientations 5–8 swap width and height,
 * because browsers apply the orientation when rendering.
 */
public final class ImageProbe {

    /** @param format jpeg, png, gif, webp or avif */
    public record ImageInfo(String format, int width, int height) {}

    /** The file is not a readable image; the message says why. */
    public static class UnreadableImageException extends Exception {
        public UnreadableImageException(String message) {
            super(message);
        }
    }

    /** Window at the end of a JPEG searched for the EOI marker (trailing bytes are tolerated). */
    static final int JPEG_EOI_WINDOW = 64 * 1024;

    private ImageProbe() {}

    public static ImageInfo probe(Path file) throws IOException, UnreadableImageException {
        try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
            Reader r = new Reader(ch);
            byte[] head = r.bytes(0, (int) Math.min(16, r.size));
            if (startsWith(head, 0xFF, 0xD8, 0xFF)) {
                return jpeg(r);
            }
            if (startsWith(head, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
                return png(r);
            }
            if (ascii(head, 0, "GIF87a") || ascii(head, 0, "GIF89a")) {
                return gif(r);
            }
            if (ascii(head, 0, "RIFF") && ascii(head, 8, "WEBP")) {
                return webp(r);
            }
            if (ascii(head, 4, "ftyp")) {
                return avif(r);
            }
            throw new UnreadableImageException("not a supported image (jpg, png, gif, webp, avif)");
        } catch (TruncatedException e) {
            throw new UnreadableImageException("file is truncated");
        }
    }

    // --- JPEG -----------------------------------------------------------------------------------

    private static ImageInfo jpeg(Reader r) throws IOException, UnreadableImageException {
        long pos = 2;
        int width = -1;
        int height = -1;
        int orientation = 1;
        long scanStart = -1;
        while (pos + 4 <= r.size) {
            if (r.u8(pos) != 0xFF) {
                throw new UnreadableImageException("corrupt JPEG marker structure");
            }
            int marker = r.u8(pos + 1);
            if (marker == 0xFF) {
                pos++; // fill byte
                continue;
            }
            if (marker == 0xD8 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                pos += 2;
                continue;
            }
            int length = r.u16be(pos + 2);
            if (length < 2) {
                throw new UnreadableImageException("corrupt JPEG segment length");
            }
            long payload = pos + 4;
            if (isSof(marker)) {
                height = r.u16be(payload + 1);
                width = r.u16be(payload + 3);
            } else if (marker == 0xE1 && length > 8 && ascii(r.bytes(payload, 6), 0, "Exif\0\0")) {
                orientation = exifOrientation(r.bytes(payload + 6, Math.min(length - 8, 64 * 1024)));
            } else if (marker == 0xDA) {
                scanStart = payload + length - 2;
                break;
            }
            pos = payload + length - 2;
        }
        if (scanStart < 0) {
            // The file ended before the image data started.
            throw new TruncatedException();
        }
        if (width <= 0 || height <= 0) {
            throw new UnreadableImageException("JPEG header has no frame size");
        }
        // Entropy-coded data stuffs 0xFF bytes, so FFD9 after the scan start is the end marker.
        long from = Math.max(scanStart, r.size - JPEG_EOI_WINDOW);
        byte[] tail = r.bytes(from, (int) (r.size - from));
        boolean eoi = false;
        for (int i = tail.length - 2; i >= 0 && !eoi; i--) {
            eoi = (tail[i] & 0xFF) == 0xFF && (tail[i + 1] & 0xFF) == 0xD9;
        }
        if (!eoi) {
            throw new TruncatedException();
        }
        boolean swap = orientation >= 5 && orientation <= 8;
        return new ImageInfo("jpeg", swap ? height : width, swap ? width : height);
    }

    private static boolean isSof(int marker) {
        return marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC;
    }

    /** Orientation tag (0x0112) of IFD0, 1 when absent or unreadable. */
    static int exifOrientation(byte[] tiff) {
        if (tiff.length < 8) {
            return 1;
        }
        ByteBuffer b = ByteBuffer.wrap(tiff);
        if (tiff[0] == 'I' && tiff[1] == 'I') {
            b.order(ByteOrder.LITTLE_ENDIAN);
        } else if (tiff[0] != 'M' || tiff[1] != 'M') {
            return 1;
        }
        long ifd = Integer.toUnsignedLong(b.getInt(4));
        if (ifd + 2 > tiff.length) {
            return 1;
        }
        int entries = Short.toUnsignedInt(b.getShort((int) ifd));
        for (int i = 0; i < entries; i++) {
            int entry = (int) ifd + 2 + i * 12;
            if (entry + 12 > tiff.length) {
                return 1;
            }
            if (Short.toUnsignedInt(b.getShort(entry)) == 0x0112) {
                int value = Short.toUnsignedInt(b.getShort(entry + 8));
                return value >= 1 && value <= 8 ? value : 1;
            }
        }
        return 1;
    }

    // --- PNG ------------------------------------------------------------------------------------

    private static ImageInfo png(Reader r) throws IOException, UnreadableImageException {
        if (!ascii(r.bytes(12, 4), 0, "IHDR")) {
            throw new UnreadableImageException("PNG has no IHDR chunk");
        }
        int width = r.s32be(16);
        int height = r.s32be(20);
        // Walk the chunks: a missing IEND (or a chunk running past the end) means truncation.
        long pos = 8;
        while (true) {
            long length = Integer.toUnsignedLong(r.s32be(pos));
            String type = new String(r.bytes(pos + 4, 4), StandardCharsets.US_ASCII);
            long next = pos + 12 + length;
            if (next > r.size) {
                throw new TruncatedException();
            }
            if (type.equals("IEND")) {
                break;
            }
            pos = next;
        }
        return checked("png", width, height);
    }

    // --- GIF ------------------------------------------------------------------------------------

    private static ImageInfo gif(Reader r) throws IOException, UnreadableImageException {
        int width = r.u16le(6);
        int height = r.u16le(8);
        byte[] tail = r.bytes(Math.max(0, r.size - 16), (int) Math.min(16, r.size));
        boolean trailer = false;
        for (byte value : tail) {
            trailer |= value == 0x3B;
        }
        if (!trailer) {
            throw new TruncatedException();
        }
        return checked("gif", width, height);
    }

    // --- WebP -----------------------------------------------------------------------------------

    private static ImageInfo webp(Reader r) throws IOException, UnreadableImageException {
        long riffSize = Integer.toUnsignedLong(r.s32le(4));
        if (riffSize + 8 > r.size) {
            throw new TruncatedException();
        }
        String chunk = new String(r.bytes(12, 4), StandardCharsets.US_ASCII);
        return switch (chunk) {
            case "VP8X" -> checked("webp", r.u24le(24) + 1, r.u24le(27) + 1);
            case "VP8 " -> {
                byte[] frame = r.bytes(23, 7);
                if ((frame[0] & 0xFF) != 0x9D || (frame[1] & 0xFF) != 0x01 || (frame[2] & 0xFF) != 0x2A) {
                    throw new UnreadableImageException("corrupt WebP (VP8) frame header");
                }
                yield checked("webp", r.u16le(26) & 0x3FFF, r.u16le(28) & 0x3FFF);
            }
            case "VP8L" -> {
                if (r.u8(20) != 0x2F) {
                    throw new UnreadableImageException("corrupt WebP (VP8L) signature");
                }
                long bits = Integer.toUnsignedLong(r.s32le(21));
                yield checked("webp", (int) (bits & 0x3FFF) + 1, (int) ((bits >> 14) & 0x3FFF) + 1);
            }
            default -> throw new UnreadableImageException("unknown WebP chunk " + chunk.strip());
        };
    }

    // --- AVIF -----------------------------------------------------------------------------------

    private static ImageInfo avif(Reader r) throws IOException, UnreadableImageException {
        String brand = new String(r.bytes(8, 4), StandardCharsets.US_ASCII);
        if (!brand.equals("avif") && !brand.equals("avis")) {
            throw new UnreadableImageException("not an AVIF file (brand " + brand + ")");
        }
        // Top-level boxes must chain exactly up to the end of the file.
        long pos = 0;
        long metaStart = -1;
        long metaEnd = -1;
        while (pos < r.size) {
            if (pos + 8 > r.size) {
                throw new TruncatedException();
            }
            long size = Integer.toUnsignedLong(r.s32be(pos));
            String type = new String(r.bytes(pos + 4, 4), StandardCharsets.US_ASCII);
            if (size == 1) {
                size = r.s64be(pos + 8);
            } else if (size == 0) {
                size = r.size - pos;
            }
            if (size < 8 || pos + size > r.size) {
                throw new TruncatedException();
            }
            if (type.equals("meta")) {
                metaStart = pos;
                metaEnd = pos + size;
            }
            pos += size;
        }
        if (metaStart < 0) {
            throw new UnreadableImageException("AVIF has no meta box");
        }
        byte[] meta = r.bytes(metaStart, (int) Math.min(metaEnd - metaStart, 1024 * 1024));
        int ispe = indexOf(meta, "ispe");
        if (ispe < 0 || ispe + 16 > meta.length) {
            throw new UnreadableImageException("AVIF has no image size (ispe)");
        }
        ByteBuffer b = ByteBuffer.wrap(meta);
        int width = b.getInt(ispe + 8);
        int height = b.getInt(ispe + 12);
        int irot = indexOf(meta, "irot");
        boolean swap = irot >= 0 && irot + 4 < meta.length && (meta[irot + 4] & 0x03) % 2 == 1;
        return checked("avif", swap ? height : width, swap ? width : height);
    }

    // --- helpers --------------------------------------------------------------------------------

    private static ImageInfo checked(String format, int width, int height) throws UnreadableImageException {
        if (width <= 0 || height <= 0) {
            throw new UnreadableImageException("invalid image dimensions " + width + "x" + height);
        }
        return new ImageInfo(format, width, height);
    }

    private static boolean startsWith(byte[] data, int... prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((data[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean ascii(byte[] data, int offset, String text) {
        if (data.length < offset + text.length()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (data[offset + i] != (byte) text.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static int indexOf(byte[] data, String text) {
        outer:
        for (int i = 0; i + text.length() <= data.length; i++) {
            for (int j = 0; j < text.length(); j++) {
                if (data[i + j] != (byte) text.charAt(j)) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    /** Reading past the end of the file. */
    private static final class TruncatedException extends RuntimeException {
        TruncatedException() {
            super(null, null, false, false);
        }
    }

    /** Positional reads; any read past the end of the file means the image is truncated. */
    private static final class Reader {
        private final FileChannel ch;
        final long size;

        Reader(FileChannel ch) throws IOException {
            this.ch = ch;
            this.size = ch.size();
        }

        byte[] bytes(long offset, int length) throws IOException {
            if (offset < 0 || length < 0 || offset + length > size) {
                throw new TruncatedException();
            }
            ByteBuffer buf = ByteBuffer.allocate(length);
            while (buf.hasRemaining()) {
                if (ch.read(buf, offset + buf.position()) < 0) {
                    throw new TruncatedException();
                }
            }
            return buf.array();
        }

        int u8(long offset) throws IOException {
            return bytes(offset, 1)[0] & 0xFF;
        }

        int u16be(long offset) throws IOException {
            return ByteBuffer.wrap(bytes(offset, 2)).getShort() & 0xFFFF;
        }

        int u16le(long offset) throws IOException {
            return ByteBuffer.wrap(bytes(offset, 2)).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
        }

        int u24le(long offset) throws IOException {
            byte[] b = bytes(offset, 3);
            return (b[0] & 0xFF) | (b[1] & 0xFF) << 8 | (b[2] & 0xFF) << 16;
        }

        int s32be(long offset) throws IOException {
            return ByteBuffer.wrap(bytes(offset, 4)).getInt();
        }

        int s32le(long offset) throws IOException {
            return ByteBuffer.wrap(bytes(offset, 4)).order(ByteOrder.LITTLE_ENDIAN).getInt();
        }

        long s64be(long offset) throws IOException {
            return ByteBuffer.wrap(bytes(offset, 8)).getLong();
        }
    }
}
