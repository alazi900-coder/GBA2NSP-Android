package com.nanita.gba2nsp;

import android.content.Context;
import android.net.Uri;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class BuilderCore {
    private static final Pattern TID = Pattern.compile("(?i)^01[0-9a-f]{14}$");
    private BuilderCore() {}

    public static String generateTitleId() {
        byte[] b = new byte[7]; new SecureRandom().nextBytes(b);
        StringBuilder s = new StringBuilder("05");
        for (byte v : b) s.append(String.format(Locale.US, "%02X", v & 0xff));
        return s.toString();
    }

    public static long parseTitleId(String s) {
        String x = s == null ? "" : s.trim().replace("0x", "").replace("0X", "");
        if (!TID.matcher(x).matches()) throw new IllegalArgumentException("Title ID يجب أن يكون 16 رقما سداسيا ويبدأ بـ 01");
        return Long.parseUnsignedLong(x, 16);
    }

    public static File importUri(Context c, Uri uri, File dst) throws IOException {
        File parent = dst.getParentFile(); if (parent != null) parent.mkdirs();
        try (InputStream in = c.getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(dst)) {
            if (in == null) throw new IOException("تعذر فتح الملف");
            byte[] buf = new byte[1024 * 128]; int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        return dst;
    }

    public static void validateGba(File f) throws IOException {
        long size = f.length();
        if (size < 0xC0) throw new IOException("ملف GBA صغير جدا");
        if (size > 32L * 1024 * 1024) throw new IOException("حجم ROM أكبر من 32 MiB");
        try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
            r.seek(0xB2);
            int fixed = r.readUnsignedByte();
            if (fixed != 0x96) throw new IOException("ترويسة GBA غير صحيحة: البايت 0xB2 ليس 0x96");
        }
    }

    public static void validateKeys(File f) throws IOException {
        String text = readTextLimited(f, 2 * 1024 * 1024).toLowerCase(Locale.ROOT);
        if (!text.contains("header_key")) throw new IOException("prod.keys لا يحتوي header_key");
        if (!text.contains("key_area_key_application_")) throw new IOException("prod.keys لا يحتوي key_area_key_application");
    }

    private static String readTextLimited(File f, int max) throws IOException {
        try (InputStream in = new FileInputStream(f); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[8192]; int n, total=0;
            while ((n=in.read(b))>0) { total += n; if (total > max) throw new IOException("ملف المفاتيح كبير بشكل غير متوقع"); out.write(b,0,n); }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    public static void validateIcon(File f) throws IOException {
        if (f == null) return;
        long size = f.length();
        if (size < 16) throw new IOException("ملف الأيقونة صغير جدا");
        if (size > 4L * 1024 * 1024) throw new IOException("الأيقونة أكبر من 4 MiB");
        try (InputStream in = new FileInputStream(f)) {
            byte[] h = new byte[8];
            int n = in.read(h);
            boolean png = n >= 8 && (h[0] & 255) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G';
            boolean jpg = n >= 3 && (h[0] & 255) == 0xFF && (h[1] & 255) == 0xD8 && (h[2] & 255) == 0xFF;
            if (!png && !jpg) throw new IOException("الأيقونة يجب أن تكون PNG أو JPG");
        }
    }

    public static File prepareWorkspace(Context c, File rom, File icon, String title, String author, String tidText) throws Exception {
        long tid = parseTitleId(tidText);
        String safeTitle = (title == null || title.trim().isEmpty()) ? "GBA Game" : title.trim();
        if (safeTitle.getBytes(StandardCharsets.UTF_8).length >= 0x200) throw new IllegalArgumentException("اسم اللعبة طويل جدا");
        File root = new File(c.getFilesDir(), "workspace"); deleteRecursive(root); root.mkdirs();
        File romfs = new File(root, "romfs"); romfs.mkdirs();
        copy(rom, new File(romfs, "game.gba"));
        File control = new File(root, "control"); control.mkdirs();
        try (OutputStream out = new FileOutputStream(new File(control, "control.nacp"))) {
            out.write(NacpBuilder.create(safeTitle, author, "1.0.0", tid));
        }
        if (icon != null) {
            validateIcon(icon);
            copy(icon, new File(control, "icon_AmericanEnglish.dat"));
        }
        File cfg = new File(root, "config"); cfg.mkdirs();
        String hex = "0x" + tidText.toUpperCase(Locale.ROOT);
        String npdm = npdmJson(hex);
        writeUtf8(new File(cfg, "npdm.json"), npdm);
        String metadata = "{\n  \"title\": \"" + jsonEscape(safeTitle) + "\",\n  \"author\": \"" + jsonEscape(author) + "\",\n  \"title_id\": \"" + hex + "\",\n  \"rom\": \"romfs/game.gba\"\n}\n";
        writeUtf8(new File(cfg, "metadata.json"), metadata);
        RuntimeManager.copyIntoWorkspace(c, root, tid);
        return root;
    }

    public static void exportWorkspaceZip(File root, OutputStream dst) throws IOException {
        try (ZipOutputStream z = new ZipOutputStream(dst)) { zipTree(root, root, z); }
    }

    private static void zipTree(File root, File f, ZipOutputStream z) throws IOException {
        File[] list = f.listFiles(); if (list == null) return;
        byte[] buf = new byte[65536];
        for (File x : list) {
            if (x.isDirectory()) { zipTree(root, x, z); continue; }
            String name = root.toPath().relativize(x.toPath()).toString().replace(File.separatorChar, '/');
            z.putNextEntry(new ZipEntry(name));
            try (InputStream in = new FileInputStream(x)) { int n; while ((n=in.read(buf))>0) z.write(buf,0,n); }
            z.closeEntry();
        }
    }

    private static void copy(File a, File b) throws IOException {
        try (InputStream in = new FileInputStream(a); OutputStream out = new FileOutputStream(b)) {
            byte[] x = new byte[131072]; int n; while ((n=in.read(x))>0) out.write(x,0,n);
        }
    }
    private static void writeUtf8(File f, String s) throws IOException { try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) { w.write(s); } }
    private static String jsonEscape(String s) { return s == null ? "" : s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n"); }
    public static void deleteRecursive(File f) { if (!f.exists()) return; if (f.isDirectory()) { File[] a=f.listFiles(); if(a!=null) for(File x:a) deleteRecursive(x); } //noinspection ResultOfMethodCallIgnored
        f.delete(); }

    public static String npdmJson(String tid) {
        return "{\n"+
            "  \"name\": \"GBA Single NSP\",\n"+
            "  \"title_id\": \""+tid+"\",\n"+
            "  \"title_id_range_min\": \""+tid+"\",\n"+
            "  \"title_id_range_max\": \""+tid+"\",\n"+
            "  \"main_thread_stack_size\": \"0x40000\",\n"+
            "  \"main_thread_priority\": 44,\n"+
            "  \"default_cpu_id\": 2,\n"+
            "  \"process_category\": 0,\n"+
            "  \"pool_partition\": 0,\n"+
            "  \"is_64_bit\": true,\n"+
            "  \"address_space_type\": 1,\n"+
            "  \"is_retail\": true,\n"+
            "  \"filesystem_access\": {\"permissions\": \"0xFFFFFFFFFFFFFFFF\"},\n"+
            "  \"service_access\": [\"fsp-srv\",\"acc:u0\",\"appletOE\",\"apm\",\"audout:u\",\"audren:u\",\"hid\",\"lm\",\"nvdrv\",\"psm\",\"set\",\"set:sys\",\"time:u\",\"vi:u\"],\n"+
            "  \"service_host\": [],\n"+
            "  \"kernel_capabilities\": [\n"+
            "    {\"type\":\"kernel_flags\",\"value\":{\"highest_thread_priority\":59,\"lowest_thread_priority\":28,\"lowest_cpu_id\":0,\"highest_cpu_id\":2}},\n"+
            "    {\"type\":\"syscalls\",\"value\":{\n"+
            "      \"svcSetHeapSize\":\"0x01\",\"svcSetMemoryPermission\":\"0x02\",\"svcSetMemoryAttribute\":\"0x03\",\"svcMapMemory\":\"0x04\",\"svcUnmapMemory\":\"0x05\",\"svcQueryMemory\":\"0x06\",\"svcExitProcess\":\"0x07\",\"svcCreateThread\":\"0x08\",\"svcStartThread\":\"0x09\",\"svcExitThread\":\"0x0A\",\"svcSleepThread\":\"0x0B\",\"svcGetThreadPriority\":\"0x0C\",\"svcSetThreadPriority\":\"0x0D\",\"svcGetThreadCoreMask\":\"0x0E\",\"svcSetThreadCoreMask\":\"0x0F\",\"svcGetCurrentProcessorNumber\":\"0x10\",\"svcSignalEvent\":\"0x11\",\"svcClearEvent\":\"0x12\",\"svcMapSharedMemory\":\"0x13\",\"svcUnmapSharedMemory\":\"0x14\",\"svcCreateTransferMemory\":\"0x15\",\"svcCloseHandle\":\"0x16\",\"svcResetSignal\":\"0x17\",\"svcWaitSynchronization\":\"0x18\",\"svcCancelSynchronization\":\"0x19\",\"svcArbitrateLock\":\"0x1A\",\"svcArbitrateUnlock\":\"0x1B\",\"svcWaitProcessWideKeyAtomic\":\"0x1C\",\"svcSignalProcessWideKey\":\"0x1D\",\"svcGetSystemTick\":\"0x1E\",\"svcConnectToNamedPort\":\"0x1F\",\"svcSendSyncRequestLight\":\"0x20\",\"svcSendSyncRequest\":\"0x21\",\"svcSendSyncRequestWithUserBuffer\":\"0x22\",\"svcBreak\":\"0x26\",\"svcOutputDebugString\":\"0x27\",\"svcReturnFromException\":\"0x28\",\"svcGetInfo\":\"0x29\",\"svcUnmapTransferMemory\":\"0x52\"\n"+
            "    }},\n"+
            "    {\"type\":\"application_type\",\"value\":1},\n"+
            "    {\"type\":\"min_kernel_version\",\"value\":\"0x91\"},\n"+
            "    {\"type\":\"handle_table_size\",\"value\":512}\n"+
            "  ]\n"+
            "}\n";
    }
}
