package com.example.bluetooth_sender


import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

//PASTA FEITA PARA O LOGIN NAO SAIR QUANDO FECHA A APP, BLOQUEIA O TELEMOVEL, DESLIGA E VOLTA A LIGAR O TELEMOVEL, PASSAM DIAS
//O LOGIN SO SAI QUANDO CARREGO EM TERMINAR SESSAO, DESINSTALO O APP E O TOKE EXPIRA (VALIDADE DE 1 ANO)

//Ficha 1 do Professor
// Guarda o token (e o nome/email para mostrar no ecrã).
// NÃO guardamos a password: o token chega para provar quem somos.

class SecureStorageManager(context: Context) {

    // 1. A "chave mestra": criada pelo Android e guardada no hardware seguro do telemóvel
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    // 2. O ficheiro encriptado onde os dados ficam guardados
    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "pretty_safe_seguro",   // nome do ficheiro (interno)
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,     // encripta as chaves
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM    // encripta os valores
    )

    // ----- Token -----
    fun saveToken(token: String) {
        sharedPreferences.edit()
            .putString("TOKEN", token)
            .apply()   // .apply() guarda sem bloquear o ecrã
    }

    fun getToken(): String? {
        return sharedPreferences.getString("TOKEN", null)   // null = não há token guardado
    }

    // ----- Nome (para mostrar "Olá, Ana") -----
    fun saveNome(nome: String) {
        sharedPreferences.edit()
            .putString("NOME", nome)
            .apply()
    }

    fun getNome(): String? {
        return sharedPreferences.getString("NOME", null)
    }

    // ----- Email (para preencher a caixa no login) -----
    fun saveEmail(email: String) {
        sharedPreferences.edit()
            .putString("EMAIL", email)
            .apply()
    }

    fun getEmail(): String? {
        return sharedPreferences.getString("EMAIL", null)
    }

    // ----- Apagar tudo (Terminar sessão / logout) -----
    fun clearCredentials() {
        sharedPreferences.edit().clear().apply()
    }
}
