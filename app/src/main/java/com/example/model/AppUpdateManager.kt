package com.example.model

import android.content.Context
import android.content.SharedPreferences
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

object AppUpdateManager {
    private const val PREFS_NAME = "hathari_prefs"
    private const val KEY_CURRENT_VERSION = "app_version_name"
    private const val KEY_AUTO_UPDATE = "app_auto_update"
    private const val DEFAULT_VERSION = "v2.1.0"

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    var isUpdateNotificationActive by mutableStateOf(false)
        private set

    sealed class UpdateState {
        object Idle : UpdateState()
        object Checking : UpdateState()
        data class UpdateAvailable(val version: String, val changelog: List<String>, val isCritical: Boolean) : UpdateState()
        data class Downloading(val progress: Float, val currentTool: String) : UpdateState()
        object Installing : UpdateState()
        data class Success(val version: String) : UpdateState()
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getCurrentVersion(context: Context): String {
        return getPrefs(context).getString(KEY_CURRENT_VERSION, DEFAULT_VERSION) ?: DEFAULT_VERSION
    }

    fun isAutoUpdateEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_UPDATE, true)
    }

    fun setAutoUpdateEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_UPDATE, enabled).apply()
    }

    fun loadDeveloperUpdateConfig(context: Context): DeveloperUpdateDetails? {
        return try {
            val assetManager = context.assets
            val inputStream = assetManager.open("developer_updates.json")
            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonStr = reader.use { it.readText() }
            val json = JSONObject(jsonStr)
            
            val version = json.optString("latest_version", "v3.2.0")
            val releaseDate = json.optString("release_date", "2026-06-10")
            val isCritical = json.optBoolean("is_critical", true)
            
            val changelogArray = json.optJSONArray("changelog")
            val changelog = mutableListOf<String>()
            if (changelogArray != null) {
                for (i in 0 until changelogArray.length()) {
                    changelog.add(changelogArray.getString(i))
                }
            }
            
            val associatedToolsArray = json.optJSONArray("associated_tools")
            val associatedTools = mutableListOf<String>()
            if (associatedToolsArray != null) {
                for (i in 0 until associatedToolsArray.length()) {
                    associatedTools.add(associatedToolsArray.getString(i))
                }
            }
            
            DeveloperUpdateDetails(version, releaseDate, isCritical, changelog, associatedTools)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun initUpdateNotification(context: Context) {
        val current = getCurrentVersion(context)
        val devInfo = loadDeveloperUpdateConfig(context)
        val targetVersion = devInfo?.version ?: "v3.2.0"
        if (current != targetVersion) {
            isUpdateNotificationActive = true
        }
    }

    fun dismissNotification() {
        isUpdateNotificationActive = false
    }

    fun triggerAutoUpdateIfNeeded(context: Context, scope: CoroutineScope, onFinished: () -> Unit = {}) {
        val current = getCurrentVersion(context)
        val devInfo = loadDeveloperUpdateConfig(context)
        val targetVersion = devInfo?.version ?: "v3.3.0"
        if (isAutoUpdateEnabled(context) && current != targetVersion) {
            scope.launch {
                delay(3000) // Small delay on startup
                runUpdateFlow(context, onFinished)
            }
        }
    }

    suspend fun checkForUpdates(context: Context): UpdateState {
        _updateState.value = UpdateState.Checking
        delay(1200) // Simulated api delay
        val current = getCurrentVersion(context)
        val devInfo = loadDeveloperUpdateConfig(context)
        val targetVersion = devInfo?.version ?: "v3.3.0"

        return if (current != targetVersion && devInfo != null) {
            isUpdateNotificationActive = true
            val state = UpdateState.UpdateAvailable(
                version = devInfo.version,
                changelog = devInfo.changelog,
                isCritical = devInfo.isCritical
            )
            _updateState.value = state
            state
        } else {
            val state = UpdateState.Idle
            _updateState.value = state
            state
        }
    }

    fun runUpdateFlow(context: Context, onFinished: () -> Unit = {}) {
        CoroutineScope(Dispatchers.Main).launch {
            if (_updateState.value is UpdateState.Downloading || _updateState.value is UpdateState.Installing) {
                return@launch
            }

            _updateState.value = UpdateState.Checking
            delay(1000)

            val devInfo = loadDeveloperUpdateConfig(context)
            val targetVersion = devInfo?.version ?: "v3.3.0"
            val toolsToDownload = devInfo?.associatedTools ?: listOf(
                "أداة الدعم المتكاملة وربط تذاكر المشاكل",
                "موارد قواعد البيانات المحلية المشفرة",
                "ترميز الباركود الذكي EAN-13",
                "نظام رصد الرفوف السحابي للمستودعات"
            )

            // Dynamic Downloading Step
            for (i in toolsToDownload.indices) {
                val toolName = toolsToDownload[i]
                var progress = 0f
                while (progress < 1.0f) {
                    val stepProgress = ((i.toFloat() / toolsToDownload.size) + (progress / toolsToDownload.size))
                    _updateState.value = UpdateState.Downloading(stepProgress, toolName)
                    delay(150)
                    progress += 0.2f
                }
            }

            // Installing Step
            _updateState.value = UpdateState.Installing
            delay(1500)

            // Save new version to prefs
            getPrefs(context).edit().putString(KEY_CURRENT_VERSION, targetVersion).apply()
            isUpdateNotificationActive = false
            _updateState.value = UpdateState.Success(targetVersion)
            
            // Execute real-time variable reconstruction, database configuration, variable modifications:
            AutoSyncManager.startAutoSync(context)

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "تم تنزيل وتثبيت الموارد بنجاح! جاري إعادة تشغيل التطبيق تلقائياً لتفعيل التغييرات...", Toast.LENGTH_LONG).show()
                delay(2500)
                restartApp(context)
                onFinished()
            }
        }
    }

    fun restartApp(context: Context) {
        try {
            val packageManager = context.packageManager
            val intent = packageManager.getLaunchIntentForPackage(context.packageName)
            val componentName = intent?.component
            val mainIntent = Intent.makeRestartActivityTask(componentName)
            context.startActivity(mainIntent)
            Runtime.getRuntime().exit(0)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun resetToDefault(context: Context) {
        getPrefs(context).edit().putString(KEY_CURRENT_VERSION, DEFAULT_VERSION).apply()
        _updateState.value = UpdateState.Idle
        isUpdateNotificationActive = true
    }
}

data class DeveloperUpdateDetails(
    val version: String,
    val releaseDate: String,
    val isCritical: Boolean,
    val changelog: List<String>,
    val associatedTools: List<String>
)
