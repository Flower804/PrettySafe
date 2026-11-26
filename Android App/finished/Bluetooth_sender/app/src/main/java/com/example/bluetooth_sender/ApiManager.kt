package com.example.bluetooth_sender

import android.content.Context
import com.android.volley.RequestQueue
import com.android.volley.toolbox.Volley

import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import kotlinx.serialization.json.Json
import org.json.JSONObject

//1.1. Criar o ficheiro ApiManager.kt e implementar a estrutura Singleton e a fila de
//requisições:
class ApiManager private constructor (context:Context) {
    private val requestQueue: RequestQueue = Volley.newRequestQueue(context.applicationContext)
   // private val baseUrl = "http://192.168.1.131:5000"
    private val baseUrl = "http://10.0.2.2:5000"   // 10.0.2.2 = o PC, visto do emulador
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        @Volatile
        private var INSTANCE: ApiManager? = null
        fun getInstance(context: Context): ApiManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ApiManager(context).also { INSTANCE = it }
            }
    }


    //2.1. Adicionar à classe ApiManager o método Login para submissão de credenciais via
    //POST:

    fun login(
        dados: Login,
        onSuccess: (LoginResponse) -> Unit,
        onError: (String) -> Unit
    ) {
        val url = "$baseUrl/api/login"

        val body = JSONObject().apply {
            put("email", dados.email)
            put("password", dados.password)
        }

        val request = JsonObjectRequest(
            Request.Method.POST, url, body,
            { resposta ->
                val utilizador = json.decodeFromString<LoginResponse>(resposta.toString())
                onSuccess(utilizador)
            },
            { error ->
                when (error.networkResponse?.statusCode) {
                    401 -> onError("Email ou password incorretos.")
                    400 -> onError("Preenche o email e a password.")
                    null -> onError("Sem ligação à API.")
                    else -> onError("Erro ao fazer login.")
                }
            }
        )

        requestQueue.add(request)
    }


    ///3.1. Adicionar à classe ApiManager o método getReadings e fechar a classe:
    fun getAlertas(
        token: String,
        onSuccess: (List<Alerta>) -> Unit,
        onError: (String) -> Unit
    ) {
        val url = "$baseUrl/api/alertas"

        // JsonObjectRequest porque a nossa API devolve {"alertas": [...]}
        val request = object : JsonObjectRequest(
            Request.Method.GET, url, null,
            { resposta ->
                try {
                    val jsonArray = resposta.getJSONArray("alertas")
                    val listaAlertas = json.decodeFromString<List<Alerta>>(jsonArray.toString())
                    onSuccess(listaAlertas)
                } catch (e: Exception) {
                    onError("Erro ao processar dados da API: ${e.message}")
                }
            },
            { error ->
                val statusCode = error.networkResponse?.statusCode
                if (statusCode == 401) {
                    onError("Token expirado ou inválido (Não autorizado)")
                } else {
                    onError(error.message ?: "Erro desconhecido na rede")
                }
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                headers["Authorization"] = "Bearer $token"
                headers["Content-Type"] = "application/json"
                return headers
            }
        }

        requestQueue.add(request)
    }

    // POST /api/registo (sem token: ainda não há conta!)
    fun registo(
        nome: String,
        email: String,
        password: String,
        onSuccess: (Utilizador) -> Unit,
        onError: (String) -> Unit
    ) {
        val url = "$baseUrl/api/registo"

        val body = JSONObject().apply {
            put("nome", nome)
            put("email", email)
            put("password", password)
        }

        val request = JsonObjectRequest(
            Request.Method.POST, url, body,
            { resposta ->
                try {
                    val utilizador = json.decodeFromString<Utilizador>(resposta.toString())
                    onSuccess(utilizador)
                } catch (e: Exception) {
                    onError("Erro ao processar dados da API: ${e.message}")
                }
            },
            { error ->
                // Mensagens diferentes conforme o código que a API devolve
                when (error.networkResponse?.statusCode) {
                    409 -> onError("Já existe uma conta com esse email.")
                    400 -> onError("Verifica os dados: nome, email e password.")
                    null -> onError("Sem ligação à API.")
                    else -> onError("Erro ao criar conta.")
                }
            }
        )

        requestQueue.add(request)
    }


    // Exercício complementar 2 (POST com token): criar alerta SOS
    fun criarAlerta(
        token: String,
        idPulseira: String,
        latitude: Double?,
        longitude: Double?,
        onSuccess: (Alerta) -> Unit,
        onError: (String) -> Unit
    ) {
        val url = "$baseUrl/api/alertas"

        val body = JSONObject().apply {
            put("id_pulseira", idPulseira)
            if (latitude != null && longitude != null) {
                put("latitude", latitude)
                put("longitude", longitude)
            }
        }

        val request = object : JsonObjectRequest(
            Request.Method.POST, url, body,
            { resposta ->
                try {
                    val alerta = json.decodeFromString<Alerta>(resposta.toString())
                    onSuccess(alerta)
                } catch (e: Exception) {
                    onError("Erro ao processar dados da API: ${e.message}")
                }
            },
            { error ->
                onError(error.message ?: "Erro ao enviar SOS")
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                headers["Authorization"] = "Bearer $token"
                return headers
            }
        }

        requestQueue.add(request)
    }


    // POST com token: adicionar um ponto GPS ao percurso do alerta
    // (do lado da API é o adicionar_localizacao do index.py)
    fun adicionarLocalizacao(
        token: String,
        idAlerta: Int,
        latitude: Double,
        longitude: Double,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val url = "$baseUrl/api/alertas/$idAlerta/localizacoes"

        val body = JSONObject().apply {
            put("latitude", latitude)
            put("longitude", longitude)
        }

        val request = object : JsonObjectRequest(
            Request.Method.POST, url, body,
            { resposta ->
                onSuccess()
            },
            { error ->
                onError(error.message ?: "Erro ao enviar localizacao")
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                headers["Authorization"] = "Bearer $token"
                return headers
            }
        }

        requestQueue.add(request)
    }


    // PATCH com token: "Estou bem" (o alerta passa a 'terminado')
    // (do lado da API é o finalizar_alerta do index.py)
    fun terminarAlerta(
        token: String,
        idAlerta: Int,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val url = "$baseUrl/api/alertas/$idAlerta"

        val body = JSONObject().apply {
            put("estado", "terminado")
        }

        val request = object : JsonObjectRequest(
            Request.Method.PATCH, url, body,
            { resposta ->
                onSuccess()
            },
            { error ->
                onError(error.message ?: "Erro ao terminar alerta")
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                headers["Authorization"] = "Bearer $token"
                return headers
            }
        }

        requestQueue.add(request)
    }
}





