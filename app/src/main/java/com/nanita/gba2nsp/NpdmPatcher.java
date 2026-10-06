package com.nanita.gba2nsp;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Minimal NPDM patcher for a reusable homebrew runtime template. */
public final class NpdmPatcher {
    private NpdmPatcher() {}

    public static void patchProgramId(File template, File output, long titleId) throws IOException {
        byte[] d = Files.readAllBytes(template.toPath());
        if (d.length < 0x100) throw new IOException("main.npdm صغير/تالف");
        requireMagic(d, 0, "META");

        int aciOff = le32(d, 0x70);
        int aciSize = le32(d, 0x74);
        int acidOff = le32(d, 0x78);
        int acidSize = le32(d, 0x7C);
        if (!range(d, aciOff, aciSize) || !range(d, acidOff, acidSize))
            throw new IOException("NPDM offsets غير صالحة");
        if (aciSize < 0x18) throw new IOException("ACI0 ناقص");
        requireMagic(d, aciOff, "ACI0");
        if (acidSize < 0x240 || acidOff + 0x238 > d.length)
            throw new IOException("ACID ناقص");
        requireMagic(d, acidOff + 0x200, "ACID");

        long min = le64(d, acidOff + 0x210);
        long max = le64(d, acidOff + 0x218);
        if (Long.compareUnsigned(titleId, min) < 0 || Long.compareUnsigned(titleId, max) > 0) {
            throw new IOException(String.format(
                    "Runtime NPDM لا يسمح بهذا Title ID. المدى: %016X-%016X", min, max));
        }

        // Preserve the original signed ACID and its NCA-signing modulus.
        // ACI0 is outside the signed ACID body, so ProgramId/owner IDs can be
        // patched without invalidating the ACID signature.

        // ACI0 is not covered by the signed ACID body. Retail templates can
        // repeat the ProgramId in ACI0 filesystem/save-owner tables, so patch
        // the ProgramId plus every matching owner-id reference inside ACI0.
        long oldTitleId = le64(d, aciOff + 0x10);
        putLe64(d, aciOff + 0x10, titleId);
        for (int off = aciOff + 0x18; off <= aciOff + aciSize - 8; off++) {
            if (le64(d, off) == oldTitleId) {
                putLe64(d, off, titleId);
                off += 7;
            }
        }

        File parent = output.getParentFile();
        if (parent != null) parent.mkdirs();
        Files.write(output.toPath(), d);
    }

    public static long[] allowedRange(File npdm) throws IOException {
        byte[] d = Files.readAllBytes(npdm.toPath());
        requireMagic(d, 0, "META");
        int acidOff = le32(d, 0x78);
        if (acidOff < 0 || acidOff + 0x220 > d.length) throw new IOException("NPDM ACID offset غير صالح");
        requireMagic(d, acidOff + 0x200, "ACID");
        return new long[]{le64(d, acidOff + 0x210), le64(d, acidOff + 0x218)};
    }

    private static boolean range(byte[] d, int off, int size) {
        return off >= 0 && size >= 0 && off <= d.length && size <= d.length - off;
    }

    private static void requireMagic(byte[] d, int off, String magic) throws IOException {
        byte[] m = magic.getBytes(StandardCharsets.US_ASCII);
        if (off < 0 || off + m.length > d.length) throw new IOException("NPDM magic خارج الملف");
        for (int i = 0; i < m.length; i++) if (d[off + i] != m[i])
            throw new IOException("NPDM magic غير صحيح: " + magic);
    }

    private static int le32(byte[] d, int o) {
        return (d[o] & 255) | ((d[o+1] & 255) << 8) | ((d[o+2] & 255) << 16) | ((d[o+3] & 255) << 24);
    }

    private static long le64(byte[] d, int o) {
        long v = 0;
        for (int i = 7; i >= 0; i--) v = (v << 8) | (d[o+i] & 255L);
        return v;
    }

    private static void putLe64(byte[] d, int o, long v) {
        for (int i = 0; i < 8; i++) d[o+i] = (byte)(v >>> (8*i));
    }
}
