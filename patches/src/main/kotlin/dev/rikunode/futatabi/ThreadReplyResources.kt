package dev.rikunode.futatabi

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.rawResourcePatch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.security.MessageDigest

internal const val REPLY_ONLY_LIBAPP_SHA256 = "9ecf4be1556011b59a21c81ceaa2cbe3246dd03fcfd4c4a82cc160cfc5e1b98a"
internal const val REPLY_BANNER_LIBAPP_SHA256 = "5841c8827d767cd2d312fa98085f760b57ba10e6b112a035b9fd8b0217064503"
internal const val REPLY_LOGIN_LIBAPP_SHA256 = "ce86925be4d65918fa6426d5cfa1a24b0c57135503ce9fee5321e9e17b678030"
internal const val REPLY_ALL_LIBAPP_SHA256 = "71a41402da095ad801064b1b0a88a5eec41b74c66f5619c90fd8c689f42c25af"

private val replyStub = "c106003625f05fb8a57c0c53a5780151bf04007128060054267040b8df980171c0000054dfa8017181050054070900508804805203000014a707001048048052263c0091a5040011c900403945000836c9004079ea1440383f010a6be1030054c600058b0805007101ffff54a8018052c900403945000836c900407929c100513f250071a8020054c600058b0805007101ffff54e706005088008052c900403945000836c9004079ea1440383f010a6b41010054c600058b0805007101ffff5467634091e73845f9e11d00a9beccfff0de432e916398f21720f05fb85d98f2178001003605f05fb8a57c0c53a5780151bf040071e8000054e10300aac2c20091645340f9f6d900a9e00100f97798f217e10300aa5598f217655340f99f0005eb41000054a254f21722c5e617687474703a2f2f3132372e302e302e313a31383736352f61696d6f67652f7265732f687474703a2f2f3132372e302e302e313a31383736352f616e696d616e63682f7265732f2e68746d0000".replyBytes()
private fun String.replyBytes() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
private fun ByteArray.replyHash() = MessageDigest.getInstance("SHA-256").digest(this)
    .joinToString("") { "%02x".format(it) }

// Original pencil dialog closure: 0x74c148. The trampoline contains no new
// safepoints; use the original split and showDialog call sites for the two
// Dart calls. The ELF RX segment is extended only into verified zero padding.
internal val threadReplyResources = rawResourcePatch {
    execute {
        val lib = get("lib/arm64-v8a/libapp.so")
        if (!lib.isFile) throw PatchException("futatabi: missing ARM64 reply resources")
        val bytes = lib.readBytes()
        val expected = when (val before = bytes.replyHash()) {
            "dd542fe3af5f266096e07f823da3880d3a6e7c771aa80afe6324137e63c5041a" -> REPLY_ONLY_LIBAPP_SHA256
            "f46c85060c997990099d8a6848d119680346bccd84371d7e34c45ac66e18a5b0" -> REPLY_BANNER_LIBAPP_SHA256
            LOGIN_ONLY_LIBAPP_SHA256 -> REPLY_LOGIN_LIBAPP_SHA256
            "a076ce46ff02e598d1e8c183ef8306ce447ec1f5117e2198bcad379bd07ec5d7" -> REPLY_ALL_LIBAPP_SHA256
            else -> throw PatchException("futatabi: unsupported ARM64 reply library (SHA-256 $before)")
        }
        if (bytes.sliceArray(0xaa6160 until 0xaa6160 + replyStub.size).any { it != 0.toByte() })
            throw PatchException("futatabi: nonzero reply trampoline padding")
        "6d670d14".replyBytes().copyInto(bytes, 0x74c3ac)
        "9f670d14".replyBytes().copyInto(bytes, 0x74c3c4)
        "8c670d94".replyBytes().copyInto(bytes, 0x74c448)
        replyStub.copyInto(bytes, 0xaa6160)
        val elf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (offset in listOf(0xd0, 0xd8)) {
            if (elf.getLong(offset) != 0x686160L)
                throw PatchException("futatabi: unexpected executable segment size")
            elf.putLong(offset, 0x686160L + replyStub.size)
        }
        if (bytes.replyHash() != expected) throw PatchException("futatabi: reply output hash mismatch")
        val stamp = Files.getLastModifiedTime(lib.toPath())
        lib.writeBytes(bytes)
        if (Files.getLastModifiedTime(lib.toPath()) == stamp)
            Files.setLastModifiedTime(lib.toPath(), FileTime.fromMillis(stamp.toMillis() + 2000))
    }
}
