package dev.rikunode.futatabi

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

/**
 * Futatter 3.1.1 / versionCode 65 only.
 *
 * This does not interfere with rewarded, interstitial, native, or app-open ads.
 * It exits before creating either banner variant, emits the plugin's normal
 * onAdFailedToLoad callback, then completes the load method call.
 */
private object BannerMethodFingerprint : Fingerprint(
    definingClass = "Lb6/I;",
    name = "onMethodCall",
    parameters = listOf("LR5/j;", "LR5/k\$d;"),
    returnType = "V",
    strings = listOf(
        "loadBannerAd",
        "loadAdManagerBannerAd",
        "loadRewardedAd",
        "loadInterstitialAd",
        "loadAppOpenAd",
        "disposeAd",
    ),
)

@Suppress("unused")
val blockBannerAdsPatch = bytecodePatch(
    name = "Block banner ads",
    description = "Disable both Google Mobile Ads banner variants without changing rewarded ads.",
    default = true,
) {
    compatibleWith(
        Compatibility(
            name = "futatter",
            packageName = "com.Nanasaki.ftutter",
            apkFileType = ApkFileType.APK,
            targets = listOf(AppTarget(version = "3.1.1", versionCode = 65)),
        )
    )

    execute {
        val method = BannerMethodFingerprint.method
        val implementation = requireNotNull(method.implementation) {
            "futatabi: onMethodCall has no bytecode"
        }
        // All v0..v6 must be local registers. Never clobber parameter registers.
        val localCount = implementation.registerCount - method.parameterTypes.size - 1
        if (localCount < 10) {
            throw PatchException("futatabi: unexpected onMethodCall register layout")
        }

        val strings = implementation.instructions.mapNotNull {
            if (it.opcode != Opcode.CONST_STRING && it.opcode != Opcode.CONST_STRING_JUMBO) null
            else (it as? ReferenceInstruction)?.reference?.toString()?.trim('"')
        }
        val guardedNames = listOf("loadBannerAd", "loadAdManagerBannerAd")
        if (guardedNames.any { name -> strings.count { it == name } != 1 } ||
            strings.none { it == "loadRewardedAd" } ||
            strings.none { it == "loadInterstitialAd" }
        ) {
            throw PatchException("futatabi: unsupported banner-dispatch fingerprint")
        }

        // DEX class and callback shapes verified against the byte-for-byte
        // XAPK/base APK fingerprints documented in README.
        val manager = classDefBy("Lb6/a;")
        val error = classDefBy("Lb6/f\$c;")
        if (manager.methods.none {
                it.name == "k" &&
                    it.parameterTypes.map(CharSequence::toString) == listOf("I", "Lb6/f\$c;") &&
                    it.returnType == "V"
            } ||
            error.methods.none {
                it.name == "<init>" &&
                    it.parameterTypes.map(CharSequence::toString) ==
                    listOf("I", "Ljava/lang/String;", "Ljava/lang/String;", "Lb6/f\$e;")
            }
        ) {
            throw PatchException("futatabi: expected onAdFailedToLoad callback signature changed")
        }
        // Patch only this method. The original dispatcher is untouched for all
        // other MethodChannel calls, especially loadRewardedAd.
        method.addInstructions(
            0,
            """
                move-object/from16 v7, p0
                move-object/from16 v8, p1
                move-object/from16 v9, p2
                iget-object v0, v8, LR5/j;->a:Ljava/lang/String;
                const-string v1, "loadBannerAd"
                invoke-virtual { v1, v0 }, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                move-result v1
                if-nez v1, :futatabi_block_banner
                const-string v1, "loadAdManagerBannerAd"
                invoke-virtual { v1, v0 }, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                move-result v1
                if-eqz v1, :futatabi_original
                :futatabi_block_banner
                iget-object v0, v7, Lb6/I;->b:Lb6/a;
                if-eqz v0, :futatabi_original
                const-string v1, "adId"
                invoke-virtual { v8, v1 }, LR5/j;->a(Ljava/lang/String;)Ljava/lang/Object;
                move-result-object v1
                check-cast v1, Ljava/lang/Integer;
                invoke-virtual { v1 }, Ljava/lang/Integer;->intValue()I
                move-result v1
                new-instance v2, Lb6/f${'$'}c;
                const/4 v3, 0x0
                const/4 v4, 0x0
                const-string v5, "FutatabiBannerBlocked"
                const-string v6, "Banner load disabled by futatabi"
                invoke-direct { v2, v3, v5, v6, v4 }, Lb6/f${'$'}c;-><init>(ILjava/lang/String;Ljava/lang/String;Lb6/f${'$'}e;)V
                invoke-virtual { v0, v1, v2 }, Lb6/a;->k(ILb6/f${'$'}c;)V
                invoke-interface { v9, v4 }, LR5/k${'$'}d;->a(Ljava/lang/Object;)V
                return-void
                :futatabi_original
                nop
            """.trimIndent(),
        )
    }
}
