package com.company.fieldapp.ui.expenses

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.company.fieldapp.data.local.ExpenseEntity
import com.company.fieldapp.navigation.NavRoutes
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

// ── Theme ─────────────────────────────────────────────────────────
private val Orange = Color(0xFFFF6B35)
private val OrangeLight = Color(0xFFFFE5D9)
private val GreenStatus = Color(0xFF4CAF50)
private val GreenLight = Color(0xFFE8F5E9)
private val AmberStatus = Color(0xFFFFA000)
private val AmberLight = Color(0xFFFFF8E1)
private val Background = Color(0xFFF5F5F5)

private fun fmt(amount: Double, currency: Currency): String {
    val df = DecimalFormat("#,##0.00")
    return "${currency.symbol}${df.format(amount)}"
}

fun createReceiptImageUri(context: Context): Pair<Uri, String> {
    val dir = File(context.cacheDir, "receipts").also { it.mkdirs() }
    val file = File.createTempFile("receipt_", ".jpg", dir)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    return Pair(uri, file.absolutePath)
}

// ── Date Picker Field ─────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    label: String,
    value: String,
    onDateSelected: (String) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

    OutlinedTextField(
        value = value,
        onValueChange = { },
        readOnly = true,
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        trailingIcon = {
            IconButton(onClick = { showPicker = true }) {
                Icon(Icons.Default.CalendarToday, null, tint = Orange, modifier = Modifier.size(18.dp))
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showPicker = true },
        shape = RoundedCornerShape(10.dp),
        textStyle = MaterialTheme.typography.bodySmall
    )

    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) onDateSelected(sdf.format(Date(millis)))
                    showPicker = false
                }) { Text("OK", color = Orange) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    selectedDayContainerColor = Orange,
                    todayDateBorderColor = Orange,
                    selectedDayContentColor = Color.White
                )
            )
        }
    }
}

// ── Screen ────────────────────────────────────────────────────────
@Composable
fun ExpensesScreen(
    navController: NavHostController,
    viewModel: ExpensesViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val form by viewModel.form.collectAsState()
    val context = LocalContext.current

    var pendingCameraItemId by remember { mutableStateOf<String?>(null) }
    var capturePath by remember { mutableStateOf<String?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && pendingCameraItemId != null && capturePath != null)
            viewModel.onReceiptCaptured(pendingCameraItemId!!, capturePath!!)
        pendingCameraItemId = null
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingCameraItemId != null) {
            val (uri, path) = createReceiptImageUri(context)
            capturePath = path
            cameraLauncher.launch(uri)
        }
    }

    fun requestCamera(itemId: String) {
        pendingCameraItemId = itemId
        permissionLauncher.launch(android.Manifest.permission.CAMERA)
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate(NavRoutes.Attendance.route) },
                    icon = { Icon(Icons.Default.AccessTime, "Attendance") },
                    label = { Text("Attendance") }
                )
                NavigationBarItem(
                    selected = true,
                    onClick = { },
                    icon = { Icon(Icons.Default.Receipt, "Expenses") },
                    label = { Text("Expenses") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Orange,
                        selectedTextColor = Orange,
                        indicatorColor = OrangeLight
                    )
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate(NavRoutes.Tasks.route) },
                    icon = { Icon(Icons.Default.Work, "Tasks") },
                    label = { Text("Tasks") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate(NavRoutes.Profile.route) },
                    icon = { Icon(Icons.Default.Person, "Profile") },
                    label = { Text("Profile") }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Background)
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {

                // ── Header ───────────────────────────────────────
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Expenses",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Submit and track your travel expenses",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CurrencyToggle(uiState.currency, viewModel::toggleCurrency)
                                FloatingActionButton(
                                    onClick = { viewModel.showAddForm() },
                                    containerColor = Orange,
                                    contentColor = Color.White,
                                    modifier = Modifier.size(48.dp)
                                ) { Icon(Icons.Default.Add, "Add") }
                            }
                        }
                    }
                }

                // ── Summary cards ────────────────────────────────
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SummaryCard(
                            "Pending",
                            viewModel.convertAmount(uiState.totalPending),
                            uiState.currency, AmberStatus, AmberLight,
                            Modifier.weight(1f)
                        )
                        SummaryCard(
                            "Approved",
                            viewModel.convertAmount(uiState.totalApproved),
                            uiState.currency, GreenStatus, GreenLight,
                            Modifier.weight(1f)
                        )
                    }
                }

                // ── Trip form ────────────────────────────────────
                item {
                    AnimatedVisibility(
                        visible = uiState.showAddForm,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        TripExpenseForm(
                            form = form,
                            currency = uiState.currency,
                            isLoading = uiState.isLoading,
                            errorMessage = uiState.errorMessage,
                            onStationChange = viewModel::onStationChange,
                            onPeriodFromChange = viewModel::onPeriodFromChange,
                            onPeriodToChange = viewModel::onPeriodToChange,
                            onAdvanceChange = viewModel::onAdvanceChange,
                            onUpdateItem = viewModel::updateLineItem,
                            onAddItem = viewModel::addLineItem,
                            onRemoveItem = viewModel::removeLineItem,
                            onCaptureReceipt = { requestCamera(it) },
                            onClearReceipt = viewModel::clearReceipt,
                            onSubmit = viewModel::submitTrip,
                            onDismiss = viewModel::hideAddForm
                        )
                    }
                }

                // ── Trips list header ────────────────────────────
                item {
                    Text(
                        "My Trips",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                if (uiState.trips.isEmpty()) {
                    item { EmptyState() }
                } else {
                    items(uiState.trips) { trip ->
                        TripCard(
                            trip = trip,
                            currency = uiState.currency,
                            onConvert = viewModel::convertAmount
                        )
                    }
                }
            }
        }
    }
}

// ── Trip expense form ─────────────────────────────────────────────
@Composable
fun TripExpenseForm(
    form: TripForm,
    currency: Currency,
    isLoading: Boolean,
    errorMessage: String?,
    onStationChange: (String) -> Unit,
    onPeriodFromChange: (String) -> Unit,
    onPeriodToChange: (String) -> Unit,
    onAdvanceChange: (String) -> Unit,
    onUpdateItem: (String, ExpenseLineItem) -> Unit,
    onAddItem: () -> Unit,
    onRemoveItem: (String) -> Unit,
    onCaptureReceipt: (String) -> Unit,
    onClearReceipt: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(Modifier.padding(20.dp)) {

            // Title row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Travel Expense Form",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, null)
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Trip header section ───────────────────────────
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFEEEEEE)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "Trip Details",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Orange
                    )
                    Spacer(Modifier.height(10.dp))

                    // Station visited
                    FormLabel("Station / Place Visited *")
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = form.stationVisited,
                        onValueChange = onStationChange,
                        placeholder = { Text("e.g. Mumbai, Delhi") },
                        leadingIcon = {
                            Icon(Icons.Default.LocationOn, null, tint = Color.Gray)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(Modifier.height(10.dp))

                    // Period From / To — date pickers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            DatePickerField(
                                label = "From",
                                value = form.periodFrom,
                                onDateSelected = onPeriodFromChange
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            DatePickerField(
                                label = "To",
                                value = form.periodTo,
                                onDateSelected = onPeriodToChange
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Advance / Imprest
                    FormLabel("Advance / Imprest Taken")
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = form.advanceAmount,
                        onValueChange = onAdvanceChange,
                        placeholder = { Text("0.00") },
                        leadingIcon = {
                            Text(
                                currency.symbol,
                                fontWeight = FontWeight.Bold,
                                color = Orange,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Divider(color = Color(0xFFEEEEEE))
            Spacer(Modifier.height(16.dp))

            // ── Expense items ─────────────────────────────────
            Text(
                "Expense Items",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Orange
            )
            Spacer(Modifier.height(10.dp))

            form.items.forEachIndexed { index, item ->
                ExpenseItemForm(
                    item = item,
                    index = index,
                    currency = currency,
                    canRemove = form.items.size > 1,
                    onUpdate = { onUpdateItem(item.id, it) },
                    onRemove = { onRemoveItem(item.id) },
                    onCaptureReceipt = { onCaptureReceipt(item.id) },
                    onClearReceipt = { onClearReceipt(item.id) }
                )
                if (index < form.items.lastIndex) {
                    Spacer(Modifier.height(8.dp))
                    Divider(color = Color(0xFFEEEEEE))
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(12.dp))

            // Add item button
            OutlinedButton(
                onClick = onAddItem,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.5.dp, Orange),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Orange)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add Expense Item", fontWeight = FontWeight.SemiBold)
            }

            // ── Total / Payable summary ───────────────────────
            val total = form.items.sumOf { item ->
                item.amount.toDoubleOrNull() ?: 0.0
            }
            val advance = form.advanceAmount.toDoubleOrNull() ?: 0.0
            val payable = total - advance

            if (total > 0) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = OrangeLight,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("Total", style = MaterialTheme.typography.bodyMedium, color = Orange)
                            Text(fmt(total, currency), fontWeight = FontWeight.Bold, color = Orange)
                        }
                        if (advance > 0) {
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text(
                                    "Advance",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                                Text(
                                    "- ${fmt(advance, currency)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                            Divider(color = Orange.copy(alpha = 0.3f))
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text(
                                    if (payable >= 0) "Payable" else "Refundable",
                                    fontWeight = FontWeight.Bold,
                                    color = Orange
                                )
                                Text(
                                    fmt(kotlin.math.abs(payable), currency),
                                    fontWeight = FontWeight.Bold,
                                    color = Orange
                                )
                            }
                        }
                    }
                }
            }

            // Error message
            if (errorMessage != null) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = Color(0xFFFFEBEE),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Error, null,
                            tint = Color(0xFFD32F2F),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            errorMessage,
                            color = Color(0xFFD32F2F),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Submit button
            Button(
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Orange),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        "Submit Trip Expenses",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// ── Single expense item inside form ──────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseItemForm(
    item: ExpenseLineItem,
    index: Int,
    currency: Currency,
    canRemove: Boolean,
    onUpdate: (ExpenseLineItem) -> Unit,
    onRemove: () -> Unit,
    onCaptureReceipt: () -> Unit,
    onClearReceipt: () -> Unit
) {
    Column {
        // Item header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(OrangeLight, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Orange
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "Expense ${index + 1}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (canRemove) {
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.RemoveCircleOutline, null,
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Expense type selector
        FormLabel("Type")
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ExpenseType.entries.forEach { type ->
                FilterChip(
                    selected = item.expenseType == type,
                    onClick = { onUpdate(item.copy(expenseType = type)) },
                    label = { Text(type.label, style = MaterialTheme.typography.bodySmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = OrangeLight,
                        selectedLabelColor = Orange
                    )
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Type-specific fields ──────────────────────────────
        when (item.expenseType) {

            ExpenseType.HOTEL -> {
                // Hotel name/place + number of days + amount
                OutlinedTextField(
                    value = item.details,
                    onValueChange = { onUpdate(item.copy(details = it)) },
                    placeholder = { Text("Hotel name / place") },
                    leadingIcon = {
                        Icon(Icons.Default.LocationOn, null, tint = Color.Gray)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    textStyle = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = item.daysCount,
                        onValueChange = { onUpdate(item.copy(daysCount = it)) },
                        placeholder = { Text("No. of days") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(10.dp),
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = item.amount,
                        onValueChange = { onUpdate(item.copy(amount = it)) },
                        placeholder = { Text("Amount") },
                        leadingIcon = {
                            Text(
                                currency.symbol,
                                fontWeight = FontWeight.SemiBold,
                                color = Orange,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(10.dp),
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                }
            }

            ExpenseType.TRAVEL -> {
                // From / To + mode + amount
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = item.travelFrom,
                        onValueChange = { onUpdate(item.copy(travelFrom = it)) },
                        placeholder = { Text("From") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = item.travelTo,
                        onValueChange = { onUpdate(item.copy(travelTo = it)) },
                        placeholder = { Text("To") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Air", "Train", "Bus").forEach { mode ->
                        FilterChip(
                            selected = item.travelMode == mode,
                            onClick = { onUpdate(item.copy(travelMode = mode)) },
                            label = { Text(mode, style = MaterialTheme.typography.bodySmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = OrangeLight,
                                selectedLabelColor = Orange
                            )
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                AmountField(currency, item.amount) { onUpdate(item.copy(amount = it)) }
            }

            ExpenseType.DAILY_ALLOWANCE -> {
                // Amount + notes only
                AmountField(currency, item.amount) { onUpdate(item.copy(amount = it)) }
            }

            ExpenseType.LOCAL_CONVEYANCE -> {
                // From / To + amount (no travel mode)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = item.travelFrom,
                        onValueChange = { onUpdate(item.copy(travelFrom = it)) },
                        placeholder = { Text("From") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = item.travelTo,
                        onValueChange = { onUpdate(item.copy(travelTo = it)) },
                        placeholder = { Text("To") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(6.dp))
                AmountField(currency, item.amount) { onUpdate(item.copy(amount = it)) }
            }

            ExpenseType.OTHER -> {
                // Amount only — details field below handles description
                AmountField(currency, item.amount) { onUpdate(item.copy(amount = it)) }
            }
        }

        // Shared notes field (shown for all types, placeholder changes per type)
        // Skip for HOTEL since details is already used for hotel name
        if (item.expenseType != ExpenseType.HOTEL) {
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = item.details,
                onValueChange = { onUpdate(item.copy(details = it)) },
                placeholder = {
                    Text(
                        when (item.expenseType) {
                            ExpenseType.TRAVEL -> "Ticket details / notes..."
                            ExpenseType.DAILY_ALLOWANCE -> "Notes..."
                            ExpenseType.LOCAL_CONVEYANCE -> "Vehicle type / notes..."
                            ExpenseType.OTHER -> "Describe the expense..."
                            else -> "Notes..."
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth().height(72.dp),
                maxLines = 2,
                shape = RoundedCornerShape(10.dp),
                textStyle = MaterialTheme.typography.bodySmall
            )
        }

        // Receipt photo
        Spacer(Modifier.height(8.dp))
        if (item.receiptImagePath != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, Orange, RoundedCornerShape(10.dp))
            ) {
                AsyncImage(
                    model = File(item.receiptImagePath),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = onCaptureReceipt) {
                        Icon(
                            Icons.Default.CameraAlt, null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Retake", color = Color.White, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = onClearReceipt) {
                        Icon(
                            Icons.Default.Delete, null,
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Remove",
                            color = Color(0xFFFF5252),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        } else {
            Surface(
                onClick = onCaptureReceipt,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFF9F9F9),
                border = BorderStroke(1.dp, Color.LightGray)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CameraAlt, null,
                        tint = Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Attach Receipt",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

// ── Trip card (list item) ─────────────────────────────────────────
@Composable
fun TripCard(
    trip: TripGroup,
    currency: Currency,
    onConvert: (Double) -> Double
) {
    val statusColor = when (trip.status) {
        "approved" -> GreenStatus
        "rejected" -> Color(0xFFD32F2F)
        else -> AmberStatus
    }
    val statusBg = when (trip.status) {
        "approved" -> GreenLight
        "rejected" -> Color(0xFFFFEBEE)
        else -> AmberLight
    }
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp)) {

            // Trip header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    Surface(color = statusBg, shape = RoundedCornerShape(4.dp)) {
                        Text(
                            trip.status.uppercase(),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.LocationOn, null,
                            tint = Orange,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            trip.stationVisited,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        "${trip.periodFrom}  →  ${trip.periodTo}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Text(
                        "${trip.items.size} expense item${if (trip.items.size > 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        fmt(onConvert(trip.total), currency),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (trip.advanceAmount > 0) {
                        Text(
                            "Adv: ${fmt(onConvert(trip.advanceAmount), currency)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        val payable = trip.total - trip.advanceAmount
                        Text(
                            "${if (payable >= 0) "Pay:" else "Ref:"} ${fmt(onConvert(kotlin.math.abs(payable)), currency)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (payable >= 0) AmberStatus else GreenStatus,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Expand / collapse
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.padding(top = 4.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    if (expanded) "Hide details" else "View details",
                    color = Orange,
                    style = MaterialTheme.typography.bodySmall
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null, tint = Orange, modifier = Modifier.size(16.dp)
                )
            }

            // Breakdown
            AnimatedVisibility(expanded) {
                Column(Modifier.fillMaxWidth()) {
                    Divider(color = Color(0xFFEEEEEE))
                    Spacer(Modifier.height(6.dp))
                    trip.items.forEachIndexed { i, expense ->
                        ExpenseBreakdownRow(expense, currency, onConvert)
                        if (i < trip.items.lastIndex)
                            Divider(
                                color = Color(0xFFF5F5F5),
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                    }
                }
            }
        }
    }
}

@Composable
fun ExpenseBreakdownRow(
    expense: ExpenseEntity,
    currency: Currency,
    onConvert: (Double) -> Double
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                expense.expenseType,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            val sub = buildString {
                if (expense.travelFrom.isNotBlank())
                    append("${expense.travelFrom} → ${expense.travelTo}")
                if (expense.travelMode.isNotBlank())
                    append("  (${expense.travelMode})")
                if (expense.daysCount > 0)
                    append("  ${expense.daysCount} days")
                if (expense.details.isNotBlank()) {
                    if (isNotEmpty()) append("  ·  ")
                    append(expense.details)
                }
            }
            if (sub.isNotBlank())
                Text(
                    sub.trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
        }
        Text(
            fmt(onConvert(expense.amount), currency),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ── Reusable composables ──────────────────────────────────────────
@Composable
fun AmountField(currency: Currency, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text("0.00") },
        leadingIcon = {
            Text(
                currency.symbol,
                fontWeight = FontWeight.SemiBold,
                color = Orange,
                modifier = Modifier.padding(start = 4.dp)
            )
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(10.dp)
    )
}

@Composable
fun CurrencyToggle(currency: Currency, onToggle: () -> Unit) {
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(20.dp),
        color = OrangeLight,
        border = BorderStroke(1.dp, Orange)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "₹",
                fontWeight = if (currency == Currency.INR) FontWeight.Bold else FontWeight.Normal,
                color = if (currency == Currency.INR) Orange else Color.Gray,
                style = MaterialTheme.typography.bodyMedium
            )
            Text("|", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            Text(
                "$",
                fontWeight = if (currency == Currency.USD) FontWeight.Bold else FontWeight.Normal,
                color = if (currency == Currency.USD) Orange else Color.Gray,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun SummaryCard(
    label: String,
    amount: Double,
    currency: Currency,
    iconTint: Color,
    iconBg: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(32.dp).background(iconBg, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.AttachMoney, null,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    label,
                    color = iconTint,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                fmt(amount, currency),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Receipt, null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
        Spacer(Modifier.height(16.dp))
        Text(
            "No trips submitted yet",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.Gray
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Tap + to submit your travel expenses",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.LightGray
        )
    }
}

@Composable
private fun FormLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
}