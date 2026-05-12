package com.addev.listaspam.privateContact

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

enum class ContactType(val value: Int) {
    WHITELIST(0),
    BLOCK(1),
    // SPAM(2)
}

@Serializable
@Entity(
    tableName = "private_contacts",
    indices = [Index(value = ["number"], unique = true)]
)
data class PrivateContact(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val number: String,
    val name: String,
    val type: Int = ContactType.WHITELIST.value
)