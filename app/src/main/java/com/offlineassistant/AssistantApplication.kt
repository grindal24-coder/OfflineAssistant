package com.offlineassistant

import android.app.Application

class AssistantApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Точка расширения: инициализация логирования, крэш-репортинга (локального!) и т.п.
    }
}
