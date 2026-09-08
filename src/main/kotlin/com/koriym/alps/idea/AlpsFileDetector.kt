package com.koriym.alps.idea

import com.intellij.openapi.vfs.VirtualFile
import java.io.IOException

/**
 * Detects whether a file is an ALPS profile, by extension plus a content
 * sniff (root `alps` object/element), so plain JSON/XML files are unaffected.
 */
object AlpsFileDetector {
    private const val SNIFF_BYTES = 4096
    private val JSON_MARKER = Regex("\"alps\"\\s*:\\s*\\{")
    private val XML_MARKER = Regex("<alps[\\s>]")

    fun isAlpsFile(file: VirtualFile): Boolean {
        val extension = file.extension?.lowercase() ?: return false
        if (extension != "json" && extension != "xml") return false

        val text = try {
            file.inputStream.use { String(it.readNBytes(SNIFF_BYTES), Charsets.UTF_8) }
        } catch (e: IOException) {
            return false
        }

        return when (extension) {
            "json" -> JSON_MARKER.containsMatchIn(text)
            "xml" -> XML_MARKER.containsMatchIn(text)
            else -> false
        }
    }
}
