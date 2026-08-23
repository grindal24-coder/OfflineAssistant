package com.offlineassistant.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.offlineassistant.wakeword.WakeWordService

/**
 * Экран запуска: запрашивает необходимые рантайм-разрешения (п.13 ТЗ) и
 * запускает WakeWordService (п.9 ТЗ) в качестве foreground-сервиса.
 *
 * TODO: полноценный UI — список контактов-алиасов, статус загруженных
 * моделей (какая GGUF найдена в ai/models/), лог последних команд.
 */
class MainActivity : AppCompatActivity() {

    private val requiredPermissions = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.CALL_PHONE)
        add(Manifest.permission.READ_CONTACTS)
        add(Manifest.permission.SEND_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            startWakeWordService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.offlineassistant.R.layout.activity_main)
        permissionLauncher.launch(requiredPermissions)
    }

    private fun startWakeWordService() {
        val intent = Intent(this, WakeWordService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
