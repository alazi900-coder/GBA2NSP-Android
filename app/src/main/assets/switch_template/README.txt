GBA Switch Runtime v0.2

Import GBA-Switch-Runtime.zip from the Android app.
Required entries:
  exefs/main
  exefs/main.npdm

The runtime is built once with runtime-builder/build_runtime.sh or the
"Build GBA Switch Runtime" GitHub Actions workflow. It uses the current
ProgramId at runtime for SaveData, so it can be reused across generated NSPs.
