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
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.company.fieldapp.data.local.ExpenseEntity
import com.company.fieldapp.navigation.NavRoutes
import java.io.File
import java.text.DecimalFormat

// ── Theme colours ────────────────────────────────────────────────
private val Orange = Color(0xFFFF6B35)
private val OrangeLight = Color(0xFFFFE5D9)
private val GreenStatus = Color(0xFF4CAF50)
private val GreenLight = Color(0xFFE8F5E9)
private val AmberStatus = Color(0xFFFFA000)
private val AmberLight = Color(0xFFFFF8E1)
private val Background = Color(0xFFF5F5F5)

// ── Helpers ──────────────────────────────────────────────────────
private fun formatAmount(amount: Double, currency: Currency): String {
    val df = DecimalFormat("#,##0.00")
    return "${currency.symbol}${df.format(amount)}"
}

/** Creates a temp file in the app cache and returns its URI via FileProvider */
fun createReceiptImageUri(context: Context): Pair<Uri, String> {
    val dir = File(context.cacheDir, "receipts").also { it.mkdirs() }
    val file = File.createTempFile("receipt_", ".jpg", dir)
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    return Pair(uri, file.absolutePath)
}

// ── Screen ───────────────────────────────────────────────────────
@Composable
fun ExpensesScreen(
    navController: NavHostController,
    viewModel: ExpensesViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val form by viewModel.form.collectAsState()
    val context = LocalContext.current

    // Camera state
    var captureUri by remember { mutableStateOf<Uri?>(null) }
    var capturePath by remember { mutableStateOf<String?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && capturePath != null) {
            viewModel.onReceiptCaptured(capturePath!!)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val (uri, path) = createReceiptImageUri(context)
            captureUri = uri
            capturePath = path
            cameraLauncher.launch(uri)
        }
    }

    fun launchCamera() {
        val (uri, path) = createReceiptImageUri(context)
        captureUri = uri
        capturePath = path
        cameraLauncher.launch(uri)
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

                // ── Header ──────────────────────────────────────
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
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
                                    "Submit and track your expenses",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Currency toggle pill
                                CurrencyToggle(
                                    currency = uiState.currency,
                                    onToggle = viewModel::toggleCurrency
                                )
                                // Add button
                                FloatingActionButton(
                                    onClick = { viewModel.showAddForm() },
                                    containerColor = Orange,
                                    contentColor = Color.White,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Default.Add, "Add Expense")
                                }
                            }
                        }
                    }
                }

                // ── Summary cards ────────────────────────────────
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SummaryCard(
                            label = "Pending",
                            amount = viewModel.convertAmount(uiState.totalPending),
                            currency = uiState.currency,
                            iconTint = AmberStatus,
                            iconBg = AmberLight,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryCard(
                            label = "Approved",
                            amount = viewModel.convertAmount(uiState.totalApproved),
                            currency = uiState.currency,
                            iconTint = GreenStatus,
                            iconBg = GreenLight,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // ── Add form (animated) ──────────────────────────
                item {
                    AnimatedVisibility(
                        visible = uiState.showAddForm,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        AddExpenseForm(
                            form = form,
                            currency = uiState.currency,
                            isLoading = uiState.isLoading,
                            errorMessage = uiState.errorMessage,
                            onCategoryChange = viewModel::onCategoryChange,
                            onAmountChange = viewModel::onAmountChange,
                            onDateChange = viewModel::onDateChange,
                            onDescriptionChange = viewModel::onDescriptionChange,
                            onCaptureReceipt = {
                                permissionLauncher.launch(android.Manifest.permission.CAMERA)
                            },
                            onClearReceipt = viewModel::clearReceipt,
                            onSubmit = viewModel::submitExpense,
                            onDismiss = viewModel::hideAddForm,
                            onClearError = viewModel::clearError
                        )
                    }
                }

                // ── List header ──────────────────────────────────
                item {
                    Text(
                        text = "Recent Expenses",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // ── List / empty ─────────────────────────────────
                if (uiState.expenses.isEmpty()) {
                    item { EmptyExpensesState() }
                } else {
                    items(uiState.expenses) { expense ->
                        ExpenseItem(
                            expense = expense,
                            currency = uiState.currency,
                            displayAmount = viewModel.convertAmount(expense.amount)
                        )
                    }
                }
            }
        }
    }
}

// ── Currency toggle ───────────────────────────────────────────────
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
                text = "₹",
                fontWeight = if (currency == Currency.INR) FontWeight.Bold else FontWeight.Normal,
                color = if (currency == Currency.INR) Orange else Color.Gray,
                style = MaterialTheme.typography.bodyMedium
            )
            Text("|", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            Text(
                text = "$",
                fontWeight = if (currency == Currency.USD) FontWeight.Bold else FontWeight.Normal,
                color = if (currency == Currency.USD) Orange else Color.Gray,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

// ── Summary card ──────────────────────────────────────────────────
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
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(iconBg, CircleShape),
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
                    text = label,
                    color = iconTint,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = formatAmount(amount, currency),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ── Add expense form ──────────────────────────────────────────────
@Composable
fun AddExpenseForm(
    form: NewExpenseForm,
    currency: Currency,
    isLoading: Boolean,
    errorMessage: String?,
    onCategoryChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onCaptureReceipt: () -> Unit,
    onClearReceipt: () -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    onClearError: () -> Unit
) {
    val categories = listOf("Travel", "Meals", "Lodging", "Supplies", "Equipment", "Other")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {

            // Title row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "New Expense",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Close")
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Category ─────────────────────────────────────
            FormLabel("Category *")
            Spacer(Modifier.height(8.dp))
            val rows = categories.chunked(3)
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { name ->
                        CategoryChip(
                            label = name,
                            selected = form.category == name,
                            onClick = { onCategoryChange(name) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(4.dp))

            // ── Amount ───────────────────────────────────────
            FormLabel("Amount *")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = form.amount,
                onValueChange = onAmountChange,
                placeholder = { Text("0.00") },
                leadingIcon = {
                    Text(
                        currency.symbol,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 4.dp),
                        color = Orange
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(12.dp))

            // ── Date ─────────────────────────────────────────
            FormLabel("Date")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = form.date,
                onValueChange = onDateChange,
                leadingIcon = { Icon(Icons.Default.CalendarToday, null, tint = Color.Gray) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(12.dp))

            // ── Description ──────────────────────────────────
            FormLabel("Description")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = form.description,
                onValueChange = onDescriptionChange,
                placeholder = { Text("Add notes...") },
                leadingIcon = { Icon(Icons.Default.Notes, null, tint = Color.Gray) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                maxLines = 4,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(12.dp))

            // ── Receipt Photo ─────────────────────────────────
            FormLabel("Receipt Photo")
            Spacer(Modifier.height(8.dp))

            if (form.receiptImagePath != null) {
                // Show captured image with retake option
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Orange, RoundedCornerShape(12.dp))
                ) {
                    AsyncImage(
                        model = File(form.receiptImagePath),
                        contentDescription = "Receipt",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    // Retake / clear button overlay
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TextButton(onClick = onCaptureReceipt) {
                            Icon(
                                Icons.Default.CameraAlt,
                                null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Retake", color = Color.White)
                        }
                        TextButton(onClick = onClearReceipt) {
                            Icon(
                                Icons.Default.Delete,
                                null,
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Remove", color = Color(0xFFFF5252))
                        }
                    }
                }
            } else {
                // Camera capture button
                Surface(
                    onClick = onCaptureReceipt,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF9F9F9),
                    border = BorderStroke(1.dp, Color.LightGray)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.CameraAlt,
                            null,
                            tint = Color.Gray,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Capture Receipt",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Error message
            if (errorMessage != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(16.dp))

            // Submit
            Button(
                onClick = onSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
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
                        "Submit Expense",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// ── Category chip (text-only, clean) ─────────────────────────────
@Composable
fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) OrangeLight else Color(0xFFF5F5F5),
        border = if (selected) BorderStroke(1.5.dp, Orange) else BorderStroke(1.dp, Color(0xFFE0E0E0))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) Orange else Color(0xFF424242),
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

// ── Expense list item ─────────────────────────────────────────────
@Composable
fun ExpenseItem(
    expense: ExpenseEntity,
    currency: Currency,
    displayAmount: Double
) {
    val statusColor = when (expense.status) {
        "approved" -> GreenStatus
        "rejected" -> Color(0xFFD32F2F)
        else -> AmberStatus
    }
    val statusBg = when (expense.status) {
        "approved" -> GreenLight
        "rejected" -> Color(0xFFFFEBEE)
        else -> AmberLight
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category initial badge
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(OrangeLight, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = expense.category.take(2).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Orange
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Surface(color = statusBg, shape = RoundedCornerShape(4.dp)) {
                    Text(
                        text = expense.status.uppercase(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (expense.description.isNotBlank()) expense.description
                    else expense.category,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = expense.date,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            Text(
                text = formatAmount(displayAmount, currency),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────
@Composable
private fun FormLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium
    )
}

@Composable
fun EmptyExpensesState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Receipt,
            null,
            modifier = Modifier.size(64.dp),
            tint = Color.LightGray
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "No expenses yet",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.Gray
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Tap + to submit your first expense",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.LightGray
        )
    }
}