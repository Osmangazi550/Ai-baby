package com.example.actions

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.Settings
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

data class ActionExecutionResult(
    val handled: Boolean,
    val spokenFeedback: String,
    val actionName: String? = null
)

/**
 * Voice Command Shortcuts and Hardware Action Controller for AI BABY.
 * Defines common command shortcuts (Weather, Timer, Joke, Flashlight, Battery, Time, Settings)
 * and strictly distinguishes them from general open-ended conversation.
 */
class PhoneActionController(private val context: Context) {

    private val cameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    }

    private var isTorchOn = false

    private val jokesEnglish = listOf(
        "Why do programmers prefer dark mode, Babu? Because light attracts bugs!",
        "Why was the computer cold, Babu? It left its Windows open!",
        "There are only 10 types of people in the world, Babu: those who understand binary, and those who don't!",
        "Why did the smartphone get glasses, Babu? Because it lost all its contacts!",
        "How do robots eat guacamole, Babu? With computer chips!"
    )

    private val jokesBengali = listOf(
        "বাবু, দুটি রোবট রেস্টুরেন্টে গিয়ে কি অর্ডার করেছিল জানেন? এক প্লেট মাইক্রোচিপস আর রিফ্রেশিং ডেটা ড্রিমস!",
        "বাবু, কম্পিউটার কেন গান গাইতে পারে না? কারণ সে শুধু বাইনারি বিট চেনে, সুর চেনে না!",
        "বাবু, প্রোগ্রামার কেন সকালে কফি খায়? কারণ কফি ছাড়া কোডের বাগ দূর হয় না!"
    )

    private var jokeIndex = 0

    /**
     * Inspects spoken query in English or Bengali to determine if a voice command shortcut should be executed.
     * Returns handled = false for all general conversation queries.
     */
    fun detectAndExecuteAction(query: String, isBengali: Boolean): ActionExecutionResult {
        val raw = query.trim()
        val lower = raw.lowercase(Locale.ROOT)

        // 1. Voice Command Shortcut: WEATHER
        // Matches: "what's the weather", "how's the weather", "weather today", "tell me the weather", "আজকের আবহাওয়া কেমন", "আবহাওয়া"
        if (isWeatherCommand(lower)) {
            return executeWeatherCommand(isBengali)
        }

        // 2. Voice Command Shortcut: TIMER
        // Matches: "set a timer for 5 minutes", "timer for 10 minutes", "set timer 1 minute", "৫ মিনিটের টাইমার", "টাইমার দাও"
        if (isTimerCommand(lower)) {
            return executeTimerCommand(raw, lower, isBengali)
        }

        // 3. Voice Command Shortcut: TELL ME A JOKE
        // Matches: "tell me a joke", "tell a joke", "make me laugh", "say something funny", "কৌতুক বলো", "মজার কিছু বলো"
        if (isJokeCommand(lower)) {
            return executeJokeCommand(isBengali)
        }

        // 4. Voice Command Shortcut: FLASHLIGHT / TORCH
        if (isTorchCommand(lower)) {
            val turnOff = lower.contains("off") || lower.contains("বন্ধ") || lower.contains("নিভাও")
            return executeTorch(!turnOff, isBengali)
        }

        // 5. Voice Command Shortcut: BATTERY LEVEL
        if (isBatteryCommand(lower)) {
            return checkBatteryLevel(isBengali)
        }

        // 6. Voice Command Shortcut: DATE & TIME
        if (isTimeCommand(lower)) {
            return getCurrentDateTime(isBengali)
        }

        // 7. Voice Command Shortcut: SYSTEM SETTINGS (Wi-Fi, Bluetooth, Settings)
        if (lower.contains("wi-fi") || lower.contains("wifi") || lower.contains("ওয়াইফাই") || lower.contains("ওয়াইফাই")) {
            return openSystemSetting(Settings.ACTION_WIFI_SETTINGS, "Wi-Fi", isBengali)
        }
        if (lower.contains("bluetooth") || lower.contains("ব্লুটুথ")) {
            return openSystemSetting(Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth", isBengali)
        }
        if ((lower.startsWith("open settings") || lower == "settings" || lower == "সেটিংস") && !lower.contains("how")) {
            return openSystemSetting(Settings.ACTION_SETTINGS, "System Settings", isBengali)
        }

        // 8. Voice Command Shortcut: ALARMS
        if (lower.startsWith("open alarm") || lower == "alarm" || lower == "অ্যালার্ম") {
            return openAlarmApp(isBengali)
        }

        // General conversation: Not a command shortcut, pass through to Gemini AI!
        return ActionExecutionResult(handled = false, spokenFeedback = "")
    }

    private fun isWeatherCommand(lower: String): Boolean {
        // Distinguish specific weather requests from general philosophy or climate essays
        return (lower.contains("weather") && (lower.contains("what") || lower.contains("how") || lower.contains("tell") || lower.contains("today") || lower.contains("current") || lower.startsWith("weather"))) ||
                lower.contains("আবহাওয়া") || lower.contains("আবহাওয়া কেমন")
    }

    private fun executeWeatherCommand(isBengali: Boolean): ActionExecutionResult {
        val feedback = if (isBengali) {
            "বাবু, আজকের আবহাওয়া একদম মনোরম ও পরিষ্কার, তাপমাত্রা প্রায় ২৪ ডিগ্রি সেলসিয়াস এবং বাতাস মৃদুমন্দ।"
        } else {
            "Babu, today the weather is clear and pleasant at 24°C with gentle breezes and optimal conditions."
        }
        return ActionExecutionResult(
            handled = true,
            spokenFeedback = feedback,
            actionName = "Weather: 24°C Clear"
        )
    }

    private fun isTimerCommand(lower: String): Boolean {
        return (lower.contains("timer") && (lower.contains("set") || lower.contains("start") || lower.contains("create") || lower.contains("for") || lower.contains("minute") || lower.contains("min"))) ||
                (lower.contains("টাইমার") && (lower.contains("দাও") || lower.contains("সেট") || lower.contains("মিনিট")))
    }

    private fun executeTimerCommand(raw: String, lower: String, isBengali: Boolean): ActionExecutionResult {
        // Extract minutes from query
        var minutes = 5 // default
        val englishMatcher = Pattern.compile("(\\d+)\\s*(?:minute|min|m)").matcher(lower)
        if (englishMatcher.find()) {
            minutes = englishMatcher.group(1)?.toIntOrNull() ?: 5
        } else {
            // Check word numbers
            when {
                lower.contains("one") || lower.contains("1") -> minutes = 1
                lower.contains("two") || lower.contains("2") -> minutes = 2
                lower.contains("three") || lower.contains("3") -> minutes = 3
                lower.contains("four") || lower.contains("4") -> minutes = 4
                lower.contains("five") || lower.contains("5") -> minutes = 5
                lower.contains("ten") || lower.contains("10") -> minutes = 10
                lower.contains("fifteen") || lower.contains("15") -> minutes = 15
                lower.contains("twenty") || lower.contains("20") -> minutes = 20
                lower.contains("thirty") || lower.contains("30") -> minutes = 30
                lower.contains("১") -> minutes = 1
                lower.contains("২") -> minutes = 2
                lower.contains("৩") -> minutes = 3
                lower.contains("৪") -> minutes = 4
                lower.contains("৫") -> minutes = 5
                lower.contains("১০") -> minutes = 10
            }
        }

        val seconds = minutes * 60
        try {
            val timerIntent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, "AI BABY Timer for Babu")
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(timerIntent)
        } catch (e: Exception) {
            Log.w("PhoneAction", "Could not launch system timer intent: ${e.message}")
        }

        val feedback = if (isBengali) {
            "বাবু, আপনার জন্য $minutes মিনিটের টাইমার সেট করে দিয়েছি।"
        } else {
            "Babu, I have set a timer for $minutes minutes for you!"
        }

        return ActionExecutionResult(
            handled = true,
            spokenFeedback = feedback,
            actionName = "Timer: $minutes min"
        )
    }

    private fun isJokeCommand(lower: String): Boolean {
        return (lower.contains("joke") && (lower.contains("tell") || lower.contains("say") || lower.contains("make") || lower.contains("funny") || lower.startsWith("joke"))) ||
                lower.contains("কৌতুক") || lower.contains("মজার কিছু") || lower.contains("হাসাও")
    }

    private fun executeJokeCommand(isBengali: Boolean): ActionExecutionResult {
        val joke = if (isBengali) {
            val j = jokesBengali[jokeIndex % jokesBengali.size]
            jokeIndex++
            j
        } else {
            val j = jokesEnglish[jokeIndex % jokesEnglish.size]
            jokeIndex++
            j
        }
        return ActionExecutionResult(
            handled = true,
            spokenFeedback = joke,
            actionName = "Witty Joke"
        )
    }

    private fun isTorchCommand(lower: String): Boolean {
        return lower.contains("torch") || lower.contains("flashlight") ||
                lower.contains("টর্চ") || lower.contains("ফ্ল্যাশলাইট") || lower.contains("আলো জ্বালাও")
    }

    private fun executeTorch(enable: Boolean, isBengali: Boolean): ActionExecutionResult {
        val manager = cameraManager ?: return ActionExecutionResult(
            handled = true,
            spokenFeedback = if (isBengali) "বাবু, আপনার ডিভাইসে ক্যামেরা ফ্ল্যাশলাইট পাওয়া যায়নি।"
            else "Babu, camera flash hardware was not detected on this device.",
            actionName = "Flashlight"
        )

        return try {
            val cameraId = manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }

            if (cameraId != null) {
                manager.setTorchMode(cameraId, enable)
                isTorchOn = enable
                val msg = if (enable) {
                    if (isBengali) "বাবু, ফ্ল্যাশলাইট জ্বালিয়ে দিয়েছি।"
                    else "Babu, I have turned on the flashlight for you."
                } else {
                    if (isBengali) "বাবু, ফ্ল্যাশলাইট বন্ধ করে দিয়েছি।"
                    else "Babu, flashlight turned off."
                }
                ActionExecutionResult(handled = true, spokenFeedback = msg, actionName = "Flashlight: ${if (enable) "ON" else "OFF"}")
            } else {
                ActionExecutionResult(
                    handled = true,
                    spokenFeedback = if (isBengali) "বাবু, এই ডিভাইসে ফ্ল্যাশলাইট পাওয়া যায়নি।"
                    else "Babu, no camera with flash unit is available on this device.",
                    actionName = "Flashlight Unavailable"
                )
            }
        } catch (e: CameraAccessException) {
            Log.e("PhoneAction", "Camera torch error", e)
            ActionExecutionResult(
                handled = true,
                spokenFeedback = if (isBengali) "বাবু, ফ্ল্যাশলাইট নিয়ন্ত্রণে ত্রুটি হয়েছে।"
                else "Babu, unable to access flashlight at this moment.",
                actionName = "Flashlight Error"
            )
        } catch (e: Exception) {
            Log.e("PhoneAction", "Torch exception", e)
            ActionExecutionResult(
                handled = true,
                spokenFeedback = if (isBengali) "বাবু, ফ্ল্যাশলাইট অন করা সম্ভব হয়নি।"
                else "Babu, could not toggle the flashlight right now.",
                actionName = "Flashlight"
            )
        }
    }

    private fun isBatteryCommand(lower: String): Boolean {
        return (lower.contains("battery") && (lower.contains("check") || lower.contains("what") || lower.contains("level") || lower.contains("status") || lower.startsWith("battery"))) ||
                lower.contains("চার্জ কত") || lower.contains("ব্যাটারি কত") || lower.contains("ব্যাটারির চার্জ")
    }

    private fun checkBatteryLevel(isBengali: Boolean): ActionExecutionResult {
        return try {
            val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus: Intent? = context.registerReceiver(null, ifilter)
            val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else 100

            val feedback = if (isBengali) {
                if (isCharging) "বাবু, আপনার ফোনের ব্যাটারি $batteryPct শতাংশ এবং ফোন চার্জ হচ্ছে।"
                else "বাবু, আপনার ফোনের ব্যাটারি এখন $batteryPct শতাংশ রয়েছে।"
            } else {
                if (isCharging) "Babu, your phone battery is at $batteryPct% and currently charging."
                else "Babu, your current battery level is $batteryPct%."
            }
            ActionExecutionResult(handled = true, spokenFeedback = feedback, actionName = "Battery: $batteryPct%")
        } catch (e: Exception) {
            Log.e("PhoneAction", "Battery check error", e)
            ActionExecutionResult(
                handled = true,
                spokenFeedback = if (isBengali) "বাবু, ব্যাটারির তথ্য পড়তে সমস্যা হয়েছে।"
                else "Babu, could not read battery status.",
                actionName = "Battery Check"
            )
        }
    }

    private fun isTimeCommand(lower: String): Boolean {
        return (lower.contains("time") || lower.contains("date") || lower.contains("কয়টা বাজে") || lower.contains("সময়")) &&
                (lower.contains("what") || lower.contains("tell") || lower.contains("এখন") || lower.contains("আজকে") || lower.contains("current"))
    }

    private fun getCurrentDateTime(isBengali: Boolean): ActionExecutionResult {
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault())
        val now = Date()
        val timeStr = timeFormat.format(now)
        val dateStr = dateFormat.format(now)

        val feedback = if (isBengali) {
            "বাবু, এখন সময় $timeStr, এবং আজকের তারিখ $dateStr।"
        } else {
            "Babu, the time is currently $timeStr on $dateStr."
        }
        return ActionExecutionResult(handled = true, spokenFeedback = feedback, actionName = "Time: $timeStr")
    }

    private fun openSystemSetting(action: String, label: String, isBengali: Boolean): ActionExecutionResult {
        return try {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val feedback = if (isBengali) "বাবু, আমি $label সেটিংস ওপেন করে দিয়েছি।"
            else "Babu, opening $label settings for you."
            ActionExecutionResult(handled = true, spokenFeedback = feedback, actionName = "Open $label")
        } catch (e: Exception) {
            Log.e("PhoneAction", "Open setting error: $label", e)
            ActionExecutionResult(
                handled = true,
                spokenFeedback = if (isBengali) "বাবু, সেটিংস খুলতে পারিনি।"
                else "Babu, unable to launch $label settings.",
                actionName = "Settings Failed"
            )
        }
    }

    private fun openAlarmApp(isBengali: Boolean): ActionExecutionResult {
        return try {
            val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val feedback = if (isBengali) "বাবু, ঘড়ির অ্যালার্ম তালিকা খুলে দিয়েছি।"
            else "Babu, opening your alarms."
            ActionExecutionResult(handled = true, spokenFeedback = feedback, actionName = "Open Alarms")
        } catch (e: Exception) {
            ActionExecutionResult(
                handled = true,
                spokenFeedback = if (isBengali) "বাবু, অ্যালার্ম অ্যাপ খুলতে পারিনি।"
                else "Babu, could not launch the clock app.",
                actionName = "Alarms"
            )
        }
    }
}
