package com.office.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.office.tracker.OfficeApp
import com.office.tracker.db.OfficeVisit
import com.office.tracker.service.WindowScheduler
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficeTrackerApp(
    onStartTracking: () -> Unit,
    onStopTracking: () -> Unit,
    initialTab: Int? = null
) {
    var selectedTab by remember { mutableIntStateOf(initialTab ?: 0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Office Tracker") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, "Dashboard") },
                    label = { Text("Today") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.List, "History") },
                    label = { Text("History") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, "Settings") },
                    label = { Text("Settings") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 }
                )
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> DashboardScreen(
                modifier = Modifier.padding(padding),
                onStartTracking = onStartTracking,
                onStopTracking = onStopTracking
            )
            1 -> HistoryScreen(modifier = Modifier.padding(padding))
            2 -> SettingsScreen(modifier = Modifier.padding(padding))
        }
    }
}

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    onStartTracking: () -> Unit,
    onStopTracking: () -> Unit
) {
    val context = LocalContext.current
    val dao = remember { OfficeApp.instance.database.officeVisitDao() }
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    val todayVisit by dao.getVisitForDateFlow(today).collectAsState(initial = null)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Status card
        val atOffice = todayVisit?.isCurrentlyAtOffice == true
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (atOffice)
                    MaterialTheme.colorScheme.tertiaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (atOffice) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "At office",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                Text(
                    text = if (atOffice) "At Office" else "Not at Office",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (atOffice) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                todayVisit?.arrivalTime?.let {
                    Text(
                        text = "Arrived: ${com.office.tracker.util.format12h(it) ?: it}",
                        fontSize = 20.sp
                    )
                }

                todayVisit?.departureTime?.let {
                    Text(
                        text = "Left: ${com.office.tracker.util.format12h(it) ?: it}",
                        fontSize = 20.sp
                    )
                }

                if (todayVisit == null) {
                    Text(
                        text = "No visit logged today",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Quick actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onStartTracking,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start")
            }

            OutlinedButton(
                onClick = onStopTracking,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Close, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Stop")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Info
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("How it works", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "The app checks your location during two daily windows: " +
                    "9:00 AM–12:00 PM (arrival) and 6:00 PM–9:00 PM (departure). " +
                    "When you're within 100m of your office, it logs the time.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { OfficeApp.instance.database.officeVisitDao() }
    val visits by dao.getAllVisits().collectAsState(initial = emptyList())
    var importStatus by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        // Import seed + manual add buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val count = com.office.tracker.db.Seeder.importFromSeedFile(context)
                        importStatus = if (count > 0) "Imported $count visits" else
                            "No seed file at ${com.office.tracker.db.Seeder.seedFile(context).absolutePath}"
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import seed")
            }
            OutlinedButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Edit, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add/Edit")
            }
        }

        importStatus?.let {
            Text(
                it,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (visits.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("No visits logged yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Date",
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Arrived",
                    modifier = Modifier.width(96.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    textAlign = TextAlign.End,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Left",
                    modifier = Modifier.width(96.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    textAlign = TextAlign.End,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LazyColumn {
                itemsIndexed(visits) { index, visit ->
                    HistoryTableRow(visit, highlighted = index % 2 == 1)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }

    if (showAddDialog) {
        AddEntryDialog(
            context = context,
            onDismiss = { showAddDialog = false },
            onSaved = { showAddDialog = false; importStatus = "Entry saved" }
        )
    }
}

@Composable
fun AddEntryDialog(
    context: android.content.Context,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val dao = OfficeApp.instance.database.officeVisitDao()
    val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        .format(java.util.Date())
    var date by remember { mutableStateOf(today) }
    var arrival by remember { mutableStateOf("") }
    var departure by remember { mutableStateOf("") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add / Edit entry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date (yyyy-MM-dd)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = arrival,
                    onValueChange = { arrival = it },
                    label = { Text("Arrival (HH:mm, 24h)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = departure,
                    onValueChange = { departure = it },
                    label = { Text("Departure (HH:mm, 24h)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val scope = kotlinx.coroutines.CoroutineScope(
                    kotlinx.coroutines.Dispatchers.IO +
                        kotlinx.coroutines.SupervisorJob()
                )
                scope.launch {
                    val arr = arrival.trim().ifEmpty { null }
                    val dep = departure.trim().ifEmpty { null }
                    val existing = dao.getVisitForDate(date)
                    val arrTs = arr?.let {
                        parseTimeMillis(date, it) ?: System.currentTimeMillis()
                    } ?: existing?.arrivalTimestamp ?: 0L
                    val depTs = dep?.let {
                        parseTimeMillis(date, it) ?: System.currentTimeMillis()
                    } ?: existing?.departureTimestamp ?: 0L
                    dao.upsert(
                        com.office.tracker.db.OfficeVisit(
                            date = date,
                            arrivalTime = arr,
                            departureTime = dep,
                            isCurrentlyAtOffice = false,
                            arrivalTimestamp = arrTs,
                            departureTimestamp = depTs
                        )
                    )
                }
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun parseTimeMillis(date: String, hhmm: String): Long? {
    return try {
        val p = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        p.setLenient(false)
        p.parse("$date $hhmm")?.time
    } catch (e: Exception) {
        null
    }
}

@Composable
fun HistoryTableRow(visit: OfficeVisit, highlighted: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { m ->
                if (highlighted) m.background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
                else m
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Date + on-site indicator
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = visit.date,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            if (visit.isCurrentlyAtOffice) {
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        "Now",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Text(
            text = com.office.tracker.util.format12h(visit.arrivalTime) ?: "—",
            modifier = Modifier.width(96.dp),
            fontSize = 14.sp,
            textAlign = TextAlign.End,
            color = if (visit.arrivalTime == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = if (visit.isCurrentlyAtOffice) "—" else (com.office.tracker.util.format12h(visit.departureTime) ?: "—"),
            modifier = Modifier.width(96.dp),
            fontSize = 14.sp,
            textAlign = TextAlign.End,
            color = if (visit.departureTime == null && !visit.isCurrentlyAtOffice)
                MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var officeLat by remember { mutableStateOf("18.531555") }
    var officeLng by remember { mutableStateOf("73.842193") }
    var arrivalStart by remember { mutableStateOf("9") }
    var arrivalEnd by remember { mutableStateOf("12") }
    var departureStart by remember { mutableStateOf("18") }
    var departureEnd by remember { mutableStateOf("21") }
    var saved by remember { mutableStateOf(false) }

    // Work days (Calendar.DAY_OF_SUNDAY..SATURDAY)
    val workDays = remember {
        mutableStateOf(
            mapOf(
                1 to true, 2 to true, 3 to true, 4 to true, 5 to true, 6 to true, 7 to false
            )
        )
    }

    // Load current values
    LaunchedEffect(Unit) {
        officeLat = com.office.tracker.util.Prefs.getOfficeLat(context).toString()
        officeLng = com.office.tracker.util.Prefs.getOfficeLng(context).toString()
        arrivalStart = com.office.tracker.util.Prefs.getArrivalWindowStart(context).toString()
        arrivalEnd = com.office.tracker.util.Prefs.getArrivalWindowEnd(context).toString()
        departureStart = com.office.tracker.util.Prefs.getDepartureWindowStart(context).toString()
        departureEnd = com.office.tracker.util.Prefs.getDepartureWindowEnd(context).toString()
        // Load work days
        val loaded = workDays.value.toMutableMap()
        for (dow in 1..7) {
            loaded[dow] = com.office.tracker.util.Prefs.isWorkDay(context, dow)
        }
        workDays.value = loaded
    }

    LazyColumn(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Office Location", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        item {
            OutlinedTextField(
                value = officeLat,
                onValueChange = { officeLat = it },
                label = { Text("Latitude") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = officeLng,
                onValueChange = { officeLng = it },
                label = { Text("Longitude") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Arrival Window", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = arrivalStart,
                    onValueChange = { arrivalStart = it },
                    label = { Text("Start hour (0-23)") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = arrivalEnd,
                    onValueChange = { arrivalEnd = it },
                    label = { Text("End hour (0-23)") },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Departure Window", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = departureStart,
                    onValueChange = { departureStart = it },
                    label = { Text("Start hour (0-23)") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = departureEnd,
                    onValueChange = { departureEnd = it },
                    label = { Text("End hour (0-23)") },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Work Days (tracking runs only these days)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(8.dp))
            val dayLabels = listOf(
                7 to "Sun", 1 to "Mon", 2 to "Tue", 3 to "Wed",
                4 to "Thu", 5 to "Fri", 6 to "Sat"
            )
            val chips = workDays.value
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                dayLabels.chunked(4).forEach { rowDays ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowDays.forEach { (dow, label) ->
                            FilterChip(
                                selected = chips[dow] ?: false,
                                onClick = {
                                    workDays.value = workDays.value.toMutableMap().apply {
                                        this[dow] = !(this[dow] ?: false)
                                    }
                                },
                                label = { Text(label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    scope.launch {
                        com.office.tracker.util.Prefs.setOfficeLocation(
                            context,
                            officeLat.toDoubleOrNull() ?: 18.531555,
                            officeLng.toDoubleOrNull() ?: 73.842193
                        )
                        com.office.tracker.util.Prefs.setArrivalWindow(
                            context,
                            arrivalStart.toIntOrNull() ?: 9,
                            arrivalEnd.toIntOrNull() ?: 12
                        )
                        com.office.tracker.util.Prefs.setDepartureWindow(
                            context,
                            departureStart.toIntOrNull() ?: 18,
                            departureEnd.toIntOrNull() ?: 21
                        )
                        WindowScheduler.scheduleToday(context)
                        WindowScheduler.scheduleTomorrow(context)
                        saved = true
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save & Reschedule")
            }
        }

        if (saved) {
            item {
                Text(
                    "Settings saved!",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
