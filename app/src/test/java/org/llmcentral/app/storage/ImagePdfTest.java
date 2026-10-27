package org.llmcentral.app.storage;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** {@link ImagePdf}: photos put into a PDF unchanged, so duck.ai sends them at full quality. */
public class ImagePdfTest {

    /** Tiny test pictures in src/test/resources (made with Python's PIL; a gradient each). */
    private static byte[] file(String name) throws Exception {
        try (InputStream in = ImagePdfTest.class.getResourceAsStream(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }
    }

    @Test
    public void readsSizeAndColoursFromAJpegHeader() throws Exception {
        ImagePdf.Page colour = ImagePdf.Page.of(file("rgb_40x30.jpg"), 0);
        assertNotNull(colour);
        assertEquals(40, colour.width);
        assertEquals(30, colour.height);
        assertEquals(3, colour.components);
        ImagePdf.Page grey = ImagePdf.Page.of(file("grey_16x8.jpg"), 0);
        assertNotNull(grey);
        assertEquals(1, grey.components);
        ImagePdf.Page progressive = ImagePdf.Page.of(file("progressive_40x30.jpg"), 0);
        assertNotNull("progressive JPEGs are allowed in PDF 1.3+", progressive);
        assertEquals(40, progressive.width);
    }

    @Test
    public void refusesWhatItCannotEmbedUnchanged() throws Exception {
        assertNull("a PNG needs re-encoding", ImagePdf.Page.of(file("rgb_8x8.png"), 0));
        byte[] jpeg = file("rgb_40x30.jpg");
        assertNull("cut-off JPEG", ImagePdf.Page.of(Arrays.copyOf(jpeg, 20), 0));
        assertNull("not a picture", ImagePdf.Page.of("hello".getBytes(StandardCharsets.US_ASCII), 0));
        assertNull("an odd rotation", ImagePdf.Page.of(jpeg, 45));
        assertNull("CMYK (4 colours) needs re-encoding", ImagePdf.Page.of(file("cmyk_16x8.jpg"), 0));
        // a panorama 20000 px wide: a PDF page can't be over 14400 points (PDF 1.4 limit), so it gets re-encoded
        byte[] panorama = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xC0, 0, 17, 8,
                0x03, (byte) 0xE8, 0x4E, 0x20, 3, 1, 0x22, 0, 2, 0x11, 1, 3, 0x11, 1};
        assertNull(ImagePdf.Page.of(panorama, 0));
    }

    @Test
    public void pdfHoldsTheJpegBytesUnchangedWithAValidCrossReferenceTable() throws Exception {
        byte[] jpeg = file("rgb_40x30.jpg");
        byte[] pdf = ImagePdf.write(Arrays.asList(ImagePdf.Page.of(jpeg, 0), ImagePdf.Page.of(jpeg, 90)));
        String text = new String(pdf, StandardCharsets.ISO_8859_1);
        assertTrue(text.startsWith("%PDF-1.4\n"));
        assertTrue(text.endsWith("%%EOF\n"));
        assertTrue(text.contains("/Count 2"));
        // the image stream is the original JPEG, byte for byte
        int at = text.indexOf("stream\n", text.indexOf("/DCTDecode")) + "stream\n".length();
        assertArrayEquals(jpeg, Arrays.copyOfRange(pdf, at, at + jpeg.length));
        // every cross-reference entry points at its "n 0 obj"
        int xref = Integer.parseInt(text.substring(text.lastIndexOf("startxref\n") + 10, text.lastIndexOf("\n%%EOF")).trim());
        assertTrue(text.startsWith("xref\n", xref));
        Matcher entries = Pattern.compile("(\\d{10}) 00000 n").matcher(text.substring(xref));
        int n = 0;
        while (entries.find()) {
            n++;
            assertTrue("object " + n, text.startsWith(n + " 0 obj", Integer.parseInt(entries.group(1))));
        }
        assertEquals(2 + 3 * 2, n); // catalog, pages, and page + content + image per picture
    }

    @Test
    public void aTurnedPhotoGetsATurnedPage() throws Exception {
        byte[] jpeg = file("rgb_40x30.jpg");
        String upright = new String(ImagePdf.write(Collections.singletonList(ImagePdf.Page.of(jpeg, 0))), StandardCharsets.ISO_8859_1);
        assertTrue(upright.contains("/MediaBox [0 0 40 30]"));
        assertTrue(upright.contains("40 0 0 30 0 0 cm"));
        String turned = new String(ImagePdf.write(Collections.singletonList(ImagePdf.Page.of(jpeg, 90))), StandardCharsets.ISO_8859_1);
        assertTrue("width and height swap", turned.contains("/MediaBox [0 0 30 40]"));
        assertTrue(turned.contains("0 -40 30 0 0 40 cm"));
    }

    @Test
    public void offeredOnlyWhenThePageTakesPdfs() {
        assertTrue("no list = anything", ImagePdf.acceptsPdf(null));
        assertTrue(ImagePdf.acceptsPdf(new String[]{""}));
        assertTrue(ImagePdf.acceptsPdf(new String[]{"image/png,image/jpeg,image/webp,image/gif,application/pdf"}));
        assertTrue(ImagePdf.acceptsPdf(new String[]{"image/*", " .PDF"}));
        assertTrue(ImagePdf.acceptsPdf(new String[]{"*/*"}));
        assertFalse("images only (a model without PDF support)",
                ImagePdf.acceptsPdf(new String[]{"image/png,image/jpeg,image/webp,image/gif"}));
    }

    @Test
    public void readsTheCameraOrientationFromExif() throws Exception {
        assertEquals("PIL's EXIF: big-endian (MM), after a JFIF segment", 6, ImagePdf.exifOrientation(file("rgb_40x30_exif6.jpg")));
        assertEquals("no EXIF = upright", 1, ImagePdf.exifOrientation(file("rgb_40x30.jpg")));
        assertEquals("not a JPEG", 1, ImagePdf.exifOrientation(file("rgb_8x8.png")));
        // little-endian (II) EXIF as many phone cameras write it: orientation 8, one IFD entry
        byte[] ii = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1, 0, 34,
            'E', 'x', 'i', 'f', 0, 0,
            'I', 'I', 42, 0, 8, 0, 0, 0,               // TIFF header, first IFD at 8
            1, 0,                                        // one entry
            0x12, 0x01, 3, 0, 1, 0, 0, 0, 8, 0, 0, 0,    // 0x0112 orientation, SHORT, 1 value: 8
            0, 0, 0, 0,                                  // no next IFD
            (byte) 0xFF, (byte) 0xD9};
        assertEquals(8, ImagePdf.exifOrientation(ii));
        // cut off in the middle of the IFD: no crash, upright
        assertEquals(1, ImagePdf.exifOrientation(Arrays.copyOf(ii, 26)));
    }
}
