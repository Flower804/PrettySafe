package com.example.bluetooth_sender

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
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

import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

import android.os.ParcelUuid
import android.bluetooth.le.ScanSettings
import android.util.Log

import android.os.Handler
import android.os.Looper

@SuppressLint("MissingPermission", "SetTextI18n")
class MainActivity : AppCompatActivity() {
    private lateinit var tvStatus: TextView
    private lateinit var btnConnect: Button
    private lateinit var btnLedOn: Button
    private lateinit var btnLedOff: Button
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var tvLocalizacao: TextView
    private lateinit var btnObterLocalizacao: Button

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var ledCharacteristic: BluetoothGattCharacteristic? = null

    private val SERVICE_UUID = UUID.fromString("19b10000-e8f2-537e-4f6c-d104768a1214")
    private val CHAR_UUID = UUID.fromString("19b10001-e8f2-537e-4f6c-d104768a1214")
    private val MESSAGE_UUID = UUID.fromString("19b10002-e8f2-537e-4f6c-d104768a1214")
    private var receivedCommand: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.txtEstado)
        btnConnect = findViewById(R.id.btnConnect)
        btnLedOn = findViewById(R.id.btnLedOn)
        btnLedOff = findViewById(R.id.btnLedOff)
        tvLocalizacao = findViewById(R.id.tvLocalizacao)
        btnObterLocalizacao = findViewById(R.id.btnObterLocalizacao)

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter

        pedirPermissoes()

        //iniciar o cliente de localizacao
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        btnConnect.setOnClickListener { iniciarProcuraBLE() }
        btnLedOn.setOnClickListener { enviarComandoLED(1) }
        btnLedOff.setOnClickListener { enviarComandoLED(0) }

        handler.post(locationRunnable)

        /*
        btnObterLocalizacao.setOnClickListener {
            lerCoordenadasGPS()
        }
         */
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ){ permissions ->
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scanGranted = permissions[Manifest.permission.BLUETOOTH_SCAN] ?: false
            val connectGranted = permissions[Manifest.permission.BLUETOOTH_CONNECT] ?: false

            if (scanGranted && connectGranted) {
                tvStatus.text = "Estado: Permissões Bluetooth concedidas"
            } else {
                tvStatus.text = "Estado: Permissões Bluetooth negadas"
            }
        } else {
            val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false

            if(locationGranted){
                tvStatus.text = "Estado: Permissão de localização concedida"
            } else {
                tvStatus.text = "Estado: Permissão de localização negada"
            }
        }
    }

    private fun pedirPermissoes() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        requestPermissionLauncher.launch(permissions)
    }

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

    private val handler = Handler(Looper.getMainLooper())

    private val locationRunnable = object:Runnable {
        override fun run() {
            lerCoordenadasGPS()

            handler.postDelayed(this, 5000)
        }
    }

    @SuppressLint("MissingPermission")
    private fun lerCoordenadasGPS() {
        tvLocalizacao.text = "A procurar sinal de GPS..."

        val priority = Priority.PRIORITY_HIGH_ACCURACY
        val cancellationTokenSource = CancellationTokenSource()

        fusedLocationClient.getCurrentLocation(priority, cancellationTokenSource.token).addOnSuccessListener { location ->
            if(location != null){
                val lat = location.latitude
                val lon = location.longitude
                tvLocalizacao.text = "Latitude: $lat \nLongitude: $lon"
            } else {
                tvLocalizacao.text = "Localizacao indisponivel"
            }
        }
            .addOnFailureListener { e ->
                Toast.makeText(this,
                    "Erro: ${e.message}",
                    Toast.LENGTH_SHORT)
                    .show()
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
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("PrettySafe", "SCAN FAILED: $errorCode")

            runOnUiThread {
                tvStatus.text = "Est    ado: Erro de procura ($errorCode)"
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

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == MESSAGE_UUID) {
                receivedCommand = value
                    .toString(Charsets.UTF_8)
                    .toIntOrNull() ?: 0

                commandProcesser(receivedCommand)
            }
        }

        fun commandProcesser(receivedcommand: Int){
            runOnUiThread {
                if(receivedCommand == 1){
                    tvStatus.text = "Estado: Commando 1 recebido"
                } else if(receivedCommand == 2){
                    tvStatus.text = "Estado: Commando 2 alternativo recebido"
                } else{
                    tvStatus.text = "Estado: commando nao reconhecido recebido"
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt?.getService(SERVICE_UUID)
                val messageCharacteristic = service?.getCharacteristic(MESSAGE_UUID)

                if(messageCharacteristic != null){
                    gatt?.setCharacteristicNotification(
                        messageCharacteristic,
                        true
                    )

                    val descriptor = messageCharacteristic.getDescriptor(
                        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                    )

                    descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE

                    gatt?.writeDescriptor(descriptor)
                }

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

        handler.removeCallbacks(locationRunnable)
    }
}
