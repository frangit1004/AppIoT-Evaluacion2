package com.example.appiot

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import java.util.UUID

class MainActivity : AppCompatActivity() {

    // Variables de Firebase
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    // Variables de la Interfaz
    private lateinit var editTextCorreo: EditText
    private lateinit var editTextPassword: EditText
    private lateinit var textViewEstado: TextView
    private lateinit var textViewDato: TextView
    private lateinit var textViewMensaje: TextView

    // Variables de Permisos y Bluetooth
    private var permisosOk = false
    private val permisos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private val lanzador = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { resultados ->
        permisosOk = resultados.values.all { it }
    }

    private val adapter: BluetoothAdapter by lazy {
        (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var socketActual: BluetoothSocket? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializar Firebase
        auth = Firebase.auth
        database = Firebase.database.reference

        // Conectar código con la pantalla
        editTextCorreo = findViewById(R.id.editTextCorreo)
        editTextPassword = findViewById(R.id.editTextPassword)
        textViewEstado = findViewById(R.id.textViewEstado)
        textViewDato = findViewById(R.id.textViewDato)
        textViewMensaje = findViewById(R.id.textViewMensaje)

        // Pedir permisos de Bluetooth al abrir la app
        lanzador.launch(permisos)

        // Botones
        findViewById<Button>(R.id.btnRegistrar).setOnClickListener { registrarUsuario() }
        findViewById<Button>(R.id.btnIniciarSesion).setOnClickListener { iniciarSesion() }

        // Si ya había una sesión iniciada antes
        if (auth.currentUser != null) {
            textViewEstado.text = "Sesión activa: ${auth.currentUser?.email}"
            leerDatosNube()
        }

        findViewById<Button>(R.id.btnServidor).setOnClickListener {
            if (permisosOk) iniciarServidor() else lanzador.launch(permisos)
        }

        findViewById<Button>(R.id.btnEncender).setOnClickListener {
            enviarComando("LED_ON")
            guardarEstadoNube("LED ENCENDIDO")
        }

        findViewById<Button>(R.id.btnApagar).setOnClickListener {
            enviarComando("LED_OFF")
            guardarEstadoNube("LED APAGADO")
        }

        findViewById<Button>(R.id.btnGuardarDato).setOnClickListener {
            guardarEstadoNube("ESTADO MANUAL ACTUALIZADO")
        }
    }

    private fun registrarUsuario() {
        val correo = editTextCorreo.text.toString().trim()
        val pass = editTextPassword.text.toString().trim()
        if (correo.isBlank() || pass.isBlank()) {
            Toast.makeText(this, "Completa los campos", Toast.LENGTH_SHORT).show()
            return
        }
        auth.createUserWithEmailAndPassword(correo, pass).addOnCompleteListener { tarea ->
            if (tarea.isSuccessful) {
                textViewEstado.text = "Registrado: ${auth.currentUser?.email}"
                leerDatosNube()
            } else {
                Toast.makeText(this, "Error: ${tarea.exception?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun iniciarSesion() {
        val correo = editTextCorreo.text.toString().trim()
        val pass = editTextPassword.text.toString().trim()
        if (correo.isBlank() || pass.isBlank()) {
            Toast.makeText(this, "Completa los campos", Toast.LENGTH_SHORT).show()
            return
        }
        auth.signInWithEmailAndPassword(correo, pass).addOnCompleteListener { tarea ->
            if (tarea.isSuccessful) {
                textViewEstado.text = "Sesión iniciada: ${auth.currentUser?.email}"
                leerDatosNube()
            } else {
                textViewEstado.text = "Error al iniciar sesión"
            }
        }
    }

    private fun guardarEstadoNube(estado: String) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Inicia sesión primero", Toast.LENGTH_SHORT).show()
            return
        }
        database.child("usuarios").child(uid).child("dispositivo").setValue(estado)
    }

    private fun leerDatosNube() {
        val uid = auth.currentUser?.uid ?: return
        database.child("usuarios").child(uid).child("dispositivo")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val estado = snapshot.getValue(String::class.java) ?: "Sin estado"
                    textViewDato.text = "Nube: $estado"
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    @SuppressLint("MissingPermission")
    private fun iniciarServidor() {
        if (auth.currentUser == null) {
            Toast.makeText(this, "Inicia sesión primero (Seguridad ISO)", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "Esperando conexión Bluetooth...", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val serverSocket = adapter.listenUsingRfcommWithServiceRecord("AppSimple", uuid)
                val socket = serverSocket.accept()
                socketActual = socket
                runOnUiThread { textViewMensaje.text = "Estado BT: Conectado" }
            } catch (e: Exception) {
                runOnUiThread { textViewMensaje.text = "Error BT: ${e.message}" }
            }
        }.start()
    }

    private fun enviarComando(comando: String) {
        Thread {
            try {
                socketActual?.outputStream?.write(comando.toByteArray())
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Presionado localmente", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }
}