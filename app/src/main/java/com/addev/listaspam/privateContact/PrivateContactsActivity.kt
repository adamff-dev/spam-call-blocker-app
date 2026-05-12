package com.addev.listaspam.privateContact

import android.app.AlertDialog
import android.os.Bundle
import android.view.*
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import androidx.appcompat.widget.SearchView;
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

import com.addev.listaspam.ListaSpamApp

import com.addev.listaspam.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PrivateContactsActivity : AppCompatActivity() {

    private val repository by lazy { (application as ListaSpamApp).repository }
    private lateinit var adapter: ContactsAdapter

    private val searchQuery = MutableStateFlow("")

    // Multi-selection
    private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    private var isSelectionMode = false

    private val fileHandler: ContactFileHandler by lazy {
        ContactFileHandler(
            contentResolver = contentResolver,
            repository = repository,
            scope = lifecycleScope,
            onError = { message ->
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        )
    }

    // import json
    private val importLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { fileHandler.handleImport(it) }
    }

    // export json
    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { fileHandler.handleExport(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_private_contacts)

        setupToolbar()
        setupRecyclerView()
        setupSearchAndDataObservation()
        setupActionButtons()
    }

    private fun setupToolbar() {
        supportActionBar?.apply {
            title = getString(R.string.private_contacts_title)
            setDisplayHomeAsUpEnabled(true)
        }
    }

    private fun setupRecyclerView() {
        adapter = ContactsAdapter(
            onItemClick = { contact, anchor -> handleItemClick(contact, anchor) },
            onItemLongClick = { contact -> enterSelectionMode(contact) },
            isSelected = { contact -> selectedIds.value.contains(contact.id) },
            isSelectionMode = { isSelectionMode }
        )

        findViewById<RecyclerView>(R.id.recyclerPrivateContacts).apply {
            layoutManager = LinearLayoutManager(this@PrivateContactsActivity)
            adapter = this@PrivateContactsActivity.adapter
        }
    }

    private fun setupSearchAndDataObservation() {
        lifecycleScope.launch {
            combine(repository.allContacts, searchQuery) { list, query ->
                if (query.isBlank()) list
                else {
                    val q = query.trim().lowercase()
                    list.filter { it.name.lowercase().contains(q) || it.number.contains(q) }
                }
            }.collect { filteredList ->
                adapter.submitList(filteredList)
            }
        }

        findViewById<SearchView>(R.id.searchView).setOnQueryTextListener(
            object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?) = false
                override fun onQueryTextChange(newText: String?): Boolean {
                    searchQuery.value = newText.orEmpty()
                    return true
                }
            }
        )
    }

    private fun setupActionButtons() {
        findViewById<ImageButton>(R.id.btnCancelSelection).setOnClickListener { exitSelectionMode() }
        findViewById<ImageButton>(R.id.btnDeleteSelected).setOnClickListener { confirmDeleteSelected() }
    }

    private fun enterSelectionMode(contact: PrivateContact) {
        isSelectionMode = true
        val position = adapter.currentList.indexOf(contact)
        selectedIds.value = setOf(contact.id)
        showSelectionToolbar()
        adapter.notifyItemChanged(position)
    }

    private fun exitSelectionMode() {
        val previouslySelected = selectedIds.value
        isSelectionMode = false
        selectedIds.value = emptySet()
        showSelectionToolbar()
        previouslySelected.forEach { id ->
            val pos = adapter.currentList.indexOfFirst { it.id == id }
            if (pos != -1) adapter.notifyItemChanged(pos)
        }
    }

    private fun toggleSelection(contact: PrivateContact) {

        val position = adapter.currentList.indexOf(contact)
        val current = selectedIds.value.toMutableSet()
        if (current.contains(contact.id)) current.remove(contact.id)
        else current.add(contact.id)

        if (current.isEmpty()) {
            exitSelectionMode()
        } else {
            selectedIds.value = current
            updateSelectionCount()
            adapter.notifyItemChanged(position)
        }
    }

    private fun showSelectionToolbar() {
        val selBar = findViewById<LinearLayout>(R.id.selectionToolbar)
        val search = findViewById<SearchView>(R.id.searchView)

        if (isSelectionMode) {
            selBar.visibility = View.VISIBLE
            search.visibility = View.GONE
            updateSelectionCount()
        } else {
            selBar.visibility = View.GONE
            search.visibility = View.VISIBLE
        }
    }

    private fun updateSelectionCount() {
        findViewById<TextView>(R.id.tvSelectionCount).text = getString(R.string.selected_count, selectedIds.value.size)
    }

    private fun confirmDeleteSelected() {
        val idsToDelete = selectedIds.value
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_private_contact))
            .setMessage(getString(R.string.delete_selected_confirm, idsToDelete.size))
            .setPositiveButton(R.string.aceptar) { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    repository.deleteByIds(idsToDelete)

                    withContext(Dispatchers.Main) {
                        exitSelectionMode()
                        Toast.makeText(
                            this@PrivateContactsActivity,
                            getString(R.string.contact_deleted),
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                }
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    private fun handleItemClick(contact: PrivateContact, anchor: View) {
        if (isSelectionMode) {
            toggleSelection(contact)
            return
        }
        val popup = PopupMenu(this, anchor)
        popup.inflate(R.menu.menu_private_contact_item)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_edit_contact -> { showEditDialog(contact); true }
                R.id.action_delete_contact -> { confirmDelete(contact); true }
                else -> false
            }
        }
        popup.show()
    }

    private fun showAddDialog() {
        AddContactDialog.newInstance().show(supportFragmentManager, "AddContactDialog")
    }

    private fun showEditDialog(contact: PrivateContact) {
        AddContactDialog.newInstance(contact.name, contact.number, contact.type)
            .show(supportFragmentManager, "AddContactDialog")
    }

    private fun confirmDelete(contact: PrivateContact) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_private_contact))
            .setPositiveButton(R.string.aceptar) { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) { repository.delete(contact) }
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
    override fun onBackPressed() { if (isSelectionMode) exitSelectionMode() else super.onBackPressed() }
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_private_contact, menu)
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.add_private_contact) { showAddDialog(); return true }
        if (item.itemId == R.id.import_contacts) { importJson(); return true }
        if (item.itemId == R.id.export_contacts) { exportJson(); return true }
        return super.onOptionsItemSelected(item)
    }

    private fun exportJson() {
        val fileName = "contacts_export_${System.currentTimeMillis()}.json"
        exportLauncher.launch(fileName)
    }

    private fun importJson() {
        importLauncher.launch("application/json")
    }

    class ContactsAdapter(
        private val onItemClick: (PrivateContact, View) -> Unit,
        private val onItemLongClick: (PrivateContact) -> Unit,
        private val isSelected: (PrivateContact) -> Boolean,
        private val isSelectionMode: () -> Boolean
    ) : ListAdapter<PrivateContact, ContactsAdapter.VH>(DIFF_CALLBACK) {

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.nameTextView)
            val tvNumber: TextView = view.findViewById(R.id.numberTextView)
            val tvType: TextView = view.findViewById(R.id.PrivateContactTypeTextView)
            val btnOverflow: ImageButton = view.findViewById(R.id.overflowMenuButton)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_private_contact, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val contact = getItem(position)
            holder.tvName.text = contact.name
            holder.tvNumber.text = contact.number
            holder.tvType.text = when (contact.type) {
                0 -> holder.itemView.context.getString(R.string.whitelisted)
                1 -> holder.itemView.context.getString(R.string.blacklisted)
                else -> ""
            }

            holder.btnOverflow.setOnClickListener { view ->
                if (!isSelectionMode()) {
                    onItemClick(contact, view)
                }
            }

            // multiple selection
            holder.itemView.setOnClickListener {
                onItemClick(contact, holder.btnOverflow)
            }

            holder.itemView.setOnLongClickListener {
                onItemLongClick(contact)
                true
            }

            holder.itemView.isActivated = isSelected(contact)
        }

        companion object {
            val DIFF_CALLBACK = object : DiffUtil.ItemCallback<PrivateContact>() {
                override fun areItemsTheSame(a: PrivateContact, b: PrivateContact) = a.id == b.id
                override fun areContentsTheSame(a: PrivateContact, b: PrivateContact) = a == b
            }
        }
    }
}