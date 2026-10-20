package com.blackchapel.recipai.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * The on-disk recipe file. Versioned so a later format (photos, the Drive
 * backup snapshot) can migrate older files instead of failing to read them.
 */
@Serializable
private data class RecipeFile(val version: Int, val recipes: List<Recipe>)

object RecipeJson {
    const val VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun encode(recipes: List<Recipe>): String =
        json.encodeToString(RecipeFile.serializer(), RecipeFile(VERSION, recipes))

    /**
     * Reads a file written by [encode]. Throws [IllegalArgumentException] for
     * text that is not a recipe file, or one written by a newer app version.
     */
    fun decode(text: String): List<Recipe> {
        val file = try {
            json.decodeFromString(RecipeFile.serializer(), text)
        } catch (e: SerializationException) {
            throw IllegalArgumentException("Not a Recipai recipe file", e)
        }
        require(file.version <= VERSION) { "Recipe file version ${file.version} is newer than this app (${VERSION})" }
        return file.recipes
    }
}
