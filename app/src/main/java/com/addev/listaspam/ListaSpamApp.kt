package com.addev.listaspam

import android.app.Application
import com.addev.listaspam.privateContact.AppDatabase
import com.addev.listaspam.privateContact.PrivateContact
import com.addev.listaspam.privateContact.PrivateContactRepository
import com.addev.listaspam.util.SpamUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ListaSpamApp : Application() {
    val database by lazy { AppDatabase.getInstance(this) }
    val repository by lazy { PrivateContactRepository(database.privateContactDao()) }

    val spamUtils by lazy { SpamUtils() }

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val contactsCache: StateFlow<Map<String, PrivateContact>> by lazy {
        repository.allContacts
            .map { list ->
                val limitedList = if (list.size > 500) {
                    list.sortedByDescending { it.number.length }.take(500)
                } else {
                    list
                }
                limitedList.associateBy { it.number }
            }
            .stateIn(appScope, SharingStarted.Eagerly, emptyMap())
    }

    companion object {
        private lateinit var instance: ListaSpamApp
        fun get(): ListaSpamApp = instance
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        contactsCache
    }

    override fun onTerminate() {
        super.onTerminate()
        appScope.cancel()
    }
}