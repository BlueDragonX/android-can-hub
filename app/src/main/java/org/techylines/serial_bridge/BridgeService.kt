package org.techylines.serial_bridge

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.IBinder
import android.provider.SyncStateContract
import android.util.Log
import androidx.core.app.NotificationCompat
import com.hoho.android.usbserial.driver.*
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.lang.Exception
import java.lang.StringBuilder

private const val CHANNEL_ID = "BridgeServiceChannel"

class BridgeService : Service() {
    private var running: Boolean = false
    private var count: Int = 0
    private var prober: UsbSerialProber? = null
    private var port : UsbSerialPort? = null
    private var ioManager : SerialInputOutputManager? = null

    public fun getRunning() : Boolean {
        return running
    }

    override fun onCreate() {
        super.onCreate()
        Log.v(TAG, "service created")

        // Create USB serial prober.
        val probeTable = UsbSerialProber.getDefaultProbeTable()
        probeTable.addProduct(0x03EB, 0x802B, CdcAcmSerialDriver::class.java) // SAME51
        prober = UsbSerialProber(probeTable)

        // Handle USB intents.
        val intentFilter = IntentFilter()
        intentFilter.addAction(ACTION_USB_DEVICE_CONNECTED)
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        intentFilter.addAction(ACTION_USB_DEVICE_PERMISSION)
        registerReceiver(broadcastReceiver, intentFilter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        running = true
        startForeground(1, createNotification())
        Log.v(TAG, "service start")

        // start listening for connections and disconnections
        // if this was triggered by a connect intent, start listening on that device
        onIntent(intent)

        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(broadcastReceiver)
        running = false
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun createNotification(): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Serial Bridge",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, 0)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Serial Bridge Online")
            .setContentText("Bridging connected USB device.")
            .setSmallIcon(R.drawable.ic_usb_plug)
            .setContentIntent(pendingIntent)
            .build()
    }

    // Create a serial connection on a USB device that we already have permission to access.
    private fun connect(device : UsbDevice) {
        val driver = prober?.probeDevice(device)
        if (driver == null) {
            Log.i(TAG, "device ${device.manufacturerName} ${device.productName} not supported")
        } else {
            Log.i(TAG, "device ${device.manufacturerName} ${device.productName} available")
            val manager = getSystemService(UsbManager::class.java)
            val con = manager.openDevice(device)
            if (con == null) {
                Log.d(TAG, "failed to open usb device")
                return;
            }
            if (driver.ports.size == 0) {
                Log.d(TAG, "device has no ports")
                return;
            }
            port = driver.ports[0]
            port?.let {
                port?.open(con)
                port?.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                port?.dtr = true
                ioManager = SerialInputOutputManager(port, ioListener)
            }
            ioManager?.let {
            it.start()
            }
        }
    }

    // Disconnect a device.
    private fun disconnect(device : UsbDevice) {
        Log.i(TAG, "disconnected from ${device.manufacturerName} ${device.productName}")
        ioManager?.let {
            it.stop()
            ioManager = null
        }
        port?.let {
            it.close()
            port = null
        }
    }

    // Request permission to connect to a device for which we did not receive an intent.
    private fun requestConnect(device : UsbDevice) {
        val manager = getSystemService(UsbManager::class.java)
        if (!manager.hasPermission(device)) {
            Log.i(TAG,"request permission for ${device.manufacturerName} ${device.productName}")
            val pendingIntent =
                PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_DEVICE_PERMISSION), 0)
            manager.requestPermission(device, pendingIntent)
            return
        }
        connect(device)
    }

    private fun onIntent(intent : Intent?) {
        when (intent?.action) {
            ACTION_USB_DEVICE_CONNECTED -> {
                count++
                Log.v(TAG, "connected ${count} times")
                val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                Log.v(TAG, "ACTION_USB_DEVICE_CONNECTED ${device?.manufacturerName} ${device?.productName}")
                device?.let {
                    connect(it)
                }
            }
            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                Log.v(TAG, "ACTION_USB_DEVICE_DETACHED ${device?.manufacturerName} ${device?.productName}")
                device?.let {
                    disconnect(it)
                }
            }
            ACTION_USB_DEVICE_PERMISSION -> {
                val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                device?.let {
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        Log.d(TAG, "usb permission granted on ${device.manufacturerName} ${device.productName} (${device.serialNumber})")
                        val sendIntent = Intent(ACTION_USB_DEVICE_CONNECTED)
                        sendIntent.putExtra(UsbManager.EXTRA_DEVICE, device)
                        sendBroadcast(sendIntent)
                    } else {
                        Log.d(TAG, "usb permission denied on ${device.manufacturerName} ${device.productName} (${device.serialNumber})")
                    }
                }
            }
        }
    }

    var broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "service received ${intent?.action}")
            onIntent(intent)
        }
    }

    var ioListener = object : SerialInputOutputManager.Listener {
        override fun onNewData(bytes: ByteArray) {
            var hex = StringBuilder()
            for (byte in bytes) {
                hex.append("%02x".format(byte))
            }
            Log.v(TAG, "read bytes \"${hex}\"")
        }

        override fun onRunError(e: Exception?) {
            Log.e(TAG, "error: ${e.toString()}")
        }
    }
}