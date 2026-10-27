package org.llmcentral.app.storage;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * duck.ai's 📎 → "Photos as PDF (sharper)": turns the picked photos into one PDF, one page each, so duck.ai sends
 * them at full quality instead of shrinking them to 512 px (see {@link ImagePdf} for why that works).
 * Used only on duck.ai tabs (MainActivity offers the option there only); nothing here runs otherwise.
 *
 * <p><b>Light on the phone:</b></p>
 * <ul>
 *   <li>A JPEG that fits the size budget goes in <b>unchanged</b>: its bytes are copied, never decoded.</li>
 *   <li>Anything else (PNG, HEIC, WebP, a mirrored selfie, a photo too big for the budget) is decoded <b>one at a
 *       time</b>, already shrunk while decoding (inSampleSize) to at most {@link #MAX_SIDE} px, saved as JPEG,
 *       and its Bitmap freed right away (recycle).</li>
 *   <li>The PDF is written straight into the file; the photos' bytes are held once (at most {@link #MAX_BYTES}).</li>
 *   <li>It runs on {@link UploadCopies}' threads and lands in {@code cache/uploads/}, so it adds no threads and
 *       is deleted by the same clean-ups as attached-file copies (app start, after 1 hour, Settings → Clear cache).</li>
 * </ul>
 */
public final class PhotoPdf {

    private static final String TAG = "PhotoPdf";

    /** duck.ai's free tier: 5 MB per file and 15 pages per PDF (its own model list, checked 2026-10-27). */
    public static final int MAX_PAGES = 15;
    static final int MAX_BYTES = 5 * 1024 * 1024 - 64 * 1024; // room for the PDF's own few hundred bytes per page
    /** Longest side of a re-encoded photo: sharp enough for small print, small enough for the budget. */
    static final int MAX_SIDE = 3072;

    /** Receives the PDF ({@code file://} address) or null, and a short reason when it failed. On the main thread. */
    public interface Done {
        void onDone(Uri pdf, String problem);
    }

    private PhotoPdf() {
        // Only static methods.
    }

    /** Makes the PDF in the background and answers on the main thread. At most {@link #MAX_PAGES} photos. */
    public static void make(Context context, List<Uri> photos, Done done) {
        Context app = context.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        List<Uri> list = new ArrayList<>(photos.subList(0, Math.min(photos.size(), MAX_PAGES)));
        UploadCopies.execute(app, () -> {
            File folder = UploadCopies.newFolder(app);
            try {
                int budget = MAX_BYTES / Math.max(1, list.size());
                List<ImagePdf.Page> pages = new ArrayList<>();
                for (Uri photo : list) {
                    ImagePdf.Page page = page(app, photo, budget);
                    if (page == null) {
                        throw new IOException("A picture couldn't be read (on Android 8 and older, HEIC photos can't be).");
                    }
                    pages.add(page);
                }
                if (!folder.mkdirs()) {
                    throw new IOException("No room for the PDF.");
                }
                File pdf = new File(folder, list.size() == 1 ? "Photo.pdf" : "Photos.pdf");
                try (OutputStream out = new BufferedOutputStream(new FileOutputStream(pdf), 64 * 1024)) {
                    ImagePdf.writeTo(pages, out);
                }
                Uri result = Uri.fromFile(pdf);
                main.post(() -> done.onDone(result, null));
            } catch (Exception | OutOfMemoryError e) {
                Log.w(TAG, "PDF not made", e);
                AppCache.deleteFolder(folder);
                String problem = e instanceof IOException && e.getMessage() != null ? e.getMessage()
                        : "The PDF couldn't be made.";
                main.post(() -> done.onDone(null, problem));
            }
        });
    }

    /** One photo as a page within {@code budget} bytes: unchanged if possible, else re-encoded. Null = unreadable. */
    private static ImagePdf.Page page(Context context, Uri uri, int budget) throws IOException {
        byte[] original = read(context, uri, budget);
        int exif = 1;
        if (original != null) {
            exif = ImagePdf.exifOrientation(original);
            int turn = clockwiseTurn(exif);
            if (turn >= 0) {
                ImagePdf.Page page = ImagePdf.Page.of(original, turn);
                if (page != null) {
                    return page; // the common case: a camera JPEG, copied as it is
                }
            }
        } else {
            // Too big to embed unchanged: only the EXIF block at the start is needed for the turn (EXIF must fit
            // in one 64 KB segment, so the first 128 KB always hold it).
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                if (in != null) {
                    byte[] head = new byte[128 * 1024];
                    int n = 0, r;
                    while (n < head.length && (r = in.read(head, n, head.length - n)) != -1) {
                        n += r;
                    }
                    exif = ImagePdf.exifOrientation(java.util.Arrays.copyOf(head, n));
                }
            } catch (Exception e) {
                Log.w(TAG, "No EXIF", e);
            }
        }
        byte[] jpeg = reencode(context, uri, exif, budget);
        return jpeg == null ? null : ImagePdf.Page.of(jpeg, 0);
    }

    /** The file's bytes if it has at most {@code limit} of them; null if it's bigger. */
    private static byte[] read(Context context, Uri uri, int limit) throws IOException {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) {
                throw new IOException("A picture couldn't be opened.");
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) != -1) {
                if (bytes.size() + n > limit) {
                    return null;
                }
                bytes.write(buffer, 0, n);
            }
            return bytes.toByteArray();
        }
    }

    /**
     * EXIF orientation (1–8) → clockwise turn for an unchanged JPEG; -1 for the mirrored ones (2, 4, 5, 7: they get
     * re-encoded). 1 upright, 3 upside down, 6 turned right, 8 turned left.
     */
    static int clockwiseTurn(int exif) {
        switch (exif) {
            case 6: return 90;
            case 3: return 180;
            case 8: return 270;
            case 1: return 0;
            default: return -1;
        }
    }

    /**
     * Decodes the picture shrunk to at most {@link #MAX_SIDE} px, turns it upright and saves it as a JPEG of at
     * most {@code budget} bytes (lower quality, then smaller, until it fits). One Bitmap at a time, freed at the end.
     */
    private static byte[] reencode(Context context, Uri uri, int exif, int budget) throws IOException {
        BitmapFactory.Options size = new BitmapFactory.Options();
        size.inJustDecodeBounds = true;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, size);
        }
        if (size.outWidth <= 0 || size.outHeight <= 0) {
            return null; // not a picture Android can read here
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (Math.max(size.outWidth, size.outHeight) / options.inSampleSize > MAX_SIDE) {
            options.inSampleSize *= 2; // decoding at 1/2, 1/4… is cheap and keeps memory low
        }
        Bitmap bitmap;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            bitmap = BitmapFactory.decodeStream(in, null, options);
        }
        if (bitmap == null) {
            return null;
        }
        try {
            bitmap = upright(bitmap, exif);
            ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
            for (int round = 0; round < 4; round++) {
                for (int quality = 90; quality >= 60; quality -= 10) {
                    jpeg.reset();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, jpeg);
                    if (jpeg.size() <= budget) {
                        return jpeg.toByteArray();
                    }
                }
                Bitmap smaller = Bitmap.createScaledBitmap(bitmap, Math.max(1, bitmap.getWidth() * 3 / 4),
                        Math.max(1, bitmap.getHeight() * 3 / 4), true);
                if (smaller != bitmap) {
                    bitmap.recycle();
                    bitmap = smaller;
                }
            }
            throw new IOException("The pictures are too big for one PDF; pick fewer.");
        } finally {
            bitmap.recycle();
        }
    }

    /** The bitmap turned/mirrored by its EXIF orientation (the old one is freed if a new one was made). */
    private static Bitmap upright(Bitmap bitmap, int exif) {
        Matrix m = new Matrix();
        // EXIF orientations (same numbers as androidx ExifInterface.ORIENTATION_*): 2 mirrored, 3 upside down,
        // 4 mirrored upside down, 5 mirrored + turned right, 6 turned right, 7 mirrored + turned left, 8 turned left
        switch (exif) {
            case 2: m.setScale(-1, 1); break;
            case 3: m.setRotate(180); break;
            case 4: m.setScale(1, -1); break;
            case 5: m.setRotate(90); m.postScale(-1, 1); break;
            case 6: m.setRotate(90); break;
            case 7: m.setRotate(-90); m.postScale(-1, 1); break;
            case 8: m.setRotate(-90); break;
            default: return bitmap;
        }
        Bitmap turned = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
        if (turned != bitmap) {
            bitmap.recycle();
        }
        return turned;
    }
}
