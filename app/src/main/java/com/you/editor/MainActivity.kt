package com.you.editor

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.you.editor.databinding.ActivityMainBinding
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val apps = mutableListOf<AppInfo>()
    private val logBuilder = StringBuilder("Ready...\n")

    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                convertFile(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.appsRecycler.layoutManager = LinearLayoutManager(this)
        loadApps()
        log("App started - You Editor")

        binding.btnTelegram.setOnClickListener {
            log("Opening Telegram channel")
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/You_Public")))
        }

        binding.btnPickFile.setOnClickListener {
            log("Opening file picker for XAPK/APKS")
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/octet-stream", "application/zip", "application/vnd.android.package-archive"))
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            pickFileLauncher.launch(intent)
        }

        binding.btnCopyLog.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("log", logBuilder.toString()))
            Toast.makeText(this, "تم نسخ اللوج", Toast.LENGTH_SHORT).show()
            log("Log copied to clipboard")
        }
    }

    private fun log(message: String) {
        logBuilder.append("[$message]\n")
        binding.logText.text = logBuilder.toString()
        // auto scroll roughly
        binding.logText.post {
            (binding.logText.parent as? android.widget.ScrollView)?.fullScroll(android.view.View.FOCUS_DOWN)
        }
    }

    private fun loadApps() {
        log("Loading installed apps...")
        val pm = packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .sortedBy { pm.getApplicationLabel(it).toString() }

        apps.clear()
        for (pkg in packages) {
            val name = pm.getApplicationLabel(pkg).toString()
            val icon = pm.getApplicationIcon(pkg)
            apps.add(AppInfo(name, pkg.packageName, pkg.sourceDir, icon))
        }
        binding.appsRecycler.adapter = AppAdapter(apps) { app ->
            shareApk(app)
        }
        log("Loaded ${apps.size} user apps")
    }

    private fun shareApk(app: AppInfo) {
        log("Sharing APK: ${app.name}")
        try {
            val source = File(app.sourceDir)
            if (!source.exists()) {
                log("ERROR: source not found")
                Toast.makeText(this, "الملف غير موجود", Toast.LENGTH_SHORT).show()
                return
            }

            val destDir = File(cacheDir, "apk")
            destDir.mkdirs()
            val dest = File(destDir, "${app.name.replace(" ", "_")}.apk")

            source.inputStream().use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
            log("APK copied to cache: ${dest.name}")

            val uri = FileProvider.getUriForFile(this, "${packageName}.provider", dest)
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(share, "مشاركة APK"))
            log("Share intent launched")
        } catch (e: Exception) {
            log("ERROR sharing: ${e.message}")
            Toast.makeText(this, "خطأ: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun convertFile(uri: Uri) {
        log("Converting file from URI...")
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: run {
                log("ERROR: cannot open input stream")
                return
            }

            val tempZip = File(cacheDir, "temp_input.zip")
            inputStream.use { input ->
                FileOutputStream(tempZip).use { output ->
                    input.copyTo(output)
                }
            }
            log("File copied to temp: ${tempZip.length()} bytes")

            // Try as ZIP (XAPK/APKS are ZIPs)
            val apkOut = File(cacheDir, "converted_${System.currentTimeMillis()}.apk")
            var extracted = false

            ZipFile(tempZip).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement() as ZipEntry
                    if (entry.name.endsWith(".apk", ignoreCase = true) && !entry.isDirectory) {
                        log("Found APK inside: ${entry.name}")
                        zip.getInputStream(entry).use { zipIn ->
                            FileOutputStream(apkOut).use { out ->
                                zipIn.copyTo(out)
                            }
                        }
                        extracted = true
                        break // take first APK (usually base)
                    }
                }
            }

            tempZip.delete()

            if (extracted && apkOut.exists()) {
                log("SUCCESS: Extracted APK -> ${apkOut.name} (${apkOut.length()} bytes)")
                Toast.makeText(this, "تم التحويل بنجاح", Toast.LENGTH_LONG).show()

                // Offer to share the converted APK
                val uriOut = FileProvider.getUriForFile(this, "${packageName}.provider", apkOut)
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "application/vnd.android.package-archive"
                    putExtra(Intent.EXTRA_STREAM, uriOut)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(share, "مشاركة الـ APK المحول"))
                log("Converted APK ready for share")
            } else {
                log("ERROR: No APK found inside the file")
                Toast.makeText(this, "ما لقيت APK داخل الملف", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            log("ERROR converting: ${e.message}")
            Toast.makeText(this, "خطأ في التحويل: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

data class AppInfo(
    val name: String,
    val packageName: String,
    val sourceDir: String,
    val icon: android.graphics.drawable.Drawable
)