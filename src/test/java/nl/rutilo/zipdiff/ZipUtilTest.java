package nl.rutilo.zipdiff;

import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static nl.rutilo.zipdiff.ZipUtil.isEqual;
import static nl.rutilo.zipdiff.ZipUtil.sizeToString;
import static nl.rutilo.zipdiff.ZipUtil.toBytes;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ZipUtilTest {

    @Test
    public void testReadWriteZipWithTextHeader() throws IOException {
        final byte[] headerData = toBytes("Testing header before zip contents");
        final List<TestUtils.TestEntry> entries = ZipPatcherTest.entriesOld;
        final File file = File.createTempFile("test-file", ".zip");

        try {
            TestUtils.createZipFile(file, headerData, entries);

            // check that start of file is textHeader
            try (final FileInputStream fin = new FileInputStream(file)) {
                final byte[] data = ZipUtil.exhaust(fin);
                final byte[] text = headerData;
                for (int i = 0; i < text.length; i++) {
                    if (data[i] != text[i])
                        fail("Header text fail at pos " + i + ": " + (int) data[i] + " vs " + (int) text[i]);
                }
            }

            // check that this file can be read
            final ZipPatcher patcher = new ZipPatcher(file);
            assertThat(patcher.getHeaderData(), is(headerData));
            final Map<String,byte[]> allData = patcher.readFully();
            assertThat(allData.size(), is(entries.size()));

            // check that this file can be written
            file.delete();
            patcher.writeTo(file, allData);

            final ZipPatcher patcher2 = new ZipPatcher(file);
            assertThat(patcher2.getHeaderData(), is(headerData));
            final Map<String,byte[]> allData2 = patcher2.readFully();
            assertThat(allData2.size(), is(entries.size()));
        } finally {
            file.delete();
        }
    }

    @Test
    public void testOpenZipForReadingInvalidFile() throws IOException {
        final File testFile = File.createTempFile("test", ".zip");
        try {
            final OutputStream out = new FileOutputStream(testFile);
            out.write("not a zip".getBytes());
            out.close();

            try {
                ZipUtil.openZipForReading(testFile);
                fail("openZipForReading() should have thrown on illegal file");
            } catch(final IOException expected) {
                // expected
            }
        } finally {
            Files.delete(testFile.toPath());
        }
    }

    @Test
    public void testIsEqualByteArray() {
        final byte[] empty = new byte[0];
        final byte[] a = toBytes("abcdefg");
        final byte[] b = toBytes("abcefg");

        //noinspection ConstantConditions -- testing this is the point
        assertTrue(isEqual(null, null));
        assertTrue(isEqual(empty, empty));
        assertFalse(isEqual(null, empty));
        assertFalse(isEqual(empty, null));
        assertFalse(isEqual(a, empty));
        assertFalse(isEqual(empty, b));
        assertFalse(isEqual(a, b));
        assertTrue(isEqual(a, a));
        assertTrue(isEqual(b, b));
    }

    @Test
    public void testSizeToString() {
        final long KB = (long)Math.pow(1024, 1);
        final long MB = (long)Math.pow(1024, 2);
        final long GB = (long)Math.pow(1024, 3);
        final long TB = (long)Math.pow(1024, 4);
        final long PB = (long)Math.pow(1024, 5);
        final long EB = (long)Math.pow(1024, 6); // 60 bit, max long
        assertThat(sizeToString(     100), is("100 B"));
        assertThat(sizeToString(    1024), is("1024 B"));
        assertThat(sizeToString(  4 * KB), is("4096 B"));
        assertThat(sizeToString(  5 * KB), is("5 KB"));
        assertThat(sizeToString( 10 * KB), is("10 KB"));
        assertThat(sizeToString(125 * KB), is("125 KB"));
        assertThat(sizeToString(  4 * MB), is("4096 KB"));
        assertThat(sizeToString(  5 * MB), is("5 MB"));
        assertThat(sizeToString( 18 * MB), is("18 MB"));
        assertThat(sizeToString(  4 * GB), is("4096 MB"));
        assertThat(sizeToString(  5 * GB), is("5 GB"));
        assertThat(sizeToString(500 * GB), is("500 GB"));
        assertThat(sizeToString(  4 * TB), is("4096 GB"));
        assertThat(sizeToString(  5 * TB), is("5 TB"));
        assertThat(sizeToString(789 * TB), is("789 TB"));
        assertThat(sizeToString(  4 * PB), is("4096 TB"));
        assertThat(sizeToString(  5 * PB), is("5 PB"));
        assertThat(sizeToString(  2 * EB), is("2048 PB"));
        assertThat(sizeToString(  8 * EB - 1), is("8191 PB")); // max long
    }
}
