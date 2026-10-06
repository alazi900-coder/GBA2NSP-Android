# Retail GBA Runtime Template

This project can import two runtime formats:

1. **mGBA runtime**: `exefs/main`, `exefs/main.npdm`, `romfs/font-new.png`.
2. **User-supplied retail GBA template**: `exefs/main`, `exefs/main.npdm`, `exefs/rtld`, `exefs/sdk`, `exefs/subsdk0`.

For the retail template, the selected GBA ROM is written to:

```
romfs/FireRed_e.gba
```

The Android app patches the ACI0 ProgramId and matching ACI0 filesystem/save-owner ID references to the generated Title ID. The signed ACID body is not modified.

No proprietary runtime binaries are stored in this repository. The user must import a template extracted from software they are authorized to use.

The retail runtime was verified against a working package whose RomFS contains `FireRed_e.gba` directly at the RomFS root. Do not place it under a `poke/` directory.


## Generic Retail Runtime V1

A locally patched runtime may include `RUNTIME-TYPE.txt` with:

```
retail-generic-v1
```

For that runtime the app writes the selected ROM to:

```
romfs/game.gba
```

The Generic V1 binary patch changes only the default ROM basename and bypasses the FireRed/Pokemon telemetry/reporting paths. The GBA core, graphics, audio, input and backup emulation are left unchanged. Proprietary binaries are not committed to this repository.


## v0.9 packaging fix

hacBrewPack emits unsigned NCA header signatures. Reusing an untouched retail NPDM is unsafe because its valid ACID contains the retail NCA-signing public key. The builder now clears the ACID signature and modulus in the copied NPDM before packaging, while keeping the ACID policy body and ACI0 permissions intact. This makes the retail template follow the same unsigned/homebrew verification path as npdmtool-generated applications.


## v0.10 control packaging fix

The retail runtime can now import the original working `control.nacp` as `control/control.nacp.template` together with the original `control/icon_*.dat` files. The builder preserves runtime-sensitive fields such as the original SaveDataOwnerId, save-data sizes, LocalCommunicationId policy, crash-report/HDCP settings and other control flags. It only patches the application-bound IDs and user-visible strings.


## v0.12 loader fix

Base application Title IDs must satisfy `title_id & 0x1FFF == 0` because Switch/Yuzu/Eden use `0xFFFFFFFFFFFFE000` as the base-title mask. Earlier versions only cleared 12 bits, which could make the loader search for Control NCA under a different title ID and abort before loading ExeFS.

v0.12 also stops clearing the retail ACID signature/public-key block. Only ACI0 ProgramId and matching ACI0 owner-id references are patched; the signed ACID policy remains byte-identical to the original template.
