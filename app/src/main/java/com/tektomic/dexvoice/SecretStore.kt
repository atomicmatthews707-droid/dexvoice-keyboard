package com.tektomic.dexvoice

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores API keys in EncryptedSharedPreferences. If the Android keystore is unavailable
 * (broken device keystore, etc.) it falls back to a private plain preferences file and logs a
 * warning. Key values are NEVER logged.
 */
class SecretStore private constructor(private val prefs: SharedPreferences, val encrypted: Boolean) {

    constructor(prefs: SharedPreferences) : this(prefs, false)

    /** Key for the OpenAI-compatible provider. */
    var openAiKey: String
        get() = read(K_OPENAI)
        set(v) = write(K_OPENAI, v)

    var wisprKey: String
        get() = read(K_WISPR)
        set(v) = write(K_WISPR, v)

    fun keyFor(provider: String): String =
        if (provider == Settings.PROVIDER_WISPR) wisprKey else openAiKey

    fun setKeyFor(provider: String, value: String) {
        if (provider == Settings.PROVIDER_WISPR) wisprKey = value else openAiKey = value
    }

    fun hasKeyFor(provider: String): Boolean = keyFor(provider).isNotBlank()

    private fun read(k: String): String = try {
        prefs.getString(k, "") ?: ""
    } catch (e: Exception) {
        Log.w(TAG, "Secret read failed: ${e.javaClass.simpleName}")
        ""
    }

    private fun write(k: String, v: String) {
        try {
            val trimmed = v.trim()
            if (trimmed.isEmpty()) prefs.edit().remove(k).apply()
            else prefs.edit().putString(k, trimmed).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Secret write failed: ${e.javaClass.simpleName}")
        }
    }

    companion object {
        private const val TAG = "SecretStore"
        private const val FILE_ENCRYPTED = "dexvoice_secrets"
        private const val FILE_FALLBACK = "dexvoice_secrets_fallback"
        const val K_OPENAI = "api_key_openai_compat"
        const val K_WISPR = "api_key_wispr"

        fun create(context: Context): SecretStore {
            val app = context.applicationContext
            return try {
                val master = MasterKey.Builder(app)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                val p = EncryptedSharedPreferences.create(
                    app, FILE_ENCRYPTED, master,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
                SecretStore(p, true)
            } catch (e: Exception) {
                Log.w(TAG, "Encrypted storage unavailable (${e.javaClass.simpleName}); using private fallback file")
                SecretStore(app.getSharedPreferences(FILE_FALLBACK, Context.MODE_PRIVATE), false)
            }
        }
    }
}
