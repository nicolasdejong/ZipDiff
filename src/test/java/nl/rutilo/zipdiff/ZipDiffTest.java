package nl.rutilo.zipdiff;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Random;
import java.util.function.BiConsumer;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ZipDiffTest {

    private static class ConsoleOut {
        final String out;
        final String err;
        public ConsoleOut(String out, String err) { this.out = out; this.err = err; }
        public void get(BiConsumer<String, String> handler) { handler.accept(out, err); }
    }
    private static ConsoleOut runTest(Runnable r) {
        final PrintStream origOut = System.out; // NOSONAR: Test output
        final PrintStream origErr = System.err; // NOSONAR: Test error output

        final ByteArrayOutputStream bout = new ByteArrayOutputStream();
        final ByteArrayOutputStream berr = new ByteArrayOutputStream();
        final PrintStream testOut = new PrintStream(bout);
        final PrintStream testErr = new PrintStream(berr);
        System.setOut(testOut);
        System.setErr(testErr);
        try {
            r.run();
        } catch(final Throwable t) { // NOSONAR: catch all
            t.printStackTrace(); // NOSONAR: will be part of ConsoleOut
        } finally {
            System.setOut(origOut);
            System.setErr(origErr);
        }

        return new ConsoleOut(
            ZipUtil.asString(bout.toByteArray()),
            ZipUtil.asString(berr.toByteArray())
        );
    }

    private static String nameOfZipA;
    private static String nameOfZipB;
    private static String nameOfZipAB;
    private static String nameOfPatch;
    private static String nameInvalid;

    @BeforeClass public static void setup() throws IOException {
        final Random rnd = new Random();
        nameOfZipA  = "testFileA-" + rnd.nextInt(100_000) + ".zip";
        nameOfZipB  = "testFileB-" + rnd.nextInt(100_000) + ".zip";
        nameOfZipAB = "testFileAB-"+ rnd.nextInt(100_000) + ".zip";
        nameOfPatch = "testFileAB-" +rnd.nextInt(100_000) + ".zpatch";
        nameInvalid = "testFileInvalid";
        TestUtils.createZipFile(new File(nameOfZipA), ZipPatcherTest.entriesOld);
        TestUtils.createZipFile(new File(nameOfZipB), ZipPatcherTest.entriesNew);
    }
    @AfterClass public static void teardown() throws IOException {
        deleteFile(nameOfZipA);
        deleteFile(nameOfZipB);
        deleteFile(nameOfZipAB);
        deleteFile(nameOfPatch);
    }
    @Before public void beforeTest() throws IOException {
        deleteFile(nameOfZipAB);
        deleteFile(nameOfPatch);
        deleteFile(nameInvalid);
    }

    private static void deleteFile(String name) throws IOException {
        final File file = new File(name);
        Files.deleteIfExists(file.toPath());
    }

    @Test public void testUnknownArg() {
        runTest(() -> ZipDiff.main("-foo")).get((out, err) -> {
            assertThat(out, is(""));
            assertThat(err, containsString("Command ERROR:"));
        });
    }
    @Test public void testNoArgsPrintsHelp() {
        runTest(() -> ZipDiff.main("-help")).get((out, err) -> {
            assertThat(out, containsString("-- by Nicolas de Jong"));
            assertThat(err, is(""));
        });
    }
    @Test public void testHelp() {
        runTest(() -> ZipDiff.main("-help")).get((out, err) -> {
            assertThat(out, containsString("-- by Nicolas de Jong"));
            assertThat(err, is(""));
        });
    }

    @Test public void testListDiff() {
        runTest(() -> ZipDiff.main(
            "--base-file",    nameOfZipA,
            "--compare-with", nameOfZipB
        )).get((out, err) -> {
            assertTrue(err.isEmpty());
            assertThat(out, containsString("Changes from " + nameOfZipA));
            assertThat(out, containsString("Added 6"));
        });
    }
    @Test public void testListDiffVerbose() {
        runTest(() -> ZipDiff.main(
            "-v",
            "--base-file",    nameOfZipA,
            "--compare-with", nameOfZipB
        )).get((out, err) -> {
            assertTrue(err.isEmpty());
            assertThat(out, containsString("Changes from " + nameOfZipA));
            assertThat(out, containsString("Added 6:"));
            assertThat(out, containsString(" - dirA/"));
            assertThat(out, containsString("Replaced 2:"));
            assertThat(out, containsString("Removed 4:"));
        });
    }

    @Test public void testGeneratePatch() {
        runTest(() -> ZipDiff.main(
            "--base-file",      nameOfZipA,
            "--compare-with",   nameOfZipB,
            "--generate-patch", nameOfPatch
        )).get((out, err) -> {
            assertTrue(err.isEmpty());
            assertTrue(out.isEmpty());
            assertTrue(new File(nameOfPatch).exists());
            assertTrue(new File(nameOfPatch).length() > 0);
        });
    }
    @Test public void testGeneratePatchVerbose() {
        runTest(() -> ZipDiff.main(
            "--base-file", nameOfZipA,
            "--compare-with", nameOfZipB,
            "--generate-patch", nameOfPatch,
            "--verbose"
        )).get((out, err) -> {
            assertTrue(err.isEmpty());
            assertTrue(new File(nameOfPatch).exists());
            assertTrue(new File(nameOfPatch).length() > 0);
            assertThat(out, containsString("Created patch file"));
        });
    }
    @Test public void testGeneratePatchNoCompare() {
        runTest(() -> ZipDiff.main(
            "--base-file",      nameOfZipA,
            "--generate-patch", nameOfPatch
        )).get((out, err) -> {
            assertTrue(out.isEmpty());
            assertFalse(new File(nameOfPatch).exists());
            assertThat(err, containsString("Cannot create"));
        });
    }

    @Test public void testPatch() {
        testGeneratePatch(); // creates patch file

        runTest(() -> ZipDiff.main(
            "--base-file",  nameOfZipA,
            "--patch-with", nameOfPatch,
            "--patch-to",   nameOfZipAB
        )).get((out, err) -> {
            assertTrue(err.isEmpty());
            assertTrue(out.isEmpty());
            assertTrue(new File(nameOfZipAB).exists());
            assertTrue(new File(nameOfZipAB).length() > 0);
        });
    }
    @Test public void testPatchVerbose() {
        testGeneratePatch(); // creates patch file

        runTest(() -> ZipDiff.main(
            "--base-file",  nameOfZipA,
            "--patch-with", nameOfPatch,
            "--patch-to",   nameOfZipAB,
            "--verbose"
        )).get((out, err) -> {
            assertTrue(err.isEmpty());
            assertTrue(out.trim().matches("^Patched testFileA-\\d+.zip to testFileAB-\\d+.zip$"));
        });
    }
    @Test public void testPatchFailed() throws IOException {
        testGeneratePatch(); // creates patch file

        // change patch file so the crc check will trigger
        ZipUtil.updateZip(new File(nameOfPatch), Collections.singletonMap("dummy", ZipUtil.toBytes("dummy")));

        runTest(() -> ZipDiff.main(
            "--base-file",  nameOfZipA,
            "--patch-with", nameOfPatch,
            "--patch-to",   nameOfZipAB
        )).get((out, err) -> {
            assertThat(err, containsString("CRC"));
        });
    }
    @Test public void testPatchFailedIgnoreValidation() throws IOException {
        testGeneratePatch(); // creates patch file

        // change patch file so the crc check will trigger
        ZipUtil.updateZip(new File(nameOfPatch), Collections.singletonMap("dummy", ZipUtil.toBytes("dummy")));

        runTest(() -> ZipDiff.main(
            "--base-file",  nameOfZipA,
            "--patch-with", nameOfPatch,
            "--patch-to",   nameOfZipAB,
            "--ignore-validation"
        )).get((out, err) -> {
            assertTrue(err.isEmpty());
        });
    }
    @Test public void testPatchNoPatchTo() {
        testGeneratePatch(); // creates patch file

        // no --patch-to will create a new name
        runTest(() -> ZipDiff.main(
            "--base-file",  nameOfZipA,
            "--patch-with", nameOfPatch,
            "--verbose"
        )).get((out, err) -> {
            final String newFile = out.replaceAll("(?s)^.* to (.+-new.zip).*$", "$1");
            new File(newFile).delete();
            assertTrue(err.isEmpty());
            assertTrue(out.trim().matches("^Patched testFileA-\\d+.zip to testFileA-\\d+-new.zip$"));
        });
    }

    @Test public void testWrongArgument() {
        runTest(() -> ZipDiff.main(
            "--base-file",    nameOfZipA,
            "--compare-with", nameOfPatch,
            "--foobar",       nameOfZipAB
        )).get((out, err) -> {
            assertTrue(out.isEmpty());
            assertThat(err, containsString("Unexpected arguments"));
        });
    }
    @Test public void testWrongFile() {
        runTest(() -> ZipDiff.main(
            "--base-file",    nameInvalid,
            "--compare-with", nameOfZipB
        )).get((out, err) -> {
            assertTrue(out.isEmpty());
            assertThat(err, containsString("system cannot find"));
        });
    }
}
