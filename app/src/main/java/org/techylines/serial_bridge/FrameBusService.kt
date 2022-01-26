package org.techylines.serial_bridge

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.net.InetAddress
import java.net.InetSocketAddress
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy


private const val SERVICE_CHANNEL_ID = "FrameBusServiceChannel"

object App {
    // Surface the bus service if it's running.
    var service : FrameBusService? = null
        internal set
}

class FrameBusService : Service() {
    // The USB manager handles all connected USB devices.
    var usbManager: UsbManager? = null
        private set
    // The serial manager handles USB serial devices.
    var serialManager: UsbSerialManager? = null
        private set
    // Server to accept frames over TCP.
    // TODO: Build a SocketManager.
    var tcpServer: SocketServer? = null
        private set
    // The bus connects CAN devices. Connected devices broadcast to all other devices on the bus.
    var eventBus: FrameBus? = null
        private set

    // A coroutine scope for the event bus.
    private var scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                onIntent(it)
            }
        }
    }

    override fun onCreate() {
        // Allow networking.
        StrictMode.setThreadPolicy(ThreadPolicy.Builder().permitNetwork().build())

        Log.v(TAG, "service created")

        // Attach USB managers.
        if (usbManager == null) {
            usbManager = getSystemService(UsbManager::class.java)
            Log.v(TAG, "init usb manager")
        }
        if (serialManager == null) {
            serialManager = UsbSerialManager(usbManager!!)
            Log.v(TAG, "init serial manager")
        }
        if (tcpServer == null) {
            tcpServer = SocketServer(scope)
            val error = tcpServer?.listen(InetSocketAddress(InetAddress.getLocalHost(), 57321), SocketProtocol.TCP) {
                //  Hardcoded protocol until we have a socket manager.
                val protocol = FrameProtocolManager.default.getProtocol("RealDash")
                protocol?.encodeStream(it)?.let {
                    Log.d(TAG, "new tpc client connection")
                    scope.launch { eventBus?.add(it) }
                } ?: Log.e(TAG, "tcp server has incorrect frame protocol")
            }
            if (error != null) {
                Log.e(TAG, "failed to listen on localhost:57321: ${error}")
            } else {
                Log.v(TAG, "init tcp server on localhost:57321")
            }
        }
        if (eventBus == null) {
            eventBus = FrameBus(scope)
            scope.launch { eventBus?.add(HeartbeatStream(Frame(0x6000, "0000000000000000".decodeHex()), 1000)) }
            Log.v(TAG, "init event bus")
        }

        // Listen for USB serial device intents.
        val intentFilter = IntentFilter()
        intentFilter.addAction(ACTION_USB_SERIAL_DEVICE_ATTACH)
        intentFilter.addAction(ACTION_USB_SERIAL_DEVICE_CONNECT)
        intentFilter.addAction(ACTION_USB_SERIAL_DEVICE_DISCONNECT)
        intentFilter.addAction(ACTION_USB_SERIAL_DEVICE_REMOVE)
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        registerReceiver(broadcastReceiver, intentFilter)
        App.service = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, createNotification())

        // TODO: attach/connect configured devices

        Log.v(TAG, "service started")
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(broadcastReceiver)
        eventBus?.close()
        scope.cancel()
        App.service = null
        super.onDestroy()
        Log.v(TAG, "service stopped")
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun createNotification(): Notification {
        Log.v(TAG, "service creating notification")
        val channel = NotificationChannel(
            SERVICE_CHANNEL_ID,
            "Serial Bridge",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setContentTitle("Serial Bridge Online")
            .setContentText("Bridging connected USB device.")
            .setSmallIcon(R.drawable.ic_service_foreground)
            .setContentIntent(pendingIntent)
            .build()
    }

    // Return true if the user has permission to access a device when receiving an intent. Will
    // request permission if necessary. In such a case a pending intent is generated from the
    // provided lambda.
    private fun acquireUsbDevicePermission(intent: Intent, device: UsbDevice, pendingIntentFactory: ()->PendingIntent): Boolean {
        // App already has permission to use the device.
        if (usbManager?.hasPermission(device) == true) {
            Log.d(TAG, "permission granted for device ${device.deviceName}")
            return true
        }

        // Request permission if the user hasn't already denied access.
        // in such a case.
        if (intent.extras?.containsKey(UsbManager.EXTRA_PERMISSION_GRANTED) == true &&
            !intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
            Log.d(TAG, "permission denied for device ${device.deviceName}")
        } else {
            Log.d(TAG, "requesting permission for  device ${device.deviceName}")
            usbManager?.requestPermission(device, pendingIntentFactory())
        }
        return false
    }

    // Attach a USB device to the serial manager. Checks if the app has permission,
    // is configured, and if auto-connect is enabled. May send intents to acquire
    // permission and configuration. A pending intent will be included with both requests
    // in order to resume attachment. If auto-connect is enabled then a connect intent will
    // be sent which should be handled by this service.
    private fun onUsbSerialAttach(intent : Intent) {
        val device: UsbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE) ?: return
        Log.d(TAG, "request to attach device ${device.deviceName}")
        Log.d(TAG, "    manufacturer=\"${device.manufacturerName}\"")
        Log.d(TAG, "    product=\"${device.productName}\"")
        Log.d(TAG, "    vendor_id=${device.vendorId}")
        Log.d(TAG, "    product_id=${device.productId}")

        // Will be sent as the "callback" for permission or configuration intents.
        val pendingIntentFactory: () -> PendingIntent = {
            PendingIntent.getBroadcast(
                this,
                0,
                Intent(ACTION_USB_SERIAL_DEVICE_ATTACH).putExtra(UsbManager.EXTRA_DEVICE, device),
                PendingIntent.FLAG_IMMUTABLE
            )
        }

        // Check (and request) permission for the device.
        if (!acquireUsbDevicePermission(intent, device, pendingIntentFactory)) {
            return
        }

        val result = serialManager?.attach(device) ?: return
        when {
            result.isSuccess -> {
                // USB serial device already configure. Attach it to the manager.
                Log.d(TAG, "device ${device.deviceName} attached")
                Log.d(TAG, "    serial_number=${device.serialNumber}")
                if (result.getOrNull()?.config?.autoConnect == true) {
                    Log.d(TAG, "auto-connecting device ${device.deviceName}")
                    sendBroadcast(
                        Intent(ACTION_USB_SERIAL_DEVICE_CONNECT)
                            .putExtra(UsbManager.EXTRA_DEVICE, device)
                    )
                }
            }
            result.exceptionOrNull() is DeviceNotConfiguredError -> {
                // USB serial device it not configured. Request configuration from the user.
                Log.d(TAG, "device ${device.deviceName} not configured")
                sendBroadcast(Intent(ACTION_USB_SERIAL_DEVICE_CONFIGURE)
                    .putExtra(UsbManager.EXTRA_DEVICE, device)
                    .putExtra(EXTRA_PENDING_INTENT, pendingIntentFactory()))
            }
            else -> {
                // TODO: surface this error to the user
                Log.w(TAG, "device ${device.deviceName} not supported: ${result.exceptionOrNull()}")
            }
        }
    }

    // Disconnect and detach a device.
    private fun onUsbSerialDetach(intent: Intent) {
        val device: UsbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE) ?: return
        serialManager?.detach(device)
    }

    // Connect an attached device. This is a noop if the device is not attached.
    private fun onUsbSerialConnect(intent: Intent) {
        val device: UsbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE) ?: return
        val result = serialManager?.connect(device)
        result?.getOrNull()?.let {
            Log.d(TAG, "device ${device.deviceName} connected")
            scope.launch {
                eventBus?.add(it)
            }
        } ?: run {
            Log.w(TAG, "device ${device.deviceName} failed to connect:\n${result?.exceptionOrNull() ?: "    unknown error"}")
        }
    }

    // Disconnect a device.
    private fun onUsbSerialDisconnect(intent: Intent) {
        val device: UsbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE) ?: return
        serialManager?.disconnect(device)
    }

    // Remove a serial device from the USB serial manager.
    private fun onUsbSerialRemove(intent : Intent) {
        val id = intent.getIntExtra(EXTRA_USB_DEVICE_ID, -1)
        if (id != -1) {
            serialManager?.remove(id)
        }
    }

    // Handle a broadcast intent.
    private fun onIntent(intent : Intent) {
        Log.d(TAG, "service received intent ${intent.action}")
        when (intent.action) {
            ACTION_USB_SERIAL_DEVICE_ATTACH -> {
                onUsbSerialAttach(intent)
            }
            ACTION_USB_SERIAL_DEVICE_CONNECT -> {
                onUsbSerialConnect(intent)
            }
            ACTION_USB_SERIAL_DEVICE_DISCONNECT -> {
                onUsbSerialDisconnect(intent)
            }
            ACTION_USB_SERIAL_DEVICE_REMOVE -> {
                onUsbSerialRemove(intent)
            }
            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                onUsbSerialDetach(intent)
            }
        }
    }
}