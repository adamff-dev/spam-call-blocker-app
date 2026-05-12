package com.addev.listaspam.privateContact

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PrivateContactDao {

    @Query("SELECT * FROM private_contacts")
    fun getAllFlow(): Flow<List<PrivateContact>>

    @Query("SELECT * FROM private_contacts")
    suspend fun getAll(): List<PrivateContact>

    @Query("SELECT * FROM private_contacts WHERE type = 1")
    fun getBlockedListFlow(): Flow<List<PrivateContact>>

    @Query("SELECT * FROM private_contacts WHERE type = 0")
    fun getWhiteListFlow(): Flow<List<PrivateContact>>

    @Query("SELECT COALESCE((SELECT type FROM private_contacts WHERE number = :num), -1)")
    suspend fun getContactType(num: String): Int

    @Query("UPDATE private_contacts SET type = :newType WHERE number = :num")
    suspend fun updateStatusByNumber(num: String, newType: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM private_contacts WHERE number = :num)")
    suspend fun exists(num: String): Boolean

    @Query("DELETE FROM private_contacts WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: Set<Long>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: PrivateContact)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(contacts: List<PrivateContact>)

    @Update
    suspend fun update(contact: PrivateContact)

    @Delete
    suspend fun delete(contact: PrivateContact)

    @Query("DELETE FROM private_contacts WHERE number = :num")
    suspend fun deleteByNumber(num: String)
}
