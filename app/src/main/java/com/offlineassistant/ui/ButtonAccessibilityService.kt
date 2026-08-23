package com.offlineassistant.ui

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.offlineassistant.wakeword.WakeWordService

/**
 * п.14 ТЗ, Вариант 1: запуск ассистента нажатием Громкость+ и Громкость- с
 * разницей до WINDOW_MS.
 *
 * По итогам ревью ТЗ: раньше требовалось буквально ОДНОВРЕМЕННОЕ нажатие
 * (оба ACTION_DOWN в один и тот же системный тик) — на практике так почти
 * никто физически не попадёт, у людей между двумя нажатиями пальцами
 * естественно проходит 50-250мс. Заменено на временное окно: если вторая
 * кнопка нажата в пределах WINDOW_MS после первой — считаем это комбинацией.
 *
 * ИЗВЕСТНОЕ ОГРАНИЧЕНИЕ (см. п.14 ТЗ): начиная примерно с Android 11, система
 * не гарантирует доставку KeyEvent для громкости в фоновые Accessibility-сервисы
 * стабильно на всех OEM-прошивках (особенно MIUI/ColorOS с их энергосбережением).
 * Протестируй на целевом Dimensity-устройстве отдельно; если нестабильно —
 * переходи на Вариант 2 (ROLE_ASSISTANT / VoiceInteractionService), см. AndroidManifest.xml.
 */
class ButtonAccessibilityService : AccessibilityService() {

    private var lastVolumeUpDownAt = 0L
    private var lastVolumeDownDownAt = 0L

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return super.onKeyEvent(event)

        val now = SystemClock.elapsedRealtime()
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> lastVolumeUpDownAt = now
            KeyEvent.KEYCODE_VOLUME_DOWN -> lastVolumeDownDownAt = now
            else -> return super.onKeyEvent(event)
        }

        val delta = kotlin.math.abs(lastVolumeUpDownAt - lastVolumeDownDownAt)
        val bothRecentlyPressed = lastVolumeUpDownAt != 0L && lastVolumeDownDownAt != 0L && delta <= WINDOW_MS

        if (bothRecentlyPressed) {
            triggerAssistant()
            lastVolumeUpDownAt = 0L
            lastVolumeDownDownAt = 0L
            return true // поглощаем событие, не даём системе изменить громкость
        }
        return super.onKeyEvent(event)
    }

    private fun triggerAssistant() {
        val intent = Intent(this, WakeWordService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        // TODO: явно вызвать "как будто wake-word сработал" — например через
        // отдельный Intent-action, который WakeWordService слушает в onStartCommand.
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Не требуется для перехвата клавиш, оставлено пустым по контракту API.
    }

    override fun onInterrupt() {}

    companion object {
        /** Максимальная разница между нажатиями Громкость+ и Громкость-, мс. */
        private const val WINDOW_MS = 300L
    }
}
