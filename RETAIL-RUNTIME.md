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
