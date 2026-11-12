package com.orangeshield.app.engine

import org.json.JSONArray
import org.json.JSONObject

/**
 * ORANGE SHIELD — serialização JSON das evidências (org.json, sem dependências extras).
 * Formato idêntico ao gravado pelo console web: [{"label": ..., "points": ...}].
 */

internal fun evidencesToJson(evidences: List<EngineEvidence>): String {
    val array = JSONArray()
    for (evidence in evidences) {
        array.put(
            JSONObject()
                .put("label", evidence.label)
                .put("points", evidence.points)
        )
    }
    return array.toString()
}

internal fun spoofFlagsToJson(flags: List<SpoofFlag>): String {
    val array = JSONArray()
    for (flag in flags) {
        array.put(
            JSONObject()
                .put("code", flag.code)
                .put("label", flag.label)
                .put("points", flag.points)
        )
    }
    return array.toString()
}

internal fun evidencesFromJson(json: String): List<EngineEvidence> {
    val result = mutableListOf<EngineEvidence>()
    try {
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            result += EngineEvidence(
                label = obj.optString("label"),
                points = obj.optInt("points")
            )
        }
    } catch (_: Exception) {
        // Histórico corrompido é simplesmente ignorado.
    }
    return result
}
