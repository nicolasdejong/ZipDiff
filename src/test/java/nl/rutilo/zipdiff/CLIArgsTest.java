package nl.rutilo.zipdiff;

import org.junit.AfterClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CLIArgsTest {
    // one of these actions should be performed:
    // - compare and list: baseFile & compareWith
    // - generate patch:   baseFile & compareWith [& generatePatch]
    // - patch:            baseFile & patchWith [& patchTo]
    private static final String AZIP = "a.zip";
    private static final String BZIP = "b.zip";
    private static final String EXISTING_PATCH;
    private static final String NONEXISTING_PATCH;

    static {
        String existing;
        try {
            existing = File.createTempFile("test", ".zpatch").getAbsolutePath();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create temp file for testing");
        }
        EXISTING_PATCH = existing;
        NONEXISTING_PATCH = EXISTING_PATCH.replace(".zpatch", "-nonexisting.zpatch");
    }
    @AfterClass  public static void teardown() throws IOException {
        Files.delete(new File(EXISTING_PATCH).toPath());
    }

    @Test public void testHelp() {
        assertTrue(CLIArgs.createFor().help);
        assertTrue(CLIArgs.createFor("?").help);
        assertTrue(CLIArgs.createFor("-?").help);
        assertTrue(CLIArgs.createFor("-h").help);
        assertTrue(CLIArgs.createFor("-help").help);
        assertTrue(CLIArgs.createFor("-help", "--base-file", AZIP).help);
        assertTrue(CLIArgs.createFor("-help", "--unknown-option").help);
    }
    @Test public void testIgnoreValidation() {
        assertFalse(CLIArgs.createFor().ignoreValidation);
        assertTrue(CLIArgs.createFor("-i",                  "--base-file", AZIP, "--compare-with", BZIP).ignoreValidation);
        assertTrue(CLIArgs.createFor("--ignore-validation", "--base-file", AZIP, "--compare-with", BZIP).ignoreValidation);
    }
    @Test public void testVerbose() {
        assertFalse(CLIArgs.createFor().verbose);
        assertTrue(CLIArgs.createFor("-v",        "--base-file", AZIP, "--compare-with", BZIP).verbose);
        assertTrue(CLIArgs.createFor("--verbose", "--base-file", AZIP, "--compare-with", BZIP).verbose);
    }
    @Test public void testCompareAndList() {
        for(int i=0; i<2; i++) {
            final CLIArgs args;
            if(i == 0) args = CLIArgs.createFor("--base-file", AZIP, "--compare-with", BZIP);
            else       args = CLIArgs.createFor("-f", AZIP, "-c", BZIP);

            assertThat("i="+i, args.baseFile,      is(AZIP));
            assertThat("i="+i, args.compareWith,   is(BZIP));
            assertThat("i="+i, args.generatePatch, is(nullValue()));
            assertThat("i="+i, args.patchWith,     is(nullValue()));
            assertThat("i="+i, args.patchTo,       is(nullValue()));
        }
    }
    @Test public void testGeneratePatch() {
        for(int i=0; i<3; i++) {
            final CLIArgs args;
            switch(i) {
                default:
                case 0: args = CLIArgs.createFor("--base-file", AZIP, "--compare-with", BZIP, "--generate-patch", NONEXISTING_PATCH); break;
                case 1: args = CLIArgs.createFor("-f", AZIP, "-c", BZIP, "-g", NONEXISTING_PATCH); break;
                case 2: args = CLIArgs.createFor("-f", AZIP, "-c", BZIP, "-g", "ab"); break;
            }

            assertThat("i="+i, args.baseFile,      is(AZIP));
            assertThat("i="+i, args.compareWith,   is(BZIP));
            assertThat("i="+i, args.generatePatch, is(i == 2 ? "ab.zpatch" :  NONEXISTING_PATCH));
            assertThat("i="+i, args.patchWith,     is(nullValue()));
            assertThat("i="+i, args.patchTo,       is(nullValue()));
        }
    }
    @Test public void testPatch() {
        for(int i=0; i<4; i++) {
            final CLIArgs args;
            switch(i) {
                default:
                case 0:  args = CLIArgs.createFor("--base-file", AZIP, "--patch-with", EXISTING_PATCH, "--patch-to", BZIP); break;
                case 1:  args = CLIArgs.createFor("--base-file", AZIP, "--patch-with", EXISTING_PATCH); break;
                case 2:  args = CLIArgs.createFor("-f", AZIP, "-p", EXISTING_PATCH, "-t", BZIP); break;
                case 3:  args = CLIArgs.createFor("-f", AZIP, "-p", EXISTING_PATCH); break;
            }
            assertThat("i="+i, args.baseFile,      is(AZIP));
            assertThat("i="+i, args.compareWith,   is(nullValue()));
            assertThat("i="+i, args.generatePatch, is(nullValue()));
            assertThat("i="+i, args.patchWith,     is(EXISTING_PATCH));
            assertThat("i="+i, args.patchTo,       is(i == 0 || i == 2 ? BZIP : "a-new.zip"));
        }
    }
    @Test public void testIllegalCombinations() {
        assertIllegalArgs("No base-file",          () -> CLIArgs.createFor("-c", AZIP));
        assertIllegalArgs("Nothing to do",         () -> CLIArgs.createFor("--base-file", AZIP));
        assertIllegalArgs("without a compare",     () -> CLIArgs.createFor("-f", AZIP, "-g", NONEXISTING_PATCH));
        assertIllegalArgs("compare when patching", () -> CLIArgs.createFor("-f", AZIP, "-c", BZIP, "-p", EXISTING_PATCH));
        assertIllegalArgs("does not exist",        () -> CLIArgs.createFor("-f", AZIP, "-patch-with", NONEXISTING_PATCH));
        assertIllegalArgs("Unexpected arguments",  () -> CLIArgs.createFor("-foo", AZIP));
        assertIllegalArgs("Unexpected arguments",  () -> CLIArgs.createFor("-f", AZIP, "-c", BZIP, AZIP));
    }

    private static void assertIllegalArgs(String msgPart, Runnable r) {
        try {
            r.run();
            fail("Excpected but didn't get IllegalArgumentException containing message: " + msgPart);
        } catch(final IllegalArgumentException ex) {
            assertThat(ex.getMessage(), containsString(msgPart));
        }
    }
}
