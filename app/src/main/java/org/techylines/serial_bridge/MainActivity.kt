package org.techylines.serial_bridge

import android.app.ActivityManager
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
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.lang.StringBuilder

const val TAG = "SerialBridge"

const val ACTION_USB_DEVICE_CONNECTED = "org.techylines.action.USB_DEVICE_CONNECTED"
const val ACTION_USB_DEVICE_PERMISSION = "org.techylines.action.USB_DEVICE_PERMISSION"

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showContent()

        Log.v(TAG, "onCreate starting service")
        ContextCompat.startForegroundService(this, Intent(this, BridgeService::class.java))

        Log.v(TAG, "got intent ${intent?.action}")
        when {
            intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                Log.v(TAG, "attach device ${device?.manufacturerName} ${device?.productName}")
                device?.let {
                    connectDevice(it)
                }
            }
        }
    }

    private fun showContent() {
        setContentView(R.layout.activity_main)
        val start: Button = findViewById(R.id.startButton)
        start.setOnClickListener() {
            Log.v(TAG, "button starting service")
            ContextCompat.startForegroundService(this, Intent(this, BridgeService::class.java))
        }

        val stop: Button = findViewById(R.id.stopButton)
        stop.setOnClickListener() {
            Log.v(TAG, "button stoppint service")
            val intent  = Intent(this, BridgeService::class.java)
            stopService(intent)
        }

        val refresh: Button = findViewById(R.id.refreshButton)
        refresh.setOnClickListener() {
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
                text.append("\n")
            }

            val textView: TextView = findViewById(R.id.deviceListView)
            textView.text = text.toString()
        }
    }

    private fun connectDevice(device : UsbDevice) {
        val intent = Intent(ACTION_USB_DEVICE_CONNECTED)
        intent.putExtra(UsbManager.EXTRA_DEVICE, device)
        sendBroadcast(intent)
    }
}