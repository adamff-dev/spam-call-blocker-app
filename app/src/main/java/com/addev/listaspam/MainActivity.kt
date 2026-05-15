package com.addev.listaspam

import android.Manifest
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import com.addev.listaspam.util.PermissionUtils
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.addev.listaspam.adapter.CallLogAdapter
import com.addev.listaspam.databinding.ActivityMainBinding
import com.addev.listaspam.privateContact.PrivateContact
import com.addev.listaspam.service.UpdateChecker
import com.addev.listaspam.util.CountryLanguageUtils
import com.addev.listaspam.util.getCallLogs
import com.addev.listaspam.util.isUpdateCheckEnabled
import com.addev.listaspam.privateContact.normalizePhone
import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity(), CallLogAdapter.OnItemChangedListener {

    private val repository by lazy { (application as ListaSpamApp).repository }
    private lateinit var intentLauncher: ActivityResultLauncher<Intent>
    private var permissionDeniedDialog: AlertDialog? = null
    private var callLogAdapter: CallLogAdapter? = null

    private lateinit var binding: ActivityMainBinding

    private var isAdapterInitialized = false


    private val spamUtils by lazy { (application as ListaSpamApp).spamUtils }

    companion object {
        private const val GITHUB_USER = "adamff-dev"
        private const val GITHUB_REPO = "spam-call-blocker-app"
        private const val ABOUT_LINK = "https://github.com/$GITHUB_USER/$GITHUB_REPO"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // backward compatibility
        lifecycleScope.launch {
            val sharedPreferences = getSharedPreferences("SPAM_PREFS", Context.MODE_PRIVATE)
            repository.migrateFromSharedPreferences(sharedPreferences)
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupRecyclerView()
        setupIntentLauncher()

        CountryLanguageUtils.setListaSpamLanguage(this)
        CountryLanguageUtils.setTellowsCountry(this)
        CountryLanguageUtils.setTruecallerCountry(this)
        if (isUpdateCheckEnabled(this)) {
            checkUpdates()
        }

        lifecycleScope.launch {
            var previousMap = emptyMap<String, PrivateContact>()
            ListaSpamApp.get().contactsCache.collect { contactsMap ->
                callLogAdapter?.let { adapter ->
                    val changed = (contactsMap.keys + previousMap.keys)
                        .filter { contactsMap[it] != previousMap[it] }
                        .toSet()
                    adapter.currentList.forEachIndexed { index, item ->
                        if (item.number.normalizePhone() in changed) {
                            adapter.notifyItemChanged(index)
                        }
                    }
                }
                previousMap = contactsMap
            }
        }
    }

    private fun setupRecyclerView() {
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)

            setHasFixedSize(true)

            recycledViewPool.setMaxRecycledViews(0, 15)

            itemAnimator?.apply {
                addDuration = 200
                removeDuration = 200
                moveDuration = 200
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }

            R.id.action_about -> {
                val intent = Intent(Intent.ACTION_VIEW, ABOUT_LINK.toUri())
                this.startActivity(intent)
                true
            }

            R.id.donate -> {
                val intent = Intent(this, DonationActivity::class.java)
                startActivity(intent)
                true
            }

            R.id.test_number -> {
                showNumberInputDialog()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun checkUpdates() {
        lifecycleScope.launch(Dispatchers.IO) {
            val checker = UpdateChecker(
                context = this@MainActivity,
                githubUser = GITHUB_USER,
                githubRepo = GITHUB_REPO
            )
            checker.checkForUpdateSync()
        }
    }

    private fun showNumberInputDialog() {
        val input = EditText(this).apply { inputType = InputType.TYPE_CLASS_PHONE }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.test_number))
            .setView(input)
            .setPositiveButton(getString(R.string.aceptar)) { dialog, _ ->
                val number = input.text.toString().trim()
                if (number.isNotEmpty()) {
                    spamUtils.checkSpamNumber(this, number, null)
                } else {
                    Toast.makeText(this, getString(R.string.type_number), Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancelar)) { dialog, _ -> dialog.cancel() }
            .show()
    }

    override fun onItemChanged(number: String) {
        callLogAdapter?.let { adapter ->
            val index = adapter.currentList.indexOfFirst {
                it.number.normalizePhone() == number
            }
            if (index >= 0) adapter.notifyItemChanged(index)
        }
    }

    private fun init() {
        checkPermissionsAndRequest()

        requestCallScreeningRole()

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CALL_LOG
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            lifecycleScope.launch {
                refreshCallLogs()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        init()
    }

    private suspend fun refreshCallLogs() {
        val callLogs = withContext(Dispatchers.IO) { getCallLogs(this@MainActivity) }
        if (!isAdapterInitialized) {
            callLogAdapter = CallLogAdapter(
                context = this,
                onItemClick = {},
                fragmentManager = supportFragmentManager
            )
            binding.recyclerView.adapter = callLogAdapter
            callLogAdapter?.setOnItemChangedListener(this)
            isAdapterInitialized = true
        }
        callLogAdapter?.submitList(callLogs)
    }

    override fun onDestroy() {
        super.onDestroy()
        callLogAdapter?.destroy()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupIntentLauncher() {
        intentLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (it.resultCode == RESULT_OK) {
                    showToast(this, getString(R.string.success_call_screening_role))
                } else {
                    showToast(this, getString(R.string.failed_call_screening_role))
                }
            }
    }

    private fun showToast(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(context, message, duration).show()
    }

    private fun checkPermissionsAndRequest() {
        PermissionUtils.checkPermissionsAndRequest(this) { deniedPermissions ->
            permissionDeniedDialog = PermissionUtils.showPermissionDialog(
                this,
                deniedPermissions,
                permissionDeniedDialog
            )
            permissionDeniedDialog?.setOnDismissListener {
                permissionDeniedDialog = null
            }
            permissionDeniedDialog?.show()
        }
    }

    /**
     * Requests the call screening role.
     */
    private fun requestCallScreeningRole() {
        val roleManager = getSystemService(ROLE_SERVICE) as RoleManager
        if (!roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
            intentLauncher.launch(intent)
            showToast(this, getString(R.string.call_screening_role_prompt), Toast.LENGTH_LONG)
        }
    }
}
