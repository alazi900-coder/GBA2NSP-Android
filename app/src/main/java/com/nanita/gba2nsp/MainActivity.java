package com.nanita.gba2nsp;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int PICK_ROM=100, PICK_KEYS=101, EXPORT_ZIP=102, PICK_RUNTIME=103, EXPORT_NSP=104, PICK_ICON=105;
    private TextView romStatus, keysStatus, runtimeStatus, engineStatus, iconStatus, log;
    private EditText title, author, tid;
    private File romFile, keysFile, iconFile, workspace, builtNsp;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(buildUi());
        refreshEngine();
        refreshRuntime();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18),dp(18),dp(18),dp(30));
        box.setGravity(Gravity.RIGHT);

        TextView h = text("GBA إلى NSP", 26, true); box.addView(h);
        box.addView(text("Android ARM64 — إنشاء الحزمة من الهاتف", 14, false));
        engineStatus = text("", 13, false); box.addView(engineStatus);
        runtimeStatus = text("", 13, false); box.addView(runtimeStatus);

        title = edit("اسم اللعبة"); box.addView(title);
        author = edit("الناشر / المعرب"); author.setText("NANITA"); box.addView(author);
        tid = edit("Title ID"); tid.setText(BuilderCore.generateTitleId()); box.addView(tid);
        Button gen = button("توليد Title ID جديد");
        gen.setOnClickListener(v -> tid.setText(BuilderCore.generateTitleId())); box.addView(gen);

        Button runtime = button("استيراد GBA Switch Runtime / Retail Template");
        runtime.setOnClickListener(v -> pick(PICK_RUNTIME, "application/zip")); box.addView(runtime);

        Button rom = button("اختيار ملف GBA");
        rom.setOnClickListener(v -> pick(PICK_ROM, "application/octet-stream")); box.addView(rom);
        romStatus = text("لم يتم اختيار ROM", 13, false); box.addView(romStatus);

        Button icon = button("اختيار الأيقونة PNG/JPG");
        icon.setOnClickListener(v -> pick(PICK_ICON, "image/*")); box.addView(icon);
        iconStatus = text("الأيقونة اختيارية", 13, false); box.addView(iconStatus);

        Button keys = button("اختيار prod.keys");
        keys.setOnClickListener(v -> pick(PICK_KEYS, "*/*")); box.addView(keys);
        keysStatus = text("لم يتم اختيار المفاتيح", 13, false); box.addView(keysStatus);

        Button prepare = button("تجهيز ملفات الحزمة");
        prepare.setOnClickListener(v -> prepare()); box.addView(prepare);
        Button export = button("تصدير Workspace ZIP");
        export.setOnClickListener(v -> exportZip()); box.addView(export);
        Button nsp = button("إنشاء NSP");
        nsp.setOnClickListener(v -> buildNsp()); box.addView(nsp);

        log = text("", 13, false); log.setTypeface(Typeface.MONOSPACE); box.addView(log);
        scroll.addView(box); return scroll;
    }

    private void pick(int request, String mime) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE); i.setType(mime);
        startActivityForResult(i, request);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (requestCode == PICK_ROM) {
                romFile = BuilderCore.importUri(this, uri, new File(getCacheDir(), "input/game.gba"));
                BuilderCore.validateGba(romFile);
                romStatus.setText(String.format(Locale.US, "✓ ROM صالح — %.2f MiB", romFile.length()/1048576.0));
            } else if (requestCode == PICK_KEYS) {
                keysFile = BuilderCore.importUri(this, uri, new File(getFilesDir(), "private/prod.keys"));
                BuilderCore.validateKeys(keysFile);
                keysStatus.setText("✓ تم التحقق من المفاتيح محليا");
            } else if (requestCode == PICK_ICON) {
                iconFile = BuilderCore.importUri(this, uri, new File(getCacheDir(), "input/icon"));
                BuilderCore.validateIcon(iconFile);
                iconStatus.setText(String.format(Locale.US, "✓ أيقونة مضافة — %.1f KiB", iconFile.length()/1024.0));
            } else if (requestCode == PICK_RUNTIME) {
                RuntimeManager.importZip(this, uri);
                refreshRuntime();
                append("✓ تم تثبيت Switch Runtime داخل مساحة التطبيق");
            } else if (requestCode == EXPORT_ZIP) {
                if (workspace == null) throw new IOException("جهز الحزمة أولا");
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new IOException("تعذر فتح ملف الإخراج");
                    BuilderCore.exportWorkspaceZip(workspace, out);
                }
                append("✓ تم تصدير Workspace ZIP");
            } else if (requestCode == EXPORT_NSP) {
                if (builtNsp == null || !builtNsp.isFile()) throw new IOException("ملف NSP غير موجود");
                try (InputStream in = new FileInputStream(builtNsp); OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new IOException("تعذر فتح ملف NSP للإخراج");
                    byte[] b = new byte[256 * 1024]; int n;
                    while ((n = in.read(b)) > 0) out.write(b, 0, n);
                }
                append("✓ تم حفظ NSP في المكان الذي اخترته");
            }
        } catch (Exception e) { fail(e); }
    }

    private void prepare() {
        try {
            if (romFile == null) throw new IOException("اختر ROM أولا");
            if (keysFile == null) throw new IOException("اختر prod.keys أولا");
            if (!RuntimeManager.isReady(this)) throw new IOException("استورد GBA Switch Runtime أولا");
            BuilderCore.validateGba(romFile); BuilderCore.validateKeys(keysFile);
            if (iconFile != null) BuilderCore.validateIcon(iconFile);
            workspace = BuilderCore.prepareWorkspace(this, romFile, iconFile, title.getText().toString(), author.getText().toString(), tid.getText().toString());
            String romPath = RuntimeManager.romRelativePath(this);
            append("✓ romfs/" + romPath);
            append("✓ exefs/main");
            append("✓ exefs/main.npdm — ProgramId/ACI0 owner IDs patched");
            if (RuntimeManager.isRetailRuntime(this)) {
                append("✓ exefs/rtld");
                append("✓ exefs/sdk");
                append("✓ exefs/subsdk0");
                append(RuntimeManager.isGenericRetailRuntime(this)
                        ? "✓ Retail GBA Generic V1 — FireRed telemetry disabled"
                        : "✓ Retail FireRed runtime template");
            } else {
                append("✓ romfs/font-new.png — mGBA GUI font");
            }
            append("✓ control/control.nacp — SaveData 8 MiB + journal 4 MiB");
            append(iconFile == null ? "✓ icon: using default hacBrewPack behavior" : "✓ control icon copied");
            append("✓ Title ID: " + tid.getText());
        } catch (Exception e) { fail(e); }
    }

    private void exportZip() {
        if (workspace == null) { fail(new IOException("اضغط تجهيز ملفات الحزمة أولا")); return; }
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("application/zip");
        i.putExtra(Intent.EXTRA_TITLE, safeName() + "-NSP-Workspace.zip");
        startActivityForResult(i, EXPORT_ZIP);
    }

    private void buildNsp() {
        try {
            if (workspace == null) prepare();
            if (workspace == null) return;
            if (!NativeEngine.nativePackerAvailable())
                throw new IOException("محرك hacBrewPack لم يدخل في هذا الـAPK. ابن المشروع مع Git/Internet أو vendor source.");
            if (keysFile == null || !keysFile.isFile()) throw new IOException("prod.keys غير موجود");

            File outDir = new File(getFilesDir(), "nsp_output");
            BuilderCore.deleteRecursive(outDir); outDir.mkdirs();
            BuilderCore.deleteRecursive(new File(workspace, "hbp_temp"));
            BuilderCore.deleteRecursive(new File(workspace, "hbp_nca"));
            append("→ بدء " + NativeEngine.nativeEngineVersion());
            int rc = NativeEngine.nativeBuildNsp(keysFile.getAbsolutePath(), workspace.getAbsolutePath(), outDir.getAbsolutePath());
            showNativeLog();
            if (rc != 0) throw new IOException("hacBrewPack انتهى برمز " + rc);

            File[] nsps = outDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".nsp"));
            if (nsps == null || nsps.length == 0) throw new IOException("لم ينتج المحرك ملف NSP");
            builtNsp = nsps[0];
            append(String.format(Locale.US, "✓ NSP جاهز — %.2f MiB", builtNsp.length()/1048576.0));

            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("application/octet-stream");
            i.putExtra(Intent.EXTRA_TITLE, safeName() + ".nsp");
            startActivityForResult(i, EXPORT_NSP);
        } catch (Throwable e) { fail(e); }
    }

    private void showNativeLog() {
        try {
            File f = new File(workspace, "hacbrewpack.log");
            if (!f.isFile()) return;
            byte[] all = Files.readAllBytes(f.toPath());
            int start = Math.max(0, all.length - 12000);
            String text = new String(all, start, all.length - start, StandardCharsets.UTF_8).trim();
            if (!text.isEmpty()) append("--- hacBrewPack ---\n" + text + "\n--- end ---");
        } catch (Throwable ignored) {}
    }

    private void refreshEngine() {
        try {
            boolean ok = NativeEngine.nativePackerAvailable();
            engineStatus.setText(ok ? "محرك NSP: hacBrewPack ARM64 جاهز ✓" : "محرك NSP: غير مدمج في هذا البناء");
        } catch(Throwable t) { engineStatus.setText("محرك NSP: غير متاح"); }
    }

    private void refreshRuntime() { runtimeStatus.setText(RuntimeManager.describe(this)); }
    private String safeName() {
        String n = title.getText().toString().trim(); if (n.isEmpty()) n="GBA-Game";
        return n.replaceAll("[^A-Za-z0-9._-]+", "_");
    }
    private void append(String s) { log.append(s + "\n"); }
    private void fail(Throwable e) { String s="✗ "+(e.getMessage()==null?e.toString():e.getMessage()); append(s); Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }
    private TextView text(String s,int sp,boolean bold){ TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setGravity(Gravity.RIGHT);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);v.setPadding(0,dp(6),0,dp(6));return v; }
    private EditText edit(String hint){ EditText e=new EditText(this);e.setHint(hint);e.setGravity(Gravity.RIGHT);e.setTextDirection(View.TEXT_DIRECTION_RTL);return e; }
    private Button button(String s){ Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b; }
    private int dp(int x){ return (int)(x*getResources().getDisplayMetrics().density+0.5f); }
}
