@file:Suppress("MagicNumber", "TooManyFunctions")

package com.ncorti.myonnaise

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.processors.PublishProcessor
import io.reactivex.subjects.BehaviorSubject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.LinkedList
import java.util.UUID
import java.util.concurrent.TimeUnit



enum class MyoStatus {
    CONNECTED, CONNECTING, READY, DISCONNECTED
}

enum class MyoControlStatus {
    STREAMING, NOT_STREAMING
}

/**
 * Class that represents a Myo Armband.
 * Use this class to connecting to it, send commands, start and stop streaming.
 *
 * @param device The [BluetoothDevice] that is backing this Myo.
 */
class Myo(private val device: BluetoothDevice) : BluetoothGattCallback() {

    /** The Device Name of this Myo */
    val name: String
        get() = device.name

    /** The Device Address of this Myo */
    val address: String
        get() = device.address

    /** The EMG Streaming frequency. 0 to reset to the [MYO_MAX_FREQUENCY]. Allowed values [0, MYO_MAX_FREQUENCY] */
    var frequency: Int = 0
        set(value) {
            field = if (value >= MYO_MAX_FREQUENCY) 0 else value
        }

    /**
     * Keep alive flag. If set to true, the library will send a [CommandList.unSleep] command
     * to the device every [KEEP_ALIVE_INTERVAL_MS] ms.
     */
    var keepAlive = true
    private var lastKeepAlive = 0L

    // Subjects for publishing outside Connection Status, Control Status and the Data (Float Arrays).
    internal val connectionStatusSubject: BehaviorSubject<MyoStatus> =
        BehaviorSubject.createDefault(MyoStatus.DISCONNECTED)
    internal val controlStatusSubject: BehaviorSubject<MyoControlStatus> =
        BehaviorSubject.createDefault(MyoControlStatus.NOT_STREAMING)
    internal val dataProcessor: PublishProcessor<FloatArray> = PublishProcessor.create()
    internal val imuDataProcessor: PublishProcessor<ImuData> = PublishProcessor.create()

    internal var gatt: BluetoothGatt? = null
    private var byteReader = ByteReader()

    private var serviceControl: BluetoothGattService? = null
    internal var characteristicCommand: BluetoothGattCharacteristic? = null
    private var characteristicInfo: BluetoothGattCharacteristic? = null
    private var serviceEmg: BluetoothGattService? = null
    private var characteristicEmg0: BluetoothGattCharacteristic? = null
    private var characteristicEmg1: BluetoothGattCharacteristic? = null
    private var characteristicEmg2: BluetoothGattCharacteristic? = null
    private var characteristicEmg3: BluetoothGattCharacteristic? = null

    // We are using two queues for writing and reading characteristics/descriptors.
    // Please note that we must always give precedence to the write.
    internal val writeQueue: LinkedList<BluetoothGattDescriptor> = LinkedList()
    private val readQueue: LinkedList<BluetoothGattCharacteristic> = LinkedList()

    /**
     * Use this method to connect to the device. You need to connect before start streaming
     * @param context A valid application context.
     */
    fun connect(context: Context) {
        Log.d("Myo", "Attempting to connect to device: ${device.address}")
        connectionStatusSubject.onNext(MyoStatus.CONNECTING)
        gatt = device.connectGatt(context, false, this)
    }

    /**
     * Use this method to disconnect from the device. This will release all the resources.
     * Don't forget to disconnect to the device when you're done (you will drain battery otherwise).
     */
    fun disconnect() {
        Log.d("Myo", "Disconnecting from device: ${device.address}")
        gatt?.close()
        controlStatusSubject.onNext(MyoControlStatus.NOT_STREAMING)
        connectionStatusSubject.onNext(MyoStatus.DISCONNECTED)
    }

    /**
     * @return true if this object is connected to a device
     */
    fun isConnected() =
        connectionStatusSubject.value == MyoStatus.CONNECTED ||
            connectionStatusSubject.value == MyoStatus.READY

    /**
     * @return true if the device is currently streaming
     */
    fun isStreaming() = controlStatusSubject.value == MyoControlStatus.STREAMING

    /**
     * Get an observable where you can check the current device status.
     * Register to this Observable to be notified when the device is Connected/Disconnected.
     */
    fun statusObservable(): Observable<MyoStatus> = connectionStatusSubject

    /**
     * Get an observable where you can check the current streaming status.
     * Register to this Observable to be notified when the device is Streaming/Not Streaming.
     */
    fun controlObservable(): Observable<MyoControlStatus> = controlStatusSubject

    /**
     * Get a [Flowable] where you can receive data from the device.
     * Data is delivered as a FloatArray of size [MYO_CHANNELS].
     * If frequency is set (!= 0) then sub-sampling is performed to achieve the desired frequency.
     */
    fun dataFlowable(): Flowable<FloatArray> {
        return if (frequency == 0) {
            dataProcessor
        } else {
            dataProcessor.sample((1000 / frequency).toLong(), TimeUnit.MILLISECONDS)
        }
    }

    fun imuDataFlowable(): Flowable<ImuData> {
        return imuDataProcessor
    }

    /**
     * Send a [Command] to the device. Before calling this please make sure the device is connected.
     */
    fun sendCommand(command: Command): Boolean {
        Log.d(TAG, "Attempting to send command: ${command.contentToString()} to device: ${device.address}")
        characteristicCommand?.apply {
            this.value = command
            if (this.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
                if (command.isStartStreamingCommand()) {
                    controlStatusSubject.onNext(MyoControlStatus.STREAMING)
                } else if (command.isStopStreamingCommand()) {
                    controlStatusSubject.onNext(MyoControlStatus.NOT_STREAMING)
                }
                val result = gatt?.writeCharacteristic(this) ?: false
                Log.d(TAG, "Command sent, result: $result. Characteristic UUID: ${this.uuid}")
                return result
            } else {
                Log.e(TAG, "Command characteristic does not have PROPERTY_WRITE")
            }
        } ?: Log.e(TAG, "Command characteristic is null")
        return false
    }
    fun sendCommandWithRetry(command: ByteArray, maxRetries: Int = 3): Boolean {
        for (i in 0 until maxRetries) {
            if (sendCommand(command)) {
                return true
            }
            Thread.sleep(100) // Wait a bit before retrying
        }
        return false
    }
    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        super.onConnectionStateChange(gatt, status, newState)
        Log.d(TAG, "onConnectionStateChange: $status -> $newState")
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            Log.d(TAG, "Bluetooth Connected")
            connectionStatusSubject.onNext(MyoStatus.CONNECTED)
            gatt.discoverServices()
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            // Calling disconnect() here will cause to release the GATT resources.
            disconnect()
            Log.d(TAG, "Bluetooth Disconnected")
        }
    }

    @Suppress("NestedBlockDepth", "ComplexMethod")
    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        super.onServicesDiscovered(gatt, status)
        Log.d(TAG, "onServicesDiscovered received: $status")

        if (status != BluetoothGatt.GATT_SUCCESS) {
            return
        }
// Add this log
        Log.d(TAG, "Discovered services: ${gatt.services.joinToString { it.uuid.toString() }}")

        // Enable IMU data
        val imuService = gatt.getService(UUID.fromString(IMU_DATA_SERVICE_UUID))
        Log.d(TAG, "IMU Service found: ${imuService != null}")

        val imuCharacteristic = imuService?.getCharacteristic(UUID.fromString(IMU_DATA_CHARACTERISTIC_UUID))
        Log.d(TAG, "IMU Characteristic found: ${imuCharacteristic != null}")

        if (imuCharacteristic != null) {
            Log.d(TAG, "IMU Characteristic properties: ${imuCharacteristic.properties}")
            Log.d(TAG, "IMU Characteristic permissions: ${imuCharacteristic.permissions}")
        }
        imuCharacteristic?.let { characteristic ->
            gatt.setCharacteristicNotification(characteristic, true)
            val descriptor = characteristic.getDescriptor(UUID.fromString(CHARACTERISTIC_CONFIG))
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }

        // Send command to enable IMU streaming
        sendCommand(byteArrayOf(0x01, 0x03, 0x02, 0x01, 0x01))
        // Find GATT Service EMG
        serviceEmg = gatt.getService(SERVICE_EMG_DATA_ID)
        serviceEmg?.apply {
            characteristicEmg0 = serviceEmg?.getCharacteristic(CHAR_EMG_0_ID)
            characteristicEmg1 = serviceEmg?.getCharacteristic(CHAR_EMG_1_ID)
            characteristicEmg2 = serviceEmg?.getCharacteristic(CHAR_EMG_2_ID)
            characteristicEmg3 = serviceEmg?.getCharacteristic(CHAR_EMG_3_ID)

            val emgCharacteristics = listOf(
                characteristicEmg0,
                characteristicEmg1,
                characteristicEmg2,
                characteristicEmg3
            )

            emgCharacteristics.forEach { emgCharacteristic ->
                emgCharacteristic?.apply {
                    if (gatt.setCharacteristicNotification(emgCharacteristic, true)) {
                        val descriptor = emgCharacteristic.getDescriptor(CHAR_CLIENT_CONFIG)
                        descriptor?.apply {
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            writeDescriptor(gatt, descriptor)
                        }
                    }
                }
            }
        }

        // Find GATT Service Control
        serviceControl = gatt.getService(SERVICE_CONTROL_ID)
        serviceControl?.apply {
            characteristicInfo = this.getCharacteristic(CHAR_INFO_ID)
            characteristicInfo?.apply {
                // if there is only 1 item in the queue, then read it.  If more than 1, we handle asynchronously in the
                // callback. GIVE PRECEDENCE to descriptor writes. They must all finish first.
                readQueue.add(this)
                if (readQueue.size == 1 && writeQueue.size == 0) {
                    gatt.readCharacteristic(this)
                }
            }
            characteristicCommand = this.getCharacteristic(CHAR_COMMAND_ID)
            characteristicCommand?.apply {
                lastKeepAlive = System.currentTimeMillis()
                sendCommand(CommandList.unSleep())
                // We send the ready event as soon as the characteristicCommand is ready.
                connectionStatusSubject.onNext(MyoStatus.READY)
            }
        }

        initializeCommandCharacteristic()
    }

    private fun initializeCommandCharacteristic() {
        Log.d(TAG, "Initializing command characteristic")
        val controlService = gatt?.getService(UUID.fromString(SERVICE_CONTROL_ID.toString()))
        Log.d(TAG, "Control service found: ${controlService != null}")

        characteristicCommand = controlService?.getCharacteristic(UUID.fromString(CHAR_COMMAND_ID.toString()))
        Log.d(TAG, "Command characteristic found: ${characteristicCommand != null}")

        if (characteristicCommand != null) {
            Log.d(TAG, "Command characteristic properties: ${characteristicCommand?.properties}")
            Log.d(TAG, "Command characteristic permissions: ${characteristicCommand?.permissions}")
        }
    }

    internal fun writeDescriptor(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor) {
        writeQueue.add(descriptor)
        // When writing, if the queue is empty, write immediately.
        if (writeQueue.size == 1) {
            gatt.writeDescriptor(descriptor)
        }
    }

    override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
        super.onDescriptorWrite(gatt, descriptor, status)
        Log.d(TAG, "onDescriptorWrite status: $status")
        writeQueue.remove()
        // if there is more to write, do it!
        if (writeQueue.size > 0)
            gatt.writeDescriptor(writeQueue.element())
        else if (readQueue.size > 0)
            gatt.readCharacteristic(readQueue.element())
    }

    override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
        super.onCharacteristicRead(gatt, characteristic, status)
        readQueue.remove()
        Log.d(TAG, "onCharacteristicRead status: $status ${characteristic.uuid}")

        if (CHAR_INFO_ID == characteristic.uuid) {
            // Myo Device Information
            val data = characteristic.value
            if (data != null && data.isNotEmpty()) {
                val byteReader = ByteReader()
                byteReader.byteData = data
                // TODO We might expose these to the public
                val callbackMsg =
                    String.format(
                        "Serial Number     : %02x:%02x:%02x:%02x:%02x:%02x",
                        byteReader.byte, byteReader.byte, byteReader.byte,
                        byteReader.byte, byteReader.byte, byteReader.byte
                    ) +
                        '\n'.toString() + String.format("Unlock            : %d", byteReader.short) +
                        '\n'.toString() + String.format(
                        "Classifier builtin:%d active:%d (have:%d)",
                        byteReader.byte, byteReader.byte, byteReader.byte
                    ) +
                        '\n'.toString() + String.format("Stream Type       : %d", byteReader.byte)
                Log.d(TAG, "MYO info string: $callbackMsg")
            }
        }

        if (readQueue.size > 0)
            gatt.readCharacteristic(readQueue.element())
    }

    override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        Log.d(TAG, "Characteristic changed: ${characteristic.uuid}, value: ${characteristic.value.contentToString()}")

        super.onCharacteristicChanged(gatt, characteristic)

        when {
            characteristic.uuid.toString().endsWith(CHAR_EMG_POSTFIX) -> {
                val emgData = characteristic.value
                byteReader.byteData = emgData

                // We receive 16 bytes of data. Let's cut them in 2 and deliver both of them.
                dataProcessor.onNext(byteReader.getBytes(EMG_ARRAY_SIZE / 2))
                dataProcessor.onNext(byteReader.getBytes(EMG_ARRAY_SIZE / 2))
            }

            characteristic.uuid.toString() == IMU_DATA_CHARACTERISTIC_UUID -> {
                val data = characteristic.value
                val byteBuffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

                val qw = byteBuffer.short.toFloat() / MYOHW_ORIENTATION_SCALE
                val qx = byteBuffer.short.toFloat() / MYOHW_ORIENTATION_SCALE
                val qy = byteBuffer.short.toFloat() / MYOHW_ORIENTATION_SCALE
                val qz = byteBuffer.short.toFloat() / MYOHW_ORIENTATION_SCALE

                val accX = byteBuffer.short.toFloat() / MYOHW_ACCELEROMETER_SCALE
                val accY = byteBuffer.short.toFloat() / MYOHW_ACCELEROMETER_SCALE
                val accZ = byteBuffer.short.toFloat() / MYOHW_ACCELEROMETER_SCALE

                val gyroX = byteBuffer.short.toFloat() / MYOHW_GYROSCOPE_SCALE
                val gyroY = byteBuffer.short.toFloat() / MYOHW_GYROSCOPE_SCALE
                val gyroZ = byteBuffer.short.toFloat() / MYOHW_GYROSCOPE_SCALE

                val imuData = ImuData(
                    floatArrayOf(qw, qx, qy, qz),
                    floatArrayOf(accX, accY, accZ),
                    floatArrayOf(gyroX, gyroY, gyroZ)
                )
                imuDataProcessor.onNext(imuData)
            }

            else -> {
                Log.d("Myo", "Unknown characteristic changed: ${characteristic.uuid}")
            }
        }

        // Finally check if keep alive makes sense.
        val currentTimeMillis = System.currentTimeMillis()
        if (keepAlive && currentTimeMillis > lastKeepAlive + KEEP_ALIVE_INTERVAL_MS) {
            lastKeepAlive = currentTimeMillis
            sendCommand(CommandList.unSleep())
        }
    }

    fun isImuCharacteristicSetUp(): Boolean {
        val imuService = gatt?.getService(UUID.fromString(IMU_DATA_SERVICE_UUID))
        val imuCharacteristic = imuService?.getCharacteristic(UUID.fromString(IMU_DATA_CHARACTERISTIC_UUID))
        return imuCharacteristic != null &&
                gatt?.setCharacteristicNotification(imuCharacteristic, true) == true
    }
}
