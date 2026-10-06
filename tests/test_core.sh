#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="${TMPDIR:-/tmp}/gba2nsp-core-test"
rm -rf "$TMP" && mkdir -p "$TMP/classes"
cat > "$TMP/TestCore.java" <<'JAVA'
import com.nanita.gba2nsp.NacpBuilder;
import com.nanita.gba2nsp.NpdmPatcher;
import java.nio.file.*;
public class TestCore {
  static long le64(byte[] d,int o){ long v=0; for(int i=7;i>=0;i--) v=(v<<8)|(d[o+i]&255L); return v; }
  static void p32(byte[] d,int o,int v){ for(int i=0;i<4;i++) d[o+i]=(byte)(v>>>(8*i)); }
  static void p64(byte[] d,int o,long v){ for(int i=0;i<8;i++) d[o+i]=(byte)(v>>>(8*i)); }
  static void magic(byte[] d,int o,String s)throws Exception{ byte[] b=s.getBytes("US-ASCII");System.arraycopy(b,0,d,o,b.length); }
  public static void main(String[] a)throws Exception{
    long tid=Long.parseUnsignedLong("05A1B2C3D4E5F607",16);
    byte[] n=NacpBuilder.create("Test","NANITA","1.0.0",tid);
    if(n.length!=0x4000||le64(n,0x3038)!=tid||le64(n,0x3078)!=tid)throw new AssertionError("NACP ids");
    if(le64(n,0x3080)!=8388608L||le64(n,0x3088)!=4194304L)throw new AssertionError("NACP save sizes");
    byte[] p=new byte[0x700]; magic(p,0,"META"); int aci=0x100,acid=0x300;
    p32(p,0x70,aci);p32(p,0x74,0x80);p32(p,0x78,acid);p32(p,0x7c,0x300);magic(p,aci,"ACI0");magic(p,acid+0x200,"ACID");
    long min=Long.parseUnsignedLong("0500000000000000",16),max=Long.parseUnsignedLong("05FFFFFFFFFFFFFF",16);
    p64(p,acid+0x210,min);p64(p,acid+0x218,max);p64(p,aci+0x10,0x0500000000000001L);
    Path i=Paths.get(a[0],"in.npdm"),o=Paths.get(a[0],"out.npdm");Files.write(i,p);NpdmPatcher.patchProgramId(i.toFile(),o.toFile(),tid);
    byte[] q=Files.readAllBytes(o);if(le64(q,aci+0x10)!=tid)throw new AssertionError("ProgramId");
    if(le64(q,acid+0x210)!=min||le64(q,acid+0x218)!=max)throw new AssertionError("ACID changed");
    System.out.println("PASS: NACP SaveData + NPDM ProgramId");
  }
}
JAVA
javac -d "$TMP/classes" \
  "$ROOT/app/src/main/java/com/nanita/gba2nsp/NacpBuilder.java" \
  "$ROOT/app/src/main/java/com/nanita/gba2nsp/NpdmPatcher.java" \
  "$TMP/TestCore.java"
java -cp "$TMP/classes" TestCore "$TMP"

# Runtime V3 regression guards (no devkitA64 required).
grep -q '"svcUnmapTransferMemory": "0x52"' "$ROOT/runtime-builder/runtime-npdm.json"
for svc in 'appletOE' 'audout:u' 'nvdrv' 'psm' 'time:u' 'vi:u'; do
  grep -q "\"$svc\"" "$ROOT/runtime-builder/runtime-npdm.json"
done
grep -q 'romfs/font-new.png' "$ROOT/runtime-builder/build_runtime.sh"
grep -q 'font-new.png' "$ROOT/app/src/main/java/com/nanita/gba2nsp/RuntimeManager.java"
grep -q 'socketInitializeDefault' "$ROOT/runtime-builder/patch_mgba_switch.py"
grep -q 'network_line' "$ROOT/runtime-builder/patch_mgba_switch.py"
echo "PASS: Runtime V3 SVC/services/font guards"
