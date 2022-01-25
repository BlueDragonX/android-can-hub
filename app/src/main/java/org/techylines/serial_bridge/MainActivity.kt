package org.techylines.serial_bridge

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.lang.StringBuilder

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.v(TAG, "app create")
        super.onCreate(savedInstanceState)

        val broadcastFilter = IntentFilter()
        broadcastFilter.addAction(ACTION_USB_SERIAL_DEVICE_CONFIGURE)
        registerReceiver(broadcastReceiver, broadcastFilter)

        if (App.service == null) {
            Log.v(TAG, "create init service")
            ContextCompat.startForegroundService(this, Intent(this, FrameBusService::class.java))
        }

        showContent()
        onIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(broadcastReceiver)
    }

    private fun onUsbSerialConfigure(intent: Intent) {
        val device : UsbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE) ?: return
        App.service?.serialManager?.let {
            //TODO: Build a UI for this.
            it.configure(device, SerialConfig(), true, "RealDash")
        }
        intent.getParcelableExtra<PendingIntent>(EXTRA_PENDING_INTENT)?.let {
            it.send()
        }
    }

    private fun onIntent(intent: Intent) {
        Log.v(TAG, "MainActivity received intent ${intent.action}")
        when (intent.action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                // Forward USB attach intents to the service.
                val device : UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                device?.let {
                    sendBroadcast(Intent(ACTION_USB_SERIAL_DEVICE_ATTACH).putExtra(UsbManager.EXTRA_DEVICE, device))
                }
            }
            ACTION_USB_SERIAL_DEVICE_CONFIGURE -> {
                onUsbSerialConfigure(intent)
            }
        }
    }

    private fun showContent() {
        setContentView(R.layout.activity_main)
        val start: Button = findViewById(R.id.startButton)
        start.setOnClickListener {
            Log.v(TAG, "button starting service")
            ContextCompat.startForegroundService(this, Intent(this, FrameBusService::class.java))
        }

        val stop: Button = findViewById(R.id.stopButton)
        stop.setOnClickListener {
            Log.v(TAG, "button stopping service")
            val intent  = Intent(this, FrameBusService::class.java)
            stopService(intent)
        }

        val refresh: Button = findViewById(R.id.refreshButton)
        refresh.setOnClickListener {
            val usbManager = getSystemService(UsbManager::class.java)
            val text = StringBuilder()
            if (usbManager.deviceList.size == 0) {
                text.append("no devices connected")
            }
            for (device in usbManager.deviceList)  {
                text.append("Manufacturer: ${device.value.manufacturerName}\n")
                text.append("Product: ${device.value.productName}\n")
                text.append("Device: ${device.value.deviceName}\n")
                text.append("Class: ${device.value.deviceClass}\n")
                text.append("Sub-Class: ${device.value.deviceSubclass}\n")
                text.append("Protocol: ${device.value.deviceProtocol}\n")
                text.append("Vendor ID: ${device.value.vendorId}\n")
                text.append("Product ID: ${device.value.productId}\n")
                text.append("\n")
            }

            val textView: TextView = findViewById(R.id.deviceListView)
            textView.text = text.toString()
        }
    }

    private var broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                onIntent(it)
            }
        }
    }
}