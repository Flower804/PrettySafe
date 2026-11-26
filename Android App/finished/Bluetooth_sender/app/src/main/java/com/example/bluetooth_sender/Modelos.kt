package com.example.bluetooth_sender

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// O que a app ENVIA no login
@Serializable
data class Login(
    @SerialName("email") val email: String,
    @SerialName("password") val password: String
)

// O que a API RESPONDE no login
@Serializable
data class LoginResponse(
    @SerialName("id_utilizador") val idUtilizador: Int,
    @SerialName("nome") val nome: String,
    @SerialName("email") val email: String,
    @SerialName("token") val token: String
)

// O que a API RESPONDE quando se cria um alerta SOS
@Serializable
data class Alerta(
    @SerialName("id_alerta") val idAlerta: Int,
    @SerialName("id_pulseira") val idPulseira: String,
    @SerialName("data_hora") val dataHora: String,
    @SerialName("estado") val estado: String
)

// O que a API RESPONDE no registo (POST /api/registo)
@Serializable
data class Utilizador(
    @SerialName("id_utilizador") val idUtilizador: Int,
    @SerialName("nome") val nome: String,
    @SerialName("email") val email: String
)