# Packaging diagnosis — v0.11

Binary comparison target: working FireRed NSP `0100554023408000`.

## Confirmed differences found in the old Android packager

- Generated Application IDs did not reserve the low 12 bits. v0.11 generates canonical application IDs ending in `000`.
- hacBrewPack hardcoded NCA SdkAddonVersion `0.12.17.0`. The working Program/Control NCAs use `21.4.0.0`; Meta uses `22.2.0.0`.
- hacBrewPack hardcoded KeyGeneration 0. The working package uses KeyGeneration `0x15` (21), with KeyGenerationOld `0x02`.
- The Android JNI forced `--nologo`. The working Program NCA has a Logo PFS0 section containing `NintendoLogo.png` and `StartupMovie.gif`.
- The old CNMT used RequiredSystemVersion 0. The working Application CNMT uses `0x54200000`.
- Earlier versions rebuilt control.nacp from scratch. v0.10+ can preserve the original retail control template and icons.

## v0.11 changes

- NCA Program/Control: SDK 21.4.0.0, KeyGeneration 21.
- NCA Meta: SDK 22.2.0.0, KeyGeneration 21.
- Key-area encryption uses `key_area_key_application_14`, matching KeyGeneration 21.
- Application Title IDs always end in `000`.
- CNMT RequiredSystemVersion = `0x54200000`.
- Original Logo section is restored when the imported runtime contains logo assets.

## Still intentionally different from the retail NSP

- The generated Program NCA is standard-crypto/homebrew style rather than the retail rights-managed Program NCA.
- No retail ticket/certificate is generated.
- LegalInformation/Manual NCA is not generated yet.
- Retail RSA NCA signatures are not generated.

These remaining differences should not be conflated with the confirmed metadata bugs above; test v0.11 first, then add LegalInformation/other structural parity only if required.
