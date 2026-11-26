package com.example.bluetooth_sender

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

// =====================================================================
// ECRÃ DE LOGIN (inspirado na Ficha N1, ponto 3)
// É o PRIMEIRO ecrã da app.
// Se já houver token guardado no cofre, salta direto para o ecrã do SOS.
// =====================================================================
class LoginActivity : AppCompatActivity() {

    // Elementos do ecrã (ligados ao activity_login.xml pelos id)
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var tvErro: TextView
    private lateinit var btnEntrar: Button
    private lateinit var tvCriarConta: TextView

    private lateinit var api: ApiManager             // o carteiro (Ficha N2)
    private lateinit var cofre: SecureStorageManager // o cofre (Ficha N1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cofre = SecureStorageManager(this)

        // Já há sessão guardada? Então nem mostra o login: vai direto para o SOS.
        // (Resposta à pergunta 2 da Ficha N1: o token vem do cofre, não do Intent)
        if (cofre.getToken() != null) {
            irParaSOS()
            return
        }

        setContentView(R.layout.activity_login)

        // Ligar as variáveis aos elementos do layout
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        tvErro = findViewById(R.id.tvErro)
        btnEntrar = findViewById(R.id.btnEntrar)
        tvCriarConta = findViewById(R.id.tvCriarConta)

        api = ApiManager.getInstance(this)

        // Se já houve login antes, preenche o email automaticamente
        etEmail.setText(cofre.getEmail() ?: "")

        // O que cada botão faz
        btnEntrar.setOnClickListener { fazerLogin() }
        // "Criar conta" → abre o ecrã de Registo
        tvCriarConta.setOnClickListener {
            startActivity(Intent(this, RegistoActivity::class.java))
        }
    }

    private fun fazerLogin() {
        // Ler o que a pessoa escreveu (o trim tira espaços a mais)
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()

        // Verificar antes de enviar: não vale a pena chamar a API com caixas vazias
        if (email.isEmpty() || password.isEmpty()) {
            mostrarErro("Preenche o email e a password.")
            return
        }

        // Esconder o erro antigo e "bloquear" o botão enquanto espera pela API
        tvErro.visibility = View.GONE
        btnEntrar.isEnabled = false
        btnEntrar.text = "A entrar..."

        // Pedir ao carteiro para fazer login (POST /api/login)
        api.login(
            Login(email, password),
            onSuccess = { resposta ->
                // Guardar tudo no cofre: na próxima vez que a app abrir, já não pede login
                cofre.saveToken(resposta.token)
                cofre.saveNome(resposta.nome)
                cofre.saveEmail(resposta.email)

                irParaSOS()
            },
            onError = { erro ->
                mostrarErro(erro)
                btnEntrar.isEnabled = true
                btnEntrar.text = "Entrar"
            }
        )
    }

    // Mostra a mensagem a vermelho por baixo da password
    private fun mostrarErro(mensagem: String) {
        tvErro.text = mensagem
        tvErro.visibility = View.VISIBLE
    }

    // Abre o ecrã do SOS e fecha o login (para o botão "voltar" não regressar aqui)
    private fun irParaSOS() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}