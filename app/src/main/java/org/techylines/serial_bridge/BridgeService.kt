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
    private var prober: UsbSerialProber? = null
    private var port : UsbSerialPort? = null
    private var ioManager : SerialInputOutputManager? = null

    public fun getRunning() : Boolean {
        return running
    }

    override fun onCreate() {
        super.onCreate()
        Log.v(TAG, "service created")

        // Create USB serial prober with extra devices.
        val probeTable = UsbSerialProber.getDefaultProbeTable()
        probeTable.addProduct(0x03EB, 0x802B, CdcAcmSerialDriver::class.java) // SAME51
        prober = UsbSerialProber(probeTable)

        // Handle USB intents.
        val intentFilter = IntentFilter()
        intentFilter.addAction(ACTION_SERIAL_DEVICE_CONNECT)
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        registerReceiver(broadcastReceiver, intentFilter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        running = true
        startForeground(1, createNotification())
        Log.v(TAG, "bridge service started")

        // start listening for connections and disconnections
        // if this was triggered by a connect intent, start listening on that device
        intent?.let {
            onIntent(it)
        }

        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(broadcastReceiver)
        Log.v(TAG, "bridge service stopped")
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
    private fun onConnect(device: UsbDevice, config: SerialConfig) {
        val driver = prober?.probeDevice(device)
        if (driver == null) {
            Log.e(TAG, "USB device ${device.deviceName} not supported by serial driver")
            Log.e(TAG, "    manufacturer=\"${device.manufacturerName}\"")
            Log.e(TAG, "    product=\"${device.productName}\"")
            Log.e(TAG, "    vendor_id=${device.vendorId}")
            Log.e(TAG, "    product_id=${device.productId}")
            return;
        }

        val manager = getSystemService(UsbManager::class.java)
        val con = manager.openDevice(device)
        if (con == null) {
            Log.e(TAG, "failed to open USB device ${device.deviceName}")
            return;
        }
        if (driver.ports.size == 0) {
            Log.e(TAG, "USB device ${device.deviceName} has no ports")
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
        Log.i(TAG, "USB device ${device.deviceName} connected to serial")
    }

    // Disconnect a device.
    private fun onDisconnect(device: UsbDevice) {
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

    private fun onIntent(intent : Intent) {
        Log.d(TAG, "BridgeService received intent ${intent.action}")
        when (intent.action) {
            ACTION_SERIAL_DEVICE_CONNECT -> {
                val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                val config: SerialConfig? = intent.getParcelableExtra(EXTRA_SERIAL_CONFIG)
                if (device != null && config != null) {
                    onConnect(device, config)
                }
            }
            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                device?.let {
                    onDisconnect(it)
                }
            }
        }
    }

    var broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                onIntent(it)
            }
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
            Log.w(TAG, "error encountered  during serial read: ${e.toString()}")
        }
    }
}