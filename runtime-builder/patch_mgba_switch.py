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
if "GBA_ANDROID_RUNTIME_PATCH_V5" in s:
    print("mGBA runtime already patched (V3)")
    raise SystemExit(0)
if "GBA_ANDROID_RUNTIME_PATCH" in s:
    raise SystemExit("Old GBA runtime patch detected; clean vendor/mgba before rebuilding")


# Installed-title runtime does not need nxlink/network debug I/O. Keeping these
# calls would require bsd:u/sfdnsres and can make a packaged NSP fail early.
for network_line in ("\tsocketInitializeDefault();\n", "\tnxlinkStdio();\n", "\tsocketExit();\n"):
    if network_line not in s:
        raise SystemExit(f"Unsupported mGBA source: expected network line not found: {network_line.strip()}")
    s = s.replace(network_line, "", 1)

main_sig = re.search(r"int main\s*\(int argc, char\* argv\[\]\)\s*\{", s)
if not main_sig:
    raise SystemExit("Unsupported mGBA source: main() signature not found")

forced = r'''
	/* GBA_ANDROID_RUNTIME_PATCH_V5: launch the ROM embedded in RomFS. */
	char* gbaSingleArgv[] = { "mgba", "romfs:/game.gba", NULL };
	argc = 2;
	argv = gbaSingleArgv;
'''
s = s[:main_sig.end()] + forced + s[main_sig.end():]

init_marker = 'mGUIInit(&runner, "switch");'
if init_marker not in s:
    raise SystemExit("Unsupported mGBA source: mGUIInit marker not found")

save_setup = r'''

	/* GBA_ANDROID_RUNTIME_PATCH_V5: persistent SaveData when available.
	 * Failure to mount SaveData must not abort the emulator. */
	bool gbaSingleSaveMounted = false;
	AccountUid gbaSingleUid = {0};
	u64 gbaSingleProgramId = 0;
	Result gbaSingleRc = accountInitialize(AccountServiceType_Application);
	if (R_SUCCEEDED(gbaSingleRc)) {
		gbaSingleRc = accountGetPreselectedUser(&gbaSingleUid);
		accountExit();
	}
	if (R_SUCCEEDED(gbaSingleRc)) {
		gbaSingleRc = svcGetInfo(&gbaSingleProgramId, InfoType_ProgramId, CUR_PROCESS_HANDLE, 0);
	}
	if (R_SUCCEEDED(gbaSingleRc) && gbaSingleProgramId) {
		gbaSingleRc = fsdevMountSaveData("save", gbaSingleProgramId, gbaSingleUid);
		if (R_SUCCEEDED(gbaSingleRc)) {
			gbaSingleSaveMounted = true;
			mCoreConfigSetOverrideValue(&runner.config, "savegamePath", "save:/");
		} else {
			printf("GBA NSP: SaveData unavailable (0x%x), continuing without mounted save.\n", gbaSingleRc);
		}
	} else {
		printf("GBA NSP: account/ProgramId unavailable (0x%x), continuing.\n", gbaSingleRc);
	}
'''
s = s.replace(init_marker, init_marker + save_setup, 1)

deinit_marker = "mGUIDeinit(&runner);"
if deinit_marker not in s:
    raise SystemExit("Unsupported mGBA source: mGUIDeinit marker not found")
s = s.replace(
    deinit_marker,
    'if (gbaSingleSaveMounted) {\n\t\tfsdevCommitDevice("save");\n\t\tfsdevUnmountDevice("save");\n\t}\n\t' + deinit_marker,
    1,
)

main.write_text(s, encoding="utf-8")
print("Patched reusable mGBA Switch runtime:", main)
