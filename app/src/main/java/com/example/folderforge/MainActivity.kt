package com.aryan.folderforge

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Switch
import android.widget.RadioGroup
import android.widget.RadioButton
import android.view.HapticFeedbackConstants
import android.media.AudioManager
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.File
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)

    private var zipUri: Uri? = null
    private var destinationTreeUri: Uri? = null
    private var zipLabel: TextView? = null
    private var destLabel: TextView? = null
    private var progress: ProgressBar? = null
    private var status: TextView? = null
    private var applyButton: Button? = null
    private var pickZipButton: Button? = null
    private var pickDestButton: Button? = null
    private var processingTitle: TextView? = null
    private var processingStatus: TextView? = null
    private var processingPercent: TextView? = null
    private var processingProgress: ProgressBar? = null
    private var processingWavy: WavyProgressView? = null
    private var processingPanel: LinearLayout? = null
    private var homeContent: View? = null
    private var processingStartedAt: Long = 0L
    private var processingElapsed: TextView? = null
    private var processingSteps: MutableList<TextView> = mutableListOf()
    private var processingResultCard: LinearLayout? = null
    private var processingResultTitle: TextView? = null
    private var processingResultMessage: TextView? = null
    private var processingReturnButton: Button? = null
    private var timerRunnable: Runnable? = null
    private var operationTitle: String = "Manual extraction"
    private var pendingAutoZipName: String? = null
    private var pendingAutoGame: String? = null
    private var pendingWholePack: Boolean = false
    private var pendingRemoveGame: String? = null
    private var pendingRemoveDestination: String? = null
    private var pendingRemoveZipPath: String? = null
    private var currentScreen: String = "home"
    private var resourceDownloadProgress: ProgressBar? = null
    private var resourceDownloadPercent: TextView? = null
    private var resourceDownloadSize: TextView? = null
    private var resourceDownloadStatus: TextView? = null
    private var resourceApplyButton: Button? = null
    private var resourceSelectedGame: String? = null
    private var resourceSelectedZipName: String? = null
    private var resourceDownloadScreen: LinearLayout? = null
    private var resourceSourceZip: File? = null
    private val driveResourceUrl = "https://drive.google.com/uc?export=download&id=1HydQLstWXzLao-QC4fKB2-X--cYrkKoi&confirm=t"
    private val prefs by lazy { getSharedPreferences("folderforge_settings", MODE_PRIVATE) }
    private var fileOps: IFileOps? = null
    private var shizukuBound = false
    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(packageName, FileOpsService::class.java.name)
        ).processNameSuffix("fileops")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }
    private val shizukuConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            fileOps = IFileOps.Stub.asInterface(binder)
            shizukuBound = true
            main.post {
                if (pendingRemoveDestination != null) runRemoveMagic()
                else confirmAutomaticApply()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            fileOps = null
            shizukuBound = false
        }
    }
    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == REQUEST_SHIZUKU) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    bindShizukuService()
                } else {
                    clearPendingAutomatic()
                    toast("Shizuku permission denied.")
                }
            }
        }

    private val isDark: Boolean
        get() = when (prefs.getString("appearance", "light")) {
            "dark" -> true
            "light" -> false
            else -> (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) != android.content.res.Configuration.UI_MODE_NIGHT_NO
        }
    private val bg: Int get() = if (isDark) Color.rgb(17, 19, 24) else Color.WHITE
    private val surface: Int get() = if (isDark) Color.rgb(29, 32, 40) else Color.WHITE
    private val surface2: Int get() = if (isDark) Color.rgb(43, 47, 58) else Color.rgb(232, 236, 246)
    private val blue: Int get() = if (isDark) Color.rgb(174, 197, 255) else Color.rgb(55, 87, 190)
    private val white: Int get() = if (isDark) Color.rgb(239, 242, 250) else Color.rgb(27, 31, 42)
    private val muted: Int get() = if (isDark) Color.rgb(174, 181, 197) else Color.rgb(91, 99, 117)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureAppMediaDirectory()
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        if (prefs.getBoolean("intro_enabled", true)) showIntro() else showHome()
    }

    private fun ensureAppMediaDirectory(): java.io.File? {
        val mediaDir = getExternalMediaDirs()
            .firstOrNull { it != null }
            ?: return null

        return try {
            if (mediaDir.exists() || mediaDir.mkdirs()) {
                mediaDir
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun applySystemBars() {
        window.statusBarColor = bg
        window.navigationBarColor = bg
        var flags = 0
        if (!isDark) {
            flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            if (android.os.Build.VERSION.SDK_INT >= 26) flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        window.decorView.systemUiVisibility = flags
    }

    private fun showScreen(view: View) {
        applySystemBars()
        setContentView(view)
        if (prefs.getBoolean("animations_enabled", true)) {
            view.alpha = 0f
            view.translationY = dp(8).toFloat()
            view.animate().alpha(1f).translationY(0f).setDuration(220L).start()
        } else {
            view.alpha = 1f
            view.translationY = 0f
        }
    }

    private fun showIntro() {
        currentScreen = "intro"
        val root = FrameLayout(this).apply { setBackgroundColor(bg) }
        val stack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val logo = ImageView(this).apply {
            setImageResource(com.aryan.folderforge.R.drawable.aryanforge_logo_transparent)
            contentDescription = "AryanForge logo"
            scaleType = ImageView.ScaleType.FIT_CENTER
            alpha = 0f
            scaleX = 0.78f
            scaleY = 0.78f
        }
        stack.addView(logo, LinearLayout.LayoutParams(dp(104), dp(104)))
        stack.addView(TextView(this).apply {
            text = "AryanForge"
            textSize = 30f
            setTextColor(white)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(5))
        })
        stack.addView(TextView(this).apply {
            text = "Smart file replacement"
            textSize = 14f
            setTextColor(muted)
            gravity = Gravity.CENTER
        })
        root.addView(stack, FrameLayout.LayoutParams(-1, -1))
        applySystemBars()
        setContentView(root)
        if (prefs.getBoolean("animations_enabled", true)) {
            logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(650L).start()
        } else { logo.alpha = 1f; logo.scaleX = 1f; logo.scaleY = 1f }
        main.postDelayed({ if (!isFinishing && currentScreen == "intro") showHome() }, 1350L)
    }

    private fun feedback(view: View) {
        if (prefs.getBoolean("haptics_enabled", true)) {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
        if (prefs.getBoolean("click_sounds_enabled", true)) {
            try { (getSystemService(AUDIO_SERVICE) as AudioManager).playSoundEffect(AudioManager.FX_KEY_CLICK) } catch (_: Exception) {}
        }
    }

    private fun showHome() {
        currentScreen = "home"
        processingWavy = null
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(20))
            setBackgroundColor(bg)
        }
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; isHorizontalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER; isFillViewport = false }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(content)

        content.addView(ImageView(this).apply {
            setImageResource(com.aryan.folderforge.R.drawable.aryanforge_logo_transparent)
            contentDescription = "AryanForge logo"
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(dp(64), dp(64)).apply { bottomMargin = dp(8) })

        content.addView(TextView(this).apply {
            text = "AryanForge"
            textSize = 30f
            setTextColor(white)
            typeface = Typeface.DEFAULT_BOLD
        })
        content.addView(TextView(this).apply {
            text = "Smart file replacement"
            textSize = 14f
            setTextColor(muted)
            setPadding(0, dp(4), 0, dp(18))
        })
        val quickRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val shizukuChip = TextView(this).apply {
            val running = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
            val permitted = running && runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
            text = when { permitted -> "●  SHIZUKU READY"; running -> "●  PERMISSION NEEDED"; else -> "●  SHIZUKU STOPPED" }
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(when { permitted -> Color.rgb(22, 132, 72); running -> Color.rgb(153, 101, 0); else -> Color.rgb(198, 40, 40) })
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = rounded(surface2, dp(18))
            setOnClickListener { feedback(this); showSettings() }
        }
        quickRow.addView(shizukuChip, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(quickRow, matchWrap(dp(14)))

        val statusCard = card()
        statusCard.addView(TextView(this).apply {
            text = "DEVICE ACCESS"
            textSize = 11f
            letterSpacing = 0.08f
            setTextColor(muted)
            typeface = Typeface.DEFAULT_BOLD
        })
        statusCard.addView(TextView(this).apply {
            val running = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
            val permitted = running && runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
            text = when { permitted -> "Shizuku ready"; running -> "Permission required"; else -> "Shizuku not running" }
            textSize = 16f
            setTextColor(when { permitted -> Color.rgb(22, 132, 72); running -> Color.rgb(153, 101, 0); else -> Color.rgb(198, 40, 40) })
            setPadding(0, dp(7), 0, dp(3))
        })
        statusCard.addView(TextView(this).apply {
            text = when { !Shizuku.pingBinder() -> "Start Shizuku to enable automatic game-folder actions."; Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> "Grant AryanForge permission in Shizuku."; else -> "Ready for automatic game-folder actions." }
            textSize = 12f
            setTextColor(muted)
        })
        content.addView(statusCard, matchWrap(dp(14)))

        content.addView(section("AUTOMATIC ACTIONS"))
        addHomeAction(content, "game", "APPLY MAGIC FFMAX",
            "Free Fire MAX resources", logoRes = com.aryan.folderforge.R.drawable.free_fire_max_logo) {
            beginAutomaticApply("aryansmagic2.zip", "Free Fire MAX")
        }
        addHomeAction(content, "game", "APPLY MAGIC FF NORMAL",
            "Free Fire resources", logoRes = com.aryan.folderforge.R.drawable.free_fire_logo) {
            beginAutomaticApply("aryansmagic1.zip", "Free Fire")
        }
        addHomeAction(content, "−", "REMOVE MAGIC",
            "Remove modified game assets") { showRemoveMagic() }
        addHomeAction(content, "archive", "WHOLE RESOURCE PACK",
            "Choose a game with a visual selection") {
            showWholePackSelection()
        }

        content.addView(section("TOOLS"))
        addHomeAction(content, "⇄", "APPLY MANUALLY",
            "Choose your own ZIP and destination") {
            showManual()
        }
        addHomeAction(content, "⚙", "SETTINGS & ABOUT",
            "Appearance, interactions and app details") { showSettings() }

        content.addView(TextView(this).apply {
            text = "An Android Application made on an Android by an Aryan"
            textSize = 12f
            setTextColor(muted)
            setPadding(2, dp(18), 2, dp(8))
        })
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        showScreen(root)
    }


    private fun showWholePackSelection() {
        currentScreen = "whole-selection"
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(22))
            setBackgroundColor(bg)
        }
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        content.addView(button("‹   BACK TO HOME", false) { showHome() }, matchWrap(dp(18)))
        content.addView(TextView(this).apply {
            text = "Choose your game"
            textSize = 28f
            setTextColor(white)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(18), 0, dp(6))
        })
        content.addView(info("Select a resource pack. The selected game will be shown on the next screen."))
        addHomeAction(content, "game", "FREE FIRE", "Full resource pack · Standard", logoRes = com.aryan.folderforge.R.drawable.free_fire_logo) {
            startResourcePackFlow("Free Fire", "fullresourceff.zip")
        }
        addHomeAction(content, "game", "FREE FIRE MAX", "Full resource pack · MAX", logoRes = com.aryan.folderforge.R.drawable.free_fire_max_logo) {
            startResourcePackFlow("Free Fire MAX", "fullresourceffm.zip")
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        showScreen(root)
    }

    private fun showStyledDialog(
        title: String,
        message: String,
        positiveLabel: String,
        negativeLabel: String? = null,
        onPositive: (() -> Unit)? = null,
        onNegative: (() -> Unit)? = null
    ) {
        val dialog = Dialog(this)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(18))
            background = rounded(surface, dp(28))
            elevation = dp(12).toFloat()
        }
        val symbol = TextView(this).apply {
            text = when {
                title.contains("missing", true) || title.contains("not found", true) -> "!"
                title.contains("failed", true) || title.contains("error", true) -> "×"
                title.contains("success", true) -> "✓"
                else -> "✦"
            }
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(blue)
            background = rounded(surface2, dp(17))
        }
        panel.addView(symbol, LinearLayout.LayoutParams(dp(50), dp(50)))
        panel.addView(TextView(this).apply {
            text = title
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(white)
            setPadding(0, dp(14), 0, dp(7))
        })
        panel.addView(TextView(this).apply {
            text = message
            textSize = 14f
            setTextColor(muted)
            setLineSpacing(dp(2).toFloat(), 1f)
        })
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(0, dp(20), 0, 0)
        }
        if (negativeLabel != null) actions.addView(button(negativeLabel, false) { dialog.dismiss(); onNegative?.invoke() }, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(8) })
        actions.addView(button(positiveLabel, true) { dialog.dismiss(); onPositive?.invoke() })
        panel.addView(actions)
        dialog.setContentView(panel)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setDimAmount(0.62f)
        dialog.setOnShowListener {
            dialog.window?.setLayout((resources.displayMetrics.widthPixels - dp(36)).coerceAtMost(dp(420)), -2)
            if (prefs.getBoolean("animations_enabled", true)) {
                panel.alpha = 0f; panel.scaleX = 0.94f; panel.scaleY = 0.94f
                panel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(190L).start()
            }
        }
        dialog.show()
    }

    private fun showResourceHelp(zipName: String) {
        showStyledDialog(
            "Add the resource pack",
            "Place $zipName in AryanForge's app media folder, then return and try again.",
            "GOT IT", "CLOSE"
        )
    }

    private fun showSettings() {
        currentScreen = "settings"
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(20))
            setBackgroundColor(bg)
        }
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; isHorizontalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER; isFillViewport = false }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        content.addView(button("‹   BACK", false) { showHome() }, matchWrap(dp(12)))
        content.addView(TextView(this).apply {
            text = "Settings"
            textSize = 30f
            setTextColor(white)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(0, dp(8), 0, dp(4))
        })
        content.addView(info("Make AryanForge feel right for you."))

        val appearanceCard = card()
        appearanceCard.addView(section("APPEARANCE"))
        appearanceCard.addView(TextView(this).apply {
            text = "Theme"
            textSize = 16f
            setTextColor(white)
            typeface = Typeface.DEFAULT_BOLD
        })
        appearanceCard.addView(info("Choose how the app looks."))
        val group = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val options = listOf("system" to "Follow system", "light" to "Light", "dark" to "Dark")
        val radioIds = mutableMapOf<String, Int>()
        options.forEach { (key, label) ->
            val radio = RadioButton(this).apply {
                id = View.generateViewId()
                text = label
                textSize = 14f
                setTextColor(white)
                buttonTintList = android.content.res.ColorStateList.valueOf(blue)
                isChecked = prefs.getString("appearance", "light") == key
            }
            radioIds[key] = radio.id
            group.addView(radio, matchWrap(dp(2)))
        }
        group.setOnCheckedChangeListener { _, checkedId ->
            val key = radioIds.entries.firstOrNull { it.value == checkedId }?.key ?: "system"
            prefs.edit().putString("appearance", key).apply()
            feedback(group)
            showSettings()
        }
        appearanceCard.addView(group)
        content.addView(appearanceCard, matchWrap(dp(12)))

        val interactionCard = card()
        interactionCard.addView(section("INTERACTIONS"))
        addPreferenceSwitch(interactionCard, "click_sounds_enabled", "System click sounds", "Play Android's standard click sound on interactions.", true)
        addPreferenceSwitch(interactionCard, "haptics_enabled", "Haptic feedback", "Use a light vibration on supported controls.", true)
        content.addView(interactionCard, matchWrap(dp(12)))

        val accessCard = card()
        accessCard.addView(section("DEVICE ACCESS"))
        val connected = Shizuku.pingBinder()
        accessCard.addView(TextView(this).apply {
            text = if (connected) "Shizuku is running" else "Shizuku is not running"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (connected) Color.rgb(67, 177, 126) else white)
        })
        accessCard.addView(info(if (connected && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) "Permission granted to AryanForge." else if (connected) "Shizuku is running, but permission may be needed for automatic actions." else "Start Shizuku, then return here to refresh its status."))
        accessCard.addView(button("Refresh status", false) {
            feedback(accessCard)
            val runningNow = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
            val allowedNow = runningNow && runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
            showSettings()
            toast(when { allowedNow -> "Shizuku ready."; runningNow -> "Shizuku is running; permission is required."; else -> "Shizuku is not running." })
        }, matchWrap(dp(8)))
        content.addView(accessCard, matchWrap(dp(12)))

        val updateCard = card()
        updateCard.addView(section("UPDATES"))
        updateCard.addView(TextView(this).apply {
            text = "AryanForge ${BuildConfig.VERSION_NAME}"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(white)
        })
        updateCard.addView(info("Check GitHub Releases for a newer version. Downloaded updates are installed through Android, with your approval."))
        updateCard.addView(button("Check for updates", true) { checkForUpdates() }, matchWrap(dp(8)))
        content.addView(updateCard, matchWrap(dp(12)))

        val aboutCard = card()
        aboutCard.addView(section("ABOUT"))
        aboutCard.addView(TextView(this).apply {
            text = "AryanForge"
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(white)
        })
        aboutCard.addView(info("Created by Aryan"))
        aboutCard.addView(TextView(this).apply {
            text = "An Android Application made on an Android by an Aryan"
            textSize = 10f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(2))
        })
        content.addView(aboutCard, matchWrap(dp(12)))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        showScreen(root)
    }

    private fun checkForUpdates() {
        if (UPDATE_API_URL.contains("/OWNER/REPO/")) {
            AlertDialog.Builder(this)
                .setTitle("Updater setup needed")
                .setMessage("Set your GitHub username and repository in MainActivity.kt before checking releases.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        toast("Checking for updates…")
        worker.execute {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(UPDATE_API_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "AryanForge-Android")
                }
                val code = connection.responseCode
                if (code !in 200..299) throw IOException("GitHub returned HTTP $code")
                val release = JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
                if (release.optBoolean("draft", false) || release.optBoolean("prerelease", false)) {
                    throw IOException("No stable release available")
                }
                val tag = release.optString("tag_name", "").removePrefix("v")
                val assets = release.optJSONArray("assets")
                var apkUrl: String? = null
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url").takeIf { it.startsWith("https://") }
                            break
                        }
                    }
                }
                if (tag.isBlank() || apkUrl == null) throw IOException("Stable release or APK asset missing")
                val latest = tag.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
                val current = BuildConfig.VERSION_NAME.removePrefix("v").substringBefore('-')
                    .split('.').map { it.toIntOrNull() ?: 0 }
                val comparison = compareVersions(latest, current)
                main.post {
                    if (comparison <= 0) {
                        AlertDialog.Builder(this)
                            .setTitle("You're up to date")
                            .setMessage("AryanForge ${BuildConfig.VERSION_NAME} is the latest version available.")
                            .setPositiveButton("OK", null)
                            .show()
                    } else {
                        AlertDialog.Builder(this)
                            .setTitle("Update available")
                            .setMessage("AryanForge $tag is available. Download the APK? Android may ask you to approve installation.")
                            .setNegativeButton("Later", null)
                            .setPositiveButton("Download") { _, _ -> downloadUpdate(apkUrl!!, tag) }
                            .show()
                    }
                }
            } catch (e: Exception) {
                val reason = e.message.orEmpty()
                val noRelease = reason.contains("HTTP 404", ignoreCase = true) ||
                    reason.contains("Stable release or APK asset missing", ignoreCase = true)
                main.post {
                    if (noRelease) {
                        showStyledDialog(
                            "No update release found",
                            "GitHub did not return a published stable release with an APK. This is not necessarily an internet problem. You can check the repository's Releases page.",
                            "OPEN GITHUB", "CLOSE",
                            onPositive = {
                                runCatching {
                                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/arr-stacks/AryanForge/releases")))
                                }.onFailure { toast("Could not open GitHub Releases.") }
                            }
                        )
                    } else {
                        showStyledDialog(
                            "Couldn't check updates",
                            "The update check failed because of a connection or server error. Try again later.",
                            "OK"
                        )
                    }
                }
            } finally {
                connection?.disconnect()
            }
        }
    }

    private var pendingInstallUri: Uri? = null

    override fun onResume() {
        super.onResume()
        val pending = pendingInstallUri
        if (pending != null &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            packageManager.canRequestPackageInstalls()
        ) {
            pendingInstallUri = null
            launchApkInstaller(pending)
        }
    }

    private fun launchApkInstaller(uri: Uri) {
        try {
            val install = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(install)
        } catch (e: Exception) {
            toast("Could not open APK installer: ${e.message ?: "unknown error"}")
        }
    }

    private fun downloadUpdate(apkUrl: String, version: String) {
        var receiver: android.content.BroadcastReceiver? = null
        try {
            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadReceiver = object : android.content.BroadcastReceiver() {
                var expectedDownloadId: Long = -1L

                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.getLongExtra(
                            DownloadManager.EXTRA_DOWNLOAD_ID, -1L
                        ) != expectedDownloadId
                    ) return

                    try {
                        unregisterReceiver(this)
                    } catch (_: Exception) {
                    }

                    val query = DownloadManager.Query().setFilterById(expectedDownloadId)
                    val cursor = manager.query(query)
                    var successful = false
                    var reason = 0
                    cursor.use {
                        if (it.moveToFirst()) {
                            successful = it.getInt(
                                it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                            ) == DownloadManager.STATUS_SUCCESSFUL
                            reason = it.getInt(
                                it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
                            )
                        }
                    }

                    if (!successful) {
                        toast("Update download failed (reason $reason). Please try again.")
                        return
                    }

                    val downloaded = manager.getUriForDownloadedFile(expectedDownloadId)
                    if (downloaded == null) {
                        toast("Downloaded APK could not be opened.")
                        return
                    }

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                        !packageManager.canRequestPackageInstalls()
                    ) {
                        pendingInstallUri = downloaded
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Allow APK installation")
                            .setMessage("Allow AryanForge to install this downloaded update in Android settings. Return here afterward to continue.")
                            .setNegativeButton("Later", null)
                            .setPositiveButton("Open settings") { _, _ ->
                                try {
                                    startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                            Uri.parse("package:$packageName")
                                        )
                                    )
                                } catch (e: Exception) {
                                    toast("Could not open installation settings.")
                                }
                            }
                            .show()
                    } else {
                        launchApkInstaller(downloaded)
                    }
                }
            }
            receiver = downloadReceiver

            if (android.os.Build.VERSION.SDK_INT >= 33) {
                registerReceiver(
                    downloadReceiver,
                    android.content.IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    Context.RECEIVER_EXPORTED
                )
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(
                    downloadReceiver,
                    android.content.IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                )
            }

            val request = DownloadManager.Request(Uri.parse(apkUrl))
                .setTitle("AryanForge update $version")
                .setDescription("Downloading update APK")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                .setDestinationInExternalFilesDir(
                    this, Environment.DIRECTORY_DOWNLOADS, "AryanForge-$version.apk"
                )

            downloadReceiver.expectedDownloadId = manager.enqueue(request)
            toast("Download started. You'll get a notification when it's ready.")
        } catch (e: Exception) {
            try {
                receiver?.let { unregisterReceiver(it) }
            } catch (_: Exception) {
            }
            toast("Could not start download: ${e.message ?: "unknown error"}")
        }
    }

    private fun compareVersions(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(i) { 0 }
            val right = b.getOrElse(i) { 0 }
            if (left != right) return left.compareTo(right)
        }
        return 0
    }

    private fun addPreferenceSwitch(parent: LinearLayout, key: String, title: String, summary: String, defaultValue: Boolean) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(TextView(this).apply { text = title; textSize = 15f; setTextColor(white); typeface = Typeface.DEFAULT_BOLD })
        labels.addView(TextView(this).apply { text = summary; textSize = 12f; setTextColor(muted); setPadding(0, dp(3), dp(8), 0) })
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        val toggle = Switch(this).apply {
            isChecked = prefs.getBoolean(key, defaultValue)
            buttonTintList = android.content.res.ColorStateList.valueOf(blue)
            setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean(key, checked).apply(); feedback(this) }
        }
        row.addView(toggle)
        parent.addView(row, matchWrap(dp(2)))
    }

    private fun startResourcePackFlow(game: String, finalZipName: String) {
        if (busy.get()) return
        val dir = ensureAppMediaDirectory() ?: run { toast("Cannot access resource storage."); return }
        val existing = File(dir, finalZipName)
        if (existing.isFile && existing.length() > 0L) {
            showStyledDialog("Resource pack ready", "$finalZipName is already available. Continue to apply it to $game?", "APPLY", "CANCEL",
                onPositive = { beginAutomaticApply(finalZipName, game, wholePack = true) })
            return
        }
        resourceSelectedGame = game
        resourceSelectedZipName = finalZipName
        showResourceDownloadScreen(game)
        showStyledDialog("Download resource pack?", "AryanForge will download the combined resource archive, prepare both game ZIPs, and keep only verified final packs.", "DOWNLOAD", "CANCEL",
            onPositive = { startCombinedResourceDownload() },
            onNegative = { showWholePackSelection() })
    }

    private fun showResourceDownloadScreen(game: String) {
        currentScreen = "resource-download"
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(22))
            setBackgroundColor(Color.BLACK)
        }
        val top = View(this).apply { setBackgroundColor(Color.rgb(38,38,38)) }
        root.addView(top, LinearLayout.LayoutParams(-1, dp(1)).apply { bottomMargin = dp(24) })
        root.addView(TextView(this).apply {
            text = "Downloading Resource Pack"
            textSize = 27f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(12), 0, dp(28))
        })
        val sizeLine = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        resourceDownloadSize = TextView(this).apply { text = "Downloaded: 0 B / checking size…"; textSize = 15f; setTextColor(Color.LTGRAY) }
        resourceDownloadPercent = TextView(this).apply { text = "0%"; textSize = 21f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(0, 200, 110)); gravity = Gravity.END }
        sizeLine.addView(resourceDownloadSize, LinearLayout.LayoutParams(0, -2, 1f))
        sizeLine.addView(resourceDownloadPercent)
        root.addView(sizeLine, matchWrap(dp(22)))
        resourceDownloadProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progress = 0; progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(0, 160, 105));
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(48,48,48))
        }
        root.addView(resourceDownloadProgress, matchWrap(dp(14)))
        resourceDownloadStatus = TextView(this).apply {
            text = "Selected: $game · waiting for confirmation"
            textSize = 13f; setTextColor(Color.LTGRAY); setPadding(0, dp(14), 0, dp(14))
        }
        root.addView(resourceDownloadStatus, matchWrap())
        resourceApplyButton = button("APPLY RESOURCE PACK", true) {
            val name = resourceSelectedZipName ?: return@button
            val selected = resourceSelectedGame ?: return@button
            beginAutomaticApply(name, selected, wholePack = true)
        }.apply { visibility = View.GONE }
        root.addView(resourceApplyButton, matchWrap(dp(8)))
        root.addView(button("BACK TO HOME", false) { if (!busy.get()) showWholePackSelection() }, matchWrap(dp(8)))
        resourceDownloadScreen = root
        showScreen(root)
    }

    /** Handles the common Google Drive large-file confirmation interstitial. */
    private fun openDriveDownloadConnection(downloadUrl: String): HttpURLConnection {
        fun request(url: String, cookies: String? = null): HttpURLConnection {
            return (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30000
                readTimeout = 60000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/130 Mobile Safari/537.36")
                setRequestProperty("Accept", "*/*")
                if (!cookies.isNullOrBlank()) setRequestProperty("Cookie", cookies)
            }
        }

        val first = request(downloadUrl)
        first.connect()
        if (!first.contentType.orEmpty().contains("text/html", true)) return first

        val cookieHeaders = first.headerFields.entries
            .filter { it.key.equals("Set-Cookie", true) }
            .flatMap { it.value.orEmpty() }
        val cookies = cookieHeaders.mapNotNull { it.substringBefore(';').takeIf { value -> value.contains('=') } }
            .joinToString("; ")
        val warningToken = cookieHeaders.firstNotNullOfOrNull { header ->
            Regex("""download_warning[^=]*=([^;]+)""").find(header)?.groupValues?.getOrNull(1)
        }
        val html = try {
            val stream = if (first.responseCode >= 400) first.errorStream else first.inputStream
            stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText().take(2_000_000) }.orEmpty()
        } catch (_: Exception) { "" }
        val base = first.url.toString()
        first.disconnect()

        val href = Regex("""href=["']([^"']*(?:confirm=)[^"']*)["']""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)?.replace("&amp;", "&")
        val formAction = Regex("""action=["']([^"']*(?:/uc|download)[^"']*)["']""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)?.replace("&amp;", "&")
        val pageToken = Regex("""(?:[?&]confirm=|name=["']confirm["']\s+value=["'])([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)
            .find((href.orEmpty() + " " + formAction.orEmpty() + " " + html))?.groupValues?.getOrNull(1)
        val token = warningToken ?: pageToken
        val retryUrl = when {
            !href.isNullOrBlank() -> URL(URL(base), href).toString()
            !formAction.isNullOrBlank() && token != null -> {
                val action = URL(URL(base), formAction).toString()
                action + (if (action.contains('?')) "&" else "?") + "id=1HydQLstWXzLao-QC4fKB2-X--cYrkKoi&export=download&confirm=" + token
            }
            token != null -> downloadUrl + (if (downloadUrl.contains('?')) "&" else "?") + "confirm=" + token
            else -> throw IOException("Google Drive returned a confirmation page but no download token. Check Drive quota or download the ZIP once in a browser.")
        }
        val retry = request(retryUrl, cookies)
        retry.connect()
        if (retry.responseCode !in 200..299 || retry.contentType.orEmpty().contains("text/html", true)) {
            retry.disconnect()
            throw IOException("Google Drive did not provide the ZIP after confirmation. The file may be over its download quota.")
        }
        return retry
    }

    private fun startCombinedResourceDownload() {
        if (!busy.compareAndSet(false, true)) return
        val dir = ensureAppMediaDirectory() ?: run { busy.set(false); toast("Cannot access resource storage."); return }
        val source = File(cacheDir, "aryanforge-resource-source.zip")
        resourceSourceZip = source
        resourceApplyButton?.visibility = View.GONE
        resourceDownloadProgress?.progress = 0
        resourceDownloadPercent?.text = "0%"
        resourceDownloadSize?.text = "Downloaded: 0 B / checking size…"
        resourceDownloadStatus?.text = "Downloading combined resource archive…"
        worker.execute {
            try {
                source.delete()
                val conn = openDriveDownloadConnection(driveResourceUrl)
                val code = conn.responseCode
                if (code !in 200..299) throw IOException("Google Drive returned HTTP $code")
                val type = conn.contentType.orEmpty()
                if (type.contains("text/html", true)) {
                    conn.disconnect()
                    throw IOException("Google Drive still returned a confirmation page. The file may require another confirmation or its download quota may be exceeded.")
                }
                val total = conn.contentLengthLong
                var got = 0L
                conn.inputStream.buffered().use { input ->
                    FileOutputStream(source).buffered().use { output ->
                        val buffer = ByteArray(256 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n); got += n
                            val pct = if (total > 0) ((got * 100L) / total).toInt().coerceIn(0, 100) else -1
                            main.post {
                                resourceDownloadProgress?.isIndeterminate = total <= 0
                                if (pct >= 0) resourceDownloadProgress?.progress = pct
                                resourceDownloadPercent?.text = if (pct >= 0) "$pct%" else "…"
                                resourceDownloadSize?.text = "Downloaded: ${formatBytes(got)} / ${if (total > 0) formatBytes(total) else "unknown"}"
                            }
                        }
                        output.flush()
                    }
                }
                conn.disconnect()
                if (source.length() < 22L) throw IOException("Downloaded file is too small to be a ZIP archive.")
                main.post { resourceDownloadStatus?.text = "Download complete. Preparing FF and FF MAX packs…"; resourceDownloadPercent?.text = "100%"; resourceDownloadProgress?.isIndeterminate = true }
                prepareBothResourcePacks(source, dir)
                source.delete()
                main.post {
                    busy.set(false)
                    resourceDownloadProgress?.isIndeterminate = false
                    resourceDownloadProgress?.progress = 100
                    resourceDownloadPercent?.text = "100%"
                    resourceDownloadStatus?.text = "Both resource ZIPs prepared and verified. Selected: ${resourceSelectedGame ?: "game"}."
                    resourceApplyButton?.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                // Keep the downloaded source archive after processing failures so the user can retry.
                main.post {
                    busy.set(false)
                    resourceDownloadStatus?.text = "Download or preparation failed: ${e.message ?: "unknown error"}"
                    resourceDownloadPercent?.text = "FAILED"
                    resourceApplyButton?.visibility = View.GONE
                }
            }
        }
    }

    private fun prepareBothResourcePacks(source: File, outputDir: File) {
        val ffTmp = File(outputDir, "fullresourceff.zip.tmp")
        val maxTmp = File(outputDir, "fullresourceffm.zip.tmp")
        ffTmp.delete(); maxTmp.delete()
        var ffEntries = 0; var maxEntries = 0
        ZipInputStream(BufferedInputStream(source.inputStream(), 256 * 1024)).use { zin ->
            ZipOutputStream(BufferedOutputStream(FileOutputStream(ffTmp), 256 * 1024)).use { ffOut ->
                ZipOutputStream(BufferedOutputStream(FileOutputStream(maxTmp), 256 * 1024)).use { maxOut ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        val entry = zin.nextEntry ?: break
                        val raw = entry.name.replace('\\', '/')
                        val lower = raw.lowercase(java.util.Locale.ROOT)
                        val androidMarker = "/android/"
                        val idx = lower.indexOf(androidMarker).let { found -> if (found >= 0) found else if (lower.startsWith("android/")) -1 else -2 }
                        val hasAndroidRoot = idx >= 0 || lower.startsWith("android/")
                        if (hasAndroidRoot && !entry.isDirectory) {
                            val before = if (idx >= 0) lower.substring(0, idx) else ""
                            val rel = if (idx >= 0) raw.substring(idx + androidMarker.length) else raw.substring("android/".length)
                            val segments = rel.split('/').filter { it.isNotEmpty() }
                            val isFf = before.contains("genuine ff normal") || before.contains("com.dts.freefireth")
                            val isMax = before.contains("genuine resource") || before.contains("com.dts.freefiremax")
                            val excludedMapFolder = segments.dropLast(1).any { it.contains("map", true) }
                            val excludedVersion = segments.lastOrNull()?.equals("versioninfo", true) == true
                            val safe = segments.isNotEmpty() && segments.none { it == "." || it == ".." }
                            if (safe && !excludedMapFolder && !excludedVersion && (isFf || isMax)) {
                                val out = if (isFf) ffOut else maxOut
                                val outEntry = ZipEntry(segments.joinToString("/"))
                                out.putNextEntry(outEntry)
                                while (true) { val n = zin.read(buffer); if (n < 0) break; out.write(buffer, 0, n) }
                                out.closeEntry()
                                if (isFf) ffEntries++ else maxEntries++
                            } else {
                                while (zin.read(buffer) >= 0) { /* drain skipped entry */ }
                            }
                        } else {
                            val bufferSkip = ByteArray(64 * 1024)
                            while (zin.read(bufferSkip) >= 0) { /* drain non-target entry */ }
                        }
                        zin.closeEntry()
                    }
                }
            }
        }
        if (ffEntries == 0 || maxEntries == 0) {
            ffTmp.delete(); maxTmp.delete()
            throw IOException("Could not find both FF and FF MAX android resource folders in the downloaded ZIP.")
        }
        verifyResourceZip(ffTmp, "fileinfo")
        verifyResourceZip(maxTmp, "fileinfo")
        val ff = File(outputDir, "fullresourceff.zip")
        val max = File(outputDir, "fullresourceffm.zip")
        if (ff.exists() && !ff.delete()) throw IOException("Could not replace old fullresourceff.zip")
        if (max.exists() && !max.delete()) throw IOException("Could not replace old fullresourceffm.zip")
        if (!ffTmp.renameTo(ff)) throw IOException("Could not finalize Free Fire ZIP")
        if (!maxTmp.renameTo(max)) { ff.delete(); throw IOException("Could not finalize Free Fire MAX ZIP") }
    }

    private fun verifyResourceZip(file: File, requiredFile: String) {
        var found = false
        ZipInputStream(BufferedInputStream(file.inputStream())).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.name.substringAfterLast('/').equals(requiredFile, true)) found = true
                zip.closeEntry()
            }
        }
        if (file.length() < 22 || !found) throw IOException("Generated ${file.name} failed verification: $requiredFile is missing.")
    }

    private fun pendingDialog(game: String) {
        AlertDialog.Builder(this)
            .setTitle(game)
            .setMessage(
                "This automatic action is not enabled yet. " +
                "The ZIP mapping, Shizuku permission and actual destination write access " +
                "must be implemented and tested before it can safely replace game files."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun beginAutomaticApply(zipName: String, game: String, wholePack: Boolean = false) {
        pendingWholePack = wholePack
        if (busy.get()) return
        pendingAutoZipName = zipName
        pendingAutoGame = game

        val mediaDir = ensureAppMediaDirectory()
        if (mediaDir == null) {
            clearPendingAutomatic()
            toast("Cannot access the app media folder.")
            return
        }

        val zip = java.io.File(mediaDir, zipName)
        if (!zip.isFile || !zip.canRead()) {
            clearPendingAutomatic()
            showStyledDialog(
                "Resource pack missing",
                "The required resource file isn't available yet. Add the pack, then try again.",
                "HOW TO FIX", "CANCEL",
                onPositive = { showResourceHelp(zipName) }
            )
            return
        }

        if (!Shizuku.pingBinder()) {
            clearPendingAutomatic()
            toast("Start Shizuku first, then try again.")
            return
        }

        try {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(REQUEST_SHIZUKU)
            } else {
                bindShizukuService()
            }
        } catch (e: Exception) {
            clearPendingAutomatic()
            toast("Shizuku error: ${e.message ?: "unable to request access"}")
        }
    }

    private fun bindShizukuService() {
        if (!Shizuku.pingBinder()) {
            clearPendingAutomatic()
            toast("Shizuku is not running.")
            return
        }
        if (fileOps != null) {
            confirmAutomaticApply()
            return
        }
        if (shizukuBound) return

        try {
            Shizuku.bindUserService(userServiceArgs, shizukuConnection)
            shizukuBound = true
        } catch (e: Exception) {
            shizukuBound = false
            clearPendingAutomatic()
            toast("Cannot connect to Shizuku service: ${e.message ?: "unknown error"}")
        }
    }

    private fun confirmAutomaticApply() {
        val zipName = pendingAutoZipName ?: return
        val game = pendingAutoGame ?: return
        val mediaDir = ensureAppMediaDirectory() ?: return
        val zip = java.io.File(mediaDir, zipName)

        if (!zip.isFile || !zip.canRead()) {
            clearPendingAutomatic()
            toast("Resource pack ZIP is missing or unreadable.")
            return
        }

        val destination = if (game == "Free Fire MAX") {
            "/storage/emulated/0/Android/data/com.dts.freefiremax/files/contentcache/optional/android"
        } else {
            "/storage/emulated/0/Android/data/com.dts.freefireth/files/contentcache/optional/android"
        }

        val isWholePack = pendingWholePack
        showStyledDialog(
            if (isWholePack) "Install resource pack?" else "Apply Magic?",
            if (isWholePack) "This will install the selected full resource pack for $game. Continue only if this is the pack you want."
            else "This will apply the selected Magic pack to $game. Make sure the correct game is selected.",
            if (isWholePack) "INSTALL" else "APPLY MAGIC",
            "CANCEL",
            onPositive = {
                clearPendingAutomatic()
                startAutomaticWork(zip, destination, game)
            },
            onNegative = { clearPendingAutomatic() }
        )
    }

    private fun startAutomaticWork(
        zip: java.io.File,
        destination: String,
        game: String
    ) {
        if (!busy.compareAndSet(false, true)) return
        operationTitle = game
        processingStartedAt = SystemClock.elapsedRealtime()
        showProcessingScreen(game)
        setBusy(true)

        worker.execute {
            val result = try {
                val service = fileOps
                    ?: throw IOException("Shizuku service is disconnected.")
                service.applyZip(zip.absolutePath, destination)
            } catch (e: Exception) {
                "Apply failed: ${e.message ?: e.javaClass.simpleName}"
            }

            main.post {
                busy.set(false)
                setBusy(false)
                val success = result.startsWith("Applied successfully", ignoreCase = true)
                val friendlyMessage = if (success) {
                    if (result.contains("backup files could not be deleted", true))
                        "Magic was applied. Some temporary backup files could not be cleaned up."
                    else "Your resource files were applied successfully."
                } else when {
                    result.contains("permission", true) || result.contains("Shizuku", true) ->
                        "Shizuku access is unavailable. Check its running state and permission, then retry."
                    result.contains("ZIP", true) || result.contains("zip", true) || result.contains("resource file", true) ->
                        "The resource pack could not be read. Check that the correct ZIP is available."
                    result.contains("destination", true) || result.contains("writable", true) || result.contains("game", true) ->
                        "The game folder is unavailable. Make sure the correct game is installed and accessible."
                    else -> "Magic could not be applied. Your operation was stopped; check the resource and access, then retry."
                }
                finishProcessingOnCurrentScreen(
                    success,
                    if (success) "Magic applied successfully" else "Operation failed",
                    friendlyMessage
                )
            }
        }
    }

    private fun clearPendingAutomatic() {
        pendingAutoZipName = null
        pendingAutoGame = null
        pendingWholePack = false
    }

    private fun showRemoveMagic() {
        currentScreen = "remove"
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(22))
            setBackgroundColor(bg)
        }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; isHorizontalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER; isFillViewport = false }
        scroll.addView(content)
        content.addView(button("‹   BACK TO HOME", false) { showHome() }, matchWrap())
        content.addView(TextView(this).apply {
            text = "REMOVE MAGIC"
            textSize = 28f
            setTextColor(white)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(22), 0, dp(6))
        })
        content.addView(info("Choose which game's modified assets to remove."))
        addHomeAction(content, "game", "FREE FIRE", "Remove Free Fire Magic", logoRes = com.aryan.folderforge.R.drawable.free_fire_logo, showDeleteBadge = true) {
            confirmRemoveMagic("Free Fire", "removeff.zip",
                "/storage/emulated/0/Android/data/com.dts.freefireth/files/contentcache/optional/android")
        }
        addHomeAction(content, "game", "FREE FIRE MAX", "Remove Free Fire MAX Magic", logoRes = com.aryan.folderforge.R.drawable.free_fire_max_logo, showDeleteBadge = true) {
            confirmRemoveMagic("Free Fire MAX", "removeffm.zip",
                "/storage/emulated/0/Android/data/com.dts.freefiremax/files/contentcache/optional/android")
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        showScreen(root)
    }

    private fun confirmRemoveMagic(game: String, zipName: String, destination: String) {
        showStyledDialog("Remove Magic?", "Remove modified assets for $game and restore the original resource pack.", "REMOVE MAGIC", "CANCEL", onPositive = {
                if (busy.get()) return@showStyledDialog
                if (!Shizuku.pingBinder()) {
                    toast("Start Shizuku first, then try again.")
                    return@showStyledDialog
                }
                val mediaDir = ensureAppMediaDirectory()
                val packZip = mediaDir?.let { File(it, zipName) }
                if (packZip == null) {
                    toast("Remove pack unavailable.")
                    return@showStyledDialog
                }
                try {
                    assets.open(zipName).use { input ->
                        packZip.outputStream().buffered().use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (_: Exception) {
                    toast("Remove pack unavailable.")
                    return@showStyledDialog
                }
                if (!packZip.isFile || !packZip.canRead()) {
                    toast("Remove pack unavailable.")
                    return@showStyledDialog
                }
                pendingRemoveGame = game
                pendingRemoveDestination = destination
                pendingRemoveZipPath = packZip.absolutePath
                try {
                    if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                        Shizuku.requestPermission(REQUEST_SHIZUKU)
                    } else if (fileOps != null) {
                        runRemoveMagic()
                    } else {
                        bindShizukuService()
                    }
                } catch (e: Exception) {
                    clearPendingRemove()
                    showStyledDialog("Shizuku error", "Shizuku couldn't start this operation. Check its status and try again.", "OK")
                }
        })
    }

    private fun clearPendingRemove() {
        pendingRemoveGame = null
        pendingRemoveDestination = null
        pendingRemoveZipPath = null
    }

    private fun runRemoveMagic() {
        val game = pendingRemoveGame ?: return
        val destination = pendingRemoveDestination ?: return
        val zipPath = pendingRemoveZipPath ?: return
        if (fileOps == null || !busy.compareAndSet(false, true)) return
        clearPendingRemove()
        operationTitle = "Remove Magic"
        processingStartedAt = SystemClock.elapsedRealtime()
        showProcessingScreen("Remove Magic: $game")
        setBusy(true)
        worker.execute {
            val result = try {
                fileOps?.installRemovePack(zipPath, destination) ?: "Shizuku service is disconnected."
            } catch (e: Exception) {
                "Remove failed: ${e.message ?: e.javaClass.simpleName}"
            }
            main.post {
                busy.set(false)
                setBusy(false)
                val success = result.startsWith("Remove pack installed:", ignoreCase = true)
                val friendlyMessage = if (success) {
                    "The modified game assets were removed and the original resource pack was installed."
                } else when {
                    result.contains("permission", true) || result.contains("Shizuku", true) ->
                        "Shizuku access is unavailable. Check its running state and permission, then retry."
                    result.contains("ZIP", true) || result.contains("zip", true) || result.contains("remove pack", true) ->
                        "The removal pack could not be read. Check that the correct ZIP is available."
                    result.contains("destination", true) || result.contains("writable", true) || result.contains("game", true) ->
                        "The game folder is unavailable. Make sure the correct game is installed and accessible."
                    else -> "Remove Magic could not finish. No automatic navigation was performed; check access and retry."
                }
                finishProcessingOnCurrentScreen(
                    success,
                    if (success) "Magic removed successfully" else "Operation failed",
                    friendlyMessage
                )
            }
        }
    }

    private fun showManual() {
        currentScreen = "manual"
        operationTitle = "Manual extraction"
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(22))
            setBackgroundColor(bg)
        }
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; isHorizontalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER; isFillViewport = false }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(content)

        val back = button("‹   BACK TO HOME", false) { showHome() }
        content.addView(back, matchWrap())
        content.addView(TextView(this).apply {
            text = "Apply Manually"
            textSize = 28f
            setTextColor(white)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(18), 0, dp(3))
        })
        content.addView(TextView(this).apply {
            text = "Select a ZIP and destination. Matching items will be replaced."
            textSize = 13f
            setTextColor(muted)
            setPadding(0, 0, 0, dp(16))
        })

        val zipCard = card()
        zipCard.addView(section("1  SOURCE ZIP"))
        pickZipButton = button("Choose ZIP file", true) { openZipPicker() }
        zipCard.addView(pickZipButton, matchWrap())
        zipLabel = info("No ZIP selected")
        zipCard.addView(zipLabel)
        content.addView(zipCard, matchWrap(dp(10)))

        val destCard = card()
        destCard.addView(section("2  DESTINATION"))
        pickDestButton = button("Choose destination folder", true) {
            openDestinationPicker()
        }
        destCard.addView(pickDestButton, matchWrap())
        destLabel = info("No destination selected")
        destCard.addView(destLabel)
        content.addView(destCard, matchWrap(dp(10)))

        progress = ProgressBar(this, null,
            android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            visibility = View.GONE
        }
        content.addView(progress, matchWrap(dp(8)))
        status = info("Ready")
        content.addView(status)

        applyButton = button("EXTRACT AND REPLACE", true) { confirmReplace() }
        content.addView(applyButton, matchWrap(dp(8)))

        content.addView(TextView(this).apply {
            text = "Safety: files are staged before replacement. Backup renaming depends " +
                "on the selected Android storage provider. Keep the app open while working."
            textSize = 12f
            setTextColor(muted)
            setPadding(2, dp(16), 2, 0)
        })

        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        showScreen(root)
    }

    private fun addHomeAction(
        parent: LinearLayout,
        icon: String,
        title: String,
        subtitle: String,
        logoRes: Int? = null,
        showDeleteBadge: Boolean = false,
        action: () -> Unit
    ) {
        val outer = card().apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(surface, dp(22))
            elevation = dp(1).toFloat()
            isClickable = true
            isFocusable = true
            contentDescription = "$title. $subtitle"
            setOnClickListener { feedback(this); action() }
        }
        val iconView: View = if (logoRes != null) {
            FrameLayout(this).apply {
                background = rounded(surface2, dp(16))
                val logo = ImageView(this@MainActivity).apply {
                    setImageResource(logoRes)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = if (logoRes == com.aryan.folderforge.R.drawable.free_fire_max_logo) "Free Fire MAX logo" else "Free Fire logo"
                    setPadding(dp(4), dp(4), dp(4), dp(4))
                }
                addView(logo, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
                if (showDeleteBadge) {
                    val delete = ImageView(this@MainActivity).apply {
                        setImageResource(android.R.drawable.ic_menu_delete)
                        imageTintList = android.content.res.ColorStateList.valueOf(Color.rgb(229, 72, 77))
                        background = rounded(surface, dp(12))
                        setPadding(dp(3), dp(3), dp(3), dp(3))
                        contentDescription = "Remove or delete"
                    }
                    addView(delete, FrameLayout.LayoutParams(dp(25), dp(25), Gravity.TOP or Gravity.END))
                }
            }
        } else {
            TextView(this).apply {
                text = when (icon) {
                    "archive" -> "▤"
                    "game" -> "✦"
                    else -> icon
                }
                textSize = 21f
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(blue)
                background = rounded(surface2, dp(16))
            }
        }
        outer.addView(iconView, LinearLayout.LayoutParams(dp(if (logoRes != null) 76 else 52), dp(52)))
        val labels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(2), 0, dp(2))
        }
        labels.addView(TextView(this).apply {
            text = title
            textSize = 14f
            setTextColor(white)
            typeface = Typeface.DEFAULT_BOLD
        })
        labels.addView(TextView(this).apply {
            text = subtitle
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(4), 0, 0)
        })
        outer.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        outer.addView(TextView(this).apply {
            text = "›"
            textSize = 26f
            setTextColor(blue)
            gravity = Gravity.CENTER
        })
        parent.addView(outer, matchWrap(dp(10)))
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(17), dp(16), dp(17), dp(16))
        background = rounded(surface, dp(22))
        elevation = dp(1).toFloat()
    }

    private fun section(s: String) = TextView(this).apply {
        text = s
        textSize = 11f
        letterSpacing = 0.08f
        setTextColor(muted)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(5), 0, dp(10))
    }

    private fun info(s: String) = TextView(this).apply {
        text = s
        textSize = 12f
        setTextColor(muted)
        setPadding(0, dp(9), 0, dp(4))
    }

    private fun button(s: String, primary: Boolean, action: () -> Unit) =
        Button(this).apply {
            text = s
            isAllCaps = false
            textSize = 14f
            setTextColor(if (primary) Color.rgb(8, 18, 34) else white)
            background = rounded(
                if (primary) blue else surface2, dp(14)
            )
            minHeight = dp(48)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { feedback(this); action() }
        }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
    }

    private fun matchWrap(bottom: Int = 0) =
        LinearLayout.LayoutParams(-1, -2).apply {
            if (bottom > 0) bottomMargin = bottom
        }

    private fun dp(v: Int) =
        (v * resources.displayMetrics.density).toInt()

    private fun openZipPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "application/zip",
                    "application/x-zip-compressed",
                    "application/octet-stream"
                )
            )
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQ_ZIP)
    }

    private fun openDestinationPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQ_DEST)
    }

    @Deprecated("Uses activity result API compatibility")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) {
            if (requestCode == REQ_ZIP_FOLDER ||
                (requestCode == REQ_DEST && pendingAutoGame != null)) {
                clearPendingAutomatic()
            }
            return
        }

        val uri = data.data!!
        val flags = data.flags and
            (Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        try {
            contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: Exception) {
        }

        when (requestCode) {
            REQ_ZIP -> {
                zipUri = uri
                zipLabel?.text =
                    "Selected: ${DocumentFile.fromSingleUri(this, uri)?.name ?: "ZIP file"}"
            }
            REQ_DEST -> {
                destinationTreeUri = uri
                destLabel?.text =
                    "Selected: ${DocumentFile.fromTreeUri(this, uri)?.name ?: "Folder"}"
                val autoGame = pendingAutoGame
                if (autoGame != null) {
                    operationTitle = autoGame
                    clearPendingAutomatic()
                    confirmReplace()
                }
            }
        }
    }

    private fun confirmReplace() {
        if (busy.get()) return
        val zip = zipUri
        val tree = destinationTreeUri

        if (zip == null || tree == null) {
            toast("Choose a ZIP file and destination folder.")
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Extract and replace items?")
            .setMessage(
                "The ZIP structure will be preserved. Matching top-level items " +
                "will be replaced; unrelated destination items will be untouched. " +
                "Keep a backup of important data."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Continue") { _, _ -> startWork(zip, tree) }
            .show()
    }

    private fun startWork(zip: Uri, tree: Uri) {
        if (!busy.compareAndSet(false, true)) return
        processingStartedAt = SystemClock.elapsedRealtime()
        showProcessingScreen(operationTitle)
        setBusy(true)

        worker.execute {
            var stage: DocumentFile? = null
            var parent: DocumentFile? = null
            val backups = linkedMapOf<String, String>()
            val installed = mutableListOf<String>()
            var success = false

            try {
                parent = DocumentFile.fromTreeUri(this, tree)
                    ?: throw IOException("Destination folder could not be opened.")
                if (!parent.canWrite()) {
                    throw IOException("Destination folder is not writable.")
                }

                val stamp = System.currentTimeMillis()
                stage = parent.createDirectory(".folderforge-staging-$stamp")
                    ?: throw IOException("Cannot create staging folder.")

                var totalBytes = 0L
                var doneBytes = 0L

                contentResolver.openInputStream(zip).use { input ->
                    if (input == null) throw IOException("Cannot read ZIP file.")
                    ZipInputStream(BufferedInputStream(input)).use { zis ->
                        var e = zis.nextEntry
                        while (e != null) {
                            if (!e.isDirectory && e.size > 0) totalBytes += e.size
                            zis.closeEntry()
                            e = zis.nextEntry
                        }
                    }
                }

                updateProgress(3, "ZIP opened and verified")

                contentResolver.openInputStream(zip).use { input ->
                    if (input == null) throw IOException("Cannot read ZIP file.")
                    ZipInputStream(BufferedInputStream(input)).use { zis ->
                        val buffer = ByteArray(64 * 1024)
                        var entry = zis.nextEntry

                        while (entry != null) {
                            val raw = entry.name.replace('\\', '/')
                            val parts = raw.split('/').filter { it.isNotEmpty() }

                            if (raw.startsWith('/') || raw.contains('\u0000') ||
                                parts.any { it == ".." || it.contains(':') }) {
                                throw IOException("Unsafe ZIP path rejected: $raw")
                            }

                            if (parts.isEmpty() && !entry.isDirectory) {
                                throw IOException("ZIP contains an invalid empty file path.")
                            }

                            if (parts.isNotEmpty()) {
                                var current = stage!!
                                for (dir in parts.dropLast(1)) {
                                    val found = current.findFile(dir)
                                    current = if (found != null && found.isDirectory) {
                                        found
                                    } else {
                                        if (found != null && !found.delete()) {
                                            throw IOException("Cannot prepare staging path: $dir")
                                        }
                                        current.createDirectory(dir)
                                            ?: throw IOException("Cannot create directory: $dir")
                                    }
                                }

                                val leaf = parts.last()
                                val found = current.findFile(leaf)

                                if (entry.isDirectory) {
                                    if (found != null && !found.isDirectory && !found.delete()) {
                                        throw IOException("Cannot prepare directory: $leaf")
                                    }
                                    if (current.findFile(leaf) == null) {
                                        current.createDirectory(leaf)
                                            ?: throw IOException("Cannot create directory: $leaf")
                                    }
                                } else {
                                    if (found != null && !found.delete()) {
                                        throw IOException("Cannot replace staging item: $leaf")
                                    }
                                    val outDoc = current.createFile(
                                        "application/octet-stream", leaf
                                    ) ?: throw IOException("Cannot create file: $leaf")

                                    contentResolver.openOutputStream(outDoc.uri, "wt").use { output ->
                                        if (output == null) throw IOException("Cannot write file: $leaf")
                                        BufferedOutputStream(output).use { out ->
                                            while (true) {
                                                val count = zis.read(buffer)
                                                if (count < 0) break
                                                if (count == 0) continue
                                                out.write(buffer, 0, count)
                                                doneBytes += count
                                                val pct = if (totalBytes > 0) {
                                                    ((doneBytes * 100) / totalBytes)
                                                        .toInt().coerceIn(0, 99)
                                                } else 0
                                                updateProgress(
                                                    pct, "Extracting $leaf · ${formatBytes(doneBytes)}"
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                }

                val staged = stage!!.listFiles()
                if (staged.isEmpty()) throw IOException("ZIP produced no files.")

                // Reject duplicate top-level names before touching existing items.
                val names = staged.map {
                    it.name ?: throw IOException("ZIP item has no usable name.")
                }
                if (names.distinct().size != names.size) {
                    throw IOException("Duplicate top-level names found in staging.")
                }

                for ((index, item) in staged.withIndex()) {
                    val name = item.name
                        ?: throw IOException("ZIP item has no usable name.")
                    if (name == "." || name == ".." ||
                        name.contains('/') || name.contains('\\')) {
                        throw IOException("Invalid top-level item name: $name")
                    }

                    val existing = parent!!.findFile(name)
                    if (existing != null) {
                        val backupName = ".folderforge-backup-$stamp-$index"
                        if (!existing.renameTo(backupName)) {
                            throw IOException("Cannot safely back up '$name'; stopped.")
                        }
                        backups[name] = backupName
                    }

                    val incomingName = ".folderforge-incoming-$stamp-$index"
                    var incoming: DocumentFile? = null
                    try {
                        incoming = copyDocument(item, parent!!, incomingName)
                        if (!incoming.renameTo(name)) {
                            throw IOException("Storage provider cannot install '$name' safely.")
                        }
                        installed.add(name)
                    } catch (e: Exception) {
                        try { incoming?.delete() } catch (_: Exception) {}
                        throw e
                    }

                    updateProgress(
                        99, "Installing item ${index + 1}/${staged.size}: $name"
                    )
                }

                updateProgress(99, "Final verification")
                var leftoverBackup: String? = null
                for (backupName in backups.values) {
                    try {
                        val old = parent!!.findFile(backupName)
                        if (old != null && !old.delete()) leftoverBackup = backupName
                    } catch (_: Exception) {
                        leftoverBackup = backupName
                    }
                }
                success = true
                updateProgress(
                    100,
                    if (leftoverBackup == null) {
                        "Completed. ZIP items are in the selected destination."
                    } else {
                        "Completed; old backup remains: $leftoverBackup"
                    }
                )
            } catch (e: Exception) {
                updateProgress(-1, "Stopped: ${e.message ?: e.javaClass.simpleName}")
                if (!success && parent != null) {
                    for (name in installed.asReversed()) {
                        try { parent.findFile(name)?.delete() } catch (_: Exception) {}
                    }
                    for ((original, backupName) in backups.entries.toList().asReversed()) {
                        try {
                            val old = parent.findFile(backupName)
                            if (old != null && parent.findFile(original) == null) {
                                old.renameTo(original)
                            }
                        } catch (_: Exception) {}
                    }
                }
            } finally {
                try { stage?.delete() } catch (_: Exception) {}
                busy.set(false)
                main.post { setBusy(false) }
            }
        }
    }

    private fun copyDocument(
        source: DocumentFile,
        targetParent: DocumentFile,
        targetName: String
    ): DocumentFile {
        if (source.isDirectory) {
            val target = targetParent.createDirectory(targetName)
                ?: throw IOException("Cannot create temporary directory: $targetName")
            for (child in source.listFiles()) {
                val childName = child.name
                    ?: throw IOException("Source item has no usable name.")
                copyDocument(child, target, childName)
            }
            return target
        }

        val target = targetParent.createFile("application/octet-stream", targetName)
            ?: throw IOException("Cannot create temporary file: $targetName")
        try {
            contentResolver.openInputStream(source.uri).use { input ->
                if (input == null) throw IOException("Cannot read staged file: $targetName")
                contentResolver.openOutputStream(target.uri, "wt").use { output ->
                    if (output == null) throw IOException("Cannot write temporary file: $targetName")
                    BufferedInputStream(input).use { ins ->
                        BufferedOutputStream(output).use { outs ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val count = ins.read(buffer)
                                if (count < 0) break
                                if (count > 0) outs.write(buffer, 0, count)
                            }
                            outs.flush()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            try { target.delete() } catch (_: Exception) {}
            throw e
        }
        return target
    }

    /** Lightweight Material-inspired wavy ring. Progress is driven only by real worker callbacks. */
    private inner class WavyProgressView(context: Context) : View(context) {
        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = dp(7).toFloat()
            color = if (isDark) Color.rgb(54, 69, 91) else Color.rgb(216, 230, 250)
        }
        private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = dp(7).toFloat()
            color = blue
        }
        private var progressValue = 0f
        private var phase = 0f
        private var animator: ValueAnimator? = null

        fun setProgressValue(value: Float) {
            progressValue = value.coerceIn(0f, 1f)
            if (progressValue >= 1f || !prefs.getBoolean("animations_enabled", true)) stopAnimator() else startAnimatorIfNeeded()
            invalidate()
        }

        private fun startAnimatorIfNeeded() {
            if (animator != null || !isAttachedToWindow) return
            animator = ValueAnimator.ofFloat(0f, (Math.PI * 2).toFloat()).apply {
                duration = 1250L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { phase = it.animatedValue as Float; invalidate() }
                start()
            }
        }

        private fun stopAnimator() { animator?.cancel(); animator = null }
        fun stopAnimating() { stopAnimator(); invalidate() }

        override fun onAttachedToWindow() { super.onAttachedToWindow(); if (progressValue < 1f && prefs.getBoolean("animations_enabled", true)) startAnimatorIfNeeded() }
        override fun onDetachedFromWindow() { stopAnimator(); super.onDetachedFromWindow() }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val radius = (minOf(width, height) / 2f) - dp(9)
            if (radius <= 0f) return
            canvas.drawCircle(cx, cy, radius, trackPaint)
            val fraction = if (progressValue > 0f) progressValue else 0.34f
            val sweep = if (progressValue > 0f) 360f * fraction else 122f
            val start = if (progressValue > 0f) -90f else -90f + (phase * 57.29578f)
            val path = Path()
            val samples = maxOf(24, (sweep / 3f).toInt())
            for (i in 0..samples) {
                val t = i.toFloat() / samples
                val angle = Math.toRadians((start + sweep * t).toDouble())
                val wave = if (progressValue > 0f) kotlin.math.sin(t * Math.PI * 9.0) * dp(1) else kotlin.math.sin(t * Math.PI * 9.0 + phase) * dp(2)
                val r = radius + wave.toFloat()
                val x = cx + kotlin.math.cos(angle).toFloat() * r
                val y = cy + kotlin.math.sin(angle).toFloat() * r
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            canvas.drawPath(path, wavePaint)
        }
    }

    private fun showProcessingScreen(title: String) {
        currentScreen = "processing"
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(20))
            setBackgroundColor(bg)
        }
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; isHorizontalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER; isFillViewport = false }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)

        content.addView(TextView(this).apply {
            text = if (title.startsWith("Remove Magic")) "Removing Magic" else "Applying Magic"
            textSize = 28f
            setTextColor(white)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(22), 0, dp(4))
        })
        processingTitle = TextView(this).apply {
            text = title
            textSize = 15f
            setTextColor(muted)
            setPadding(0, 0, 0, dp(18))
        }
        content.addView(processingTitle)

        val progressCard = card()
        val topLine = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        processingStatus = TextView(this).apply {
            text = if (title.startsWith("Remove Magic")) "Removing game assets…" else "Preparing files…"
            textSize = 14f
            setTextColor(white)
        }
        processingPercent = TextView(this).apply {
            text = "WORKING"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(blue)
            gravity = Gravity.END
        }
        topLine.addView(processingStatus, LinearLayout.LayoutParams(0, -2, 1f))
        topLine.addView(processingPercent)
        progressCard.addView(topLine)
        processingWavy = WavyProgressView(this).apply {
            contentDescription = "Live operation progress"
            setProgressValue(0f)
        }
        progressCard.addView(processingWavy, LinearLayout.LayoutParams(dp(116), dp(116)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(18)
            bottomMargin = dp(14)
        })
        processingProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            visibility = View.GONE
        }
        progressCard.addView(processingProgress, matchWrap(dp(12)))
        processingElapsed = TextView(this).apply {
            text = "Elapsed 00:00"
            textSize = 12f
            setTextColor(muted)
        }
        progressCard.addView(processingElapsed)
        content.addView(progressCard, matchWrap(dp(14)))

        content.addView(section("PROCESS CHECKLIST"))
        processingSteps = mutableListOf()
        val stepNames = listOf(
            "ZIP opened and verified",
            "Files extracted to staging",
            "Existing items backed up",
            "New items installed",
            "Final verification"
        )
        for (name in stepNames) {
            val row = TextView(this).apply {
                text = "○   $name"
                textSize = 14f
                setTextColor(muted)
                setPadding(dp(4), dp(12), dp(4), dp(12))
                background = rounded(surface, dp(12))
            }
            processingSteps.add(row)
            content.addView(row, matchWrap(dp(7)))
        }
        content.addView(TextView(this).apply {
            text = "Keep AryanForge open until the operation finishes."
            textSize = 12f
            setTextColor(muted)
            setPadding(2, dp(14), 2, 0)
        })
        processingResultCard = card().apply { visibility = View.GONE }
        processingResultTitle = TextView(this).apply {
            textSize = 18f; typeface = Typeface.DEFAULT_BOLD; setTextColor(white)
        }
        processingResultMessage = TextView(this).apply {
            textSize = 13f; setTextColor(muted); setPadding(0, dp(6), 0, dp(12))
        }
        processingResultCard?.addView(processingResultTitle)
        processingResultCard?.addView(processingResultMessage)
        content.addView(processingResultCard, matchWrap(dp(12)))
        processingReturnButton = button("BACK TO HOME", true) { showHome() }.apply { visibility = View.GONE }
        content.addView(processingReturnButton, matchWrap(dp(16)))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        showScreen(root)
        startElapsedTimer()
    }

    private fun startElapsedTimer() {
        timerRunnable?.let { main.removeCallbacks(it) }
        val tick = object : Runnable {
            override fun run() {
                val seconds = ((SystemClock.elapsedRealtime() - processingStartedAt) / 1000L).coerceAtLeast(0L)
                processingElapsed?.text = String.format(
                    java.util.Locale.US, "Elapsed %02d:%02d", seconds / 60, seconds % 60
                )
                if (busy.get()) main.postDelayed(this, 1000L)
            }
        }
        timerRunnable = tick
        main.post(tick)
    }

    private fun updateChecklist(message: String, pct: Int) {
        val completed = when {
            message.startsWith("Completed") -> 5
            message.contains("Final verification", ignoreCase = true) -> 4
            message.contains("Installing item", ignoreCase = true) -> 3
            message.contains("Extracting", ignoreCase = true) -> 1
            message.contains("Verifying staged", ignoreCase = true) -> 2
            message.contains("ZIP opened and verified", ignoreCase = true) -> 1
            message.contains("Starting") || message.contains("staging", ignoreCase = true) -> 0
            else -> 0
        }
        for ((index, view) in processingSteps.withIndex()) {
            val done = index < completed
            view.text = (if (done) "✓   " else if (index == completed) "◉   " else "○   ") +
                listOf("ZIP opened and verified", "Files extracted to staging", "Existing items backed up", "New items installed", "Final verification")[index]
            view.setTextColor(if (done) Color.rgb(109, 214, 160) else if (index == completed) white else muted)
        }
        if (pct >= 100) {
            processingSteps.forEach { it.setTextColor(Color.rgb(109, 214, 160)) }
        }
    }

    private fun showBottomMessage(success: Boolean, title: String, message: String) {
        val content = findViewById<FrameLayout>(android.R.id.content) ?: return
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(13), dp(16), dp(13))
            background = rounded(surface2, dp(18))
            elevation = dp(12).toFloat()
        }
        val icon = TextView(this).apply {
            text = if (success) "✓" else "!"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(if (success) Color.rgb(109, 214, 160) else Color.rgb(255, 130, 130))
        }
        card.addView(icon, LinearLayout.LayoutParams(dp(34), dp(34)))
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }
        texts.addView(TextView(this).apply {
            text = title
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(white)
        })
        texts.addView(TextView(this).apply {
            text = message
            textSize = 12f
            setTextColor(muted)
            maxLines = 3
        })
        card.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        val lp = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            leftMargin = dp(16); rightMargin = dp(16); bottomMargin = dp(24)
        }
        content.addView(card, lp)
        card.alpha = 0f
        card.translationY = dp(30).toFloat()
        card.animate().alpha(1f).translationY(0f).setDuration(220).start()
        main.postDelayed({
            if (card.parent != null) {
                card.animate().alpha(0f).translationY(dp(20).toFloat()).setDuration(180)
                    .withEndAction { (card.parent as? android.view.ViewGroup)?.removeView(card) }
                    .start()
            }
        }, 3200L)
    }


    private fun finishProcessingOnCurrentScreen(success: Boolean, title: String, message: String) {
        timerRunnable?.let { main.removeCallbacks(it) }
        processingStatus?.text = if (success) "Completed successfully" else "Operation stopped"
        processingStatus?.setTextColor(if (success) Color.rgb(22, 132, 72) else Color.rgb(198, 40, 40))
        processingPercent?.text = if (success) "DONE" else "STOPPED"
        processingPercent?.textSize = 11f
        processingWavy?.setProgressValue(if (success) 1f else 0f)
        if (!success) processingWavy?.stopAnimating()
        if (success) {
            processingProgress?.progress = 100
            processingSteps.forEach { step ->
                step.text = "✓   " + step.text.toString().substringAfter("   ", step.text.toString())
                step.setTextColor(Color.rgb(109, 214, 160))
            }
        }
        processingResultTitle?.text = title
        processingResultTitle?.setTextColor(if (success) Color.rgb(22, 132, 72) else Color.rgb(198, 40, 40))
        processingResultMessage?.text = message
        processingResultCard?.visibility = View.VISIBLE
        processingReturnButton?.visibility = View.VISIBLE
        processingResultCard?.alpha = 0f
        processingResultCard?.animate()?.alpha(1f)?.setDuration(220L)?.start()
    }

    private fun updateProgress(pct: Int, message: String) {
        main.post {
            progress?.visibility = View.VISIBLE
            if (pct >= 0) progress?.progress = pct
            status?.text = message
            processingStatus?.text = message
            if (pct >= 0) {
                processingProgress?.progress = pct
                processingPercent?.text = "$pct%"
                processingWavy?.setProgressValue((pct.coerceIn(0, 100) / 100f))
            } else if (pct < 0) {
                processingWavy?.setProgressValue(0f)
            }
            updateChecklist(message, pct)
            if (pct >= 100) {
                finishProcessingOnCurrentScreen(true, "Magic applied successfully", "Your resource files were installed and verified.")
            } else if (pct < 0) {
                val safeMessage = when {
                    message.contains("permission", true) -> "Check Shizuku permission and try again."
                    message.contains("zip", true) || message.contains("archive", true) -> "The resource file could not be processed. Check the ZIP and try again."
                    else -> "The operation couldn't be completed. No automatic navigation was performed."
                }
                finishProcessingOnCurrentScreen(false, "Operation failed", safeMessage)
            }
        }
    }

    private fun setBusy(value: Boolean) {
        main.post {
            applyButton?.isEnabled = !value
            pickZipButton?.isEnabled = !value
            pickDestButton?.isEnabled = !value
            if (value) status?.text = "Starting extraction…"
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(java.util.Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(java.util.Locale.US, "%.1f MB", mb)
        return String.format(java.util.Locale.US, "%.2f GB", mb / 1024.0)
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (busy.get()) return
        when (currentScreen) {
            "manual", "remove", "processing", "settings", "intro" -> showHome()
            "home" -> super.onBackPressed()
            else -> showHome()
        }
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        if (shizukuBound) {
            try {
                Shizuku.unbindUserService(userServiceArgs, shizukuConnection, true)
            } catch (_: Exception) {}
        }
        fileOps = null
        shizukuBound = false
        super.onDestroy()
    }

    companion object {
        private const val UPDATE_API_URL = "https://api.github.com/repos/arr-stacks/AryanForge/releases/latest"
        const val REQ_ZIP = 101
        const val REQ_DEST = 102
        const val REQ_ZIP_FOLDER = 103
        const val REQUEST_SHIZUKU = 7001
    }
}
