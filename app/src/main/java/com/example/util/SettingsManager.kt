package com.example.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "m4dichat_settings")

object SettingsManager {
    val KEY_ASSISTANT_NAME = stringPreferencesKey("assistant_name")
    val KEY_AI_MODEL = stringPreferencesKey("ai_model")
    val KEY_AVATAR_INDEX = stringPreferencesKey("avatar_index")

    fun getAssistantName(context: Context): Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_ASSISTANT_NAME] ?: "M4Di AI"
    }

    fun getAiModel(context: Context): Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_AI_MODEL] ?: "openrouter/free"
    }

    fun getAvatarIndex(context: Context): Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_AVATAR_INDEX]?.toIntOrNull() ?: 0
    }

    suspend fun saveSettings(context: Context, name: String, model: String, avatarIndex: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ASSISTANT_NAME] = name
            preferences[KEY_AI_MODEL] = model
            preferences[KEY_AVATAR_INDEX] = avatarIndex.toString()
        }
    }
}
