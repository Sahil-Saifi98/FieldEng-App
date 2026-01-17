package com.company.fieldapp.ui.admin

import android.app.Application
import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.company.fieldapp.data.remote.AdminUser
import com.company.fieldapp.data.remote.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class AdminExportViewModel(application: Application) : AndroidViewModel(application) {

    private val _users = MutableStateFlow<List<AdminUser>>(emptyList())
    val users: StateFlow<List<AdminUser>> = _users

    private val _selectedUser = MutableStateFlow<AdminUser?>(null)
    val selectedUser: StateFlow<AdminUser?> = _selectedUser

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _exportStatus = MutableStateFlow<ExportStatus?>(null)
    val exportStatus: StateFlow<ExportStatus?> = _exportStatus

    private val _startDate = MutableStateFlow<String?>(null)
    val startDate: StateFlow<String?> = _startDate

    private val _endDate = MutableStateFlow<String?>(null)
    val endDate: StateFlow<String?> = _endDate

    private val context = application.applicationContext

    init {
        loadUsers()
        // Set default date range (last 7 days instead of 30)
        val calendar = Calendar.getInstance()
        val endDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        val startDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)

        _startDate.value = startDateStr
        _endDate.value = endDateStr
    }

    private fun loadUsers() {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.adminApi.getAllUsers()
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    if (body.success) {
                        _users.value = body.data
                        Log.d("AdminExportVM", "Loaded ${body.data.size} users")
                    }
                }
            } catch (e: Exception) {
                Log.e("AdminExportVM", "Error loading users: ${e.message}", e)
                showToast("Failed to load users: ${e.message}")
            }
        }
    }

    fun selectUser(user: AdminUser) {
        _selectedUser.value = user
        _exportStatus.value = null
    }

    fun setStartDate(date: String) {
        _startDate.value = date
        _exportStatus.value = null
    }

    fun setEndDate(date: String) {
        _endDate.value = date
        _exportStatus.value = null
    }

    fun exportUserData() {
        val user = _selectedUser.value ?: return

        viewModelScope.launch {
            _isLoading.value = true
            _exportStatus.value = null

            try {
                Log.d("AdminExportVM", "Starting export for user: ${user.employeeId}")

                // Create request body with date range
                val jsonBody = JSONObject().apply {
                    _startDate.value?.let { put("startDate", it) }
                    _endDate.value?.let { put("endDate", it) }
                }
                val requestBody = jsonBody.toString()
                    .toRequestBody("application/json".toMediaTypeOrNull())

                // Make API call
                val response = RetrofitClient.adminApi.exportUserDataZip(user._id, requestBody)

                Log.d("AdminExportVM", "Response code: ${response.code()}")

                if (response.isSuccessful && response.body() != null) {
                    val fileName = "${user.employeeId}_export_${System.currentTimeMillis()}.zip"

                    // Save to Downloads with proper buffering
                    val savedFile = saveToDownloadsOptimized(response.body()!!, fileName)

                    if (savedFile != null) {
                        _exportStatus.value = ExportStatus(
                            isSuccess = true,
                            message = "✅ Downloaded to Downloads folder: $fileName"
                        )
                        showToast("File downloaded successfully!")
                        Log.d("AdminExportVM", "Exported user data: $savedFile")
                    } else {
                        _exportStatus.value = ExportStatus(
                            isSuccess = false,
                            message = "Failed to save export file"
                        )
                        showToast("Failed to save file")
                    }
                } else {
                    val errorMsg = when (response.code()) {
                        404 -> "No records found for selected date range"
                        502 -> "Server error. Try a smaller date range."
                        503 -> "Service unavailable. Please try again."
                        504 -> "Request timeout. Try a smaller date range."
                        else -> "Failed to export: ${response.code()}"
                    }

                    _exportStatus.value = ExportStatus(
                        isSuccess = false,
                        message = errorMsg
                    )
                    showToast(errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = when {
                    e.message?.contains("timeout") == true ->
                        "Request timeout. Try a smaller date range (2-3 days max)."
                    e.message?.contains("Unable to resolve host") == true ->
                        "Network error. Check your connection."
                    else -> "Error: ${e.message}"
                }

                _exportStatus.value = ExportStatus(
                    isSuccess = false,
                    message = errorMsg
                )
                showToast(errorMsg)
                Log.e("AdminExportVM", "Export error: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun exportAllData() {
        viewModelScope.launch {
            _isLoading.value = true
            _exportStatus.value = null

            try {
                val jsonBody = JSONObject().apply {
                    _startDate.value?.let { put("startDate", it) }
                    _endDate.value?.let { put("endDate", it) }
                }
                val requestBody = jsonBody.toString()
                    .toRequestBody("application/json".toMediaTypeOrNull())

                val response = RetrofitClient.adminApi.exportAllDataZip(requestBody)

                if (response.isSuccessful && response.body() != null) {
                    val fileName = "all_data_export_${System.currentTimeMillis()}.zip"
                    val savedFile = saveToDownloadsOptimized(response.body()!!, fileName)

                    if (savedFile != null) {
                        _exportStatus.value = ExportStatus(
                            isSuccess = true,
                            message = "✅ Downloaded to Downloads: $fileName"
                        )
                        showToast("File downloaded successfully!")
                    } else {
                        _exportStatus.value = ExportStatus(
                            isSuccess = false,
                            message = "Failed to save export file"
                        )
                    }
                } else {
                    val errorMsg = when (response.code()) {
                        404 -> "No records found for selected date range"
                        502 -> "Server error. Try a smaller date range."
                        503 -> "Service unavailable. Please try again."
                        504 -> "Timeout. Try exporting fewer records."
                        else -> "Export failed: ${response.code()}"
                    }
                    _exportStatus.value = ExportStatus(isSuccess = false, message = errorMsg)
                    showToast(errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = when {
                    e.message?.contains("timeout") == true ->
                        "Timeout. Try a smaller date range."
                    else -> "Error: ${e.message}"
                }
                _exportStatus.value = ExportStatus(
                    isSuccess = false,
                    message = errorMsg
                )
                showToast("Export failed: $errorMsg")
                Log.e("AdminExportVM", "Export all error: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun exportAttendance(format: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _exportStatus.value = null

            try {
                val response = when (format) {
                    "csv" -> RetrofitClient.adminApi.exportAttendanceCSV(
                        _startDate.value,
                        _endDate.value
                    )
                    "pdf" -> RetrofitClient.adminApi.exportAttendancePDF(
                        _startDate.value,
                        _endDate.value
                    )
                    else -> {
                        _exportStatus.value = ExportStatus(
                            isSuccess = false,
                            message = "Unsupported format: $format"
                        )
                        _isLoading.value = false
                        return@launch
                    }
                }

                if (response.isSuccessful && response.body() != null) {
                    val fileName = "attendance_${System.currentTimeMillis()}.$format"
                    val savedFile = saveToDownloadsOptimized(response.body()!!, fileName)

                    if (savedFile != null) {
                        _exportStatus.value = ExportStatus(
                            isSuccess = true,
                            message = "✅ Downloaded: $fileName"
                        )
                        showToast("Downloaded successfully!")
                    } else {
                        _exportStatus.value = ExportStatus(
                            isSuccess = false,
                            message = "Failed to save file"
                        )
                    }
                } else {
                    _exportStatus.value = ExportStatus(
                        isSuccess = false,
                        message = "Export failed: ${response.code()}"
                    )
                }
            } catch (e: Exception) {
                _exportStatus.value = ExportStatus(
                    isSuccess = false,
                    message = "Error: ${e.message}"
                )
                Log.e("AdminExportVM", "Export attendance error: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun exportExpenses(format: String) {
        _exportStatus.value = ExportStatus(
            isSuccess = false,
            message = "Expenses module not yet implemented"
        )
    }

    fun exportTasks(format: String) {
        _exportStatus.value = ExportStatus(
            isSuccess = false,
            message = "Tasks module not yet implemented"
        )
    }

    /**
     * Optimized file download with proper buffering
     */
    private suspend fun saveToDownloadsOptimized(body: ResponseBody, fileName: String): String? {
        return withContext(Dispatchers.IO) {
            var inputStream: InputStream? = null
            var outputStream: OutputStream? = null

            try {
                inputStream = body.byteStream()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Use MediaStore for Android 10+
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, getMimeType(fileName))
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }

                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

                    uri?.let {
                        outputStream = resolver.openOutputStream(it)
                        outputStream?.let { output ->
                            copyStreamWithProgress(inputStream, output)
                        }
                        Log.d("AdminExportVM", "File saved via MediaStore: $uri")
                        fileName
                    }
                } else {
                    // For Android 9 and below
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS
                    )

                    if (!downloadsDir.exists()) {
                        downloadsDir.mkdirs()
                    }

                    val file = File(downloadsDir, fileName)
                    outputStream = FileOutputStream(file)

                    copyStreamWithProgress(inputStream, outputStream)

                    // Notify download manager
                    notifyDownloadManager(file, fileName)

                    Log.d("AdminExportVM", "File saved: ${file.absolutePath}")
                    file.absolutePath
                }
            } catch (e: Exception) {
                Log.e("AdminExportVM", "Error saving to downloads: ${e.message}", e)
                null
            } finally {
                inputStream?.close()
                outputStream?.close()
            }
        }
    }

    /**
     * Copy stream with progress logging and proper buffering
     */
    private fun copyStreamWithProgress(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(16384) // 16KB buffer
        var bytesRead: Int
        var totalBytes = 0L
        var lastLogTime = System.currentTimeMillis()

        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
            totalBytes += bytesRead

            // Log progress every 2 seconds
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastLogTime > 2000) {
                Log.d("AdminExportVM", "Downloaded: ${totalBytes / 1024} KB")
                lastLogTime = currentTime
            }
        }

        output.flush()
        Log.d("AdminExportVM", "Total downloaded: ${totalBytes / 1024} KB")
    }

    private fun notifyDownloadManager(file: File, fileName: String) {
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.addCompletedDownload(
                fileName,
                "FieldApp Export",
                true,
                getMimeType(fileName),
                file.absolutePath,
                file.length(),
                true
            )
        } catch (e: Exception) {
            Log.e("AdminExportVM", "Error notifying download manager: ${e.message}")
        }
    }

    private fun getMimeType(fileName: String): String {
        return when {
            fileName.endsWith(".zip") -> "application/zip"
            fileName.endsWith(".pdf") -> "application/pdf"
            fileName.endsWith(".csv") -> "text/csv"
            fileName.endsWith(".json") -> "application/json"
            else -> "application/octet-stream"
        }
    }

    private fun showToast(message: String) {
        viewModelScope.launch(Dispatchers.Main) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
}

data class ExportStatus(
    val isSuccess: Boolean,
    val message: String
)