# GBA Switch Runtime

هذا Runtime يبنى مرة واحدة فقط بواسطة devkitA64، ثم تستورده داخل تطبيق Android.
بعد ذلك لا يحتاج الهاتف إلى devkitA64 ولا إلى إعادة بناء mGBA لكل لعبة.

الملف الناتج:

`runtime-builder/dist/GBA-Switch-Runtime.zip`

ويحتوي فقط:

- `exefs/main` — mGBA 0.10.5 للسويتش، مضبوط لفتح `romfs:/game.gba`.
- `exefs/main.npdm` — قالب NPDM يسمح بـTitle ID من `05xxxxxxxxxxxxxx` ويحتوي SVC 0x26–0x29.

## الحفظ

الـRuntime لا يحمل Title ID ثابتا في كود mGBA. عند التشغيل يستعلم عن ProgramId الحالي بواسطة `svcGetInfo` ثم يفتح SaveData لذلك العنوان والمستخدم الحالي. تطبيق Android يحدد مساحة SaveData في `control.nacp` ويعدل ProgramId في ACI0 قبل التغليف.

## البناء محليا

من بيئة devkitPro/devkitA64:

```bash
./runtime-builder/build_runtime.sh
```

أو استخدم GitHub Actions المرفق: **Build GBA Switch Runtime**.
