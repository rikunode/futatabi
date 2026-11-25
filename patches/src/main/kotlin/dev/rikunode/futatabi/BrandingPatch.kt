package dev.rikunode.futatabi

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

@Suppress("unused")
val brandingPatch = resourcePatch(
    name = "futatabi branding",
    description = "Rename the app to futatabi. Optional; keeps the current launcher icon.",
    default = false,
) {
    compatibleWith(Compatibility(
        name = "futatter", packageName = "com.Nanasaki.ftutter", apkFileType = ApkFileType.APK,
        targets = listOf(AppTarget(version = "3.1.1", versionCode = 65)),
    ))
    execute {
        document("AndroidManifest.xml").use { doc ->
            val app = doc.documentElement.getElementsByTagName("application").item(0) as? Element
                ?: throw PatchException("futatabi: application manifest element missing")
            // Set both application and original launcher activity labels.
            // Leave the separate posting launcher label and all icon resources intact.
            app.setAttribute("android:label", "futatabi")
            val activities = app.getElementsByTagName("activity")
            val main = (0 until activities.length).map { activities.item(it) as Element }
                .singleOrNull { it.getAttribute("android:name") == "com.Nanasaki.ftutter.MainActivity" }
                ?: throw PatchException("futatabi: original launcher activity missing")
            main.setAttribute("android:label", "futatabi")
        }
    }
}
