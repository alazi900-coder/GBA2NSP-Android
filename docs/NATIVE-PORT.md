# Native engine — v0.2

## Android side

`app/src/main/cpp/CMakeLists.txt` يثبت `hacBrewPack` على commit:

`56698df456eac4d5076e85499fe045a90fba790b`

ويبني مصادره وmbedTLS المرفق معه كـARM64 بواسطة Android NDK. إذا وضعت نسخة المصدر يدويا تحت:

`app/src/main/cpp/vendor/hacbrewpack/`

فسيستخدمها CMake بدلا من FetchContent.

`main()` الخاص بـhacBrewPack يعاد تسميته إلى `hacbrewpack_main()`، ثم يستدعيه JNI مباشرة. `exit()` يعترضه shim مبني على `setjmp/longjmp` لكي لا يغلق تطبيق Android عند خطأ من hacBrewPack.

المسارات التي يمررها JNI:

- `--keyset <private prod.keys>`
- `--tempdir <workspace>/hbp_temp`
- `--ncadir <workspace>/hbp_nca`
- `--nspdir <private output>`
- `--exefsdir <workspace>/exefs`
- `--romfsdir <workspace>/romfs`
- `--controldir <workspace>/control`
- `--nologo`

## Switch runtime side

`runtime-builder/build_runtime.sh` يبني mGBA 0.10.5 مرة واحدة عبر devkitA64، ثم ينتج:

- `exefs/main`
- `exefs/main.npdm`

Patch mGBA يجبر فتح `romfs:/game.gba`، ويستعلم عن ProgramId الحالي عبر `svcGetInfo`, ثم يستخدمه في `fsdevMountSaveData`. لذلك لا يحمل `main` Title ID ثابتا.

NPDM Runtime يسمح بنطاق `05xxxxxxxxxxxxxx`. تطبيق Android يتحقق من هذا النطاق ثم يغير ACI0 ProgramId فقط؛ لا يعيد توقيع أو تعديل ACID.

## SaveData

`NacpBuilder` يخصص 8 MiB user save + 4 MiB journal، ويضع SaveDataOwnerId مساويًا للـTitle ID. هذا يعالج النقص الموجود في النسخة الأولى التي كانت تترك أحجام SaveData صفرا.

## ما لم يتم التحقق منه هنا

بيئة التنفيذ التي أنشئ منها هذا المصدر لا تحتوي Android SDK/NDK ولا devkitA64، لذلك لم يتم إخراج APK/NSO ثنائي محليا. لهذا أضيف Workflowان مستقلان لبناء APK والـSwitch Runtime في GitHub Actions مع الأدوات الصحيحة.
