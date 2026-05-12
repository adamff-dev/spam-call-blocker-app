package com.addev.listaspam.privateContact;

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ContactFileHandler(
    private val contentResolver: ContentResolver,
    private val repository: PrivateContactRepository,
    private val scope: CoroutineScope,
    private val onError: (String) -> Unit
) {
    fun handleImport(uri: Uri) {
        scope.launch(Dispatchers.IO) {
            try {
                val jsonString = contentResolver.openInputStream(uri)?.use {
                    it.bufferedReader().readText()
                } ?: ""

                val contacts = PrivateContactParser.parseContacts(jsonString)
                if (contacts.isNotEmpty()) {
                    repository.insertAll(contacts)
                }
            } catch (e: Exception) {
                onError(e.message ?: "Import failed")
            }
        }
    }

    fun handleExport(uri: Uri) {
        scope.launch(Dispatchers.IO) {
            try {
                val contacts = repository.getAll()
                val jsonString = PrivateContactParser.toJson(contacts)

                contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.bufferedWriter().use { it.write(jsonString) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

// Normalizes a phone number by removing all non-digit characters.
fun String.normalizePhone(): String = replace("\\D".toRegex(), "")