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
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.UUID

// =====================================================================
// PRETTY SAFE — Ecrã principal
//
// Caminho de um SOS:
//   Pulseira (ESP32) --Bluetooth BLE--> esta app --internet--> API Flask --> PostgreSQL
//
// O que este ecrã faz:
//   1. Vai buscar o token ao cofre (o login é feito no LoginActivity)
//   2. Liga-se à pulseira por Bluetooth (BLE)
//   3. Quando a pulseira manda o comando "1" → cria um alerta SOS na API
//   4. Durante o alerta, envia a posição GPS de X em X segundos
//   5. O botão "Estou bem" termina o alerta
//
// Inspirado nas fichas do professor:
//   - Ficha N1: token guardado no SecureStorageManager (o "cofre")
//   - Ficha N2: uso do ApiManager (Singleton + callbacks onSuccess/onError)
//   - Ficha N3: atualização periódica com Handler + Runnable
// O Bluetooth e o GPS vêm do código original do colega.
// =====================================================================

@SuppressLint("MissingPermission", "SetTextI18n")
class MainActivity : AppCompatActivity() {

    // ---------- Elementos do ecrã (ligados ao layout activity_main.xml) ----------
    private lateinit var tvStatus: TextView            // texto com o estado da app
    private lateinit var btnConnect: Button            // botão "ligar à pulseira"
    private lateinit var btnLedOn: Button              // liga o LED do ESP32
    private lateinit var btnLedOff: Button             // desliga o LED do ESP32
    private lateinit var tvLocalizacao: TextView       // mostra a latitude/longitude
    private lateinit var btnObterLocalizacao: Button   // passa a ser o botão "Estou bem"

    // Cliente do GPS (serviço de localização da Google)
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // ---------- Bluetooth (código do colega) ----------
    private var bluetoothAdapter: BluetoothAdapter? = null                // o Bluetooth do telemóvel
    private var bluetoothGatt: BluetoothGatt? = null                      // a ligação à pulseira
    private var ledCharacteristic: BluetoothGattCharacteristic? = null    // "canal" para mandar comandos ao LED

    // Identificadores (UUID) do serviço e dos "canais" do ESP32.
    // Têm de ser IGUAIS aos que estão programados no ESP32.
    private val SERVICE_UUID = UUID.fromString("19b10000-e8f2-537e-4f6c-d104768a1214")
    private val CHAR_UUID = UUID.fromString("19b10001-e8f2-537e-4f6c-d104768a1214")     // app → ESP32 (LED)
    private val MESSAGE_UUID = UUID.fromString("19b10002-e8f2-537e-4f6c-d104768a1214")  // ESP32 → app (botão)
    private var receivedCommand: Int = 0   // último comando recebido da pulseira

    // ---------- NOVO: Ligação à API ----------
    private lateinit var api: ApiManager       // o "carteiro" (preenchido no onCreate)
    private var token: String? = null          // vem do cofre (guardado pelo LoginActivity)
    private var idAlertaAtivo: Int? = null     // número do alerta a decorrer (null = não há SOS ativo)

    // Id desta pulseira na base de dados (tabela pulseiras).
    // Tem de estar associada ao utilizador que faz login (passo 7 do testes.http)!
    private val ID_PULSEIRA = "PS-ESP32-002"

    // De quanto em quanto tempo se envia o GPS durante um alerta
    private val INTERVALO_GPS = 15000L   // 15000 milissegundos = 15 segundos


    // =====================================================================
    // onCreate — corre UMA vez, quando o ecrã é aberto
    // =====================================================================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // NOVO: ir buscar o token guardado no cofre (Ficha N1)
        val cofre = SecureStorageManager(this)
        token = cofre.getToken()

        // Segurança: se não houver token, volta para o Login
        if (token == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)   // "desenha" o ecrã a partir do XML

        // Ligar as variáveis aos elementos do layout (pelo id de cada um)
        tvStatus = findViewById(R.id.txtEstado)
        btnConnect = findViewById(R.id.btnConnect)
        btnLedOn = findViewById(R.id.btnLedOn)
        btnLedOff = findViewById(R.id.btnLedOff)
        tvLocalizacao = findViewById(R.id.tvLocalizacao)
        btnObterLocalizacao = findViewById(R.id.btnObterLocalizacao)

        // Ir buscar o Bluetooth do telemóvel
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter

        // Pedir as permissões (Bluetooth + localização). Só aparece na 1.ª vez.
        pedirPermissoes()

        // Iniciar o cliente de localização (GPS)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // O que cada botão faz quando é carregado
        btnConnect.setOnClickListener { iniciarProcuraBLE() }
        // TESTE NO EMULADOR: o botão LED ON finge ser o botão da pulseira
        btnLedOn.setOnClickListener { iniciarSOS() }
        // btnLedOn.setOnClickListener { enviarComandoLED(1) }   // ← REPOR com a pulseira verdadeira!
        btnLedOff.setOnClickListener { enviarComandoLED(0) }

        // NOVO: o GPS já NÃO começa aqui (antes corria sempre e gastava bateria).
        // Só começa quando chega um SOS (ver iniciarSOS).

        // NOVO: este botão passa a ser o "Estou bem"
        btnObterLocalizacao.text = "Estou bem"
        btnObterLocalizacao.setOnClickListener { terminarSOS() }

        // NOVO: ir buscar O carteiro (Singleton da Ficha N2).
        // O login já foi feito no LoginActivity: o token veio do cofre.
        api = ApiManager.getInstance(this)
        tvStatus.text = "Estado: Olá, ${cofre.getNome() ?: ""}!"
    }


    // =====================================================================
    // PERMISSÕES
    // Localização e Bluetooth são permissões "perigosas": não chega estarem no
    // manifest, o utilizador tem de as aceitar numa janela (só na 1.ª vez).
    // =====================================================================

    // Recebe a resposta do utilizador (Permitir / Não permitir)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ){ permissions ->
        // NOVO: a localização é verificada em TODOS os casos (o GPS precisa sempre dela)
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 ou mais recente
            val scanGranted = permissions[Manifest.permission.BLUETOOTH_SCAN] ?: false
            val connectGranted = permissions[Manifest.permission.BLUETOOTH_CONNECT] ?: false

            // && = "e": só está tudo bem se as 3 forem aceites
            if (scanGranted && connectGranted && locationGranted) {
                tvStatus.text = "Estado: Permissões concedidas"
            } else {
                tvStatus.text = "Estado: Faltam permissões (Bluetooth ou localização)"
            }
        } else {
            // Android 11 ou mais antigo: o Bluetooth usa a permissão de localização
            if(locationGranted){
                tvStatus.text = "Estado: Permissão de localização concedida"
            } else {
                tvStatus.text = "Estado: Permissão de localização negada"
            }
        }
    }

    // Mostra a janela a pedir as permissões
    private fun pedirPermissoes() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+: o Bluetooth tem permissões próprias
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                // NOVO: sem isto o GPS não funcionava no Android 12 ou mais recente
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            // Android 11 ou mais antigo: a localização serve para o Bluetooth e o GPS
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        requestPermissionLauncher.launch(permissions)
    }


    // =====================================================================
    // BLUETOOTH: procurar a pulseira (código do colega)
    // =====================================================================
    @SuppressLint("MissingPermission")
    private fun iniciarProcuraBLE(){
        // Fechar alguma ligação antiga antes de procurar outra vez
        bluetoothGatt?.close()
        bluetoothGatt = null
        ledCharacteristic = null

        if(bluetoothAdapter == null || bluetoothAdapter?.isEnabled == false){
            tvStatus.text = "Estado: Bluetooth desativado"
            return
        }

        val scanner = bluetoothAdapter?.bluetoothLeScanner

        if(scanner == null){
            tvStatus.text = "Estado: Scanner não disponível"
            return
        }

        tvStatus.text = "Estado: A procurar.."

        try{
            // Procurar dispositivos BLE o mais depressa possível
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            // Cada dispositivo encontrado vai parar ao scanCallback (mais abaixo)
            scanner.startScan(null, settings, scanCallback)
            Log.d("PrettySafe", "BLE scan started")
        } catch (e: SecurityException){
            tvStatus.text = "Estado: Permissão negada"
        }
    }


    // =====================================================================
    // NOVO: SOS — chamado quando a pulseira envia o comando 1 por Bluetooth
    // =====================================================================
    private fun iniciarSOS() {
        // Sem login não há token, e sem token a API recusa o pedido (401)
        val tokenAtual = token
        if (tokenAtual == null) {
            tvStatus.text = "Estado: SOS recebido, mas ainda sem login!"
            return
        }

        tvStatus.text = "Estado: SOS! A enviar alerta..."

        // 1. Ir buscar a posição ao GPS
        obterGPS { lat, lon ->
            // 2. Quando tiver a posição (ou null, se o GPS falhar), criar o alerta na API
            api.criarAlerta(
                tokenAtual, ID_PULSEIRA, lat, lon,
                onSuccess = { alerta ->
                    // A API criou o alerta e devolveu o número dele
                    idAlertaAtivo = alerta.idAlerta
                    tvStatus.text = "Estado: ALERTA #${alerta.idAlerta} ATIVO"
                    Log.d("PrettySafe", "ALERTA CRIADO: ${alerta.idAlerta}")

                    // 3. Começar a enviar o GPS de X em X segundos (Handler da Ficha N3).
                    //    O removeCallbacks evita ter dois temporizadores ao mesmo tempo.
                    handler.removeCallbacks(locationRunnable)
                    handler.postDelayed(locationRunnable, INTERVALO_GPS)
                },
                onError = { erro ->
                    tvStatus.text = "Estado: Erro ao enviar SOS: $erro"
                    Log.e("PrettySafe", "SOS ERRO: $erro")
                }
            )
        }
    }

    // =====================================================================
    // NOVO: "ESTOU BEM" — termina o alerta na API e pára o envio do GPS
    // =====================================================================
    private fun terminarSOS() {
        val tokenAtual = token
        val idAlerta = idAlertaAtivo

        // Só faz sentido se houver login E um alerta a decorrer
        if (tokenAtual == null || idAlerta == null) {
            Toast.makeText(this, "Não há nenhum alerta ativo", Toast.LENGTH_SHORT).show()
            return
        }

        // PATCH /api/alertas/<id> com {"estado": "terminado"}
        api.terminarAlerta(
            tokenAtual, idAlerta,
            onSuccess = {
                handler.removeCallbacks(locationRunnable)   // desliga o temporizador do GPS
                idAlertaAtivo = null                        // já não há alerta ativo
                tvStatus.text = "Estado: Alerta terminado. Ainda bem!"
            },
            onError = { erro ->
                // Se falhar, o GPS continua a ser enviado (é mais seguro assim)
                tvStatus.text = "Estado: Erro ao terminar alerta: $erro"
            }
        )
    }


    // =====================================================================
    // TEMPORIZADOR DO GPS (Handler + Runnable, como na Ficha N3)
    // Diferença em relação à ficha: o professor liga/desliga no onResume/onPause;
    // aqui liga/desliga com o alerta (iniciarSOS / terminarSOS), porque o GPS
    // tem de continuar a ser enviado mesmo que a pessoa saia do ecrã.
    // =====================================================================
    private val handler = Handler(Looper.getMainLooper())

    private val locationRunnable = object:Runnable {
        override fun run() {
            val tokenAtual = token
            val idAlerta = idAlertaAtivo

            // Se o alerta já acabou, pára (não se volta a agendar)
            if (tokenAtual == null || idAlerta == null) return

            // Ler o GPS e enviar o ponto à API (POST /api/alertas/<id>/localizacoes)
            obterGPS { lat, lon ->
                if (lat != null && lon != null) {
                    api.adicionarLocalizacao(
                        tokenAtual, idAlerta, lat, lon,
                        onSuccess = { Log.d("PrettySafe", "LOCALIZACAO ENVIADA: $lat, $lon") },
                        onError = { erro -> Log.e("PrettySafe", "LOCALIZACAO ERRO: $erro") }
                    )
                }
            }

            // Voltar a correr daqui a INTERVALO_GPS milissegundos
            handler.postDelayed(this, INTERVALO_GPS)
        }
    }


    // =====================================================================
    // NOVO: GPS — lê a posição e "entrega-a" através de um callback
    // (o GPS demora, por isso não dá para fazer "return" — igual à API).
    // Se o GPS falhar, entrega null — e o SOS é enviado na mesma, sem posição.
    // =====================================================================
    @SuppressLint("MissingPermission")
    private fun obterGPS(quandoTiver: (Double?, Double?) -> Unit) {
        tvLocalizacao.text = "A procurar sinal de GPS..."

        val priority = Priority.PRIORITY_HIGH_ACCURACY   // a posição mais precisa possível
        val cancellationTokenSource = CancellationTokenSource()

        try {
            fusedLocationClient.getCurrentLocation(priority, cancellationTokenSource.token)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        tvLocalizacao.text = "Latitude: ${location.latitude} \nLongitude: ${location.longitude}"
                        quandoTiver(location.latitude, location.longitude)   // entrega a posição
                    } else {
                        tvLocalizacao.text = "Localizacao indisponivel"
                        quandoTiver(null, null)                             // entrega "sem posição"
                    }
                }
                .addOnFailureListener { e ->
                    tvLocalizacao.text = "Erro GPS: ${e.message}"
                    quandoTiver(null, null)
                }
        } catch (e: SecurityException) {
            // Acontece se o utilizador não deu permissão de localização
            tvLocalizacao.text = "Sem permissão de localização"
            quandoTiver(null, null)
        }
    }


    // =====================================================================
    // BLUETOOTH: resultado da procura (código do colega)
    // =====================================================================
    private val scanCallback = object : ScanCallback() {
        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            Log.d("PrettySafe", "BATCH RESULTS: ${results.size}")

            for (result in results) {
                Log.d("PrettySafe", "BATCH DEVICE: ${result.device.address}")
            }
        }

        // Chamado para cada dispositivo Bluetooth encontrado
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

            // É a NOSSA pulseira? (endereço MAC do ESP32)
            if (address == "7C:87:CE:2D:3F:46" && bluetoothGatt == null) {

                // Encontrada: parar de procurar e ligar-se a ela
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(this)

                runOnUiThread {
                    tvStatus.text = "Estado: ESP32 encontrado!"
                }

                Log.d("PrettySafe", "ESP32 FOUND! Connecting...")

                // O que acontece depois vai parar ao gattCallback (mais abaixo)
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
                tvStatus.text = "Estado: Erro de procura ($errorCode)"
            }
        }
    }


    // =====================================================================
    // BLUETOOTH: eventos da ligação à pulseira (código do colega)
    // Nota: estas funções correm "fora" do ecrã, por isso para mexer nos
    // textos usa-se runOnUiThread { ... }.
    // =====================================================================
    private val gattCallback = object : BluetoothGattCallback() {

        // Ligou-se ou desligou-se da pulseira
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                runOnUiThread {
                    tvStatus.text = "Estado: Conectado"
                }
                gatt?.discoverServices()   // descobrir os "canais" da pulseira
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                runOnUiThread {
                    tvStatus.text = "Estado: Desconectado"
                    gatt?.close()
                    bluetoothGatt = null
                }
            }
        }

        // A pulseira enviou uma mensagem (Android 13 ou mais recente)
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == MESSAGE_UUID) {
                // Converter os bytes em texto e depois em número (ex.: "1" → 1)
                receivedCommand = value
                    .toString(Charsets.UTF_8)
                    .trim()                // NOVO: tira espaços/quebras de linha
                    .toIntOrNull() ?: 0    // se não for número, fica 0

                commandProcesser(receivedCommand)
            }
        }

        // NOVO: a mesma coisa, para Android 12 ou mais antigo (versão sem o "value").
        // Sem ela, nesses telemóveis os comandos do ESP32 nunca chegavam.
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                @Suppress("DEPRECATION")
                val value = characteristic.value ?: return
                onCharacteristicChanged(gatt, characteristic, value)
            }
        }

        // Decide o que fazer com cada comando da pulseira
        fun commandProcesser(command: Int){
            runOnUiThread {
                if(command == 1){
                    tvStatus.text = "Estado: Comando 1 (SOS) recebido"
                    iniciarSOS()   // NOVO: o botão da pulseira dispara o SOS 🚨
                } else if(command == 2){
                    tvStatus.text = "Estado: Comando 2 alternativo recebido"
                } else{
                    tvStatus.text = "Estado: comando nao reconhecido recebido"
                }
            }
        }

        // Descobriu os "canais" da pulseira: ativar as notificações do botão
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt?.getService(SERVICE_UUID)
                val messageCharacteristic = service?.getCharacteristic(MESSAGE_UUID)

                if(messageCharacteristic != null){
                    // "Avisa-me sempre que o ESP32 mandar alguma coisa neste canal"
                    gatt?.setCharacteristicNotification(
                        messageCharacteristic,
                        true
                    )

                    // Descritor padrão do Bluetooth para ligar as notificações
                    val descriptor = messageCharacteristic.getDescriptor(
                        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                    )

                    descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE

                    gatt?.writeDescriptor(descriptor)
                }

                // Guardar o canal do LED para o enviarComandoLED
                ledCharacteristic = service?.getCharacteristic(CHAR_UUID)
                runOnUiThread {
                    tvStatus.text = "Estado: Pronto a enviar comando"
                }
            }
        }
    }


    // =====================================================================
    // BLUETOOTH: enviar um comando ao LED do ESP32 (código do colega)
    // =====================================================================
    private fun enviarComandoLED(valor: Byte) {
        val caract = ledCharacteristic ?: return   // ainda não está ligado → não faz nada
        val gatt = bluetoothGatt ?: return
        val bytes = byteArrayOf(valor)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13 ou mais recente
            gatt.writeCharacteristic(caract, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            // Android 12 ou mais antigo
            @Suppress("DEPRECATION")
            caract.value = bytes
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(caract)
        }
        tvStatus.text = "Estado: comando $valor enviado"
    }


    // =====================================================================
    // onDestroy — corre quando o ecrã é fechado de vez: arrumar tudo
    // =====================================================================
    override fun onDestroy() {
        super.onDestroy()
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null

        handler.removeCallbacks(locationRunnable)   // parar o temporizador do GPS
    }
}