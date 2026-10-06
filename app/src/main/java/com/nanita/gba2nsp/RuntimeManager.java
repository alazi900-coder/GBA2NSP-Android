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
    public static File rtldFile(Context c) { return new File(runtimeDir(c), "exefs/rtld"); }
    public static File sdkFile(Context c) { return new File(runtimeDir(c), "exefs/sdk"); }
    public static File subsdk0File(Context c) { return new File(runtimeDir(c), "exefs/subsdk0"); }
    public static File fontFile(Context c) { return new File(runtimeDir(c), "romfs/font-new.png"); }
    public static File typeFile(Context c) { return new File(runtimeDir(c), "RUNTIME-TYPE.txt"); }

    public static boolean isRetailRuntime(Context c) {
        return mainFile(c).isFile() && mainFile(c).length() > 0x1000
                && npdmFile(c).isFile() && npdmFile(c).length() >= 0x300
                && rtldFile(c).isFile() && rtldFile(c).length() > 0x100
                && sdkFile(c).isFile() && sdkFile(c).length() > 0x1000
                && subsdk0File(c).isFile() && subsdk0File(c).length() > 0x1000;
    }

    public static boolean isGenericRetailRuntime(Context c) {
        if (!isRetailRuntime(c) || !typeFile(c).isFile()) return false;
        try {
            String s = readText(typeFile(c), 128).trim();
            return s.equalsIgnoreCase("retail-generic-v1");
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean isMgbaRuntime(Context c) {
        return mainFile(c).isFile() && mainFile(c).length() > 0x1000
                && npdmFile(c).isFile() && npdmFile(c).length() >= 0x300
                && fontFile(c).isFile() && fontFile(c).length() > 1024;
    }

    public static boolean isReady(Context c) {
        return isRetailRuntime(c) || isMgbaRuntime(c);
    }

    public static String romRelativePath(Context c) {
        if (isGenericRetailRuntime(c)) return "game.gba";
        return isRetailRuntime(c) ? "FireRed_e.gba" : "game.gba";
    }

    public static String runtimeKind(Context c) {
        if (isGenericRetailRuntime(c)) return "retail-generic-v1";
        if (isRetailRuntime(c)) return "retail-firered";
        if (isMgbaRuntime(c)) return "mgba";
        return "none";
    }

    public static String describe(Context c) {
        if (!isReady(c)) return "Runtime Switch: غير مثبت";
        try {
            long[] r = NpdmPatcher.allowedRange(npdmFile(c));
            String kind = isGenericRetailRuntime(c) ? "Retail GBA Generic V1" : (isRetailRuntime(c) ? "Retail FireRed Template" : "mGBA");
            return String.format("Runtime Switch: %s جاهز ✓  [%016X-%016X]", kind, r[0], r[1]);
        } catch (Throwable e) {
            return "Runtime Switch: NPDM غير صالح";
        }
    }

    public static void importZip(Context c, Uri uri) throws Exception {
        File root = runtimeDir(c);
        deleteRecursive(root);
        root.mkdirs();

        try (InputStream raw = c.getContentResolver().openInputStream(uri)) {
            if (raw == null) throw new IOException("تعذر فتح Runtime ZIP");
            try (ZipInputStream z = new ZipInputStream(new BufferedInputStream(raw))) {
                ZipEntry e;
                byte[] buf = new byte[128 * 1024];
                while ((e = z.getNextEntry()) != null) {
                    if (e.isDirectory()) continue;
                    String name = normalize(e.getName());

                    String outName = null;
                    if (name.equals("exefs/main") || name.equals("main")) outName = "exefs/main";
                    else if (name.equals("exefs/main.npdm") || name.equals("main.npdm")) outName = "exefs/main.npdm";
                    else if (name.equals("exefs/rtld") || name.equals("rtld")) outName = "exefs/rtld";
                    else if (name.equals("exefs/sdk") || name.equals("sdk")) outName = "exefs/sdk";
                    else if (name.equals("exefs/subsdk0") || name.equals("subsdk0")) outName = "exefs/subsdk0";
                    else if (name.equals("romfs/font-new.png") || name.equals("font-new.png")) outName = "romfs/font-new.png";
                    else if (name.equalsIgnoreCase("RUNTIME-TYPE.txt")) outName = "RUNTIME-TYPE.txt";

                    if (outName == null) continue;
                    File out = safeChild(root, outName);
                    File parent = out.getParentFile();
                    if (parent != null) parent.mkdirs();
                    try (OutputStream os = new FileOutputStream(out)) {
                        int n;
                        while ((n = z.read(buf)) > 0) os.write(buf, 0, n);
                    }
                }
            }
        }

        if (!isReady(c)) {
            throw new IOException(
                    "Runtime ZIP غير مكتمل. المقبول: mGBA (main + main.npdm + font-new.png) " +
                    "أو Retail Template (main + main.npdm + rtld + sdk + subsdk0)");
        }
        NpdmPatcher.allowedRange(npdmFile(c));
    }

    public static void copyIntoWorkspace(Context c, File workspace, long titleId) throws Exception {
        if (!isReady(c)) throw new IOException("استورد GBA Switch Runtime أولا");

        File exefs = new File(workspace, "exefs");
        exefs.mkdirs();
        copy(mainFile(c), new File(exefs, "main"));
        NpdmPatcher.patchProgramId(npdmFile(c), new File(exefs, "main.npdm"), titleId);

        File romfs = new File(workspace, "romfs");
        romfs.mkdirs();

        if (isRetailRuntime(c)) {
            copy(rtldFile(c), new File(exefs, "rtld"));
            copy(sdkFile(c), new File(exefs, "sdk"));
            copy(subsdk0File(c), new File(exefs, "subsdk0"));
        } else {
            copy(fontFile(c), new File(romfs, "font-new.png"));
        }
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

    private static String readText(File f, int maxBytes) throws IOException {
        try (InputStream in = new FileInputStream(f); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[128];
            int n, total = 0;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > maxBytes) throw new IOException("Runtime type marker كبير بشكل غير متوقع");
                out.write(buf, 0, n);
            }
            return out.toString("UTF-8");
        }
    }

    private static void copy(File a, File b) throws IOException {
        File parent = b.getParentFile();
        if (parent != null) parent.mkdirs();
        try (InputStream in = new FileInputStream(a); OutputStream out = new FileOutputStream(b)) {
            byte[] x = new byte[131072];
            int n;
            while ((n = in.read(x)) > 0) out.write(x, 0, n);
        }
    }

    private static void deleteRecursive(File f) {
        if (!f.exists()) return;
        if (f.isDirectory()) {
            File[] a = f.listFiles();
            if (a != null) for (File x : a) deleteRecursive(x);
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
