package io.vanta.app.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public abstract class ElfHelper {
    private static final byte ELF_CLASS_32 = 1;
    private static final byte ELF_CLASS_64 = 2;
    private static final int ELF_MACHINE_X86_64 = 62;

    private static int getEIClass(File binFile) {
        try (InputStream inStream = new FileInputStream(binFile)) {
            byte[] header = new byte[52];
            inStream.read(header);
            if (header[0] == 0x7F && header[1] == 'E' && header[2] == 'L' && header[3] == 'F') {
                return header[4];
            }
        }
        catch (IOException e) {}
        return 0;
    }

    public static boolean is32Bit(File binFile) {
        return getEIClass(binFile) == ELF_CLASS_32;
    }

    public static boolean is64Bit(File binFile) {
        return getEIClass(binFile) == ELF_CLASS_64;
    }

    public static boolean isX86_64(File binFile) {
        try (InputStream inStream = new FileInputStream(binFile)) {
            byte[] header = new byte[20];
            if (inStream.read(header) != header.length) return false;
            return header[0] == 0x7F && header[1] == 'E' && header[2] == 'L' && header[3] == 'F' &&
                   header[4] == ELF_CLASS_64 && (header[18] & 0xff) + ((header[19] & 0xff) << 8) == ELF_MACHINE_X86_64;
        }
        catch (IOException e) {
            return false;
        }
    }
}