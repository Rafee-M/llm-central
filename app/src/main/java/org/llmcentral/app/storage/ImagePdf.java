package org.llmcentral.app.storage;

import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Puts JPEG photos into a PDF without touching their pixels: one page per photo, the JPEG bytes copied
 * into the file as they are (a PDF can hold a JPEG directly, as a "DCTDecode" image). Plain Java, unit-tested.
 *
 * <p><b>Why:</b> duck.ai shrinks every attached <i>image</i> to 512 px (free models) or 1024 px before sending
 * it, but sends a <i>PDF</i> unchanged (its own code, checked 2026-10-27; CHANGES.md). A photo inside a PDF
 * therefore reaches the model at full resolution. The owner confirmed on the phone that duck.ai reads it.</p>
 *
 * <p><b>Rotation:</b> phone cameras store the picture sideways and note the turn in the EXIF "orientation".
 * PDF readers ignore EXIF, so the turn is drawn: the page gets the turned size and the picture is placed with
 * a rotation matrix ({@code cm}). The JPEG itself stays unchanged.</p>
 *
 * <p>Format reference: ISO 32000-1 (PDF 1.7) §7.5 file structure, §8.3.4 the {@code cm} matrix, §8.9 images,
 * §7.4.8 DCTDecode. A page is as many points as the photo has pixels, so a reader drawing it at its natural size
 * shows every pixel.</p>
 */
public final class ImagePdf {

    /** Largest page side, in points (ISO 32000-1 Annex C: 14 400 units = 200 inches). */
    static final int MAX_PAGE = 14400;

    private ImagePdf() {
        // Only static methods.
    }

    /** One photo, ready to be a page: an unchanged JPEG that a PDF can hold as it is. */
    public static final class Page {
        final byte[] jpeg;
        final int width;
        final int height;
        /** 1 = grey, 3 = colour. */
        final int components;
        /** Clockwise turn needed to show the photo upright: 0, 90, 180 or 270. */
        final int rotation;

        private Page(byte[] jpeg, int width, int height, int components, int rotation) {
            this.jpeg = jpeg;
            this.width = width;
            this.height = height;
            this.components = components;
            this.rotation = rotation;
        }

        /**
         * @param jpeg     the file's bytes
         * @param rotation clockwise turn to show it upright (from the EXIF orientation): 0, 90, 180 or 270
         * @return the page, or null if these bytes can't go into a PDF unchanged: not a JPEG, a cut-off one,
         *         an unusual kind (lossless, arithmetic-coded) or CMYK colours. The caller then re-encodes.
         */
        public static Page of(byte[] jpeg, int rotation) {
            if (rotation != 0 && rotation != 90 && rotation != 180 && rotation != 270) {
                return null;
            }
            int[] info = frameInfo(jpeg);
            if (info == null) {
                return null;
            }
            return new Page(jpeg, info[0], info[1], info[2], rotation);
        }
    }

    /**
     * Reads the "start of frame" header: {width, height, colour components}, or null.
     * A JPEG is a list of segments, each "FF xx" + a 2-byte length; the SOF segment holds the size.
     */
    static int[] frameInfo(byte[] b) {
        if (b == null || b.length < 4 || (b[0] & 0xFF) != 0xFF || (b[1] & 0xFF) != 0xD8) {
            return null;
        }
        int i = 2;
        while (i + 4 <= b.length) {
            if ((b[i] & 0xFF) != 0xFF) {
                return null;
            }
            int marker = b[i + 1] & 0xFF;
            if (marker == 0xFF) { // fill byte
                i++;
                continue;
            }
            if (marker == 0xD9 || marker == 0xDA) { // end of image / image data started without a frame header
                return null;
            }
            int length = (b[i + 2] & 0xFF) << 8 | (b[i + 3] & 0xFF);
            // SOF0 baseline, SOF1 extended, SOF2 progressive: what PDF's DCTDecode reads. (C4, C8, CC are other segments.)
            if (marker == 0xC0 || marker == 0xC1 || marker == 0xC2) {
                if (i + 10 > b.length) {
                    return null;
                }
                int height = (b[i + 5] & 0xFF) << 8 | (b[i + 6] & 0xFF);
                int width = (b[i + 7] & 0xFF) << 8 | (b[i + 8] & 0xFF);
                int components = b[i + 9] & 0xFF;
                // a page is as many points as the photo has pixels, and PDF 1.4 pages stop at 14400 points
                boolean ok = width > 0 && height > 0 && width <= MAX_PAGE && height <= MAX_PAGE
                        && (components == 1 || components == 3);
                return ok ? new int[]{width, height, components} : null;
            }
            if (marker >= 0xC3 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                return null; // lossless or arithmetic-coded frame: not for DCTDecode
            }
            i += 2 + length;
        }
        return null;
    }

    /**
     * Would the page's file field take a PDF? Its {@code accept} list (WebView's getAcceptTypes(); each entry may
     * itself be a comma list): empty = anything; otherwise PDF must be named ("application/pdf", ".pdf") or
     * allowed by a wildcard. duck.ai's models without PDF support (Gemma 4) ask for images only.
     */
    public static boolean acceptsPdf(String[] accept) {
        boolean any = false;
        if (accept != null) {
            for (String entry : accept) {
                if (entry == null) {
                    continue;
                }
                for (String raw : entry.split(",")) {
                    String type = raw.trim().toLowerCase(java.util.Locale.ROOT);
                    if (type.isEmpty()) {
                        continue;
                    }
                    any = true;
                    if (type.equals("application/pdf") || type.equals(".pdf") || type.equals("application/*")
                            || type.equals("*/*")) {
                        return true;
                    }
                }
            }
        }
        return !any;
    }

    /**
     * The EXIF orientation (1–8; 1 = upright) from the start of a JPEG, or 1 if there is none or it can't be read.
     * Only this one tag is needed, so it is read here (bounds-checked) rather than with a whole EXIF library
     * (lint warns about android.media.ExifInterface's bugs on old Androids; androidx's would add a dependency).
     * EXIF = an APP1 segment "Exif\0\0" holding a TIFF block: byte order (II/MM), 42, offset of the first
     * directory, then 12-byte entries; tag 0x0112 is the orientation (TIFF 6.0 / EXIF 2.3 §4.6.4).
     */
    public static int exifOrientation(byte[] b) {
        if (b == null || b.length < 4 || (b[0] & 0xFF) != 0xFF || (b[1] & 0xFF) != 0xD8) {
            return 1;
        }
        int i = 2;
        while (i + 4 <= b.length && (b[i] & 0xFF) == 0xFF) {
            int marker = b[i + 1] & 0xFF;
            if (marker == 0xDA || marker == 0xD9) {
                return 1; // picture data started: no EXIF before it
            }
            int length = (b[i + 2] & 0xFF) << 8 | (b[i + 3] & 0xFF);
            if (marker == 0xE1 && length >= 8 && i + 10 <= b.length
                    && b[i + 4] == 'E' && b[i + 5] == 'x' && b[i + 6] == 'i' && b[i + 7] == 'f' && b[i + 8] == 0 && b[i + 9] == 0) {
                int tiff = i + 10, end = Math.min(b.length, i + 2 + length);
                if (tiff + 8 > end) {
                    return 1;
                }
                boolean little = b[tiff] == 'I' && b[tiff + 1] == 'I';
                if (!little && !(b[tiff] == 'M' && b[tiff + 1] == 'M')) {
                    return 1;
                }
                long ifd = tiff + number(b, tiff + 4, 4, little);
                if (ifd + 2 > end) {
                    return 1;
                }
                int entries = (int) number(b, (int) ifd, 2, little);
                for (int e = 0; e < entries; e++) {
                    int at = (int) ifd + 2 + 12 * e;
                    if (at + 12 > end) {
                        return 1;
                    }
                    if (number(b, at, 2, little) == 0x0112 && number(b, at + 2, 2, little) == 3) { // SHORT
                        int value = (int) number(b, at + 8, 2, little);
                        return value >= 1 && value <= 8 ? value : 1;
                    }
                }
                return 1;
            }
            i += 2 + length;
        }
        return 1;
    }

    private static long number(byte[] b, int at, int size, boolean little) {
        long v = 0;
        for (int k = 0; k < size; k++) {
            int unsigned = b[little ? at + size - 1 - k : at + k] & 0xFF;
            v = v << 8 | unsigned;
        }
        return v;
    }

    /** The PDF file with one page per photo, in order, as bytes (tests; the app writes straight to a file). */
    public static byte[] write(List<Page> pages) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            writeTo(pages, bytes);
        } catch (IOException e) {
            throw new IllegalStateException(e); // a byte array doesn't fail
        }
        return bytes.toByteArray();
    }

    /** Writes the PDF to {@code stream} (not closed). The photos are copied straight through, never held twice. */
    public static void writeTo(List<Page> pages, OutputStream stream) throws IOException {
        Out out = new Out(stream);
        out.text("%PDF-1.4\n%âãÏÓ\n"); // the 4 high bytes mark the file as binary
        List<Integer> offsets = new ArrayList<>();
        int count = pages.size();
        // objects: 1 catalog, 2 page list, then per photo i: 3+3i page, 4+3i drawing, 5+3i picture
        offsets.add(out.size());
        out.text("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < count; i++) {
            kids.append(i == 0 ? "" : " ").append(3 + 3 * i).append(" 0 R");
        }
        offsets.add(out.size());
        out.text("2 0 obj\n<< /Type /Pages /Kids [" + kids + "] /Count " + count + " >>\nendobj\n");
        for (int i = 0; i < count; i++) {
            Page p = pages.get(i);
            int page = 3 + 3 * i, content = page + 1, image = page + 2;
            boolean sideways = p.rotation == 90 || p.rotation == 270;
            int w = sideways ? p.height : p.width, h = sideways ? p.width : p.height;
            offsets.add(out.size());
            out.text(page + " 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + w + " " + h + "]"
                    + " /Resources << /XObject << /Im0 " + image + " 0 R >> >> /Contents " + content + " 0 R >>\nendobj\n");
            String draw = "q " + matrix(p.rotation, w, h) + " cm /Im0 Do Q\n";
            offsets.add(out.size());
            out.text(content + " 0 obj\n<< /Length " + draw.length() + " >>\nstream\n" + draw + "endstream\nendobj\n");
            offsets.add(out.size());
            out.text(image + " 0 obj\n<< /Type /XObject /Subtype /Image /Width " + p.width + " /Height " + p.height
                    + " /ColorSpace " + (p.components == 1 ? "/DeviceGray" : "/DeviceRGB")
                    + " /BitsPerComponent 8 /Filter /DCTDecode /Length " + p.jpeg.length + " >>\nstream\n");
            out.write(p.jpeg, 0, p.jpeg.length);
            out.text("\nendstream\nendobj\n");
        }
        int xref = out.size();
        StringBuilder table = new StringBuilder("xref\n0 " + (offsets.size() + 1) + "\n0000000000 65535 f \n");
        for (int offset : offsets) {
            table.append(String.format(java.util.Locale.ROOT, "%010d 00000 n \n", offset));
        }
        out.text(table + "trailer\n<< /Size " + (offsets.size() + 1) + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
        out.flush();
    }

    /**
     * Where the picture goes on a w x h page, as a PDF matrix "a b c d e f": a picture is drawn into the unit
     * square (u to the right, v upwards, v = 1 = its top row) and x = a*u + c*v + e, y = b*u + d*v + f.
     */
    static String matrix(int rotation, int w, int h) {
        switch (rotation) {
            case 90:  // turn clockwise: the picture's top edge becomes the right edge
                return "0 -" + h + " " + w + " 0 0 " + h;
            case 180:
                return "-" + w + " 0 0 -" + h + " " + w + " " + h;
            case 270: // turn anticlockwise: the top edge becomes the left edge
                return "0 " + h + " -" + w + " 0 " + w + " 0";
            default:
                return w + " 0 0 " + h + " 0 0";
        }
    }

    /** Counts the bytes written (the cross-reference table needs every object's position) and takes PDF text. */
    private static final class Out extends FilterOutputStream {
        private int size;

        Out(OutputStream out) {
            super(out);
        }

        int size() {
            return size;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len); // FilterOutputStream's own version would go byte by byte
            size += len;
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
            size++;
        }

        /** PDF text is ASCII; the 4 marker bytes in the header are Latin-1. */
        void text(String s) throws IOException {
            byte[] bytes = s.getBytes(StandardCharsets.ISO_8859_1);
            write(bytes, 0, bytes.length);
        }
    }
}
