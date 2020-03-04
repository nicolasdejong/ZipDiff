package nl.rutilo.zipdiff;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class TestUtils {
    private TestUtils() { /*singleton*/ }

    /** Creates zip containing given filenames with content being the path:crc */
    public static void createZipFile(File file, List<TestEntry> entries) throws IOException {
        createZipFile(file, null, entries);
    }

    /** Creates zip containing given filenames with content being the path:version */
    public static void createZipFile(File file, byte[] headerData, List<TestEntry> entries) throws IOException {
        Files.deleteIfExists(file.toPath());

        if(headerData != null) {
            try(final FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(headerData);
            }
        }
        try(final ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(file, /*append:*/true)))) {
            for (final TestEntry entry : entries) {
                final ZipEntry zipEntry = new ZipEntry(entry.name);
                zos.putNextEntry(zipEntry);
                zos.write((entry.name + ":" + entry.version).getBytes());
                zos.closeEntry();
            }
            zos.finish();
        }
    }

    public static class TestEntry {
        final String name;
        final int version;
        public TestEntry(String name, int version) { this.name = name; this.version= version; }
        public static List<TestEntry> createFrom(Object[] objs) {
            return IntStream.iterate(0, i -> i + 2)
                .limit(objs.length/2)
                .mapToObj(i -> new TestEntry((String)objs[i], ((Number)objs[i+1]).intValue()))
                .collect(Collectors.toList());
        }
    }

    /** The user of this ThrowingConsumer should make sure to throw
      * cause because here cause is wrapped in a RuntimeException
      */
    @FunctionalInterface
    public interface ThrowingConsumer<T> extends Consumer<T> {

        @Override
        default void accept(T t) {
            try {
                acceptThrows(t);
            } catch(final Throwable cause) {
                throw new RuntimeException(cause);
            }
        }

        void acceptThrows(T t) throws Throwable;
    }
    /** The user of this ThrowingBiConsumer should make sure to throw
      * cause because here cause is wrapped in a RuntimeException
      */
    @FunctionalInterface
    public interface ThrowingBiConsumer<S,T> extends BiConsumer<S,T> {

        @Override
        default void accept(S s, T t) {
            try {
                acceptThrows(s, t);
            } catch(final Throwable cause) {
                throw new RuntimeException(cause);
            }
        }

        void acceptThrows(S s, T t) throws Throwable;
    }
    /** The user of this ThrowingTriConsumer should make sure to throw
      * cause because here cause is wrapped in a RuntimeException
      */
    @FunctionalInterface
    public interface ThrowingTriConsumer<S,T,U> {

        default void accept(S s, T t, U u) {
            try {
                acceptThrows(s, t, u);
            } catch(final Throwable cause) {
                throw new RuntimeException(cause);
            }
        }

        void acceptThrows(S s, T t, U u) throws Throwable;
    }
}
