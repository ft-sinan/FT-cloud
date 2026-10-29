package com.ftcloud.app.core.crypto

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ftcloud.app.core.model.StoredBotToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Base64
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

interface CredentialStore {
    val botsFlow: StateFlow<List<StoredBotToken>>
    val channelIdFlow: StateFlow<String>
    val channelTitleFlow: StateFlow<String>

    fun getBots(): List<StoredBotToken>
    fun saveBots(bots: List<StoredBotToken>)

    fun getChannelId(): String
    fun getChannelTitle(): String
    fun saveChannel(channelId: String, channelTitle: String)

    fun setVaultEncryptionKey(secretKey: SecretKey, passphraseRecordSaltB64: String, verifier: String)
    fun getVaultEncryptionKey(): SecretKey?
    fun getVaultPassphraseSaltB64(): String?
    fun getVaultKeyVerifier(): String?
    fun clearVaultEncryption()

    fun clearAll()
}

class AndroidKeystoreCredentialStore(context: Context) : CredentialStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val prefs: SharedPreferences = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse {
        context.getSharedPreferences("${PREFS_FILE_NAME}_fallback", Context.MODE_PRIVATE)
    }

    private val _botsFlow = MutableStateFlow(readBotsFromPrefs())
    override val botsFlow: StateFlow<List<StoredBotToken>> = _botsFlow.asStateFlow()

    private val _channelIdFlow = MutableStateFlow(prefs.getString(KEY_CHANNEL_ID, "").orEmpty())
    override val channelIdFlow: StateFlow<String> = _channelIdFlow.asStateFlow()

    private val _channelTitleFlow = MutableStateFlow(prefs.getString(KEY_CHANNEL_TITLE, "").orEmpty())
    override val channelTitleFlow: StateFlow<String> = _channelTitleFlow.asStateFlow()

    override fun getBots(): List<StoredBotToken> = _botsFlow.value

    override fun saveBots(bots: List<StoredBotToken>) {
        val distinct = bots.distinctBy { it.botId }
        prefs.edit().putString(KEY_BOTS_JSON, json.encodeToString(distinct)).apply()
        _botsFlow.value = distinct
    }

    override fun getChannelId(): String = _channelIdFlow.value

    override fun getChannelTitle(): String = _channelTitleFlow.value

    override fun saveChannel(channelId: String, channelTitle: String) {
        prefs.edit()
            .putString(KEY_CHANNEL_ID, channelId.trim())
            .putString(KEY_CHANNEL_TITLE, channelTitle.trim())
            .apply()
        _channelIdFlow.value = channelId.trim()
        _channelTitleFlow.value = channelTitle.trim()
    }

    override fun setVaultEncryptionKey(
        secretKey: SecretKey,
        passphraseRecordSaltB64: String,
        verifier: String,
    ) {
        val keyB64 = Base64.getEncoder().encodeToString(secretKey.encoded)
        prefs.edit()
            .putString(KEY_VAULT_SECRET_B64, keyB64)
            .putString(KEY_VAULT_SALT_B64, passphraseRecordSaltB64)
            .putString(KEY_VAULT_VERIFIER, verifier)
            .apply()
    }

    override fun getVaultEncryptionKey(): SecretKey? {
        val keyB64 = prefs.getString(KEY_VAULT_SECRET_B64, null) ?: return null
        return runCatching {
            val raw = Base64.getDecoder().decode(keyB64)
            SecretKeySpec(raw, "AES")
        }.getOrNull()
    }

    override fun getVaultPassphraseSaltB64(): String? = prefs.getString(KEY_VAULT_SALT_B64, null)

    override fun getVaultKeyVerifier(): String? = prefs.getString(KEY_VAULT_VERIFIER, null)

    override fun clearVaultEncryption() {
        prefs.edit()
            .remove(KEY_VAULT_SECRET_B64)
            .remove(KEY_VAULT_SALT_B64)
            .remove(KEY_VAULT_VERIFIER)
            .apply()
    }

    override fun clearAll() {
        prefs.edit().clear().apply()
        _botsFlow.value = emptyList()
        _channelIdFlow.value = ""
        _channelTitleFlow.value = ""
    }

    private fun readBotsFromPrefs(): List<StoredBotToken> {
        val raw = prefs.getString(KEY_BOTS_JSON, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<StoredBotToken>>(raw) }.getOrDefault(emptyList())
    }

    companion object {
        private const val PREFS_FILE_NAME = "ftcloud_secure_credentials"
        private const val KEY_BOTS_JSON = "bots_json"
        private const val KEY_CHANNEL_ID = "channel_id"
        private const val KEY_CHANNEL_TITLE = "channel_title"
        private const val KEY_VAULT_SECRET_B64 = "vault_secret_b64"
        private const val KEY_VAULT_SALT_B64 = "vault_salt_b64"
        private const val KEY_VAULT_VERIFIER = "vault_verifier"
    }
}

class InMemoryCredentialStore : CredentialStore {
    private val _botsFlow = MutableStateFlow<List<StoredBotToken>>(emptyList())
    override val botsFlow: StateFlow<List<StoredBotToken>> = _botsFlow.asStateFlow()

    private val _channelIdFlow = MutableStateFlow("")
    override val channelIdFlow: StateFlow<String> = _channelIdFlow.asStateFlow()

    private val _channelTitleFlow = MutableStateFlow("")
    override val channelTitleFlow: StateFlow<String> = _channelTitleFlow.asStateFlow()

    private var secretKey: SecretKey? = null
    private var saltB64: String? = null
    private var verifier: String? = null

    override fun getBots(): List<StoredBotToken> = _botsFlow.value

    override fun saveBots(bots: List<StoredBotToken>) {
        _botsFlow.value = bots.distinctBy { it.botId }
    }

    override fun getChannelId(): String = _channelIdFlow.value

    override fun getChannelTitle(): String = _channelTitleFlow.value

    override fun saveChannel(channelId: String, channelTitle: String) {
        _channelIdFlow.value = channelId.trim()
        _channelTitleFlow.value = channelTitle.trim()
    }

    override fun setVaultEncryptionKey(
        secretKey: SecretKey,
        passphraseRecordSaltB64: String,
        verifier: String,
    ) {
        this.secretKey = secretKey
        this.saltB64 = passphraseRecordSaltB64
        this.verifier = verifier
    }

    override fun getVaultEncryptionKey(): SecretKey? = secretKey

    override fun getVaultPassphraseSaltB64(): String? = saltB64

    override fun getVaultKeyVerifier(): String? = verifier

    override fun clearVaultEncryption() {
        secretKey = null
        saltB64 = null
        verifier = null
    }

    override fun clearAll() {
        _botsFlow.value = emptyList()
        _channelIdFlow.value = ""
        _channelTitleFlow.value = ""
        clearVaultEncryption()
    }
}
