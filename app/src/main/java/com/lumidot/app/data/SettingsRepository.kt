package com.lumidot.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lumidot_settings")

/** Preferencias locales (DataStore). Nada se sincroniza ni sale del dispositivo. */
class SettingsRepository(context: Context) {
    private val store = context.applicationContext.dataStore

    val settings: Flow<LedSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toSettings() }
        .distinctUntilChanged()

    suspend fun current(): LedSettings = settings.first()

    suspend fun update(transform: (LedSettings) -> LedSettings) {
        store.edit { prefs ->
            val old = prefs.toSettings()
            val new = transform(old)
            if (new != old) prefs.write(new)
        }
    }

    suspend fun setAppRule(rule: AppRule) = update { s ->
        s.copy(appRules = s.appRules + (rule.packageName to rule))
    }

    suspend fun recordSeenPackage(pkg: String) = update { s ->
        if (pkg in s.seenPackages) s
        else s.copy(seenPackages = (s.seenPackages + pkg).toList().takeLast(MAX_SEEN).toSet())
    }

    private object Keys {
        val enabled = booleanPreferencesKey("enabled")
        val color = intPreferencesKey("color")
        val size = intPreferencesKey("size_dp")
        val posX = floatPreferencesKey("pos_x")
        val posY = floatPreferencesKey("pos_y")
        val pattern = stringPreferencesKey("pattern")
        val period = intPreferencesKey("period_ms")
        val glow = booleanPreferencesKey("glow")
        val brightness = floatPreferencesKey("screen_brightness")
        val maxDuration = intPreferencesKey("max_duration_min")
        val burnIn = booleanPreferencesKey("burn_in_shift")
        val respectDnd = booleanPreferencesKey("respect_dnd")
        val ignoreSilent = booleanPreferencesKey("ignore_silent")
        val skipInCall = booleanPreferencesKey("skip_in_call")
        val lowBattery = intPreferencesKey("low_battery_cutoff")
        val quietEnabled = booleanPreferencesKey("quiet_enabled")
        val quietStart = intPreferencesKey("quiet_start")
        val quietEnd = intPreferencesKey("quiet_end")
        val cycleColors = booleanPreferencesKey("cycle_colors")
        val filterMode = stringPreferencesKey("filter_mode")
        val appRules = stringPreferencesKey("app_rules")
        val seen = stringSetPreferencesKey("seen_packages")
        val disclosure = booleanPreferencesKey("disclosure_accepted")
        val keepAlive = booleanPreferencesKey("keep_alive")
    }

    private fun Preferences.toSettings(): LedSettings {
        val d = LedSettings()
        return LedSettings(
            enabled = this[Keys.enabled] ?: d.enabled,
            color = this[Keys.color] ?: d.color,
            sizeDp = (this[Keys.size] ?: d.sizeDp).coerceIn(LedSettings.MIN_SIZE_DP, LedSettings.MAX_SIZE_DP),
            posX = (this[Keys.posX] ?: d.posX).coerceIn(0f, 1f),
            posY = (this[Keys.posY] ?: d.posY).coerceIn(0f, 1f),
            pattern = BlinkPattern.fromName(this[Keys.pattern]),
            periodMs = (this[Keys.period] ?: d.periodMs).coerceIn(LedSettings.MIN_PERIOD_MS, LedSettings.MAX_PERIOD_MS),
            glow = this[Keys.glow] ?: d.glow,
            screenBrightness = (this[Keys.brightness] ?: d.screenBrightness).coerceIn(0.01f, 1f),
            maxDurationMin = (this[Keys.maxDuration] ?: d.maxDurationMin).coerceAtLeast(0),
            burnInShift = this[Keys.burnIn] ?: d.burnInShift,
            respectDnd = this[Keys.respectDnd] ?: d.respectDnd,
            ignoreSilent = this[Keys.ignoreSilent] ?: d.ignoreSilent,
            skipInCall = this[Keys.skipInCall] ?: d.skipInCall,
            lowBatteryCutoff = (this[Keys.lowBattery] ?: d.lowBatteryCutoff).coerceIn(0, 100),
            quietHoursEnabled = this[Keys.quietEnabled] ?: d.quietHoursEnabled,
            quietStart = (this[Keys.quietStart] ?: d.quietStart).coerceIn(0, 24 * 60 - 1),
            quietEnd = (this[Keys.quietEnd] ?: d.quietEnd).coerceIn(0, 24 * 60 - 1),
            cycleAppColors = this[Keys.cycleColors] ?: d.cycleAppColors,
            filterMode = AppFilterMode.fromName(this[Keys.filterMode]),
            appRules = decodeRules(this[Keys.appRules]),
            seenPackages = this[Keys.seen] ?: emptySet(),
            disclosureAccepted = this[Keys.disclosure] ?: false,
            keepAlive = this[Keys.keepAlive] ?: d.keepAlive,
        )
    }

    private fun MutablePreferences.write(s: LedSettings) {
        this[Keys.enabled] = s.enabled
        this[Keys.color] = s.color
        this[Keys.size] = s.sizeDp
        this[Keys.posX] = s.posX
        this[Keys.posY] = s.posY
        this[Keys.pattern] = s.pattern.name
        this[Keys.period] = s.periodMs
        this[Keys.glow] = s.glow
        this[Keys.brightness] = s.screenBrightness
        this[Keys.maxDuration] = s.maxDurationMin
        this[Keys.burnIn] = s.burnInShift
        this[Keys.respectDnd] = s.respectDnd
        this[Keys.ignoreSilent] = s.ignoreSilent
        this[Keys.skipInCall] = s.skipInCall
        this[Keys.lowBattery] = s.lowBatteryCutoff
        this[Keys.quietEnabled] = s.quietHoursEnabled
        this[Keys.quietStart] = s.quietStart
        this[Keys.quietEnd] = s.quietEnd
        this[Keys.cycleColors] = s.cycleAppColors
        this[Keys.filterMode] = s.filterMode.name
        this[Keys.appRules] = encodeRules(s.appRules)
        this[Keys.seen] = s.seenPackages
        this[Keys.disclosure] = s.disclosureAccepted
        this[Keys.keepAlive] = s.keepAlive
    }

    private fun encodeRules(rules: Map<String, AppRule>): String {
        val arr = JSONArray()
        rules.values.forEach { r ->
            arr.put(JSONObject().apply {
                put("p", r.packageName)
                put("e", r.enabled)
                r.color?.let { put("c", it) }
            })
        }
        return arr.toString()
    }

    private fun decodeRules(json: String?): Map<String, AppRule> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val arr = JSONArray(json)
            buildMap {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val pkg = o.getString("p")
                    put(pkg, AppRule(pkg, o.optBoolean("e", true), if (o.has("c")) o.getInt("c") else null))
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private companion object {
        const val MAX_SEEN = 200
    }
}
