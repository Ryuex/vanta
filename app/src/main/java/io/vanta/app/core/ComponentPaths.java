package io.vanta.app.core;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/**
 * Safety net for prebuilt native components that were originally built for the
 * upstream Winlator application and therefore embed absolute paths such as
 *
 *     /data/data/com.winlator/files/rootfs/lib/ld-linux-aarch64.so.1
 *
 * inside their ELF header (PT_INTERP), in DT_RUNPATH or in companion JSON files.
 * Under a different application id those paths do not resolve and the affected
 * process simply fails to start.
 *
 * The migration performed here is deliberately conservative:
 *   * only the exact absolute path prefix is touched;
 *   * it is applied in place only when the replacement is exactly the same
 *     length, so ELF offsets, segment sizes and string table layout are kept
 *     byte-for-byte identical (no blind binary rewriting);
 *   * executable files are validated again after a repair, and when the path
 *     cannot be repaired safely a descriptive error is returned instead of
 *     letting the startup hang without any diagnostic.
 */
public abstract class ComponentPaths {
    /** Absolute data directory used by upstream Winlator. */
    public static final String LEGACY_DATA_DIR = "/data/data/com.winlator";

    private static final int ELF_MAGIC = 0x464c457f; // \x7fELF (little endian)
    private static final int PT_INTERP = 3;

    private ComponentPaths() {}

    /**
     * Migrates the legacy application data path inside the given file when this can be
     * done safely (same string length) and when the file really contains it.
     *
     * @return {@code null} when the file is usable, otherwise a description of the problem.
     */
    public static String migrate(File file, String dataDir) {
        if (file == null || !file.isFile()) return "missing file: "+file;

        byte[] legacy = LEGACY_DATA_DIR.getBytes(StandardCharsets.UTF_8);
        byte[] replacement = dataDir.getBytes(StandardCharsets.UTF_8);

        byte[] content;
        try {
            content = readFile(file);
        }
        catch (IOException e) {
            return "unable to read "+file.getName()+": "+e.getMessage();
        }

        if (indexOf(content, legacy, 0) < 0) return null; // nothing to migrate

        if (legacy.length != replacement.length) {
            return file.getName()+" still embeds the legacy path "+LEGACY_DATA_DIR+
                   " but the current data directory "+dataDir+
                   " has a different length, so it cannot be migrated in place";
        }

        int magic = content.length >= 4 ? ((content[3] & 0xff) << 24) | ((content[2] & 0xff) << 16) |
                                          ((content[1] & 0xff) << 8) | (content[0] & 0xff) : -1;
        boolean isElf = magic == ELF_MAGIC;

        int count = 0;
        int index = 0;
        while ((index = indexOf(content, legacy, index)) >= 0) {
            System.arraycopy(replacement, 0, content, index, replacement.length);
            count++;
            index += replacement.length;
        }

        try {
            write(file, content);
        }
        catch (IOException e) {
            return "unable to rewrite "+file.getName()+": "+e.getMessage();
        }

        if (isElf && !isElf(file)) return file.getName()+" became unreadable after the path migration";
        return null;
    }

    /**
     * Verifies that the ELF interpreter referenced by an executable exists on this device.
     * A missing interpreter means {@code exec} fails with ENOENT, which would otherwise
     * leave the container in an endless "starting" state.
     *
     * @return {@code null} when the executable can be started, otherwise a description
     *         of the problem.
     */
    public static String verifyExecutable(File file, String dataDir) {
        if (file == null || !file.isFile()) return "missing executable: "+file;

        String interp = readInterp(file);
        if (interp == null) return file.getName()+" has no ELF interpreter (PT_INTERP)";

        if (new File(interp).isFile()) return null;

        // The interpreter does not resolve: try the conservative in place migration.
        String problem = migrate(file, dataDir);
        if (problem != null) return problem;

        interp = readInterp(file);
        if (interp != null && new File(interp).isFile()) return null;
        return "interpreter "+interp+" of "+file.getName()+" does not exist";
    }

    /** Returns the PT_INTERP string of an ELF file, or {@code null} when unavailable. */
    public static String readInterp(File file) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            if (raf.length() < 64) return null;
            if (readIntLE(raf, 0) != ELF_MAGIC) return null;

            int classByte = readByteAt(raf, 4);
            boolean is64 = classByte == 2;
            if (readByteAt(raf, 5) != 1) return null; // little endian only

            long phoff = is64 ? readLongLE(raf, 32) : readIntLE(raf, 28) & 0xffffffffL;
            int phentsize = is64 ? readShortLE(raf, 54) : readShortLE(raf, 42);
            int phnum = is64 ? readShortLE(raf, 56) : readShortLE(raf, 44);
            if (phentsize <= 0 || phnum <= 0) return null;

            for (int i = 0; i < phnum; i++) {
                long base = phoff + (long)i * phentsize;
                if (base + phentsize > raf.length()) break;
                raf.seek(base);
                int pType = Integer.reverseBytes(raf.readInt());
                if (pType != PT_INTERP) continue;

                long offset;
                long size;
                if (is64) {
                    raf.seek(base + 8);
                    offset = Long.reverseBytes(raf.readLong());
                    raf.seek(base + 32);
                    size = Long.reverseBytes(raf.readLong());
                }
                else {
                    raf.seek(base + 4);
                    offset = Integer.reverseBytes(raf.readInt()) & 0xffffffffL;
                    raf.seek(base + 16);
                    size = Integer.reverseBytes(raf.readInt()) & 0xffffffffL;
                }

                if (size <= 0 || size > 4096 || offset + size > raf.length()) return null;
                byte[] buffer = new byte[(int)size];
                raf.seek(offset);
                raf.readFully(buffer);
                int length = 0;
                while (length < buffer.length && buffer[length] != 0) length++;
                return new String(buffer, 0, length, StandardCharsets.UTF_8);
            }
        }
        catch (IOException e) {
            return null;
        }
        return null;
    }

    /** Convenience variant that derives the data directory from the application context. */
    public static String verifyExecutable(Context context, File file) {
        return verifyExecutable(file, context.getApplicationInfo().dataDir);
    }

    public static String migrate(Context context, File file) {
        return migrate(file, context.getApplicationInfo().dataDir);
    }

    private static boolean isElf(File file) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            return raf.length() >= 4 && readIntLE(raf, 0) == ELF_MAGIC;
        }
        catch (IOException e) {
            return false;
        }
    }

    private static byte[] readFile(File file) throws IOException {
        byte[] content = new byte[(int)file.length()];
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.readFully(content);
        }
        return content;
    }

    private static void write(File file, byte[] content) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.setLength(0);
            raf.write(content);
        }
    }

    private static int indexOf(byte[] source, byte[] target, int from) {
        if (target.length == 0 || source.length < target.length) return -1;
        for (int i = Math.max(0, from); i <= source.length - target.length; i++) {
            int j = 0;
            while (j < target.length && source[i + j] == target[j]) j++;
            if (j == target.length) return i;
        }
        return -1;
    }

    private static int readByteAt(RandomAccessFile raf, long position) throws IOException {
        raf.seek(position);
        return raf.readUnsignedByte();
    }

    private static int readIntLE(RandomAccessFile raf, long position) throws IOException {
        raf.seek(position);
        return Integer.reverseBytes(raf.readInt());
    }

    private static int readShortLE(RandomAccessFile raf, long position) throws IOException {
        raf.seek(position);
        return Short.toUnsignedInt(Short.reverseBytes(raf.readShort()));
    }

    private static long readLongLE(RandomAccessFile raf, long position) throws IOException {
        raf.seek(position);
        return Long.reverseBytes(raf.readLong());
    }
}
