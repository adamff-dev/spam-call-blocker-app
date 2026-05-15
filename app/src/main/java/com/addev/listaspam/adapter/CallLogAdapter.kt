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
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.addev.listaspam.ListaSpamApp
import com.addev.listaspam.R
import com.addev.listaspam.privateContact.AddContactDialog
import com.addev.listaspam.privateContact.ContactType
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

class CallLogDiffCallback : DiffUtil.ItemCallback<CallLogEntry>() {
    override fun areItemsTheSame(oldItem: CallLogEntry, newItem: CallLogEntry): Boolean {
        return oldItem.number == newItem.number && oldItem.date == newItem.date
    }

    override fun areContentsTheSame(oldItem: CallLogEntry, newItem: CallLogEntry): Boolean {
        return oldItem == newItem
    }

    override fun getChangePayload(oldItem: CallLogEntry, newItem: CallLogEntry): Any? {
        return when {
            oldItem.duration != newItem.duration -> "duration"
            oldItem.type != newItem.type -> "type"
            else -> null
        }
    }
}

class CallLogAdapter(
    private val context: AppCompatActivity,
    private val onItemClick: (CallLogEntry) -> Unit = {},
    private val fragmentManager: FragmentManager? = null
) : ListAdapter<CallLogEntry, CallLogAdapter.CallLogViewHolder>(CallLogDiffCallback()) {

    interface OnItemChangedListener {
        fun onItemChanged(number: String)
    }

    companion object {
        const val GOOGLE_URL_TEMPLATE = "https://www.google.com/search?q=%s"
        const val LISTA_SPAM_URL_TEMPLATE = "https://www.listaspam.com/busca.php?Telefono=%s"
        const val UNKNOWN_PHONE_URL_TEMPLATE = "https://www.unknownphone.com/phone/%s"
        const val WHATSAPP_URL_TEMPLATE = "https://wa.me/%s"
        const val TELEGRAM_URL_TEMPLATE = "https://t.me/%s"
    }

    private val adapterScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val formatter: DateTimeFormatter = getSystemLocalizedFormatter()
    private fun getSystemLocalizedFormatter(): DateTimeFormatter {
        val locale = Locale.getDefault()
        return DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(locale)
    }

    private var onItemChangedListener: OnItemChangedListener? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CallLogViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_call_log, parent, false)
        return CallLogViewHolder(view)
    }

    override fun onBindViewHolder(holder: CallLogViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(
        holder: CallLogViewHolder,
        position: Int,
        payloads: List<Any>
    ) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
        } else {
            holder.updatePayload(getItem(position), payloads)
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        destroy()
    }

    inner class CallLogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val numberTextView: TextView = itemView.findViewById(R.id.numberTextView)
        private val dateTextView: TextView = itemView.findViewById(R.id.dateTextView)
        private val durationTextView: TextView = itemView.findViewById(R.id.durationTextView)
        private val actionTextView: TextView = itemView.findViewById(R.id.actionTextView)
        private val overflowMenuButton = itemView.findViewById<ImageButton>(R.id.overflowMenuButton)

        init {

            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }

            itemView.setOnLongClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    clipboardAction(getItem(position).number)
                    true
                } else {
                    false
                }
            }

            overflowMenuButton.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    showPopupMenu(getItem(position))
                }
            }
        }

        fun bind(callLog: CallLogEntry) {

            val number = callLog.number.normalizePhone()
            val contact = ListaSpamApp.get().contactsCache.value[number]
            val isBlocked = contact?.type == 1
            val isWhitelisted = contact?.type == 0

            bindView(
                callLog = callLog,
                isBlocked = isBlocked,
                isWhitelisted = isWhitelisted,
                privateName = contact?.name
            )
        }

        fun updatePayload(callLog: CallLogEntry, payloads: List<Any>) {
            when {
                payloads.contains("duration") -> {
                    durationTextView.text = context.getString(R.string.duration_label, callLog.duration)
                }
                payloads.contains("type") -> {
                    updateTypeView(callLog.type)
                }
                else -> bind(callLog)
            }
        }

        private fun bindView(
            callLog: CallLogEntry,
            isBlocked: Boolean,
            isWhitelisted: Boolean,
            privateName: String?
        ) {
            val number = callLog.number.normalizePhone()
            numberTextView.tag = number ?: "Unknown number"

            adapterScope.launch {

                val contactName = if (number.isBlank()) "Unknown number" else (callLog.name?.takeIf { it.isNotBlank() } ?: getContactNameAsync(context, number))

                withContext(Dispatchers.Main) {
                    val displayName = privateName ?: contactName ?: callLog.number
                    updateNumberDisplay(displayName, isBlocked, isWhitelisted)
                }
            }

            dateTextView.text = formatter.format(
                Instant.ofEpochMilli(callLog.date.time).atZone(ZoneId.systemDefault())
            )
            durationTextView.text = context.getString(R.string.duration_label, callLog.duration)

            updateTypeView(callLog.type)

            overflowMenuButton.visibility = if (number.isBlank()) View.GONE else View.VISIBLE
        }

        private fun updateNumberDisplay(
            displayName: String,
            isBlocked: Boolean,
            isWhitelisted: Boolean
        ) {
            numberTextView.text = when {
                isBlocked -> context.getString(R.string.blocked_text_format, displayName)
                isWhitelisted -> context.getString(R.string.whitelisted_text_format, displayName)
                else -> displayName
            }

            numberTextView.setTextColor(ContextCompat.getColor(context, when {
                isBlocked -> android.R.color.holo_red_light
                isWhitelisted -> android.R.color.holo_blue_dark
                else -> R.color.textColor
            }))
        }

        private fun updateTypeView(type: Int) {
            val action = when (type) {
                CallLog.Calls.INCOMING_TYPE -> context.getString(R.string.call_incoming)
                CallLog.Calls.MISSED_TYPE -> context.getString(R.string.call_missed)
                CallLog.Calls.REJECTED_TYPE -> context.getString(R.string.call_rejected)
                CallLog.Calls.BLOCKED_TYPE -> context.getString(R.string.call_blocked)
                else -> context.getString(R.string.call_unknown)
            }

            actionTextView.text = action
        }

        private suspend fun getContactNameAsync(context: Context, phoneNumber: String): String? {
            return withContext(Dispatchers.IO) {
                getContactName(context, phoneNumber)
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

        private fun showPopupMenu(callLog: CallLogEntry) {
            val number = callLog.number.normalizePhone()
            val currentContact = ListaSpamApp.get().contactsCache.value[number]
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
                handleMenuClick(menuItem.itemId, number, callLog, currentlyBlocked, currentlyWhitelisted)
            }
            popupMenu.show()
        }

        private fun handleMenuClick(
            itemId: Int,
            number: String,
            callLog: CallLogEntry,
            currentlyBlocked: Boolean,
            currentlyWhitelisted: Boolean
        ): Boolean {
            return when (itemId) {
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
                        editContact(callLog.number, -1)
                    } else {
                        editContact(callLog.number, ContactType.WHITELIST.value)
                    }
                    true
                }

                R.id.block_action -> {
                    if (currentlyBlocked) {
                        editContact(callLog.number, -1)
                    } else {
                        editContact(callLog.number, ContactType.BLOCK.value)
                    }
                    true
                }

                else -> false
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

        private fun editContact(number: String, type: Int) {
            fragmentManager?.clearFragmentResultListener("contact_edited")
            fragmentManager?.setFragmentResultListener("contact_edited", context) { _, bundle ->
                val addedNumber = bundle.getString("number")?.normalizePhone() ?: ""
                onItemChangedListener?.onItemChanged(addedNumber)
            }

            val dialog = AddContactDialog.newInstance(
                name = number,
                number = number,
                type = type
            )
            fragmentManager?.let { dialog.show(it, "AddContactDialog") }
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

        private fun openReportAlert(number: String) {
            val reportDialogManager = ReportDialogManager(context)
            reportDialogManager.show(number)
        }
    }

    fun setOnItemChangedListener(listener: OnItemChangedListener) {
        this.onItemChangedListener = listener
    }

    fun destroy() {
        adapterScope.cancel()
    }
}