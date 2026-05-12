package com.addev.listaspam.privateContact

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.Flow

class PrivateContactRepository(private val dao: PrivateContactDao) {

    val allContacts: Flow<List<PrivateContact>> = dao.getAllFlow()

    suspend fun getNumberType(incomingNumber: String): Int {
        return dao.getContactType(normalize(incomingNumber))
    }

    suspend fun getAll(): List<PrivateContact> = dao.getAll()

    suspend fun insert(name: String, number: String, type: Int = ContactType.WHITELIST.value) {
        dao.insert(
            PrivateContact(
                name = name.trim(),
                number = normalize(number),
                type = type
            )
        )
    }

    suspend fun insert(contact: PrivateContact) {
        dao.insert(contact.copy(
            name = contact.name.trim(),
            number = normalize(contact.number),
            type = contact.type
        ))
    }

    suspend fun insertAll(contacts: List<PrivateContact>) {
        if (contacts.isNotEmpty()) {
            val normalizedContacts = contacts.map { item ->
                item.copy(number  = normalize(item.number))
            }

            dao.insertAll(normalizedContacts)
        }
    }

    suspend fun update(contact: PrivateContact, newName: String, newNumber: String, type: Int = ContactType.WHITELIST.value) {
        dao.update(
            contact.copy(
                name = newName.trim(),
                number = normalize(newNumber),
                type = type
            )
        )
    }

    suspend fun deleteByIds(ids: Set<Long>) {
        dao.deleteByIds(ids)
    }

    suspend fun delete(contact: PrivateContact) {
        dao.delete(contact)
    }

    suspend fun deleteByNumber(number: String) {
        dao.deleteByNumber(normalize(number))
    }

    suspend fun migrateFromSharedPreferences(sharedPreferences: SharedPreferences){

        if (sharedPreferences.getBoolean("MIGRATION_DONE", false)) return

        // WHITELIST(0),
        migrate(sharedPreferences, "WHITELIST_NUMBERS", 0)
        // BLOCK(1),
        migrate(sharedPreferences, "BLOCK_NUMBERS", 1)

        sharedPreferences.edit {
            putBoolean("MIGRATION_DONE", true)
            apply()
        }
    }

    suspend fun migrate(
        sharedPreferences: SharedPreferences,
        key: String,
        type: Int
    ) {

        val oldList = sharedPreferences.getStringSet(key, emptySet()) ?: emptySet()
        if (oldList.isNotEmpty()) {
            oldList.forEach { number ->
                if (getNumberType(number) == -1) {
                    insert(
                        name = number,
                        number = number,
                        type = type
                    )
                }
            }

            sharedPreferences.edit { remove(key) }
        }
    }

    fun normalize(number: String): String {
        return number.normalizePhone()
    }
}