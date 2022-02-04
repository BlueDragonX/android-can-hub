package org.techylines.can_hub

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.techylines.can_hub.serial.SerialConfig
import org.techylines.can_hub.serial.UsbSerial

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val broadcastFilter = IntentFilter()
        broadcastFilter.addAction(ACTION_UI_DEVICE_CONFIGURE)
        broadcastFilter.addAction(ACTION_UI_DEVICE_CONNECT)
        broadcastFilter.addAction(ACTION_UI_DEVICE_DISCONNECT)
        registerReceiver(broadcastReceiver, broadcastFilter)

        if (App.service == null) {
            ContextCompat.startForegroundService(this, Intent(this, HubService::class.java))
        }

        showContent()
        onIntent(intent)
        Log.d(TAG, "main activity created")
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(broadcastReceiver)
        Log.d(TAG, "main activity destroyed")
    }

    private fun onUsbSerialConnect(intent: Intent) {
        val device: UsbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE) ?: return
        findViewById<TextView>(R.id.deviceStatusTextView)?.let {
            it.text = "${device.manufacturerName} ${device.productName}"
        }
        findViewById<Button>(R.id.connectButton)?.let {
            it.text = "Disconnect"
            it.setEnabled(true)
        }
    }

    private fun onUsbSerialDisconnect(intent: Intent) {
        findViewById<TextView>(R.id.deviceStatusTextView)?.let {
            it.text = "Disconnected"
        }
        findViewById<Button>(R.id.connectButton)?.let {
            it.text = "Connect"
            it.setEnabled(true)
        }
    }

    private fun onUsbSerialConfigure(intent: Intent) {
        val device: UsbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE) ?: return
        App.service?.serialManager?.configure(device, SerialConfig(dtr=true), true, "RealDash")
        intent.getParcelableExtra<PendingIntent>(EXTRA_PENDING_INTENT)?.send()
    }

    private fun onConnectButton() {
        App.service?.connectedDevice?.let {
            Log.d(TAG, "user disconnect")
            findViewById<Button>(R.id.connectButton)?.let {
                it.text = "Disconnecting..."
                it.setEnabled(false)
            }
            App.service?.connectedDevice?.let {
                sendBroadcast(Intent(ACTION_USB_SERIAL_DEVICE_REMOVE).putExtra(EXTRA_USB_DEVICE_ID, UsbSerial.getId(it)))
            }
        } ?: run {
            findAttachedDevice()?.let {
                Log.d(TAG, "user connect to vendor_id=${it.vendorId} product_id=${it.productId}")
                findViewById<Button>(R.id.connectButton)?.let {
                    it.text = "Connecting..."
                    it.setEnabled(false)
                }
                sendBroadcast(Intent(ACTION_USB_SERIAL_DEVICE_ATTACH)
                    .putExtra(UsbManager.EXTRA_DEVICE, it)
                    .putExtra(EXTRA_ACQUIRE_PERMISSION, true))
            } ?: run {
                Log.d(TAG, "user connect but no device")
                Toast.makeText(applicationContext, "No serial devices connected.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun findAttachedDevice(): UsbDevice? {
        App.service?.usbManager?.deviceList?.values?.let {
            for (device in it) {
                if (UsbSerial.isSupported(device)) {
                    return device
                }
            }
        }
        return null
    }

    private fun onIntent(intent: Intent) {
        when (intent.action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                // Forward USB attach intents to the service.
                val device : UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                device?.let {
                    sendBroadcast(Intent(ACTION_USB_SERIAL_DEVICE_ATTACH).putExtra(UsbManager.EXTRA_DEVICE, device))
                }
            }
            ACTION_UI_DEVICE_CONNECT -> onUsbSerialConnect(intent)
            ACTION_UI_DEVICE_DISCONNECT -> onUsbSerialDisconnect(intent)
            ACTION_UI_DEVICE_CONFIGURE -> onUsbSerialConfigure(intent)
        }
    }

    private fun showContent() = runCatching {
        setContentView(R.layout.activity_main)
        this.supportActionBar?.hide()
        findViewById<Button>(R.id.connectButton)?.setOnClickListener { onConnectButton() }
    }

    private var broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                onIntent(it)
            }
        }
    }
}