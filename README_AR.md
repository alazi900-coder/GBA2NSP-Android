# GBA إلى NSP — Android v0.3

مشروع Android ARM64 لتحويل ROM من نوع `.gba` إلى حزمة NSP على الهاتف، اعتمادا على نفس فكرة مشروع الكمبيوتر الذي أرسلتَه.

## ما أصبح فعليا في v0.3

- اختيار ROM والتحقق من ترويسة GBA والحجم حتى 32 MiB.
- استيراد `prod.keys` إلى مساحة التطبيق الخاصة فقط؛ لا توجد مفاتيح داخل المصدر أو APK.
- توليد Title ID يبدأ بـ`05`.
- إنشاء `control.nacp` بحجم `0x4000` مع:
  - Title ID / PresenceGroupId.
  - SaveDataOwnerId.
  - User SaveData = **8 MiB**.
  - Journal = **4 MiB**.
- استيراد **GBA Switch Runtime V3** مرة واحدة (`exefs/main` + `main.npdm` + `romfs/font-new.png`).
- تعديل ACI0 ProgramId داخل `main.npdm` لكل لعبة، مع إبقاء ACID كما هو.
- SVC المصححة: `0x26`, `0x27`, `0x28`, `0x29`, و`0x52` (`svcUnmapTransferMemory`).
- خدمات Runtime الموسعة للصوت والعرض وApplet والطاقة.
- إزالة nxlink/socket من Runtime المثبت لتجنب اعتماد شبكة غير مطلوب.
- دمج مصدر `hacBrewPack` أثناء بناء APK عبر CMake/NDK وتحويل CLI إلى محرك JNI داخل التطبيق.
- زر **إنشاء NSP** يشغل `hacBrewPack` داخل عملية Android ويصدر NSP عبر SAF.
- ARM64 فقط (`arm64-v8a`) ولا يحتاج Root.

## لماذا يوجد Runtime منفصل؟

برنامج mGBA الذي يعمل على Nintendo Switch يجب أن يبنى بأدوات Switch (`devkitA64`). هذا يتم **مرة واحدة** فقط. بعد استيراد `GBA-Switch-Runtime.zip` في التطبيق، لا يحتاج الهاتف إلى devkitA64 ولا يعيد بناء mGBA لكل لعبة.

الـRuntime في هذه النسخة لا يحتوي Title ID ثابتا في كود mGBA: يحصل على ProgramId الحالي وقت التشغيل باستخدام `svcGetInfo` ثم يفتح SaveData لذلك العنوان والمستخدم الحالي.

## بناء الـRuntime بدون إعداد كمبيوتر يدوي

المشروع يحتوي Workflow جاهزا:

`.github/workflows/build-switch-runtime.yml`

بعد رفع المشروع إلى GitHub شغّل **Build GBA Switch Runtime** يدويا. الناتج:

`GBA-Switch-Runtime.zip`

ثم افتح التطبيق واضغط **استيراد GBA Switch Runtime** مرة واحدة.

## بناء APK

يوجد Workflow ثان:

`.github/workflows/android.yml`

يبني Debug APK مع Android NDK و`hacBrewPack` ARM64. ويمكن أيضا فتح المشروع في Android Studio وبناؤه مباشرة. أول بناء يحتاج إنترنت لأن CMake يجلب نسخة `hacBrewPack` المثبتة على commit محدد.

## الاستخدام بعد تثبيت APK والـRuntime

1. استورد `GBA-Switch-Runtime.zip` مرة واحدة.
2. اختر ROM `.gba`.
3. اختر `prod.keys` الخاص بجهازك/بيئتك.
4. أدخل الاسم والناشر أو اترك Title ID المولد.
5. اضغط **تجهيز ملفات الحزمة**.
6. اضغط **إنشاء NSP** واختر مكان حفظ الملف.

## فحص Core بدون Android SDK

```bash
./tests/test_core.sh
```

يفحص NACP SaveData وNPDM ProgramId.

> ملاحظة: لا يتضمن المشروع `prod.keys` أو مفاتيح Nintendo أو ألعابا.
