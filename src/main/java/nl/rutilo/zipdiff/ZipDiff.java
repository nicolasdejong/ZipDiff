package nl.rutilo.zipdiff;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import static nl.rutilo.zipdiff.ZipUtil.asString;
import static nl.rutilo.zipdiff.ZipUtil.exhaust;
import static nl.rutilo.zipdiff.ZipUtil.sizeToString;
import static nl.rutilo.zipdiff.ZipUtil.sorted;

public class ZipDiff {
    private ZipDiff() { /*singleton*/ }

    public static void main(String... argsIn) {
        try {
            final CLIArgs args = CLIArgs.createFor(argsIn);

            if (args.help) {
                printHelp();
            } else {
                // patch an existing zip to a new zip
                if(args.patchTo != null) patch(args.baseFile, args.patchWith, args.patchTo, args.ignoreValidation, args.verbose);
                else

                // compare two files and generate a patch file
                if(args.generatePatch != null) generatePatch(args.baseFile, args.compareWith, args.generatePatch, args.verbose);
                else

                // compare two files and list the differences
                    listDiff(args.baseFile, args.compareWith, args.verbose);
            }
        } catch(final IOException e) {
            err("ERROR: " + e.getMessage());
        } catch(final IllegalArgumentException e) {
            err("Command ERROR: " + e.getMessage());
        }
    }

    public static void printHelp() throws IOException {
        out(
            asString(exhaust(
                ZipDiff.class.getClassLoader().getResourceAsStream("help.txt")
            ))
        );
    }

    public static void listDiff(String fileA, String fileB, boolean verbose) throws IOException {
        final ZipPatcher zipA = new ZipPatcher(new File(fileA));
        final ZipPatcher zipB = new ZipPatcher(new File(fileB));
        final ZipPatcher.Changes changes = zipA.getChangesTo(zipB);

        final int added    = changes.added   .size();
        final int replaced = changes.replaced.size();
        final int removed  = changes.removed .size();

        if(added > 0 || replaced > 0 || removed > 0 || changes.hasNewHeaderData()) {
            out("Changes from " + fileA + " to " + fileB + ":");
            if (changes.hasNewHeaderData()) out("New header data (before start of zip)");
            if(added    > 0) out("Added "    + added    + (verbose ? ":\n" + listItems(sorted(changes.added   )) : ""));
            if(replaced > 0) out("Replaced " + replaced + (verbose ? ":\n" + listItems(sorted(changes.replaced)) : ""));
            if(removed  > 0) out("Removed "  + removed  + (verbose ? ":\n" + listItems(sorted(changes.removed )) : ""));
        } else {
            out("No changes from " + fileA + " to " + fileB);
        }
    }
    public static void generatePatch(String fileA, String fileB, String patchName, boolean verbose) throws IOException {
        final ZipPatcher zipA = new ZipPatcher(new File(fileA));
        final ZipPatcher zipB = new ZipPatcher(new File(fileB));
        final File patchFile = new File(patchName);

        zipA.generatePatchFileTo(zipB, patchFile);
        if(verbose) out("Created patch file \"" + patchName + "\" of " + sizeToString(patchFile.length()));
    }
    public static void patch(String fileBase, String patchName, String patchTarget, boolean ignoreValidation, boolean verbose) throws IOException {
        final ZipPatcher zipBase  = new ZipPatcher(new File(fileBase));

        zipBase.patchTo(new File(patchName), new File(patchTarget), ignoreValidation);
        if(verbose) out("Patched " + fileBase + " to " + patchTarget);
    }

    private static void err(String txt) { System.err.println(txt); } // NOSONAR -- app too simple to add logger
    private static void out(String txt) { System.out.println(txt); } // NOSONAR -- app too simple to add logger
    private static String listItems(Collection<String> items) {
        return items.isEmpty() ? "" : " - " + String.join("\n - ", items);
    }
}
