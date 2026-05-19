package com.yamtrack.app.data.api

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.ToJson

/**
 * Adapter for `Any` slots — the API returns several opaque, chart-shaped
 * blobs (`details`, `related`, `activity_data`, `media_count`, …) typed as
 * `Map<String, Any?>` / `List<Map<String, Any?>>`. Moshi ships no `Any`
 * adapter (unlike Gson), so register this one.
 *
 * `JsonReader.readJsonValue()` yields the same Java shapes Gson produced:
 * Map<String, Object?>, List<Object?>, String, Double, Boolean, or null —
 * so the existing `as? Number` / `as? Map` / `as? List` consumers keep
 * working unchanged.
 */
class MoshiAnyAdapter {

    @FromJson
    fun fromJson(reader: JsonReader): Any? = reader.readJsonValue()

    @ToJson
    fun toJson(writer: JsonWriter, value: Any?) {
        when (value) {
            null -> writer.nullValue()
            is Map<*, *> -> {
                writer.beginObject()
                value.forEach { (k, v) ->
                    writer.name(k.toString())
                    toJson(writer, v)
                }
                writer.endObject()
            }
            is List<*> -> {
                writer.beginArray()
                value.forEach { toJson(writer, it) }
                writer.endArray()
            }
            is String -> writer.value(value)
            is Boolean -> writer.value(value)
            is Double -> writer.value(value)
            is Float -> writer.value(value.toDouble())
            is Long -> writer.value(value)
            is Int -> writer.value(value.toLong())
            is Number -> writer.value(value.toDouble())
            else -> writer.value(value.toString())
        }
    }
}
