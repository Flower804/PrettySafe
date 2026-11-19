package com.example.bluetooth_sender

import android.content.Context
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject

class ApiManager private constructor(context: Context) {
    private val requestQueue: RequestQueue = Volley.newRequestQueue(
        context.applicationContext
    )
    private val baseUrl = "https://flask-tam-kotlin.vercel.app"

    companion object {
        @Volatile
        private var INSTANCE: ApiManager? = null
        fun getInstance(context: Context): ApiManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ApiManager(context).also { INSTANCE = it }
            }

    }

    /**
     * Rota de Autenticação (POST /login)
     */
    fun Login(
        userRequest: Login,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val url = "$baseUrl/login"

        // Centraliza a construção do corpo JSON (payload) dentro do Manager
        val body = JSONObject().apply {
            put("username", userRequest.username)
            put("password", userRequest.password)
        }

        val request = JsonObjectRequest(
            Request.Method.POST, url, body, { json ->
                // Devolve a resposta JSON em formato String através do callback de sucesso
                onSuccess(json.toString())
                2
            },
            { error ->
                onError(error.message ?: "Erro ao fazer login")
            }
        )
        requestQueue.add(request)

    }

    /*
    Rota de consulta de Dados (GET /data)
     */

    fun getReadings(
        token: String,
        onSuccess: (List<Reading>) -> Unit,
        onError: (String) -> Unit
                ){
            val url = "$baseUrl/data"

        val request = object : JsonArrayRequest (
            Method.GET, url, null,
            { jsonArray ->
                try {
                    val listaReadings = Json.decodeFromString<List<Reading>>(
                        jsonArray.toString()
                    )
                    onSuccess(listaReadings)
                } catch (e: Exception) {
                    onError("Erro ao processar a API: ${e.message}")
                }
            },
            { error ->
                val statusCode = error.networkResponse?.statusCode
                if (statusCode == 401) {
                    onError("Token expirado ou invalido (Nao autorizado)")
                } else {
                    onError(error.message ?: "Erro desconhecido na rede")
                }
            }
        ) {
            override fun getHeaders() : MutableMap<String, String> {
                val headers = HashMap<String, String>()
                headers["Authorization"] = "Bearer $token"
                headers["Content-Type"] = "application/json"
                return headers
            }
        }
        requestQueue.add(request)
        }
    }