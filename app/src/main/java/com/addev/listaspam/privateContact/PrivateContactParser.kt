package com.addev.listaspam.privateContact

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

object PrivateContactParser {
    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun parseContacts(jsonString: String): List<PrivateContact> {
        return try {
            jsonConfig.decodeFromString<List<PrivateContact>>(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun toJson(contacts: List<PrivateContact>): String {
        return try {
            jsonConfig.encodeToString(contacts)
        } catch (e: Exception) {
            ""
        }
    }
}