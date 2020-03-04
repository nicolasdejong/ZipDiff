package nl.rutilo.zipdiff;

import nl.rutilo.zipdiff.TestUtils.TestEntry;
import nl.rutilo.zipdiff.TestUtils.ThrowingBiConsumer;
import nl.rutilo.zipdiff.TestUtils.ThrowingConsumer;
import nl.rutilo.zipdiff.TestUtils.ThrowingTriConsumer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static nl.rutilo.zipdiff.ZipUtil.toBytes;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ZipPatcherTest {
    static final List<TestEntry> entriesOld = TestUtils.TestEntry.createFrom(new Object[] { // name, version
        "fileA", 1,
        "fileB", 1,
        "fileC", 1,
        "dirA/fileAA.txt", 1,
        "dirA/fileAB.txt", 1,
        "dirA/dirAA/fileAAA.txt", 1,
        "dirA/dirAA/fileAAB.txt", 1,
        "dirA/dirAB/fileABA.txt", 1,
        "dirA/dirAB/fileABB.txt", 1,
        "dirB/fileBA.txt", 1,
    });
    static final List<TestEntry> entriesNew = TestUtils.TestEntry.createFrom(new Object[] { // name, version
        "fileA", 1,
      //"fileB", 1, // removed
        "fileC", 2, // replaced
        "fileD", 1, // added
        "dirA/fileAA.txt", 1,
      //"dirA/fileAB.txt", 1, // removed
        "dirA/fileAC.txt", 1, // added
        "dirA/dirAA/fileAAA.txt", 1,
      //"dirA/dirAA/fileAAB.txt", 1, // removed
        "dirA/dirAA/fileAAC.txt", 1, // added
      //"dirA/dirAB/fileABA.txt", 1, // removed
        "dirA/dirAB/fileABB.txt", 2, // replaced
        "dirA/dirAC/fileACA.txt", 1, // added
        "dirA/dirAC/fileACB.txt", 1, // added
        "dirB/fileBA.txt", 1,
        "dirB/fileBB.txt", 1,        // added
    });

    private File fileOld;
    private File fileNew;

    @Before
    public void setup() throws IOException {
        fileOld = File.createTempFile("test-old", ".zip");
        fileNew = File.createTempFile("test-new", ".zip");

        TestUtils.createZipFile(fileOld, entriesOld);
        TestUtils.createZipFile(fileNew, entriesNew);
    }
    @After
    public void teardown() throws IOException {
        Files.delete(fileOld.toPath());
        Files.delete(fileNew.toPath());
    }

    @Test public void testGetChanges() throws IOException {
        final ZipPatcher zipOld = new ZipPatcher(fileOld);
        final ZipPatcher zipNew = new ZipPatcher(fileNew);

        assertThat(zipOld.getHeaderData(), is(zipNew.getHeaderData()));

        final ZipPatcher.Changes changes = zipOld.getChangesTo(zipNew);
        assertThat("Additions",    changes.added   .size(), is(6));
        assertThat("Removals",     changes.removed .size(), is(4));
        assertThat("Replacements", changes.replaced.size(), is(2));

        zipOld.setHeaderData(toBytes("abc"));
        zipNew.setHeaderData(null);
        assertThat(zipOld.getChangesTo(zipNew).newHeaderData, is(new byte[0]));

        zipOld.setHeaderData(null);
        zipNew.setHeaderData(toBytes("abc"));
        assertThat(zipOld.getChangesTo(zipNew).newHeaderData, is(toBytes("abc")));

        zipOld.setHeaderData(toBytes("abc"));
        zipNew.setHeaderData(toBytes("abc"));
        assertThat(zipOld.getChangesTo(zipNew).newHeaderData, is(ZipPatcher.CODE_HEADER_NOCHANGE));

        zipOld.setHeaderData(toBytes("abc"));
        zipNew.setHeaderData(toBytes("def"));
        assertThat(zipOld.getChangesTo(zipNew).newHeaderData, is(toBytes("def")));
    }

    @Test public void testZipWithTextHeader() throws IOException {
        byte[] headerText = toBytes("Testing header before zip contents");
        TestUtils.createZipFile(fileOld, headerText, entriesOld);

        // check that start of file is textHeader
        try(final FileInputStream fin = new FileInputStream(fileOld)) {
            final byte[] data = ZipUtil.exhaust(fin);
            for(int i=0; i<headerText.length; i++) {
                if(data[i] != headerText[i]) fail("Header text fail at pos " + i + ": " + (int)data[i] + " vs " + (int)headerText[i]);
            }
        }

        // check that this file can be read
        final ZipPatcher patcher = new ZipPatcher(fileOld);
        assertThat(patcher.getHeaderData(), is(headerText));
        assertThat(patcher.readFully().size(), is(entriesOld.size()));
    }

    private void runPatchTest(ThrowingBiConsumer<ZipPatcher, ZipPatcher> init,
                              ThrowingConsumer<File> patchFileModifier,
                              ThrowingTriConsumer<ZipPatcher,ZipPatcher,ZipPatcher> patchChecker) throws IOException {
        final ZipPatcher zipOld = new ZipPatcher(fileOld);
        final ZipPatcher zipNew = new ZipPatcher(fileNew);
        final File patchFile    = new File(fileOld.getAbsolutePath() + ".patch");
        final File patchedFile  = new File(fileOld.getAbsolutePath() + ".patched");

        try {
            init.accept(zipOld, zipNew);

            zipOld.generatePatchFileTo(zipNew, patchFile);

            patchFileModifier.accept(patchFile);

            zipOld.patchTo(patchFile, patchedFile, /*ignoreValidation:*/false);

            assertTrue(patchedFile.exists());

            patchChecker.accept(zipOld, zipNew, new ZipPatcher(patchedFile));
        } catch(final RuntimeException e) {
            if(e.getCause() instanceof IOException) throw (IOException)e.getCause();
            throw e;
        } finally {
            Files.deleteIfExists(patchFile.toPath());
            Files.deleteIfExists(patchedFile.toPath());
        }
    }

    @Test public void testPatchFile() throws IOException {
        runPatchTest(
            (zipOld, zipNew) -> {},
            (patchFile) -> {},
            (zipOld, zipNew, zipPatched) -> {
                final ZipPatcher.Changes changes = zipPatched.getChangesTo(zipNew);

                // at this point, patchedFile should hold the same data as fileNew -- no changes
                assertThat("patched has no new headerText", changes.newHeaderData, is(ZipPatcher.CODE_HEADER_NOCHANGE));
                assertTrue("patched has no added", changes.added.isEmpty());
                assertTrue("patched has no removed", changes.removed.isEmpty());
                assertTrue("patched has no replaced", changes.replaced.isEmpty());

                // check that the content of all entries is the same
                final Map<String,byte[]> dataNew = zipNew.readFully();
                final Map<String,byte[]> dataPatched = zipPatched.readFully();

                assertThat(dataNew.size(), is(dataPatched.size())); // sanity check: changes is empty already
                for(final String name : dataNew.keySet()) { // NOSONAR: keyset used to link two maps
                    assertThat("Equal entry content", dataNew.get(name), is(dataPatched.get(name)));
                }
            }
        );
    }
    @Test public void testPatchFileHeaderNoChange() throws IOException {
        final byte[] HDR_TEXT = toBytes("abc");
        runPatchTest(
            (zipOld, zipNew) -> {
                zipOld.setHeaderData(HDR_TEXT);
                zipNew.setHeaderData(zipOld.getHeaderData());
            },
            (patchFile) -> {},
            (zipOld, zipNew, zipPatched) -> {
                assertThat(zipPatched.getHeaderData(), is(HDR_TEXT));
            }
        );
    }
    @Test public void testPatchFileHeaderEmptyToValue() throws IOException {
        final byte[] HDR_TEXT = toBytes("abc");
        runPatchTest(
            (zipOld, zipNew) -> {
                zipOld.setHeaderData(toBytes(""));
                zipNew.setHeaderData(HDR_TEXT);
            },
            (patchFile) -> {},
            (zipOld, zipNew, zipPatched) -> {
                assertThat(zipPatched.getHeaderData(), is(HDR_TEXT));
            }
        );
    }
    @Test public void testPatchFileHeaderValueToEmpty() throws IOException {
        final byte[] HDR_TEXT = toBytes("abc");
        runPatchTest(
            (zipOld, zipNew) -> {
                zipOld.setHeaderData(HDR_TEXT);
                zipNew.setHeaderData(toBytes(""));
            },
            (patchFile) -> {},
            (zipOld, zipNew, zipPatched) -> {
                assertThat(zipPatched.getHeaderData(), is(new byte[0]));
            }
        );
    }
    @Test public void testPatchFileHeaderValueToValue() throws IOException {
        final byte[] HDR_TEXT = toBytes("abc");
        final byte[] HDR_TEXT_OLD = toBytes("abc_old");
        runPatchTest(
            (zipOld, zipNew) -> {
                zipOld.setHeaderData(HDR_TEXT_OLD);
                zipNew.setHeaderData(HDR_TEXT);
            },
            (patchFile) -> {},
            (zipOld, zipNew, zipPatched) -> {
                assertThat(zipPatched.getHeaderData(), is(HDR_TEXT));
            }
        );
    }

    @Test public void testPatchFailed() {
        try {
            runPatchTest(
                (zipOld, zipNew) -> {},
                (patchFile) -> {
                    // change patch file so the crc check will trigger
                    ZipUtil.updateZip(patchFile, Collections.singletonMap("dummy", toBytes("dummy")));
                },
                (zipOld, zipNew, zipPatched) -> {}
            );
            fail("Expected CRC error");
        } catch(final IOException e) {
            assertThat(e.getMessage(), containsString("CRC"));
        }
    }
    @Test public void testPatchFailedBecauseUnknownExpectedCRC() {
        try {
            runPatchTest(
                (zipOld, zipNew) -> {},
                (patchFile) -> {
                    // Remove file holding expected CRC
                    ZipUtil.updateZip(patchFile, Collections.emptyMap(), ZipPatcher.EXPECTED_CRC_FILENAME);
                },
                (zipOld, zipNew, zipPatched) -> {}
            );
            fail("Expected CRC error");
        } catch(final IOException e) {
            assertThat(e.getMessage(), containsString("CRC"));
        }
    }
}