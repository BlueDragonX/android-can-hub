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

const val TAG = "SerialBridge"

const val ACTION_USB_DEVICE_READY = "org.techylines.action.USB_DEVICE_READY"
const val ACTION_USB_DEVICE_PERMISSION = "org.techylines.action.USB_DEVICE_PERMISSION"
const val ACTION_SERIAL_DEVICE_CONFIGURE = "org.techylines.action.SERIAL_DEVICE_CONFIGURE"
const val ACTION_SERIAL_DEVICE_CONNECT = "org.techylines.action.SERIAL_DEVICE_CONNECT"
const val EXTRA_USB_SERIAL_CONFIG = "org.techylines.EXTRA_USB_SERIAL_CONFIG"

class MainActivity : AppCompatActivity() {
    private var usbManager : UsbManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        usbManager = getSystemService(UsbManager::class.java)

        val broadcastFilter = IntentFilter()
        broadcastFilter.addAction(ACTION_USB_DEVICE_PERMISSION)
        broadcastFilter.addAction(ACTION_USB_DEVICE_READY)
        registerReceiver(broadcastReceiver, broadcastFilter)

        ContextCompat.startForegroundService(this, Intent(this, BridgeService::class.java))

        showContent()
        onIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(broadcastReceiver)
    }

    private fun onIntent(intent: Intent) {
        Log.v(TAG, "MainActivity received intent ${intent.action}")
        when (intent.action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                val device : UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                device?.let {
                    onUsbDeviceAttached(device)
                }
            }
            ACTION_USB_DEVICE_PERMISSION -> {
                val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                device?.let {
                    onUsbDevicePermission(device)
                }
            }
            ACTION_USB_DEVICE_READY -> {
                val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                device?.let {
                    onUsbDeviceReady(it)
                }
            }
        }
    }

    private fun onUsbDeviceAttached(device : UsbDevice) {
        Log.i(TAG,"attach USB device ${device.deviceName}\n")
        Log.i(TAG,"    manufacturer=\"${device.manufacturerName}\"")
        Log.i(TAG,"    product=\"${device.productName}\"")
        Log.i(TAG,"    vendor_id=${device.vendorId}")
        Log.i(TAG,"    product_id=${device.productId}")

        if (usbManager?.hasPermission(device) == true) {
            val sendIntent = Intent(ACTION_USB_DEVICE_READY)
            sendIntent.putExtra(UsbManager.EXTRA_DEVICE, device)
            sendBroadcast(sendIntent)
        } else {
            requestUsbDevicePermission(device)
        }
    }

    private fun onUsbDeviceReady(device : UsbDevice) {
        Log.d(TAG, "USB device ${device.deviceName} ready")
        val config = loadSerialConfig(device)
        if (config == null) {
            // ask for configuration
            val sendIntent = Intent(ACTION_SERIAL_DEVICE_CONFIGURE)
            sendIntent.putExtra(UsbManager.EXTRA_DEVICE, device)
            sendBroadcast(sendIntent)
        } else {
            // connect the device
            val sendIntent = Intent(ACTION_SERIAL_DEVICE_CONNECT)
            sendIntent.putExtra(UsbManager.EXTRA_DEVICE, device)
            sendIntent.putExtra(EXTRA_USB_SERIAL_CONFIG, config)
            sendBroadcast(sendIntent)
        }
    }

    private fun onUsbDevicePermission(device : UsbDevice) {
        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
            Log.i(TAG, "user granted permission to USB device ${device.deviceName}")
            val sendIntent = Intent(ACTION_USB_DEVICE_READY)
            sendIntent.putExtra(UsbManager.EXTRA_DEVICE, device)
            sendBroadcast(sendIntent)
        } else {
            Log.i(TAG, "user denied permission to USB device ${device.deviceName}")
        }
    }

    private fun requestUsbDevicePermission(device : UsbDevice) {
        val pendingIntent =
            PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_DEVICE_PERMISSION), PendingIntent.FLAG_IMMUTABLE)
        usbManager?.requestPermission(device, pendingIntent)
    }

    private fun loadSerialConfig(device: UsbDevice): SerialConfig? {
        // TODO: Implement a storage system of some flavor.
        return SerialConfig()
    }

    private fun saveSerialConfig(device: UsbDevice, config: SerialConfig)  {
        // TODO: Implement a storage system of some flavor.
    }

    private fun showContent() {
        setContentView(R.layout.activity_main)
        val start: Button = findViewById(R.id.startButton)
        start.setOnClickListener {
            Log.v(TAG, "button starting service")
            ContextCompat.startForegroundService(this, Intent(this, BridgeService::class.java))
        }

        val stop: Button = findViewById(R.id.stopButton)
        stop.setOnClickListener {
            Log.v(TAG, "button stopping service")
            val intent  = Intent(this, BridgeService::class.java)
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