package com.example.bluetooth_sender

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.util.UUID

import android.os.ParcelUuid
import android.bluetooth.le.ScanSettings
import android.util.Log

@SuppressLint("MissingPermission", "SetTextI18n")
class MainActivity : AppCompatActivity() {
    private lateinit var tvStatus: TextView
    private lateinit var btnConnect: Button
    private lateinit var btnLedOn: Button
    private lateinit var btnLedOff: Button

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var ledCharacteristic: BluetoothGattCharacteristic? = null

    private val SERVICE_UUID = UUID.fromString("19b10000-e8f2-537e-4f6c-d104768a1214")
    private val CHAR_UUID = UUID.fromString("19b10001-e8f2-537e-4f6c-d104768a1214")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.txtEstado)
        btnConnect = findViewById(R.id.btnConnect)
        btnLedOn = findViewById(R.id.btnLedOn)
        btnLedOff = findViewById(R.id.btnLedOff)

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter

        pedirPermissoesBLE()

        btnConnect.setOnClickListener { iniciarProcuraBLE() }
        btnLedOn.setOnClickListener { enviarComandoLED(1) }
        btnLedOff.setOnClickListener { enviarComandoLED(0) }
    }

    private fun pedirPermissoesBLE() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this, arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            ), 1)
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            ), 1)
        }
    }

    /*
    private fun iniciarProcuraBLE() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()

        if (bluetoothAdapter == null || bluetoothAdapter?.isEnabled == false) {
            tvStatus.text = "Estado: Bluetooth desativado"
            return
        }

        tvStatus.text = "Estado: A procura..."
        bluetoothAdapter?.bluetoothLeScanner?.startScan(scanCallback)
    }
    */

     @SuppressLint("MissingPermission")
     private fun iniciarProcuraBLE(){
         bluetoothGatt?.close()
         bluetoothGatt = null
         ledCharacteristic = null

         if(bluetoothAdapter == null || bluetoothAdapter?.isEnabled == false){
             tvStatus.text = "Estado: Bluetooth desativate"
             return
         }

         val scanner = bluetoothAdapter?.bluetoothLeScanner

         if(scanner == null){
             tvStatus.text = "Estado: Scanner não disponível"
             return
         }

         tvStatus.text = "Estado: A procurar.."

         try{
             //scanner.startScan(scanCallback)
             val settings = ScanSettings.Builder()
                 .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                 .build()

             scanner.startScan(null, settings, scanCallback)
             Log.d("Pretty Safe", "BLE scan started")
         } catch (e: SecurityException){
             tvStatus.text = "Estado: Permissão negada"
         }
     }


    private val scanCallback = object : ScanCallback() {
        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            Log.d("PrettySafe", "BATCH RESULTS: ${results.size}")

            for (result in results) {
                Log.d("PrettySafe", "BATCH DEVICE: ${result.device.address}")
            }
        }

        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val address = result.device.address

            val deviceName = result.scanRecord?.deviceName
                ?: result.device.name
                ?: "Unknown device"

            val services = result.scanRecord?.serviceUuids

            Log.d(
                "PrettySafe",
                "DEVICE: $deviceName | ADDRESS: $address | SERVICES: $services"
            )

            if (address == "7C:87:CE:2D:3F:46" && bluetoothGatt == null) {

                bluetoothAdapter?.bluetoothLeScanner?.stopScan(this)

                runOnUiThread {
                    tvStatus.text = "Estado: ESP32 encontrado!"
                }

                Log.d("PrettySafe", "ESP32 FOUND! Connecting...")

                bluetoothGatt = result.device.connectGatt(
                    this@MainActivity,
                    false,
                    gattCallback
                )
            }u
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("PrettySafe", "SCAN FAILED: $errorCode")

            runOnUiThread {
                tvStatus.text = "Estado: Erro de procura ($errorCode)"
            }
        }
    }


    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                runOnUiThread {
                    tvStatus.text = "Estado: Conectado"
                }
                gatt?.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                runOnUiThread {
                    tvStatus.text = "Estado: Desconectado"
                    gatt?.close()
                    bluetoothGatt = null
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt?.getService(SERVICE_UUID)
                ledCharacteristic = service?.getCharacteristic(CHAR_UUID)
                runOnUiThread {
                    tvStatus.text = "Estado: Pronto a enviar comando"
                }
            }
        }
    }

    private fun enviarComandoLED(valor: Byte) {
        val caract = ledCharacteristic ?: return
        val gatt = bluetoothGatt ?: return
        val bytes = byteArrayOf(valor)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(caract, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION")
            caract.value = bytes
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(caract)
        }
        tvStatus.text = "Estado: comando $valor enviado"
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
    }
}
