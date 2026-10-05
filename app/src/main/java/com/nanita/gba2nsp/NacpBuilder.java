package com.nanita.gba2nsp;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class NacpBuilder {
    private static final long SAVE_SIZE = 8L * 1024 * 1024;      // 8 MiB for SRAM + save states/config.
    private static final long SAVE_JOURNAL = 4L * 1024 * 1024;   // 4 MiB journal.

    private NacpBuilder() {}

    public static byte[] create(String title, String publisher, String version, long titleId) {
        byte[] out = new byte[0x4000];
        for (int lang = 0; lang < 16; lang++) {
            int base = lang * 0x300;
            putCString(out, base, 0x200, title);
            putCString(out, base + 0x200, 0x100, publisher);
        }

        // Require/select a user account because saves are mounted per-user.
        out[0x3025] = 1; // StartupUserAccount: Required.
        out[0x3026] = 0; // UserAccountSwitchLock: Disable.

        // All legacy language slots contain fallback strings above.
        putLe32(out, 0x302C, 0xFFFF);
        out[0x3034] = 0; // Screenshot allowed.
        out[0x3035] = 0; // Video capture disabled/default.

        putLe64(out, 0x3038, titleId);           // PresenceGroupId.
        putCString(out, 0x3060, 0x10, version); // DisplayVersion.
        putLe64(out, 0x3070, titleId + 0x1000L); // AddOnContentBaseId.
        putLe64(out, 0x3078, titleId);           // SaveDataOwnerId.

        // Critical for persistent mGBA SRAM/save states. Zero here can leave
        // fsdevMountSaveData() with no application save-data container to open.
        putLe64(out, 0x3080, SAVE_SIZE);
        putLe64(out, 0x3088, SAVE_JOURNAL);
        putLe64(out, 0x3148, SAVE_SIZE);
        putLe64(out, 0x3150, SAVE_JOURNAL);

        return out;
    }

    private static void putCString(byte[] dst, int off, int max, String value) {
        byte[] b = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
        int n = Math.min(max - 1, b.length);
        Arrays.fill(dst, off, off + max, (byte)0);
        System.arraycopy(b, 0, dst, off, n);
    }

    private static void putLe32(byte[] d, int o, long v) {
        for (int i = 0; i < 4; i++) d[o + i] = (byte)(v >>> (8 * i));
    }

    private static void putLe64(byte[] d, int o, long v) {
        for (int i = 0; i < 8; i++) d[o + i] = (byte)(v >>> (8 * i));
    }
}
