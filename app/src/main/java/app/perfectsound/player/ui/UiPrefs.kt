package app.perfectsound.player.ui

import android.content.Context
import app.perfectsound.player.audio.SpectrumAnalyzer
import app.perfectsound.player.remote.RemoteSessions
import org.json.JSONArray
import org.json.JSONObject

/** Remembers window-level choices between launches: source, visible panels, visualizer mode and EQ sliders. */
class UiPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE)

    var source: Source
        get() = prefs.getString("source", null)
            ?.let { name -> RemoteSessions.App.entries.firstOrNull { it.name == name } }
            ?.let { Source.Remote(it) } ?: Source.Local
        set(value) = prefs.edit().putString("source", (value as? Source.Remote)?.app?.name ?: "Local").apply()

    var equalizerVisible: Boolean
        get() = prefs.getBoolean("equalizerVisible", true)
        set(value) = prefs.edit().putBoolean("equalizerVisible", value).apply()

    var playlistVisible: Boolean
        get() = prefs.getBoolean("playlistVisible", true)
        set(value) = prefs.edit().putBoolean("playlistVisible", value).apply()

    var visMode: VisMode
        get() = prefs.getString("visMode", null)?.let { name -> VisMode.entries.firstOrNull { it.name == name } } ?: VisMode.Bands
        set(value) = prefs.edit().putString("visMode", value.name).apply()

    var eq: EqSettings
        get() = runCatching {
            val o = JSONObject(prefs.getString("eq", null) ?: return EqSettings())
            val bands = o.getJSONArray("bands")
            EqSettings(
                enabled = o.optBoolean("enabled"),
                preamp = o.optDouble("preamp", 0.0).toFloat(),
                bands = List(SpectrumAnalyzer.BAND_CENTERS_HZ.size) { i -> bands.optDouble(i, 0.0).toFloat() },
            )
        }.getOrDefault(EqSettings())
        set(value) = prefs.edit().putString("eq", JSONObject()
            .put("enabled", value.enabled)
            .put("preamp", value.preamp.toDouble())
            .put("bands", JSONArray(value.bands.map { it.toDouble() }))
            .toString()).apply()
}
