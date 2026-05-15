package com.addev.listaspam.privateContact

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.addev.listaspam.ListaSpamApp
import com.addev.listaspam.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.widget.RadioGroup
import android.widget.RadioButton
import kotlinx.coroutines.withContext

class AddContactDialog : DialogFragment() {

    companion object {
        private const val ARG_NAME = "arg_name"
        private const val ARG_NUMBER = "arg_number"
        private const val ARG_TYPE = "arg_type" // 0: White, 1: Black, -1 remove

        fun newInstance(name: String? = null, number: String? = null, type: Int = ContactType.WHITELIST.value,): AddContactDialog {
            val fragment = AddContactDialog()
            val args = Bundle().apply {
                putString(ARG_NAME, name)
                putString(ARG_NUMBER, number)
                putInt(ARG_TYPE, type)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        val view = requireActivity().layoutInflater.inflate(R.layout.dialog_add_contact, null)

        val nameInput = view.findViewById<EditText>(R.id.edit_name)
        val numberInput = view.findViewById<EditText>(R.id.edit_number)
        val radioGroup = view.findViewById<RadioGroup>(R.id.radio_group_type)

        val initialType = arguments?.getInt(ARG_TYPE) ?: 0
        val isRemoveMode = initialType == -1
        val number = arguments?.getString(ARG_NUMBER) ?: ""

        if (isRemoveMode) {
            // Nascondi i campi in modalità remove
            nameInput.visibility = View.GONE
            numberInput.visibility = View.GONE
            radioGroup.visibility = View.GONE

            builder.setView(view)
                .setTitle("Remove Contact: $number")
                .setPositiveButton("Remove") { _, _ ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        ListaSpamApp.get().repository.deleteByNumber(number)

                        withContext(Dispatchers.Main) {
                            val result = Bundle().apply { putString("number", number) }
                            parentFragmentManager.setFragmentResult("contact_edited", result)
                        }
                    }
                }
        } else {
            // Modalità add/edit
            nameInput.setText(arguments?.getString(ARG_NAME))
            numberInput.setText(arguments?.getString(ARG_NUMBER))

            val initialType = arguments?.getInt(ARG_TYPE) ?: 0
            if (initialType == 1) {
                view.findViewById<RadioButton>(R.id.radio_blacklist).isChecked = true
            } else {
                view.findViewById<RadioButton>(R.id.radio_whitelist).isChecked = true
            }

            builder.setView(view)
                .setTitle("Add Contact")
                .setPositiveButton("Save") { _, _ ->
                    val name = nameInput.text.toString()
                    val number = numberInput.text.toString()
                    val selectedType = if (radioGroup.checkedRadioButtonId == R.id.radio_blacklist) 1 else 0

                    if (number.isNotBlank()) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            val existingNumber = arguments?.getString(ARG_NUMBER)
                            val repo = ListaSpamApp.get().repository

                            if (existingNumber != null) {
                                val existing = repo.getByNumber(existingNumber)
                                if (existing != null) {
                                    repo.update(existing, name, number, selectedType)
                                } else {
                                    repo.insert(PrivateContact(name = name, number = number, type = selectedType))
                                }
                            } else {
                                repo.insert(PrivateContact(name = name, number = number, type = selectedType))
                            }

                            withContext(Dispatchers.Main) {
                                val result = Bundle().apply { putString("number", number) }
                                parentFragmentManager.setFragmentResult("contact_edited", result)
                            }
                        }
                    }
                }
        }
        builder.setNegativeButton("Cancel", null)
        return builder.create()
    }
}