package com.addev.listaspam.adapter

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import com.addev.listaspam.ListaSpamApp
import com.addev.listaspam.R
import com.addev.listaspam.privateContact.AddContactDialog
import com.addev.listaspam.privateContact.normalizePhone
import com.addev.listaspam.util.CallLogEntry
import com.addev.listaspam.util.ReportDialogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class CallLogAdapter(
    private val context: AppCompatActivity,
    var callLogs: List<CallLogEntry>
) : RecyclerView.Adapter<CallLogAdapter.CallLogViewHolder>() {

    interface OnItemChangedListener {
        fun onItemChanged(number: String)
    }

    val adapterScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val GOOGLE_URL_TEMPLATE = "https://www.google.com/search?q=%s"
        const val LISTA_SPAM_URL_TEMPLATE = "https://www.listaspam.com/busca.php?Telefono=%s"
        const val UNKNOWN_PHONE_URL_TEMPLATE = "https://www.unknownphone.com/phone/%s"
        const val WHATSAPP_URL_TEMPLATE = "https://wa.me/%s"
        const val TELEGRAM_URL_TEMPLATE = "https://t.me/%s"
    }

    private val formatter: DateTimeFormatter = getSystemLocalizedFormatter()
    private fun getSystemLocalizedFormatter(): DateTimeFormatter {
        val locale = Locale.getDefault()
        return DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(locale)
    }

    private var onItemChangedListener: OnItemChangedListener? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CallLogViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_call_log, parent, false)
        return CallLogViewHolder(view)
    }

    override fun onBindViewHolder(holder: CallLogViewHolder, position: Int) {
        val callLog = callLogs[position]
        val number = callLog.number.normalizePhone()

        val contact = ListaSpamApp.get().contactsCache.value[number]
        val isBlocked = contact?.type == 1
        val isWhitelisted = contact?.type == 0

        holder.bind(
            callLog,
            isBlocked,
            isWhitelisted,
            contact?.name
        )
    }

    override fun getItemCount(): Int = callLogs.size

    inner class CallLogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val numberTextView: TextView = itemView.findViewById(R.id.numberTextView)
        private val dateTextView: TextView = itemView.findViewById(R.id.dateTextView)
        private val durationTextView: TextView = itemView.findViewById(R.id.durationTextView)
        private val actionTextView: TextView = itemView.findViewById(R.id.actionTextView)
        private val overflowMenuButton = itemView.findViewById<ImageButton>(R.id.overflowMenuButton)

        fun bind(callLog: CallLogEntry, isBlocked: Boolean, isWhitelisted: Boolean = false, privateName: String? = null) {
            val number = callLog.number ?: "Unknown number"
            numberTextView.tag = number

            val contactName = getContactName(context, number)

            val displayName = privateName ?: contactName ?: number.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.unknown_value)

            val textToShow = when {
                isBlocked -> context.getString(R.string.blocked_text_format, displayName)
                isWhitelisted -> context.getString(R.string.whitelisted_text_format, displayName)
                else -> contactName ?: number
            }
            numberTextView.text = textToShow
            dateTextView.text = formatter.format(
                Instant.ofEpochMilli(callLog.date.time).atZone(ZoneId.systemDefault())
            )
            durationTextView.text = context.getString(R.string.duration_label, callLog.duration)

            val action = when (callLog.type) {
                CallLog.Calls.INCOMING_TYPE -> context.getString(R.string.call_incoming)
                CallLog.Calls.MISSED_TYPE -> context.getString(R.string.call_missed)
                CallLog.Calls.REJECTED_TYPE -> context.getString(R.string.call_rejected)
                CallLog.Calls.BLOCKED_TYPE -> context.getString(R.string.call_blocked)
                else -> context.getString(R.string.call_unknown)
            }

            actionTextView.text = action

            if (callLog.type == CallLog.Calls.BLOCKED_TYPE) {
                actionTextView.setTextColor(
                    ContextCompat.getColor(
                        context,
                        android.R.color.holo_red_light
                    )
                )
            } else {
                actionTextView.setTextColor(
                    ContextCompat.getColor(
                        context,
                        android.R.color.darker_gray
                    )
                )
            }

            when {
                isBlocked -> numberTextView.setTextColor(
                    ContextCompat.getColor(
                        context,
                        android.R.color.holo_red_light
                    )
                )

                isWhitelisted -> numberTextView.setTextColor(
                    ContextCompat.getColor(
                        context,
                        android.R.color.holo_blue_dark
                    )
                )

                else -> {
                    numberTextView.setTextColor(ContextCompat.getColor(context, R.color.textColor))
                }
            }

            if (number.isBlank()) {
                overflowMenuButton.visibility = View.GONE
                return
            }

            overflowMenuButton.visibility = View.VISIBLE
            overflowMenuButton.setOnClickListener {
                val currentContact = ListaSpamApp.get().contactsCache.value[number.normalizePhone()]
                val currentlyBlocked = currentContact?.type == 1
                val currentlyWhitelisted = currentContact?.type == 0

                val popupMenu = PopupMenu(
                    itemView.context,
                    overflowMenuButton,
                    Gravity.NO_GRAVITY,
                    android.R.attr.popupMenuStyle,
                    R.style.PopupMenuStyle
                )
                popupMenu.inflate(R.menu.item_actions)

                setDynamicTitles(popupMenu, currentlyBlocked, currentlyWhitelisted)

                popupMenu.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.search_action -> {
                            searchAction(number)
                            true
                        }

                        R.id.open_report_alert -> {
                            openReportAlert(number)
                            true
                        }

                        R.id.open_in_lista_spam_action -> {
                            openInListaSpam(number)
                            true
                        }

                        R.id.open_in_unknown_phone_action -> {
                            openInUnknownPhone(number)
                            true
                        }

                        R.id.open_in_whatsapp_action -> {
                            openInWhatsApp(number)
                            true
                        }

                        R.id.open_in_telegram_action -> {
                            openInTelegram(number)
                            true
                        }

                        R.id.add_to_contacts_action -> {
                            addToContacts(number)
                            true
                        }

                        R.id.whitelist_action -> {
                            if (currentlyWhitelisted) {
                                removeNumber(number)
                            } else {
                                addNumber(number,textToShow,0,context.supportFragmentManager)
                            }
                            true
                        }

                        R.id.block_action -> {
                            if (currentlyBlocked) {
                                removeNumber(number)
                            } else {
                                addNumber(number,textToShow,1,context.supportFragmentManager)
                            }
                            true
                        }

                        else -> false
                    }
                }
                popupMenu.show()
            }

            // Copy number to clipboard
            itemView.setOnLongClickListener {
                clipboardAction(number)
                true
            }
        }

        private fun setDynamicTitles(
            popupMenu: PopupMenu,
            isBlocked: Boolean,
            isWhitelisted: Boolean
        ) {
            popupMenu.menu.findItem(R.id.block_action)
                .setTitle(if (isBlocked) R.string.unblock else R.string.block)
            popupMenu.menu.findItem(R.id.whitelist_action)
                .setTitle(if (isWhitelisted) R.string.remove_from_whitelist else R.string.add_to_whitelist)
        }
    }

    private fun getContactName(context: Context, phoneNumber: String): String? {
        if (phoneNumber.isBlank()) return null

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) return null

        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)

            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME))
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun clipboardAction(number: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("phone number", number)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(
            context,
            context.getString(R.string.number_copied_to_clipboard),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun openInListaSpam(number: String) {
        val url = String.format(LISTA_SPAM_URL_TEMPLATE, number)
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        context.startActivity(intent)
    }

    private fun openInUnknownPhone(number: String) {
        val url = String.format(UNKNOWN_PHONE_URL_TEMPLATE, number)
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        context.startActivity(intent)
    }

    private fun openInWhatsApp(number: String) {
        // Remove any non-digit characters except +
        val cleanNumber = number.replace(Regex("[^+\\d]"), "")
        val url = String.format(WHATSAPP_URL_TEMPLATE, cleanNumber)
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        context.startActivity(intent)
    }

    private fun openInTelegram(number: String) {
        // Remove any non-digit characters except +
        val cleanNumber = number.replace(Regex("[^+\\d]"), "")
        val url = String.format(TELEGRAM_URL_TEMPLATE, cleanNumber)
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        context.startActivity(intent)
    }

    private fun removeNumber(number: String) {
        adapterScope.launch {
            ListaSpamApp.get().repository.deleteByNumber(number)
            withContext(Dispatchers.Main) {
                onItemChangedListener?.onItemChanged(number)
            }
        }
    }

    fun addNumber(number: String, name: String, type: Int,fragmentManager: FragmentManager) {

        fragmentManager.clearFragmentResultListener("contact_added")

        fragmentManager.setFragmentResultListener("contact_added", context) { _, bundle ->
            val addedNumber = bundle.getString("number") ?: ""
            onItemChangedListener?.onItemChanged(addedNumber)
        }

        val dialog = AddContactDialog.newInstance(
            name = name,
            number = number,
            type = type
        )
        dialog.show(fragmentManager, "AddContactDialog")
    }

    private fun addToContacts(number: String) {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            type = ContactsContract.Contacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.PHONE, number)
        }
        context.startActivity(intent)
    }

    private fun searchAction(number: String) {
        val url = String.format(GOOGLE_URL_TEMPLATE, number)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }

    fun setOnItemChangedListener(listener: OnItemChangedListener) {
        this.onItemChangedListener = listener
    }

    private fun openReportAlert(number: String) {
        val reportDialogManager = ReportDialogManager(context)
        reportDialogManager.show(number)
    }

    fun destroy() = adapterScope.cancel()
}