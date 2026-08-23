package com.offlineassistant.ui

import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.offlineassistant.database.AppDatabase
import com.offlineassistant.database.ContactEntity
import kotlinx.coroutines.launch

/**
 * Экран привязки алиаса к контакту (см. ContactResolver.kt).
 *
 * Открывается, когда CommandResult.needsContactRegistration != null — то есть
 * пользователь сказал "Позвони маме"/"Напиши маме ...", а в Room ещё нет
 * записи "мама -> номер". Показываем системный picker контактов; выбранный
 * контакт сохраняем с этим алиасом, дальше команда резолвится мгновенно.
 *
 * Запуск: передать alias через intent extra EXTRA_ALIAS.
 *
 * TODO: сейчас использует системный ACTION_PICK (открывает стандартный UI
 * контактов Android) — самый надёжный путь без лишнего кода. Можно заменить
 * на кастомный список в стиле ассистента (см. обсуждение UI-концепта в
 * README, раздел "Отложено на будущее"), но это отдельная задача.
 */
class ContactRegistrationActivity : AppCompatActivity() {

    private var pendingAlias: String = ""

    private val pickContactLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val contactUri: Uri? = result.data?.data
        if (result.resultCode != RESULT_OK || contactUri == null) {
            finish()
            return@registerForActivityResult
        }
        saveContactFromUri(contactUri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingAlias = intent.getStringExtra(EXTRA_ALIAS) ?: run {
            finish()
            return
        }
        launchSystemContactPicker()
    }

    private fun launchSystemContactPicker() {
        val intent = android.content.Intent(
            android.content.Intent.ACTION_PICK,
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        )
        pickContactLauncher.launch(intent)
    }

    private fun saveContactFromUri(contactUri: Uri) {
        val cursor: Cursor? = contentResolver.query(contactUri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val displayName = if (nameIndex >= 0) it.getString(nameIndex) else pendingAlias
                val phoneNumber = if (numberIndex >= 0) it.getString(numberIndex) else null

                if (phoneNumber != null) {
                    lifecycleScope.launch {
                        AppDatabase.getInstance(this@ContactRegistrationActivity).contactDao().upsert(
                            ContactEntity(
                                alias = pendingAlias,
                                displayName = displayName ?: pendingAlias,
                                phoneNumber = phoneNumber
                            )
                        )
                        finish()
                    }
                    return
                }
            }
        }
        finish()
    }

    companion object {
        const val EXTRA_ALIAS = "alias"
    }
}
