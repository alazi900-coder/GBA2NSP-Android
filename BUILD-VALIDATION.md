# Build validation — v0.3 Runtime Boot Fix

## إصلاحات V3

- تمت إضافة `svcUnmapTransferMemory` (`SVC 0x52`) إلى NPDM.
- تمت إضافة خدمات التطبيق التي يحتاجها mGBA/libnx: `appletOE`, `apm`, `audout:u`, `nvdrv`, `psm`, `set`, `time:u`, `vi:u` وغيرها.
- أزيل `socketInitializeDefault` و`nxlinkStdio` و`socketExit` من Runtime المخصص للـNSP حتى لا يعتمد على خدمات الشبكة/debug.
- أصبح Runtime ZIP يحتوي `romfs/font-new.png` إضافة إلى `exefs/main` و`exefs/main.npdm`.
- تطبيق Android يرفض Runtime القديم الناقص وينسخ الخط إلى Workspace تلقائيا.
- Workflow يفشل إذا غاب SVC 0x52 أو الخدمات الأساسية أو ملف الخط.

## تحقق محلي بدون devkitA64

شغّل:

```bash
./tests/test_core.sh
```

## يحتاج بيئة خارجية

- بناء APK: Android SDK 35 + NDK 27 + CMake 3.22.1.
- بناء Runtime: devkitA64 + `npdmtool` + `elf2nso`.
- الاختبار النهائي: Switch/Atmosphere أو محاكي Switch يدعم NSP بشكل صحيح.
