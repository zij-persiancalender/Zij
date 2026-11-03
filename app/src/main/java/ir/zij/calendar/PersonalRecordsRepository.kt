package ir.zij.calendar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class PersonalRecord(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val isBirthday: Boolean,
    val month: Int,
    val day: Int,
    val birthImperialYear: Int? = null,
    val note: String = "",
    val remindWeekBefore: Boolean = true,
    val remindDayBefore: Boolean = true,
    val remindOnDay: Boolean = true
)

object PersonalRecordsRepository {
    private const val PREFS_NAME = "zij_personal_records"
    private const val KEY_LIST = "records_json_v1"

    fun loadAll(context: Context): List<PersonalRecord> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LIST, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val result = mutableListOf<PersonalRecord>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val yearVal = obj.optInt("birthImperialYear", -1)
                result.add(
                    PersonalRecord(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        isBirthday = obj.optBoolean("isBirthday", true),
                        month = obj.getInt("month").coerceIn(1, 12),
                        day = obj.getInt("day").coerceIn(1, 31),
                        birthImperialYear = if (yearVal > 0) yearVal else null,
                        note = obj.optString("note", ""),
                        remindWeekBefore = obj.optBoolean("remindWeekBefore", true),
                        remindDayBefore = obj.optBoolean("remindDayBefore", true),
                        remindOnDay = obj.optBoolean("remindOnDay", true)
                    )
                )
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveRecord(context: Context, record: PersonalRecord): List<PersonalRecord> {
        val current = loadAll(context).toMutableList()
        val existingIdx = current.indexOfFirst { it.id == record.id }
        if (existingIdx >= 0) {
            current[existingIdx] = record
        } else {
            current.add(record)
        }
        persist(context, current)
        ZijWidgetUpdater.updateAllWidgets(context)
        return current
    }

    fun deleteRecord(context: Context, id: String): List<PersonalRecord> {
        val updated = loadAll(context).filterNot { it.id == id }
        persist(context, updated)
        ZijWidgetUpdater.updateAllWidgets(context)
        return updated
    }

    private fun persist(context: Context, list: List<PersonalRecord>) {
        val arr = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("isBirthday", item.isBirthday)
                put("month", item.month)
                put("day", item.day)
                if (item.birthImperialYear != null) put("birthImperialYear", item.birthImperialYear)
                put("note", item.note)
                put("remindWeekBefore", item.remindWeekBefore)
                put("remindDayBefore", item.remindDayBefore)
                put("remindOnDay", item.remindOnDay)
            }
            arr.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LIST, arr.toString()).apply()
    }
}
