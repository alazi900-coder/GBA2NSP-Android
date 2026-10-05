#!/usr/bin/env python3
"""Patch mGBA 0.10.5 Switch frontend into a reusable single-ROM NSP runtime.

The resulting NSO always opens romfs:/game.gba. SaveData is mounted using the
*current* Horizon ProgramId queried at runtime, so the same exefs/main can be
reused by many NSPs whose ACI0 ProgramId is patched by the Android app.
"""
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parent
main = root / "vendor" / "mgba" / "src" / "platform" / "switch" / "main.c"
if not main.is_file():
    raise SystemExit(f"mGBA Switch main.c not found: {main}")

s = main.read_text(encoding="utf-8")
if "GBA_ANDROID_RUNTIME_PATCH" in s:
    print("mGBA runtime already patched")
    raise SystemExit(0)

main_sig = re.search(r"int main\s*\(int argc, char\* argv\[\]\)\s*\{", s)
if not main_sig:
    raise SystemExit("Unsupported mGBA source: main() signature not found")

forced = r'''
	/* GBA_ANDROID_RUNTIME_PATCH: launch the ROM embedded in RomFS. */
	char* gbaSingleArgv[] = { "mgba", "romfs:/game.gba", NULL };
	argc = 2;
	argv = gbaSingleArgv;
'''
s = s[:main_sig.end()] + forced + s[main_sig.end():]

init_marker = 'mGUIInit(&runner, "switch");'
if init_marker not in s:
    raise SystemExit("Unsupported mGBA source: mGUIInit marker not found")

save_setup = r'''

	/* GBA_ANDROID_RUNTIME_PATCH: persistent per-title/per-user SaveData. */
	AccountUid gbaSingleUid = {0};
	u64 gbaSingleProgramId = 0;
	Result gbaSingleRc = accountInitialize(AccountServiceType_Application);
	if (R_SUCCEEDED(gbaSingleRc)) gbaSingleRc = accountGetPreselectedUser(&gbaSingleUid);
	accountExit();
	if (R_SUCCEEDED(gbaSingleRc)) {
		gbaSingleRc = svcGetInfo(&gbaSingleProgramId, InfoType_ProgramId, CUR_PROCESS_HANDLE, 0);
	}
	if (R_FAILED(gbaSingleRc) || !gbaSingleProgramId) {
		printf("GBA NSP: ProgramId/account query failed: 0x%x\n", gbaSingleRc);
		return 1;
	}
	gbaSingleRc = fsdevMountSaveData("save", gbaSingleProgramId, gbaSingleUid);
	if (R_FAILED(gbaSingleRc)) {
		printf("GBA NSP: fsdevMountSaveData failed: 0x%x\n", gbaSingleRc);
		return 1;
	}
	mCoreConfigSetOverrideValue(&runner.config, "savegamePath", "save:/");
'''
s = s.replace(init_marker, init_marker + save_setup, 1)

deinit_marker = "mGUIDeinit(&runner);"
if deinit_marker not in s:
    raise SystemExit("Unsupported mGBA source: mGUIDeinit marker not found")
s = s.replace(
    deinit_marker,
    'fsdevCommitDevice("save");\n\tfsdevUnmountDevice("save");\n\t' + deinit_marker,
    1,
)

main.write_text(s, encoding="utf-8")
print("Patched reusable mGBA Switch runtime:", main)
