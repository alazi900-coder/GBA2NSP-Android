# Build validation — v0.2

## اختبارات نجحت في بيئة العمل الحالية

- Java `NacpBuilder` compiles and outputs exactly `0x4000` bytes.
- PresenceGroupId at `0x3038` matches the selected Title ID.
- SaveDataOwnerId at `0x3078` matches the selected Title ID.
- UserAccountSaveDataSize at `0x3080` = 8 MiB.
- UserAccountSaveDataJournalSize at `0x3088` = 4 MiB.
- `NpdmPatcher` changes ACI0 ProgramId and leaves ACID min/max unchanged.
- Runtime NPDM JSON parses and includes SVC `0x26`, `0x27`, `0x28`, `0x29`.
- Runtime patch Python syntax passes.
- Native exit shim passes C11 syntax check.

## يحتاج بيئة خارجية للتأكيد الثنائي

- Android APK native link/build: يحتاج Android SDK 35 + NDK 27 + CMake 3.22.1.
- Switch Runtime NSO/NPDM build: يحتاج devkitA64/npdmtool/elf2nso.
- التجربة النهائية على Atmosphère: تحتاج Switch فعلي و`prod.keys` يقدمه المستخدم.

تم تضمين GitHub Actions لكل من APK والـSwitch Runtime لكي يتم هذا التحقق في البيئة المناسبة.
