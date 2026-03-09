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

// Export type options
enum class ExportType(val label: String, val value: String) {
    ALL("All Data", "all"),
    ATTENDANCE("Attendance", "attendance"),
    EXPENSES("Expenses", "expenses")
}

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

    // Export type for per-user ZIP section
    private val _userExportType = MutableStateFlow(ExportType.ALL)
    val userExportType: StateFlow<ExportType> = _userExportType

    // Export type for all-data ZIP section
    private val _allExportType = MutableStateFlow(ExportType.ALL)
    val allExportType: StateFlow<ExportType> = _allExportType

    private val context = application.applicationContext

    init {
        loadUsers()
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
                if (response.isSuccessful && response.body()?.success == true) {
                    _users.value = response.body()!!.data
                    Log.d("AdminExportVM", "Loaded ${_users.value.size} users")
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

    fun setUserExportType(type: ExportType) {
        _userExportType.value = type
        _exportStatus.value = null
    }

    fun setAllExportType(type: ExportType) {
        _allExportType.value = type
        _exportStatus.value = null
    }

    // ── Per-user ZIP export ───────────────────────────────────────────

    fun exportUserData() {
        val user = _selectedUser.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _exportStatus.value = null
            try {
                val jsonBody = JSONObject().apply {
                    _startDate.value?.let { put("startDate", it) }
                    _endDate.value?.let { put("endDate", it) }
                    put("exportType", _userExportType.value.value)
                }
                val requestBody = jsonBody.toString()
                    .toRequestBody("application/json".toMediaTypeOrNull())

                Log.d("AdminExportVM", "Exporting user ${user.employeeId}, type=${_userExportType.value.value}")
                val response = RetrofitClient.adminApi.exportUserDataZip(user._id, requestBody)

                if (response.isSuccessful && response.body() != null) {
                    val typeTag = _userExportType.value.value
                    val fileName = "${user.employeeId}_${typeTag}_export_${System.currentTimeMillis()}.zip"
                    val savedFile = saveToDownloadsOptimized(response.body()!!, fileName)
                    if (savedFile != null) {
                        _exportStatus.value = ExportStatus(true, "✅ Saved to Downloads: $fileName")
                        showToast("Download complete!")
                    } else {
                        _exportStatus.value = ExportStatus(false, "Failed to save file")
                    }
                } else {
                    val msg = when (response.code()) {
                        404 -> "No records found for selected date range"
                        504 -> "Timeout. Try a smaller date range."
                        else -> "Export failed: ${response.code()}"
                    }
                    _exportStatus.value = ExportStatus(false, msg)
                    showToast(msg)
                }
            } catch (e: Exception) {
                val msg = if (e.message?.contains("timeout") == true)
                    "Timeout. Try a smaller date range (2-3 days)."
                else "Error: ${e.message}"
                _exportStatus.value = ExportStatus(false, msg)
                Log.e("AdminExportVM", "exportUserData error", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ── All-data ZIP export ───────────────────────────────────────────

    fun exportAllData() {
        viewModelScope.launch {
            _isLoading.value = true
            _exportStatus.value = null
            try {
                val jsonBody = JSONObject().apply {
                    _startDate.value?.let { put("startDate", it) }
                    _endDate.value?.let { put("endDate", it) }
                    put("exportType", _allExportType.value.value)
                }
                val requestBody = jsonBody.toString()
                    .toRequestBody("application/json".toMediaTypeOrNull())

                val response = RetrofitClient.adminApi.exportAllDataZip(requestBody)

                if (response.isSuccessful && response.body() != null) {
                    val typeTag = _allExportType.value.value
                    val fileName = "all_${typeTag}_export_${System.currentTimeMillis()}.zip"
                    val savedFile = saveToDownloadsOptimized(response.body()!!, fileName)
                    if (savedFile != null) {
                        _exportStatus.value = ExportStatus(true, "✅ Saved to Downloads: $fileName")
                        showToast("Download complete!")
                    } else {
                        _exportStatus.value = ExportStatus(false, "Failed to save file")
                    }
                } else {
                    val msg = when (response.code()) {
                        404 -> "No records found for selected date range"
                        504 -> "Timeout. Try a smaller date range."
                        else -> "Export failed: ${response.code()}"
                    }
                    _exportStatus.value = ExportStatus(false, msg)
                    showToast(msg)
                }
            } catch (e: Exception) {
                val msg = if (e.message?.contains("timeout") == true)
                    "Timeout. Try a smaller date range." else "Error: ${e.message}"
                _exportStatus.value = ExportStatus(false, msg)
                Log.e("AdminExportVM", "exportAllData error", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ── Quick exports ─────────────────────────────────────────────────

    fun exportAttendance(format: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _exportStatus.value = null
            try {
                val response = when (format) {
                    "csv" -> RetrofitClient.adminApi.exportAttendanceCSV(_startDate.value, _endDate.value)
                    "pdf" -> RetrofitClient.adminApi.exportAttendancePDF(_startDate.value, _endDate.value)
                    else  -> {
                        _exportStatus.value = ExportStatus(false, "Unsupported format: $format")
                        _isLoading.value = false
                        return@launch
                    }
                }
                if (response.isSuccessful && response.body() != null) {
                    val fileName = "attendance_${System.currentTimeMillis()}.$format"
                    val saved = saveToDownloadsOptimized(response.body()!!, fileName)
                    if (saved != null) {
                        _exportStatus.value = ExportStatus(true, "✅ Downloaded: $fileName")
                        showToast("Downloaded!")
                    } else {
                        _exportStatus.value = ExportStatus(false, "Failed to save file")
                    }
                } else {
                    _exportStatus.value = ExportStatus(false, "Export failed: ${response.code()}")
                }
            } catch (e: Exception) {
                _exportStatus.value = ExportStatus(false, "Error: ${e.message}")
                Log.e("AdminExportVM", "exportAttendance error", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun exportExpenses(format: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _exportStatus.value = null
            try {
                val response = when (format) {
                    "pdf" -> RetrofitClient.adminApi.exportAllExpensesPDF(
                        _startDate.value, _endDate.value
                    )
                    "csv" -> RetrofitClient.adminApi.exportAllExpensesCSV(
                        _startDate.value, _endDate.value
                    )
                    else -> {
                        _exportStatus.value = ExportStatus(false, "Unsupported format: $format")
                        _isLoading.value = false
                        return@launch
                    }
                }
                if (response.isSuccessful && response.body() != null) {
                    val fileName = "expenses_${System.currentTimeMillis()}.$format"
                    val saved = saveToDownloadsOptimized(response.body()!!, fileName)
                    if (saved != null) {
                        _exportStatus.value = ExportStatus(true, "✅ Downloaded: $fileName")
                        showToast("Downloaded!")
                    } else {
                        _exportStatus.value = ExportStatus(false, "Failed to save file")
                    }
                } else {
                    _exportStatus.value = ExportStatus(false, "Export failed: ${response.code()}")
                }
            } catch (e: Exception) {
                _exportStatus.value = ExportStatus(false, "Error: ${e.message}")
                Log.e("AdminExportVM", "exportExpenses error", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun exportTasks(format: String) {
        _exportStatus.value = ExportStatus(false, "Tasks export coming soon")
    }

    // ── File saving ───────────────────────────────────────────────────

    private suspend fun saveToDownloadsOptimized(body: ResponseBody, fileName: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream = body.byteStream()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, getMimeType(fileName))
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = context.contentResolver.insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues
                    ) ?: throw Exception("Could not create file")
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        copyStream(inputStream, out)
                    }
                    inputStream.close()
                    fileName
                } else {
                    @Suppress("DEPRECATION")
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS
                    )
                    if (!downloadsDir.exists()) downloadsDir.mkdirs()
                    val file = File(downloadsDir, fileName)
                    FileOutputStream(file).use { out -> copyStream(inputStream, out) }
                    inputStream.close()
                    notifyDownloadManager(file, fileName)
                    file.absolutePath
                }
            } catch (e: Exception) {
                Log.e("AdminExportVM", "Save error: ${e.message}", e)
                null
            }
        }
    }

    private fun copyStream(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(16384)
        var bytesRead: Int
        var total = 0L
        var lastLog = System.currentTimeMillis()
        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
            total += bytesRead
            val now = System.currentTimeMillis()
            if (now - lastLog > 2000) {
                Log.d("AdminExportVM", "Downloaded: ${total / 1024} KB")
                lastLog = now
            }
        }
        output.flush()
        Log.d("AdminExportVM", "Total: ${total / 1024} KB")
        if (total == 0L) throw Exception("Downloaded file is empty")
    }

    private fun notifyDownloadManager(file: File, fileName: String) {
        try {
            @Suppress("DEPRECATION")
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.addCompletedDownload(
                fileName, "FieldApp Export", true,
                getMimeType(fileName), file.absolutePath, file.length(), true
            )
        } catch (e: Exception) {
            Log.e("AdminExportVM", "DownloadManager notify error: ${e.message}")
        }
    }

    private fun getMimeType(fileName: String) = when {
        fileName.endsWith(".zip")  -> "application/zip"
        fileName.endsWith(".pdf")  -> "application/pdf"
        fileName.endsWith(".csv")  -> "text/csv"
        fileName.endsWith(".json") -> "application/json"
        else                       -> "application/octet-stream"
    }

    private fun showToast(message: String) {
        viewModelScope.launch(Dispatchers.Main) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    fun clearStatus() {
        _exportStatus.value = null
    }
}

data class ExportStatus(
    val isSuccess: Boolean,
    val message: String
)