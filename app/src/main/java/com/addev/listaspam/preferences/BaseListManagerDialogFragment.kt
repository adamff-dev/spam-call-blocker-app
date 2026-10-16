package com.addev.listaspam.preferences

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.text.InputType
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.preference.PreferenceDialogFragmentCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.addev.listaspam.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class BaseListManagerDialogFragment : PreferenceDialogFragmentCompat() {

    private val entries = mutableListOf<String>()
    private lateinit var adapter: EntryAdapter
    private lateinit var inputField: EditText

    companion object {
        fun newInstance(key: String): BaseListManagerDialogFragment {
            return BaseListManagerDialogFragment().apply {
                arguments = Bundle().apply { putString(ARG_KEY, key) }
            }
        }
    }

    private val listPref: BaseListManagerPreference
        get() = preference as BaseListManagerPreference

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        entries.clear()
        entries.addAll(listPref.getEntries())

        // Use themedContext from MaterialAlertDialogBuilder so Material attrs resolve in XML
        val builder = MaterialAlertDialogBuilder(requireContext())
        val themedContext = builder.context
        val inflater = LayoutInflater.from(themedContext)
        val view = inflater.inflate(R.layout.dialog_list_manager, null)

        val recyclerView: RecyclerView = view.findViewById(R.id.recycler_entries)
        val emptyText: TextView = view.findViewById(R.id.text_empty)
        inputField = view.findViewById(R.id.edit_new_entry)
        val addButton: View = view.findViewById(R.id.btn_add_entry)

        adapter = EntryAdapter(themedContext, entries, emptyText) { position, newValue ->
            onEntryEdited(position, newValue)
        }
        recyclerView.layoutManager = LinearLayoutManager(themedContext)
        recyclerView.adapter = adapter
        updateEmptyState(emptyText)

        // Force re-measure after the dialog window has its final width
        recyclerView.post { adapter.notifyDataSetChanged() }

        addButton.setOnClickListener {
            val text = inputField.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            if (!listPref.validator.validate(text)) {
                Toast.makeText(requireContext(), listPref.errorMessageResId, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val cleaned = listPref.validator.cleanInput(text)
            val newEntries = cleaned.filter { it !in entries }
            if (newEntries.isEmpty()) {
                Toast.makeText(requireContext(), R.string.list_entry_duplicate, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            entries.addAll(newEntries)
            adapter.notifyItemRangeInserted(entries.size - newEntries.size, newEntries.size)
            updateEmptyState(emptyText)
            inputField.text.clear()
        }

        return builder
            .setTitle(listPref.title)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ -> onDialogClosed(true) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> onDialogClosed(false) }
            .create()
    }

    private fun onEntryEdited(position: Int, newValue: String) {
        if (!listPref.validator.validate(newValue)) {
            Toast.makeText(requireContext(), listPref.errorMessageResId, Toast.LENGTH_LONG).show()
            return
        }
        val cleaned = listPref.validator.cleanInput(newValue)
        if (cleaned.size == 1) {
            entries[position] = cleaned[0]
            adapter.notifyItemChanged(position)
        }
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (positiveResult) {
            addPendingInput()
            listPref.saveEntries(entries)
        }
    }

    /**
     * Adds the text typed in the input field that the user did not confirm with the "+" button,
     * so it is not silently lost when pressing OK.
     */
    private fun addPendingInput() {
        if (!::inputField.isInitialized) return
        val text = inputField.text.toString().trim()
        if (text.isEmpty() || !listPref.validator.validate(text)) return
        entries.addAll(listPref.validator.cleanInput(text).filter { it !in entries })
    }

    private fun updateEmptyState(emptyText: TextView) {
        emptyText.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
    }

    // --- Adapter ---

    inner class EntryAdapter(
        private val themedContext: android.content.Context,
        private val data: MutableList<String>,
        private val emptyText: TextView,
        private val onEdit: (Int, String) -> Unit
    ) : RecyclerView.Adapter<EntryAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val label: TextView = view.findViewById(R.id.text_entry)
            val editBtn: ImageButton = view.findViewById(R.id.btn_edit)
            val deleteBtn: ImageButton = view.findViewById(R.id.btn_delete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(themedContext).inflate(R.layout.item_list_entry, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val entry = data[position]
            holder.label.text = entry

            holder.editBtn.setOnClickListener {
                val pos = holder.adapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                showEditDialog(pos, data[pos])
            }

            holder.deleteBtn.setOnClickListener {
                val pos = holder.adapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                data.removeAt(pos)
                notifyItemRemoved(pos)
                updateEmptyState(emptyText)
            }
        }

        override fun getItemCount() = data.size

        private fun showEditDialog(position: Int, currentValue: String) {
            val editText = EditText(themedContext).apply {
                setText(currentValue)
                setSelection(currentValue.length)
                setPadding(48, 24, 48, 24)
                hint = getString(listPref.hintResId)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                maxLines = 1
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.list_entry_edit_title)
                .setView(editText)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val newVal = editText.text.toString().trim()
                    if (newVal.isNotEmpty()) onEdit(position, newVal)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }
}
