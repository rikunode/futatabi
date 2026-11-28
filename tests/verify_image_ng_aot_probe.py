"""Host-only experiment, not installed in an APK.
Run with Unicorn 2.1 and stock/v0.4.4 APK as the argument.
The probe has no runtime calls; the decision rows are synthetic RAM inputs.
"""
import hashlib
import struct
import sys
import zipfile
from unicorn import Uc, UC_ARCH_ARM64, UC_MODE_ARM
from unicorn.arm64_const import *

# ARM64 leaf compiled from tests/image_ng_aot_probe.S.
PROBE = bytes.fromhex(
    "630c0034e10c00b45f002071a80c0054820c00349f040071480c0054200c003605f05fb8a57c0c53bf401271a10b005405b040b8650b0036a5801c8ba6f05fb8c67c0c53dfd41271c10a0054a5b040b8850a0036a5801c8ba6f05fb8c67c0c53c6780151df040071c8090054a77040b887090037e77c0153a53c00916809001009154038e90800340a154038ff00096ba10100540b0080520c496b3866000035ad486b3802000014ad586b789f010d6ba10000546b0500117f01096be3feff54030000140801098beeffff1705b041b885060036a5801c8ba6f05fb8c67c0c53c6780151df040071c8050054a77040b887050037e77c0153ff28007123050054ff500071e8040054a53c00910b00805266000035ad486b3802000014ad586b78adc10051bf250071c80300546b0500117f01076be3feff54290040393f010a6ba1020054290440393f01046b41020054290840393f01076be1010054280c00910b0080520c496b3866000035ad486b3802000014ad586b789f010d6bc10000546b0500117f01076be3feff5400008052c0035fd62160009142040071e1fcff5420008052c0035fd6180168747470733a2f2f696d672e326368616e2e6e65742f622f1701687474703a2f2f696d672e326368616e2e6e65742f622f180268747470733a2f2f6d61792e326368616e2e6e65742f622f1702687474703a2f2f6d61792e326368616e2e6e65742f622f1a0368747470733a2f2f6a756e2e326368616e2e6e65742f6a756e2f1903687474703a2f2f6a756e2e326368616e2e6e65742f6a756e2f1e04687474703a2f2f3132372e302e302e313a31383736352f61696d6f67652f2005687474703a2f2f3132372e302e302e313a31383736352f616e696d616e63682f1e06687474703a2f2f3132372e302e302e313a31383736352f6d6562756b692f00")
BASES = ["https://img.2chan.net/b/", "https://may.2chan.net/b/",
         "https://jun.2chan.net/jun/", "http://127.0.0.1:18765/aimoge/",
         "http://127.0.0.1:18765/animanch/", "http://127.0.0.1:18765/mebuki/"]
HEAP = 0x7000000000
CODE = 0x2000000
TABLE = 0x3000000
STACK = 0x4000000
STOP = CODE + 0x1000

def fixture(lib, base, number, wide):
    u = Uc(UC_ARCH_ARM64, UC_MODE_ARM)
    u.mem_map(0x873000, 0x2000)
    u.mem_write(0x873000, lib[0x873000:0x875000])
    u.mem_map(CODE, 0x2000)
    u.mem_write(CODE, PROBE)
    u.mem_map(TABLE, 0x10000)
    u.mem_map(STACK, 0x2000)
    u.mem_map(HEAP, 0x10000)
    def obj(off, cid):
        u.mem_write(HEAP+off, struct.pack("<I",cid<<12))
        return HEAP+off+1
    def field(o, off, v):
        u.mem_write(o+off,struct.pack("<I",v&0xffffffff))
    def string(off, s):
        o=obj(off,95 if wide else 94)
        field(o,7,len(s)*2)
        u.mem_write(o+15,s.encode("utf-16le" if wide else "ascii"))
        return o
    data=obj(0x100,1168)
    ita=obj(0x200,1205)
    baseptr=string(0x300,base)
    idptr=string(0x400,number)
    urlptr=string(0x500,base+"res/1234567890.htm")
    widget=obj(0x600,3834)
    context=obj(0x700,1)
    field(data,0xb,ita)
    field(data,0x1b,idptr)
    field(data,7,urlptr)
    field(ita,0xb,baseptr)
    field(widget,0xb,data)
    field(context,0xf,widget)
    u.reg_write(UC_ARM64_REG_X28,HEAP>>32)
    u.reg_write(UC_ARM64_REG_X29,STACK+0x1000)
    # Actual stock image path: starts with widget in r0.
    u.reg_write(UC_ARM64_REG_X0,widget)
    u.emu_start(0x873880,0x87389c,count=16)
    assert u.reg_read(UC_ARM64_REG_X2)==baseptr
    u.reg_write(UC_ARM64_REG_X0,data)
    u.emu_start(0x8738b8,0x8738c0,count=4)
    assert u.reg_read(UC_ARM64_REG_X2)==idptr
    # Actual stock long-press menu action model access.
    u.reg_write(UC_ARM64_REG_X0,context)
    u.emu_start(0x874114,0x87412c,count=8)
    assert u.reg_read(UC_ARM64_REG_X3)==data
    assert u.reg_read(UC_ARM64_REG_X2)==urlptr
    return u,data

def row(board, variant, number):
    b=number.encode("ascii")
    return bytes((board,variant,len(b)))+b.ljust(21,b"\0")

def run(u,data,rows,visible=1,variant=0,count=None):
    u.mem_write(TABLE, b"".join(rows) or b"\0")
    old=bytes(u.mem_read(HEAP,0x10000))
    for reg,value in ((UC_ARM64_REG_X0,data),(UC_ARM64_REG_X1,TABLE),
                      (UC_ARM64_REG_X2,len(rows) if count is None else count),
                      (UC_ARM64_REG_X3,visible),(UC_ARM64_REG_X4,variant),
                      (UC_ARM64_REG_X30,STOP)):
        u.reg_write(reg,value)
    u.emu_start(CODE,STOP,count=1000000)
    assert u.reg_read(UC_ARM64_REG_PC)==STOP
    assert bytes(u.mem_read(HEAP,0x10000))==old
    return u.reg_read(UC_ARM64_REG_X0)

def return_hook(lib, u, data, rows, stock_visible):
    # In-memory branch at the existing predicate's epilogue. The wrapper
    # uses a synthetic RAM table (CAT variant), not a deployed IPC mechanism.
    # No new Dart calls or allocation. Preserve canonical stock false.
    cave=0xaa7000
    site=0x73786c
    def branch(src,dst):
        delta=dst-src
        assert delta%4==0 and -(1<<27)<=delta<(1<<27)
        return struct.pack("<I",0x14000000|((delta//4)&0x3ffffff))
    u.mem_map(0x737000,0x1000)
    u.mem_write(0x737000,lib[0x737000:0x738000])
    assert lib[site:site+12].hex()=="ef031daafd79c1a8c0035fd6"
    u.mem_map(cave,0x2000)
    # tbnz false; load original argument; load test table; call probe;
    # form canonical bool; execute displaced mov x15,x29 and resume.
    wrapper=bytes.fromhex("60012037a00b40f9c17f0010224440b823008052040080523a00009460000034c082009102000014c0c20091ef031daa")
    u.mem_write(cave,wrapper+branch(cave+len(wrapper),site+4))
    u.mem_write(cave+0x100,PROBE)
    u.mem_write(cave+0x1000,struct.pack("<I",len(rows))+b"".join(rows))
    u.mem_write(site,branch(site,cave))
    fp=STACK+0x1000
    null=HEAP+0x9001
    u.mem_write(fp,struct.pack("<QQQ",STACK+0x800,STOP,data))
    u.reg_write(UC_ARM64_REG_X29,fp)
    u.reg_write(UC_ARM64_REG_X15,fp-8)
    u.reg_write(UC_ARM64_REG_X22,null)
    u.reg_write(UC_ARM64_REG_X0,null+(0x20 if stock_visible else 0x30))
    before=bytes(u.mem_read(HEAP,0x10000))
    u.emu_start(site,STOP,count=1000000)
    assert u.reg_read(UC_ARM64_REG_PC)==STOP
    assert u.reg_read(UC_ARM64_REG_X29)==STACK+0x800
    assert u.reg_read(UC_ARM64_REG_X15)==fp+16
    assert bytes(u.mem_read(HEAP,0x10000))==before
    result=u.reg_read(UC_ARM64_REG_X0)
    assert result in (null+0x20,null+0x30)
    return result==null+0x20

def main():
    with zipfile.ZipFile(sys.argv[1]) as z:
        lib=z.read("lib/arm64-v8a/libapp.so")
    print("libapp sha256",hashlib.sha256(lib).hexdigest())
    cases=0
    for board,base in enumerate(BASES,1):
        n="1234567890123"
        for wide in (False,True):
            for alias in ([base,base.replace("https:","http:")] if board<=3 else [base]):
                u,data=fixture(lib,alias,n,wide)
                for variant in (0,1):
                    good=row(board,variant,n)
                    wrong=row(board%6+1,variant,n)
                    assert run(u,data,[wrong,good],variant=variant)==0
                    assert run(u,data,[wrong],variant=variant)==1
                    assert run(u,data,[row(board,1-variant,n)],variant=variant)==1
                    assert run(u,data,[row(board,variant,n[:-1]+"4")],variant=variant)==1
                    assert run(u,data,[],variant=variant)==1
                    assert run(u,data,[],visible=0,variant=variant)==0
                    assert run(u,data,[good],variant=variant,count=2049)==1
                    cases+=7
        print("PASS board",board,base)
    for base,n in [(BASES[0],"123"),(BASES[0],"123456789012X"),
                   (BASES[0]+"extra/","1234567890123"),
                   ("https://evil.example/","1234567890123")]:
        u,data=fixture(lib,base,n,False)
        assert run(u,data,[row(1,0,n)])==1
        cases+=1
    print("PASS",cases,"read-only AOT probe decisions; simulated heap and decision table")
    if len(sys.argv)>2:
        from pathlib import Path
        raw=Path(sys.argv[2]).read_bytes()
        assert len(raw)==288
        rows=[raw[i:i+24] for i in range(0,len(raw),24)]
        checks=0
        for board,base in enumerate(BASES,1):
            for wide in (False,True):
                for visible,expected_rows in ((True,rows),(True,[]),(False,[])):
                    u,data=fixture(lib,base,"1234567890123",wide)
                    expected=visible and not expected_rows
                    assert return_hook(lib,u,data,expected_rows,visible)==expected
                    checks+=1
        print("PASS",checks,"actual predicate epilogue branch/return cases with Java-generated table")
    print("NOT VERIFIED: real Flutter callback installation, JNI/IPC, list rebuild, image retrieval, UI.")
if __name__=="__main__":
    main()
