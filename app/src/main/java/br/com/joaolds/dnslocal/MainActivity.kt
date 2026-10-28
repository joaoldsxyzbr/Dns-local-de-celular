package br.com.joaolds.dnslocal

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import br.com.joaolds.dnslocal.blocklist.HaGeZiListDownloader
import br.com.joaolds.dnslocal.blocklist.HaGeZiListStore
import java.io.File
import java.util.Date
import java.text.DateFormat
import android.widget.TextView
import android.widget.Toast
import br.com.joaolds.dnslocal.dns.DnsPacketCodec
import br.com.joaolds.dnslocal.vpn.LocalDnsVpnService

class MainActivity : Activity() {
    private lateinit var primary: EditText
    private lateinit var start: Button
    private lateinit var stop: Button
    private lateinit var status: TextView
    private lateinit var secondaryInput: EditText
    private lateinit var secondaryEnabled: Switch
    private lateinit var blockingEnabled: Switch
    private lateinit var listStatus: TextView
    private lateinit var updateList: Button
    @Volatile private var updatingList = false
    private var statusLoader: Thread? = null
    private var updateThread: Thread? = null
    private data class DnsSelection(
        val primary: String,
        val secondary: String?,
        val backupEnabled: Boolean,
        val blockEnabled: Boolean
    )
    private var pendingSelection: DnsSelection? = null
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        primary = findViewById(R.id.primaryDnsInput)
        start = findViewById(R.id.startDns)
        stop = findViewById(R.id.stopDns)
        status = findViewById(R.id.protectionStatus)
        val prefs = getPreferences(MODE_PRIVATE)
        primary.setText(prefs.getString("primaryIpv4", ""))
        secondaryInput = findViewById(R.id.secondaryDnsInput)
        secondaryEnabled = findViewById(R.id.secondaryDnsToggle)
        secondaryInput.setText(prefs.getString("secondaryIpv4", ""))
        secondaryEnabled.isChecked = prefs.getBoolean("secondaryEnabled", false)
        secondaryInput.isEnabled = secondaryEnabled.isChecked
        secondaryEnabled.setOnCheckedChangeListener { _, checked ->
            secondaryInput.isEnabled = checked
        }
        blockingEnabled = findViewById(R.id.blockingEnabled)
        blockingEnabled.isChecked = prefs.getBoolean("blockEnabled", true)
        blockingEnabled.setOnCheckedChangeListener { _, enabled ->
            getPreferences(MODE_PRIVATE).edit().putBoolean("blockEnabled", enabled).apply()
        }
        listStatus = findViewById(R.id.blocklistStatus)
        updateList = findViewById(R.id.updateBlocklist)
        updateList.setOnClickListener { updateHaGeZi() }
        refreshListStatus()
        start.setOnClickListener { requestStart() }
        stop.setOnClickListener {
            stopService(Intent(this, LocalDnsVpnService::class.java))
            updateStatus()
        }
        updateStatus()
    }

    // API 26–32 requires the legacy overload; API 33+ sets NOT_EXPORTED.
    // This annotation only suppresses the false positive on the older branch.
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(LocalDnsVpnService.ACTION_STATUS)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(receiver, filter)
        }
        updateStatus()
    }

    override fun onPause() {
        unregisterReceiver(receiver)
        super.onPause()
    }

    override fun onDestroy() {
        // Do not keep parsing a large blocklist for a closed screen.
        statusLoader?.interrupt()
        statusLoader = null
        // Cancel the manual network update when this screen closes.
        updateThread?.interrupt()
        updateThread = null
        super.onDestroy()
    }

    private fun isAllowedIpv4(value: String): Boolean {
        val ip = DnsPacketCodec.parseIpv4Address(value) ?: return false
        val first = ip ushr 24
        return ip != 0 && ip != -1 &&
            first !in listOf(0, 127, 169) &&
            first < 224 &&
            value != LocalDnsVpnService.DNS_IP && value != "198.18.0.2"
    }

    private fun requestStart() {
        val server = primary.text.toString().trim()
        if (!isAllowedIpv4(server)) {
            Toast.makeText(this, R.string.invalid_primary_dns, Toast.LENGTH_LONG).show()
            return
        }
        val enabled = secondaryEnabled.isChecked
        val secondary = secondaryInput.text.toString().trim()
        if (enabled && (!isAllowedIpv4(secondary) || secondary == server)) {
            Toast.makeText(this, R.string.invalid_secondary_dns, Toast.LENGTH_LONG).show()
            return
        }
        val selection = DnsSelection(
            server, if (enabled) secondary else null, enabled, blockingEnabled.isChecked
        )
        // Preserve the entered secondary address, but never send it to the
        // service while backup is disabled.
        getPreferences(MODE_PRIVATE).edit()
            .putString("primaryIpv4", server)
            .putString("secondaryIpv4", secondary)
            .putBoolean("secondaryEnabled", enabled)
            .putBoolean("blockEnabled", blockingEnabled.isChecked)
            .apply()
        val request = VpnService.prepare(this)
        if (request == null) {
            pendingSelection = null
            startDnsService(selection)
        } else {
            pendingSelection = selection
            @Suppress("DEPRECATION")
            startActivityForResult(request, REQUEST_VPN)
        }
    }

    private fun startDnsService(selection: DnsSelection) {
        val intent = Intent(this, LocalDnsVpnService::class.java)
            .setAction(LocalDnsVpnService.ACTION_START)
            .putExtra(LocalDnsVpnService.EXTRA_PRIMARY, selection.primary)
            .putExtra(LocalDnsVpnService.EXTRA_USE_SECONDARY, selection.backupEnabled)
            .putExtra(LocalDnsVpnService.EXTRA_BLOCK_ENABLED, selection.blockEnabled)
        if (selection.backupEnabled) {
            intent.putExtra(LocalDnsVpnService.EXTRA_SECONDARY, selection.secondary)
        }
        try {
            startForegroundService(intent)
        } catch (_: RuntimeException) {
            Toast.makeText(this, R.string.vpn_start_failed, Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Uses the Android platform VPN consent activity without extra dependencies.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_VPN) {
            val approved = pendingSelection
            pendingSelection = null
            if (resultCode == RESULT_OK && approved != null) {
                startDnsService(approved)
            } else {
                Toast.makeText(this, R.string.vpn_permission_denied, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateStatus() {
        val state = LocalDnsVpnService.status
        status.setText(
            when (state) {
                LocalDnsVpnService.Status.OFF -> R.string.protection_off
                LocalDnsVpnService.Status.STARTING -> R.string.protection_starting
                LocalDnsVpnService.Status.RUNNING -> R.string.protection_dns_only
                LocalDnsVpnService.Status.RUNNING_FILTER -> R.string.protection_dns_filter
                LocalDnsVpnService.Status.ERROR -> R.string.protection_error
            }
        )
        start.isEnabled = state == LocalDnsVpnService.Status.OFF ||
            state == LocalDnsVpnService.Status.ERROR
        stop.isEnabled = state == LocalDnsVpnService.Status.STARTING ||
            state == LocalDnsVpnService.Status.RUNNING ||
            state == LocalDnsVpnService.Status.RUNNING_FILTER
    }

    private fun listStore(): HaGeZiListStore =
        HaGeZiListStore(File(filesDir, "dns-lists"))

    private fun refreshListStatus() {
        statusLoader?.interrupt()
        statusLoader = Thread({
            val loaded = listStore().load()
            val updatedAt = File(filesDir, "dns-lists/hagezi-pro-mini-onlydomains.txt").lastModified()
            runOnUiThread {
                if (isDestroyed || updatingList) return@runOnUiThread
                showListStatus(loaded?.size, updatedAt)
            }
        }, "dns-list-status").apply { start() }
    }

    private fun showListStatus(count: Int?, updatedAt: Long) {
        listStatus.text = if (count == null) {
            getString(R.string.blocklist_not_loaded)
        } else {
            getString(
                R.string.blocklist_ready_format,
                count,
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(Date(updatedAt))
            )
        }
    }

    private fun updateHaGeZi() {
        if (updatingList) return
        updatingList = true
        updateList.isEnabled = false
        listStatus.setText(R.string.blocklist_downloading)
        updateThread = Thread({
            val result = runCatching {
                HaGeZiListDownloader().refresh(listStore())
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                updatingList = false
                updateList.isEnabled = true
                result.fold(
                    onSuccess = { list ->
                        showListStatus(list.size, File(filesDir, "dns-lists/hagezi-pro-mini-onlydomains.txt").lastModified())
                    },
                    onFailure = {
                        listStatus.setText(R.string.blocklist_update_failed)
                    }
                )
            }
        }, "dns-list-manual-update").apply { start() }
    }

    companion object { private const val REQUEST_VPN = 101 }
}
