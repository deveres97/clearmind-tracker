package com.clearmind.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * The small slice of state Android needs: enough to schedule notifications,
 * draw the widget and write the clean-time clock. The full app state lives in
 * the WebView's localStorage; the page pushes this summary over on every save.
 */
object Store {
    private const val PREFS = "clearmind"

    fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun put(c: Context, json: String) {
        val o = JSONObject(json)
        val risk = o.optJSONArray("risk") ?: JSONArray()
        val set = LinkedHashSet<String>()
        for (i in 0 until risk.length()) set.add(risk.optString(i))
        prefs(c).edit()
            .putBoolean("onboarded", o.optBoolean("onboarded"))
            .putString("name", o.optString("name"))
            .putLong("lastUse", o.optLong("lastUse"))
            .putLong("tbStart", o.optLong("tbStart"))
            .putInt("tbDays", o.optInt("tbDays", 21))
            .putBoolean("tbOpen", o.optBoolean("tbOpen"))
            .putBoolean("tbEnded", o.optBoolean("tbEnded"))
            .putStringSet("risk", set)
            .putString("personName", o.optString("personName"))
            .putBoolean("personUpdates", o.optBoolean("personUpdates"))
            .putInt("cleanDays", o.optInt("cleanDays"))
            .putInt("wins", o.optInt("wins"))
            .putString("reason", o.optString("reason"))
            .apply()
    }

    fun onboarded(c: Context) = prefs(c).getBoolean("onboarded", false)
    fun name(c: Context): String = prefs(c).getString("name", "") ?: ""
    fun lastUse(c: Context) = prefs(c).getLong("lastUse", 0L)
    fun tbStart(c: Context) = prefs(c).getLong("tbStart", 0L)
    fun tbDays(c: Context) = prefs(c).getInt("tbDays", 21)
    fun tbOpen(c: Context) = prefs(c).getBoolean("tbOpen", false)
    fun tbEnded(c: Context) = prefs(c).getBoolean("tbEnded", false)
    fun risk(c: Context): List<String> = (prefs(c).getStringSet("risk", emptySet()) ?: emptySet()).sorted()
    fun personName(c: Context): String = prefs(c).getString("personName", "") ?: ""
    fun personUpdates(c: Context) = prefs(c).getBoolean("personUpdates", false)
    fun cleanDays(c: Context) = prefs(c).getInt("cleanDays", 0)
    fun wins(c: Context) = prefs(c).getInt("wins", 0)
    fun reason(c: Context): String = prefs(c).getString("reason", "") ?: ""

    /** Day number of the break, 1-based; 0 before it starts. */
    fun tbDay(c: Context): Int {
        val s = tbStart(c)
        if (s == 0L || System.currentTimeMillis() < s) return 0
        return ((System.currentTimeMillis() - s) / 86_400_000L).toInt() + 1
    }

    fun running(c: Context): Boolean =
        onboarded(c) && !tbEnded(c) && tbStart(c) > 0

    // ---- events queued for the web page (notification taps, action buttons) ----
    fun queue(c: Context, type: String, value: String) {
        val arr = JSONArray(prefs(c).getString("events", "[]"))
        arr.put(JSONObject().put("type", type).put("value", value).put("at", System.currentTimeMillis()))
        // keep the queue small
        val trimmed = JSONArray()
        val from = maxOf(0, arr.length() - 20)
        for (i in from until arr.length()) trimmed.put(arr.get(i))
        prefs(c).edit().putString("events", trimmed.toString()).apply()
    }

    fun drainEvents(c: Context): String {
        val s = prefs(c).getString("events", "[]") ?: "[]"
        prefs(c).edit().putString("events", "[]").apply()
        return s
    }
}
