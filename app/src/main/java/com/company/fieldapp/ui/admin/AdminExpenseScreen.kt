package com.company.fieldapp.ui.admin

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import java.text.DecimalFormat

// ── Colours (aligned with AdminAttendanceScreen / AdminExportScreen) ──
private val Orange      = Color(0xFFFF6B35)
private val OrangeLight = Color(0xFFFFD5C2)
private val Green       = Color(0xFF2E7D32)
private val GreenLight  = Color(0xFFC8E6C9)
private val Red         = Color(0xFFC62828)
private val RedLight    = Color(0xFFFFCDD2)
private val Amber       = Color(0xFFE65100)
private val AmberLight  = Color(0xFFFFE0B2)
private val PageBg      = Color(0xFFF5F5F5)   // matches Attendance/Export
private val CardBg      = Color.White          // matches Attendance/Export
private val DividerColor = Color(0xFFEEEEEE)
private val TextMain    = Color(0xFF212121)    // matches Attendance/Export
private val TextSub     = Color.Gray           // matches Attendance/Export

private fun inr(v: Double) = "₹${DecimalFormat("#,##0.00").format(v)}"

// ─────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminExpenseScreen(
    navController: NavHostController,
    viewModel: AdminExpenseViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    // Reject dialog state
    var showRejectDialog by remember { mutableStateOf(false) }
    var rejectNote       by remember { mutableStateOf("") }
    var rejectTripId     by remember { mutableStateOf<String?>(null) }

    // Messages → snackbar
    LaunchedEffect(state.successMessage) {
        state.successMessage?.let { snackbar.showSnackbar(it); viewModel.clearMessages() }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbar.showSnackbar(it); viewModel.clearMessages() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = PageBg
    ) { pad ->

        Box(
            Modifier
                .fillMaxSize()
                .padding(pad)
        ) {
            if (state.isLoading && state.trips.isEmpty()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Orange)
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 40.dp)
                ) {

                    // ── Header Card (matches Attendance / Export style) ─
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(4.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp)
                            ) {
                                // Top row: back + admin label (matches Attendance/Export exactly)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { navController.navigateUp() },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(Color(0xFFF5F5F5), CircleShape)
                                    ) {
                                        Icon(
                                            Icons.Default.ArrowBack,
                                            contentDescription = "Back",
                                            tint = Color(0xFF212121)
                                        )
                                    }
                                    Text(
                                        text = "Administrator",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.Gray
                                    )
                                }

                                Spacer(Modifier.height(20.dp))

                                // Title row: icon box + text + pending badge + refresh
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .background(
                                                Orange.copy(alpha = 0.1f),
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Receipt,
                                            contentDescription = null,
                                            tint = Orange,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = "Expense Approvals",
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Review and approve employee trip expenses",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        IconButton(
                                            onClick = { viewModel.loadTrips() },
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(Color(0xFFF5F5F5), CircleShape)
                                        ) {
                                            if (state.isLoading)
                                                CircularProgressIndicator(
                                                    Modifier.size(18.dp),
                                                    color = Orange, strokeWidth = 2.dp
                                                )
                                            else
                                                Icon(Icons.Default.Refresh, "Refresh",
                                                    tint = Color(0xFF212121),
                                                    modifier = Modifier.size(20.dp))
                                        }
                                        if (state.stats.pendingCount > 0) {
                                            Spacer(Modifier.height(4.dp))
                                            Surface(
                                                color = Red,
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text(
                                                    "${state.stats.pendingCount} pending",
                                                    color = Color.White,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── Stats strip ───────────────────────────────
                    item { StatsStrip(state.stats) }

                    // ── Search + filters ──────────────────────────
                    item {
                        SearchAndFilters(
                            query        = state.searchQuery,
                            statusFilter = state.statusFilter,
                            onQuery      = viewModel::onSearchChange,
                            onFilter     = viewModel::onStatusFilterChange
                        )
                    }

                    // ── Count header ──────────────────────────────
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            Arrangement.SpaceBetween,
                            Alignment.CenterVertically
                        ) {
                            Text(
                                "${state.filteredTrips.size} trip${if (state.filteredTrips.size != 1) "s" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSub
                            )
                            if (state.statusFilter == "all" || state.statusFilter == "pending") {
                                val pendingCount = state.filteredTrips.count { it.status == "pending" }
                                if (pendingCount > 0) {
                                    Text(
                                        "$pendingCount awaiting review",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Amber,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    // ── List ──────────────────────────────────────
                    if (state.filteredTrips.isEmpty()) {
                        item { EmptyState(state.statusFilter) }
                    } else {
                        items(state.filteredTrips, key = { it.id }) { trip ->
                            TripCard(
                                trip     = trip,
                                onTap    = { viewModel.openDetail(trip) },
                                onApprove = { viewModel.approveTrip(trip.id) },
                                onReject = {
                                    rejectTripId = trip.id
                                    rejectNote   = ""
                                    showRejectDialog = true
                                }
                            )
                        }
                    }
                }
            }
        }

        // ── Reject dialog ─────────────────────────────────────────
        if (showRejectDialog) {
            AlertDialog(
                onDismissRequest = { showRejectDialog = false },
                icon = { Icon(Icons.Default.Close, null, tint = Red) },
                title = { Text("Reject Trip", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            "Add a note to explain the rejection (optional):",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSub
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = rejectNote,
                            onValueChange = { rejectNote = it },
                            placeholder = { Text("e.g. Missing receipts, incorrect amounts…") },
                            modifier = Modifier.fillMaxWidth().height(110.dp),
                            maxLines = 5,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Red,
                                unfocusedBorderColor = DividerColor
                            )
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            rejectTripId?.let { viewModel.rejectTrip(it, rejectNote) }
                            showRejectDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Red),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Confirm Reject") }
                },
                dismissButton = {
                    TextButton(onClick = { showRejectDialog = false }) {
                        Text("Cancel", color = TextSub)
                    }
                },
                shape = RoundedCornerShape(16.dp)
            )
        }

        // ── Detail sheet ──────────────────────────────────────────
        if (state.showDetailSheet && state.selectedTrip != null) {
            TripDetailSheet(
                trip      = state.selectedTrip!!,
                onDismiss = viewModel::closeDetail,
                onApprove = { viewModel.approveTrip(state.selectedTrip!!.id) },
                onReject  = {
                    rejectTripId = state.selectedTrip!!.id
                    rejectNote   = ""
                    viewModel.closeDetail()
                    showRejectDialog = true
                }
            )
        }
    }
}

// ── Stats strip ───────────────────────────────────────────────────
@Composable
private fun StatsStrip(stats: AdminExpenseStats) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatTile("Pending",  stats.pendingCount,  inr(stats.totalPendingAmount),  Amber,  AmberLight, Modifier.weight(1f))
        StatTile("Approved", stats.approvedCount, inr(stats.totalApprovedAmount), Green,  GreenLight, Modifier.weight(1f))
        StatTile("Rejected", stats.rejectedCount, "",                             Red,    RedLight,   Modifier.weight(1f))
        StatTile("Total",    stats.pendingCount + stats.approvedCount + stats.rejectedCount,
            inr(stats.totalAmount), Orange, OrangeLight, Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(
    label: String, count: Int, sub: String,
    textColor: Color, bg: Color, modifier: Modifier
) {
    Card(
        modifier    = modifier,
        colors      = CardDefaults.cardColors(containerColor = CardBg),
        shape       = RoundedCornerShape(12.dp),
        elevation   = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(bg, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$count",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize   = 14.sp,
                    color      = textColor
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = TextMain,
                fontWeight = FontWeight.SemiBold
            )
            if (sub.isNotBlank()) {
                Text(
                    sub,
                    style    = MaterialTheme.typography.labelSmall,
                    color    = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ── Search + filter pills ─────────────────────────────────────────
@Composable
private fun SearchAndFilters(
    query: String,
    statusFilter: String,
    onQuery: (String) -> Unit,
    onFilter: (String) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        OutlinedTextField(
            value         = query,
            onValueChange = onQuery,
            placeholder   = { Text("Search name, ID or destination…") },
            leadingIcon   = { Icon(Icons.Default.Search, null, tint = TextSub) },
            trailingIcon  = {
                if (query.isNotBlank())
                    IconButton(onClick = { onQuery("") }) {
                        Icon(Icons.Default.Clear, null, tint = TextSub)
                    }
            },
            modifier    = Modifier.fillMaxWidth(),
            singleLine  = true,
            shape       = RoundedCornerShape(12.dp),
            colors      = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = Orange,
                unfocusedBorderColor = DividerColor,
                unfocusedContainerColor = CardBg,
                focusedContainerColor   = CardBg
            )
        )
        Spacer(Modifier.height(10.dp))

        val pills = listOf("all" to "All", "pending" to "Pending",
            "approved" to "Approved", "rejected" to "Rejected")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(pills) { (key, lbl) ->
                val sel = statusFilter == key
                Surface(
                    onClick = { onFilter(key) },
                    shape   = RoundedCornerShape(20.dp),
                    color   = if (sel) Orange else CardBg,
                    border  = BorderStroke(1.dp, if (sel) Orange else DividerColor)
                ) {
                    Text(
                        lbl,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style    = MaterialTheme.typography.bodySmall,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                        color    = if (sel) Color.White else TextSub
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))

    }
}

// ── Trip card ─────────────────────────────────────────────────────
@Composable
private fun TripCard(
    trip: AdminTripItem,
    onTap: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val (statusColor, statusBg) = statusColors(trip.status)

    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onTap() },
        colors    = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(2.dp),
        shape     = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(14.dp)) {

            // ── Row 1: Avatar + name / status ─────────────────────
            Row(
                Modifier.fillMaxWidth(),
                Arrangement.SpaceBetween,
                Alignment.Top
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Avatar circle
                    Box(
                        Modifier
                            .size(42.dp)
                            .background(OrangeLight, CircleShape),
                        Alignment.Center
                    ) {
                        Text(
                            trip.employeeName.take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            fontSize   = 17.sp,
                            color      = Orange
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(trip.employeeName, fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyLarge, color = TextMain)
                        Text(
                            "${trip.employeeId}${if (trip.designation.isNotBlank()) "  ·  ${trip.designation}" else ""}",
                            style = MaterialTheme.typography.bodySmall, color = TextSub,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Status badge
                Surface(color = statusBg, shape = RoundedCornerShape(6.dp)) {
                    Text(
                        trip.status.uppercase(),
                        Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        style      = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color      = statusColor
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Divider(color = DividerColor)
            Spacer(Modifier.height(12.dp))

            // ── Row 2: Trip info + amounts ────────────────────────
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                // Left: destination, period, count
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, null, tint = Orange,
                            modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(
                            trip.stationVisited,
                            fontWeight = FontWeight.SemiBold,
                            style      = MaterialTheme.typography.bodyMedium,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis,
                            color      = TextMain
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarToday, null, tint = TextSub,
                            modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(
                            "${trip.periodFrom}  →  ${trip.periodTo}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSub
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${trip.expenseCount} item${if (trip.expenseCount != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSub
                    )
                }

                // Right: amounts column
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        inr(trip.totalAmount),
                        fontWeight = FontWeight.Bold,
                        style      = MaterialTheme.typography.bodyLarge,
                        color      = TextMain
                    )
                    if (trip.advanceAmount > 0) {
                        Spacer(Modifier.height(2.dp))
                        Text("Adv: ${inr(trip.advanceAmount)}", style = MaterialTheme.typography.labelSmall, color = TextSub)
                        val pay = trip.totalAmount - trip.advanceAmount
                        Text(
                            "${if (pay >= 0) "Pay" else "Refund"}: ${inr(Math.abs(pay))}",
                            style      = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color      = if (pay >= 0) Amber else Green
                        )
                    }
                }
            }

            // ── Receipt count hint ────────────────────────────────
            val receiptCount = trip.expenses.count { !it.receiptUrl.isNullOrBlank() }
            if (receiptCount > 0) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Attachment, null, tint = Orange,
                        modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(
                        "$receiptCount receipt${if (receiptCount > 1) "s" else ""} attached",
                        style = MaterialTheme.typography.labelSmall,
                        color = Orange
                    )
                }
            }

            // ── Rejection note ────────────────────────────────────
            if (!trip.adminNote.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Surface(color = RedLight, shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, tint = Red, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(trip.adminNote, style = MaterialTheme.typography.bodySmall,
                            color = Red, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            // ── Action buttons (pending only) ─────────────────────
            if (trip.status == "pending") {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick  = onReject,
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(10.dp),
                        border   = BorderStroke(1.dp, Red),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = Red)
                    ) {
                        Icon(Icons.Default.Close, null, Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reject", fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick  = onApprove,
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(10.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = Green)
                    ) {
                        Icon(Icons.Default.Check, null, Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Approve", fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = onTap,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("View full details", color = Orange,
                        style = MaterialTheme.typography.bodySmall)
                    Icon(Icons.Default.ChevronRight, null, tint = Orange,
                        modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ── Detail bottom sheet ───────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripDetailSheet(
    trip: AdminTripItem,
    onDismiss: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val uriHandler = LocalUriHandler.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape            = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor   = CardBg
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {

            // ── Header ────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                Arrangement.SpaceBetween,
                Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .background(OrangeLight, CircleShape),
                        Alignment.Center
                    ) {
                        Text(
                            trip.employeeName.take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            fontSize   = 22.sp,
                            color      = Orange
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(trip.employeeName, fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${trip.employeeId}${if (trip.designation.isNotBlank()) "  ·  ${trip.designation}" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSub
                        )
                    }
                }
                val (sc, sb) = statusColors(trip.status)
                Surface(color = sb, shape = RoundedCornerShape(8.dp)) {
                    Text(
                        trip.status.uppercase(),
                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        fontWeight = FontWeight.Bold,
                        color      = sc
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // ── Summary box ───────────────────────────────────────
            Surface(
                color     = PageBg,
                shape     = RoundedCornerShape(12.dp),
                border    = BorderStroke(1.dp, DividerColor),
                modifier  = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InfoRow(Icons.Default.LocationOn,     "Destination",    trip.stationVisited)
                    InfoRow(Icons.Default.CalendarToday,  "Period",         "${trip.periodFrom}  →  ${trip.periodTo}")
                    InfoRow(Icons.Default.AccountBalanceWallet, "Advance",  inr(trip.advanceAmount))
                    InfoRow(Icons.Default.Receipt,        "Total Expenses", inr(trip.totalAmount))
                    val pay = trip.totalAmount - trip.advanceAmount
                    InfoRow(
                        if (pay >= 0) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                        if (pay >= 0) "Payable to Employee" else "Refundable",
                        inr(Math.abs(pay)),
                        valueColor = if (pay >= 0) Amber else Green
                    )
                    InfoRow(Icons.Default.Schedule, "Submitted On", trip.createdAt)
                }
            }

            Spacer(Modifier.height(18.dp))

            // ── Expense breakdown ─────────────────────────────────
            Text(
                "EXPENSE BREAKDOWN",
                style      = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color      = Orange,
                letterSpacing = 0.8.sp
            )
            Spacer(Modifier.height(8.dp))

            if (trip.expenses.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    Alignment.Center
                ) {
                    Text("No line items recorded", color = TextSub,
                        style = MaterialTheme.typography.bodySmall)
                }
            } else {
                trip.expenses.forEachIndexed { i, exp ->
                    LineItemRow(
                        index     = i,
                        expense   = exp,
                        onReceipt = { url -> uriHandler.openUri(url) }
                    )
                    if (i < trip.expenses.lastIndex) Spacer(Modifier.height(6.dp))
                }
            }

            // ── Rejection note ────────────────────────────────────
            if (!trip.adminNote.isNullOrBlank()) {
                Spacer(Modifier.height(14.dp))
                Surface(
                    color   = RedLight,
                    shape   = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("REJECTION NOTE", style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold, color = Red, letterSpacing = 0.8.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(trip.adminNote, style = MaterialTheme.typography.bodySmall, color = Red)
                    }
                }
            }

            // ── Action buttons ────────────────────────────────────
            if (trip.status == "pending") {
                Spacer(Modifier.height(22.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick  = onReject,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape    = RoundedCornerShape(12.dp),
                        border   = BorderStroke(1.5.dp, Red),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = Red)
                    ) {
                        Icon(Icons.Default.Close, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Reject", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick  = onApprove,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = Green)
                    ) {
                        Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Approve", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ── Line item row ─────────────────────────────────────────────────
@Composable
private fun LineItemRow(
    index: Int,
    expense: AdminExpenseLineItem,
    onReceipt: (String) -> Unit
) {
    Surface(
        color    = CardBg,
        shape    = RoundedCornerShape(10.dp),
        border   = BorderStroke(1.dp, DividerColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(28.dp).background(OrangeLight, CircleShape),
                Alignment.Center
            ) {
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Orange
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    expense.expenseType,
                    fontWeight = FontWeight.SemiBold,
                    style      = MaterialTheme.typography.bodyMedium,
                    color      = TextMain
                )
                val sub = buildString {
                    if (expense.travelFrom.isNotBlank())
                        append("${expense.travelFrom} → ${expense.travelTo}")
                    if (expense.travelMode.isNotBlank())
                        append("  (${expense.travelMode})")
                    if (expense.daysCount > 0)
                        append("  ${expense.daysCount} day${if (expense.daysCount > 1) "s" else ""}")
                    if (expense.details.isNotBlank()) {
                        if (isNotEmpty()) append("  ·  ")
                        append(expense.details)
                    }
                }.trim()
                if (sub.isNotBlank()) {
                    Text(sub, style = MaterialTheme.typography.bodySmall,
                        color = TextSub, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    inr(expense.amount),
                    fontWeight = FontWeight.Bold,
                    style      = MaterialTheme.typography.bodyMedium,
                    color      = TextMain
                )
                if (!expense.receiptUrl.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        onClick = { onReceipt(expense.receiptUrl) },
                        color   = OrangeLight,
                        shape   = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Launch, null, tint = Orange,
                                modifier = Modifier.size(11.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("Receipt", style = MaterialTheme.typography.labelSmall,
                                color = Orange, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

// ── Info row (detail sheet) ───────────────────────────────────────
@Composable
private fun InfoRow(
    icon: ImageVector, label: String, value: String,
    valueColor: Color = TextMain
) {
    Row(
        Modifier.fillMaxWidth(),
        Arrangement.SpaceBetween,
        Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = TextSub, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = TextSub)
        }
        Text(
            value,
            fontWeight = FontWeight.SemiBold,
            color      = valueColor,
            style      = MaterialTheme.typography.bodySmall,
            textAlign  = TextAlign.End,
            modifier   = Modifier.widthIn(max = 180.dp),
            maxLines   = 2
        )
    }
}

// ── Empty state ───────────────────────────────────────────────────
@Composable
private fun EmptyState(filter: String) {
    Column(
        Modifier.fillMaxWidth().padding(56.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Receipt, null,
            Modifier.size(60.dp), tint = Color(0xFFCCCCCC)
        )
        Spacer(Modifier.height(14.dp))
        Text(
            when (filter) {
                "pending"  -> "No pending approvals"
                "approved" -> "No approved trips yet"
                "rejected" -> "No rejected trips"
                else       -> "No trips submitted yet"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFAAAAAA)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Field engineer submissions will appear here",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFCCCCCC),
            textAlign = TextAlign.Center
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────────
private fun statusColors(status: String): Pair<Color, Color> = when (status) {
    "approved" -> Green to GreenLight
    "rejected" -> Red   to RedLight
    else       -> Amber to AmberLight
}