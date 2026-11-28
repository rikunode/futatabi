.text
.global probe
// Host-only proof ABI: x0=ThreadData, x1=rows, w2=count, w3=stock-visible,
// w4=variant; x28=heap high. Returns 0/1, no GC calls or heap writes.
// Build: clang --target=aarch64-linux-gnu -c image_ng_aot_probe.S -o probe.o
// llvm-objcopy -O binary -j .text probe.o probe.bin
probe:
 cbz w3, hidden
 cbz x1, visible
 cmp w2,#2048
 b.hi visible
 cbz w2, visible
 cmp w4,#1
 b.hi visible
 tbz x0,#0,visible
 ldur w5,[x0,#-1]
 ubfx w5,w5,#12,#20
 cmp w5,#1168
 b.ne visible
 ldur w5,[x0,#0xb]
 tbz w5,#0,visible
 add x5,x5,x28,lsl #32
 ldur w6,[x5,#-1]
 ubfx w6,w6,#12,#20
 cmp w6,#1205
 b.ne visible
 ldur w5,[x5,#0xb]
 tbz w5,#0,visible
 add x5,x5,x28,lsl #32
 ldur w6,[x5,#-1]
 ubfx w6,w6,#12,#20
 sub w6,w6,#94
 cmp w6,#1
 b.hi visible
 ldur w7,[x5,#7]
 tbnz w7,#0,visible
 lsr w7,w7,#1
 add x5,x5,#15
 adr x8,bases
base_next:
 ldrb w9,[x8],#1
 cbz w9,visible
 ldrb w10,[x8],#1
 cmp w7,w9
 b.ne base_skip
 mov w11,#0
base_cmp:
 ldrb w12,[x8,w11,uxtw]
 cbnz w6,base_wide
 ldrb w13,[x5,w11,uxtw]
 b base_char
base_wide:
 ldrh w13,[x5,w11,uxtw #1]
base_char:
 cmp w12,w13
 b.ne base_skip
 add w11,w11,#1
 cmp w11,w9
 b.lo base_cmp
 b base_found
base_skip:
 add x8,x8,x9
 b base_next
base_found:
 ldur w5,[x0,#0x1b]
 tbz w5,#0,visible
 add x5,x5,x28,lsl #32
 ldur w6,[x5,#-1]
 ubfx w6,w6,#12,#20
 sub w6,w6,#94
 cmp w6,#1
 b.hi visible
 ldur w7,[x5,#7]
 tbnz w7,#0,visible
 lsr w7,w7,#1
 cmp w7,#10
 b.lo visible
 cmp w7,#20
 b.hi visible
 add x5,x5,#15
 mov w11,#0
validate_digit:
 cbnz w6,digit_wide
 ldrb w13,[x5,w11,uxtw]
 b digit_check
digit_wide:
 ldrh w13,[x5,w11,uxtw #1]
digit_check:
 sub w13,w13,#48
 cmp w13,#9
 b.hi visible
 add w11,w11,#1
 cmp w11,w7
 b.lo validate_digit
row_next:
 ldrb w9,[x1]
 cmp w9,w10
 b.ne row_skip
 ldrb w9,[x1,#1]
 cmp w9,w4
 b.ne row_skip
 ldrb w9,[x1,#2]
 cmp w9,w7
 b.ne row_skip
 add x8,x1,#3
 mov w11,#0
row_cmp:
 ldrb w12,[x8,w11,uxtw]
 cbnz w6,row_wide
 ldrb w13,[x5,w11,uxtw]
 b row_char
row_wide:
 ldrh w13,[x5,w11,uxtw #1]
row_char:
 cmp w12,w13
 b.ne row_skip
 add w11,w11,#1
 cmp w11,w7
 b.lo row_cmp
hidden:
 mov w0,#0
 ret
row_skip:
 add x1,x1,#24
 subs w2,w2,#1
 b.ne row_next
visible:
 mov w0,#1
 ret
bases:
 .byte 24,1
 .ascii "https://img.2chan.net/b/"
 .byte 23,1
 .ascii "http://img.2chan.net/b/"
 .byte 24,2
 .ascii "https://may.2chan.net/b/"
 .byte 23,2
 .ascii "http://may.2chan.net/b/"
 .byte 26,3
 .ascii "https://jun.2chan.net/jun/"
 .byte 25,3
 .ascii "http://jun.2chan.net/jun/"
 .byte 30,4
 .ascii "http://127.0.0.1:18765/aimoge/"
 .byte 32,5
 .ascii "http://127.0.0.1:18765/animanch/"
 .byte 30,6
 .ascii "http://127.0.0.1:18765/mebuki/"
 .byte 0
