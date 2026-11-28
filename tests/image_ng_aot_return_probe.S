.text
.global wrapper
// Host-only wrapper. Assemble alongside image_ng_aot_probe.S compiled to
// probe.bin (clang --target=aarch64-linux-gnu, llvm-objcopy -O binary -j .text).
// The Python harness redirects the final branch to the actual stock epilogue.
wrapper:
 tbnz x0,#4,done
 ldr x0,[x29,#0x10]
 adr x1,table
 ldr w2,[x1],#4
 mov w3,#1
 mov w4,#0
 bl probe
 cbz w0,blocked
 add x0,x22,#0x20
 b done
blocked:
 add x0,x22,#0x30
done:
 mov x15,x29
 b continuation
.org 0x100
probe:
.incbin "probe.bin"
.org 0x1000
table:
.word 0
.org 0x2000
continuation:
