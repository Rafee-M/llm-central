package org.llmcentral.app;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

/**
 * Checks the SHA-256 helpers used to verify the downloaded converter (R-SEC-2) against the
 * official test vectors from the SHA-2 standard (FIPS 180-2), for both the byte-array and file versions.
 */
public class DocConverterHashTest {

    private static final String ABC_SHA256 =
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
    private static final String EMPTY_SHA256 =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @Test
    public void byteArrayHashMatchesStandardVectors() throws Exception {
        assertEquals(ABC_SHA256, DocConverter.sha256Hex("abc".getBytes(StandardCharsets.US_ASCII)));
        assertEquals(EMPTY_SHA256, DocConverter.sha256Hex(new byte[0]));
    }

    @Test
    public void fileHashMatchesByteArrayHash() throws Exception {
        File tmp = File.createTempFile("hash", ".bin");
        tmp.deleteOnExit();
        // Larger than the 16 KB read buffer, so the chunked reading loop is exercised too.
        byte[] data = new byte[50_000];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i * 31);
        }
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(data);
        }
        assertEquals(DocConverter.sha256Hex(data), DocConverter.sha256Hex(tmp));
    }
}
