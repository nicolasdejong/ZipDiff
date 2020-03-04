package nl.rutilo.zipdiff;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

public class CLIArgs {
    public final String baseFile;
    public final String compareWith;
    public final String generatePatch;
    public final String patchWith;
    public final String patchTo;
    public final boolean ignoreValidation;
    public final boolean verbose;
    public final boolean help;

    private CLIArgs(String baseFile, String compareWith, String generatePatch, // NOSONAR -- only called from createFor()
                    String patchWith, String patchTo,
                    boolean ignoreValidation, boolean verbose, boolean help) {
        this.baseFile = baseFile;
        this.compareWith = compareWith;
        this.generatePatch = generatePatch;
        this.patchWith = patchWith;
        this.patchTo = patchTo;
        this.ignoreValidation = ignoreValidation;
        this.verbose = verbose;
        this.help = help;

        if(!help) {
            if(baseFile    == null)                          throw error("No base-file provided.");
            if(compareWith == null && generatePatch != null) throw error("Cannot create patch without a compare-with.");
            if(compareWith == null && patchWith     == null) throw error("No compare and not patch. Nothing to do.");
            if(patchWith   != null && compareWith   != null) throw error("Cannot compare when patching. Remove the -compare-with.");
            if(patchWith   != null && !new File(patchWith).exists()) throw error("Cannot patch -- file does not exist: " + patchWith);
        }
    }

    public static CLIArgs createFor(String... args) {
        return createFor(Arrays.asList(args));
    }
    public static CLIArgs createFor(List<String> argsIn) {
        final List<String> args = new ArrayList<>(argsIn);

        final String baseFile          = getAndRemoveArgOrNull(args, "-f", "--base-file");
        final String compareWith       = getAndRemoveArgOrNull(args, "-c", "--compare-with");
              String generatePatch     = getAndRemoveArgOrNull(args, "-g", "--generate-patch");
        final String patchWith         = getAndRemoveArgOrNull(args, "-p", "--patch-with");
              String patchTo           = getAndRemoveArgOrNull(args, "-t", "--patch-to");
        final boolean ignoreValidation = getAndRemoveArgOrFalse(args, "-i", "--ignore-validation");
        final boolean verbose          = getAndRemoveArgOrFalse(args, "-v", "--verbose");
        final boolean help             = argsIn.isEmpty()
                                     || argsIn.contains("?")
                                     || getAndRemoveArgOrFalse(args, "-?", "-h", "-help");

        if(!argsIn.isEmpty() && !help) {
            if(!args.isEmpty()) throw error("Unexpected arguments:", String.join(", ", args));

            if(baseFile != null && patchWith != null && patchTo == null) {
                patchTo = replaceExt(baseFile, ext -> "-new" + ext);
            }
            if(generatePatch != null && !hasExt(generatePatch)) {
                generatePatch += ".zpatch";
            }
        }
        return new CLIArgs(baseFile, compareWith, generatePatch, patchWith, patchTo, ignoreValidation, verbose, help);
    }

    private static RuntimeException error(String... msg) {
        throw new IllegalArgumentException(String.join(" ", msg));
    }
    private static String getAndRemoveArgOrNull(List<String> args, String... names) {
        for(final String name : names) {
            final int index = indexOfArgVariants(args, name);
            if(index >= 0) {
                args.remove(index);
                final String value = index < args.size() ? args.remove(index) : null;
                if(value == null || value.startsWith("-")) throw error(name, " requires argument");
                return value;
            }
        }
        return null;
    }
    private static boolean getAndRemoveArgOrFalse(List<String> args, String... names) {
        final Set<String> found = new HashSet<>();
        for(final String name : names) {
            final String ndName = name.replace("-", "");
            args.stream()
                .filter(n -> n.startsWith("-") && n.replace("-", "").equals(ndName))
                .forEach(found::add);
        }
        args.removeAll(found);
        return !found.isEmpty();
    }
    private static int indexOfArgVariants(List<String> args, String name) {
        final String ndName = name.replace("-", "");

        return args.stream()
            .filter(n -> n.startsWith("-") && n.replace("-", "").equals(ndName))
            .findAny()
            .map(args::indexOf)
            .orElse(-1);
    }
    private static boolean hasExt(String name) {
        return name.matches("^.+\\.[^.]+$");
    }
    private static String replaceExt(String name, UnaryOperator<String> replacer) {
        final int    dot      = name.lastIndexOf('.');
        final String baseName = dot < 0 ? name : name.substring(0, dot);
        final String ext      = dot < 0 ? "" : name.substring(dot);

        return baseName + replacer.apply(ext);
    }
}
