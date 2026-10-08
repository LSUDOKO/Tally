package app.tally.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File

/** Reads the repository's shared fixtures (../../fixtures relative to app/core). */
object Fixtures {
    private fun file(name: String): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val f = File(dir, "fixtures/$name")
            if (f.exists()) return f
            dir = dir.parentFile
        }
        error("fixtures/$name not found above ${File("").absolutePath}")
    }

    fun json(name: String): JsonObject = Json.parseToJsonElement(file(name).readText()).jsonObject
}
