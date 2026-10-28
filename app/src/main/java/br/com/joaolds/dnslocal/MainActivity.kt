package br.com.joaolds.dnslocal

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
import android.widget.TextView
import android.widget.Toast
import br.com.joaolds.dnslocal.dns.DnsPacketCodec
import br.com.joaolds.dnslocal.vpn.LocalDnsVpnService

class MainActivity : Activity() {
    private lateinit var primary: EditText
    private lateinit var start: Button
    private lateinit var stop: Button
    private lateinit var status: TextView
    private var pendingServer: String? = null
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
        primary.setText(getPreferences(MODE_PRIVATE).getString("primaryIpv4", ""))
        start.setOnClickListener { requestStart() }
        stop.setOnClickListener {
            stopService(Intent(this, LocalDnsVpnService::class.java))
            updateStatus()
        }
        updateStatus()
    }

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

    private fun requestStart() {
        val server = primary.text.toString().trim()
        val ip = DnsPacketCodec.parseIpv4Address(server)
        if (ip == null || server == LocalDnsVpnService.DNS_IP || server == "198.18.0.2" ||
            ip == 0 || ip == -1 || (ip ushr 24) == 127 ||
            (ip ushr 28) == 0xE) {
            Toast.makeText(this, R.string.invalid_primary_dns, Toast.LENGTH_LONG).show()
            return
        }
        getPreferences(MODE_PRIVATE).edit().putString("primaryIpv4", server).apply()
        val request = VpnService.prepare(this)
        if (request == null) {
            pendingServer = null
            startDnsService(server)
        } else {
            pendingServer = server // Preserve the validated value across the consent dialog.
            @Suppress("DEPRECATION")
            startActivityForResult(request, REQUEST_VPN)
        }
    }

    private fun startDnsService(server: String) {
        val intent = Intent(this, LocalDnsVpnService::class.java)
            .setAction(LocalDnsVpnService.ACTION_START)
            .putExtra(LocalDnsVpnService.EXTRA_PRIMARY, server)
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent)
            else startService(intent)
        } catch (_: RuntimeException) {
            Toast.makeText(this, R.string.vpn_start_failed, Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Uses the Android platform VPN consent activity without extra dependencies.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_VPN) {
            val approvedServer = pendingServer
            pendingServer = null
            if (resultCode == RESULT_OK && approvedServer != null) {
                startDnsService(approvedServer)
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
                LocalDnsVpnService.Status.ERROR -> R.string.protection_error
            }
        )
        start.isEnabled = state == LocalDnsVpnService.Status.OFF ||
            state == LocalDnsVpnService.Status.ERROR
        stop.isEnabled = state == LocalDnsVpnService.Status.STARTING ||
            state == LocalDnsVpnService.Status.RUNNING
    }

    companion object { private const val REQUEST_VPN = 101 }
}
