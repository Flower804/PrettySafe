package com.example.bluetooth_sender

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

// =====================================================================
// ECRÃ DE REGISTO
// Cria a conta (POST /api/registo) e, se correr bem, faz login logo a
// seguir (POST /api/login), guarda o token no cofre e vai para o SOS.
// =====================================================================
class RegistoActivity : AppCompatActivity() {

    private lateinit var etNome: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirmar: EditText
    private lateinit var tvErro: TextView
    private lateinit var btnCriarConta: Button

    private lateinit var api: ApiManager             // o carteiro (Ficha N2)
    private lateinit var cofre: SecureStorageManager // o cofre (Ficha N1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registo)

        etNome = findViewById(R.id.etNome)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        etConfirmar = findViewById(R.id.etConfirmar)
        tvErro = findViewById(R.id.tvErro)
        btnCriarConta = findViewById(R.id.btnCriarConta)

        api = ApiManager.getInstance(this)
        cofre = SecureStorageManager(this)

        // "← Voltar": fecha este ecrã e volta ao Login
        findViewById<TextView>(R.id.tvVoltar).setOnClickListener { finish() }

        btnCriarConta.setOnClickListener { criarConta() }
    }

    private fun criarConta() {
        val nome = etNome.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()
        val confirmar = etConfirmar.text.toString()

        // ----- Verificações ANTES de chamar a API -----
        if (nome.isEmpty() || email.isEmpty() || password.isEmpty() || confirmar.isEmpty()) {
            mostrarErro("Preenche todos os campos.")
            return
        }
        if (!email.contains("@")) {
            mostrarErro("O email não parece válido.")
            return
        }
        if (password.length < 6) {
            mostrarErro("A password tem de ter pelo menos 6 caracteres.")
            return
        }
        if (password != confirmar) {
            mostrarErro("As passwords não são iguais.")
            return
        }

        tvErro.visibility = View.GONE
        btnCriarConta.isEnabled = false
        btnCriarConta.text = "A criar conta..."

        // 1. Criar a conta (POST /api/registo)
        api.registo(
            nome, email, password,
            onSuccess = {
                // 2. Conta criada! Fazer login logo a seguir, para receber o token
                fazerLoginAutomatico(email, password)
            },
            onError = { erro ->
                mostrarErro(erro)
                reativarBotao()
            }
        )
    }

    private fun fazerLoginAutomatico(email: String, password: String) {
        api.login(
            Login(email, password),
            onSuccess = { resposta ->
                // 3. Guardar no cofre (igual ao LoginActivity)
                cofre.saveToken(resposta.token)
                cofre.saveNome(resposta.nome)
                cofre.saveEmail(resposta.email)

                // 4. Ir para o SOS e "limpar" os ecrãs de trás
                //    (para o botão "voltar" não regressar ao registo nem ao login)
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            },
            onError = { erro ->
                mostrarErro("Conta criada! Mas não foi possível entrar: $erro")
                reativarBotao()
            }
        )
    }

    private fun mostrarErro(mensagem: String) {
        tvErro.text = mensagem
        tvErro.visibility = View.VISIBLE
    }

    private fun reativarBotao() {
        btnCriarConta.isEnabled = true
        btnCriarConta.text = "Criar conta"
    }
}