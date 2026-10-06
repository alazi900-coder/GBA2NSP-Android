package com.nanita.gba2nsp;

import android.content.Context;
import android.net.Uri;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class RuntimeManager {
    private RuntimeManager() {}

    public static File runtimeDir(Context c) { return new File(c.getFilesDir(), "switch_runtime"); }
    public static File mainFile(Context c) { return new File(runtimeDir(c), "exefs/main"); }
    public static File npdmFile(Context c) { return new File(runtimeDir(c), "exefs/main.npdm"); }
    public static File fontFile(Context c) { return new File(runtimeDir(c), "romfs/font-new.png"); }

    public static boolean isReady(Context c) {
        File m = mainFile(c), n = npdmFile(c), f = fontFile(c);
        return m.isFile() && m.length() > 0x1000
                && n.isFile() && n.length() >= 0x300
                && f.isFile() && f.length() > 1024;
    }

    public static String describe(Context c) {
        if (!isReady(c)) return "Runtime Switch: غير مثبت";
        try {
            long[] r = NpdmPatcher.allowedRange(npdmFile(c));
            return String.format("Runtime Switch: جاهز ✓  [%016X-%016X]", r[0], r[1]);
        } catch (Throwable e) {
            return "Runtime Switch: NPDM غير صالح";
        }
    }

    public static void importZip(Context c, Uri uri) throws Exception {
        File root = runtimeDir(c);
        deleteRecursive(root); root.mkdirs();
        try (InputStream raw = c.getContentResolver().openInputStream(uri)) {
            if (raw == null) throw new IOException("تعذر فتح Runtime ZIP");
            try (ZipInputStream z = new ZipInputStream(new BufferedInputStream(raw))) {
                ZipEntry e;
                byte[] buf = new byte[128 * 1024];
                while ((e = z.getNextEntry()) != null) {
                    if (e.isDirectory()) continue;
                    String name = normalize(e.getName());
                    boolean isMain = name.equals("exefs/main") || name.equals("main");
                    boolean isNpdm = name.equals("exefs/main.npdm") || name.equals("main.npdm");
                    boolean isFont = name.equals("romfs/font-new.png") || name.equals("font-new.png");
                    if (!(isMain || isNpdm || isFont)) continue;
                    String outName = isNpdm ? "exefs/main.npdm" : (isFont ? "romfs/font-new.png" : "exefs/main");
                    File out = safeChild(root, outName);
                    File parent = out.getParentFile(); if (parent != null) parent.mkdirs();
                    try (OutputStream os = new FileOutputStream(out)) {
                        int n; while ((n = z.read(buf)) > 0) os.write(buf, 0, n);
                    }
                }
            }
        }
        if (!isReady(c)) throw new IOException("Runtime ZIP V3 يجب أن يحتوي exefs/main و exefs/main.npdm و romfs/font-new.png");
        NpdmPatcher.allowedRange(npdmFile(c));
    }

    public static void copyIntoWorkspace(Context c, File workspace, long titleId) throws Exception {
        if (!isReady(c)) throw new IOException("استورد GBA Switch Runtime أولا");
        File exefs = new File(workspace, "exefs"); exefs.mkdirs();
        copy(mainFile(c), new File(exefs, "main"));
        NpdmPatcher.patchProgramId(npdmFile(c), new File(exefs, "main.npdm"), titleId);
        File romfs = new File(workspace, "romfs"); romfs.mkdirs();
        copy(fontFile(c), new File(romfs, "font-new.png"));
    }

    private static String normalize(String n) {
        n = n.replace('\\','/');
        while (n.startsWith("/")) n = n.substring(1);
        return n;
    }
    private static File safeChild(File root, String rel) throws IOException {
        File f = new File(root, rel);
        String rp = root.getCanonicalPath() + File.separator;
        if (!f.getCanonicalPath().startsWith(rp)) throw new IOException("ZIP path غير آمن");
        return f;
    }
    private static void copy(File a, File b) throws IOException {
        try (InputStream in = new FileInputStream(a); OutputStream out = new FileOutputStream(b)) {
            byte[] x = new byte[131072]; int n; while ((n=in.read(x))>0) out.write(x,0,n);
        }
    }
    private static void deleteRecursive(File f) {
        if (!f.exists()) return;
        if (f.isDirectory()) { File[] a=f.listFiles(); if(a!=null) for(File x:a) deleteRecursive(x); }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
