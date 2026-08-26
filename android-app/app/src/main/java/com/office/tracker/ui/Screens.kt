package com.office.tracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
    onStopTracking: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

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
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (todayVisit?.isCurrentlyAtOffice == true)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (todayVisit?.isCurrentlyAtOffice == true) "At Office" else "Not at Office",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                todayVisit?.arrivalTime?.let { time ->
                    Text(
                        text = "Arrived: $time",
                        fontSize = 20.sp
                    )
                }

                todayVisit?.departureTime?.let { time ->
                    Text(
                        text = "Left: $time",
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
    val dao = remember { OfficeApp.instance.database.officeVisitDao() }
    val visits by dao.getAllVisits().collectAsState(initial = emptyList())

    if (visits.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("No visits logged yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(
            modifier = modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(visits) { visit ->
                VisitCard(visit)
            }
        }
    }
}

@Composable
fun VisitCard(visit: OfficeVisit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = visit.date,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Arrived: ${visit.arrivalTime ?: "—"}",
                    fontSize = 14.sp
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                if (visit.isCurrentlyAtOffice) {
                    Text(
                        text = "Still here",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = "Left: ${visit.departureTime ?: "—"}",
                        fontSize = 14.sp
                    )
                }
            }
        }
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

    // Load current values
    LaunchedEffect(Unit) {
        officeLat = com.office.tracker.util.Prefs.getOfficeLat(context).toString()
        officeLng = com.office.tracker.util.Prefs.getOfficeLng(context).toString()
        arrivalStart = com.office.tracker.util.Prefs.getArrivalWindowStart(context).toString()
        arrivalEnd = com.office.tracker.util.Prefs.getArrivalWindowEnd(context).toString()
        departureStart = com.office.tracker.util.Prefs.getDepartureWindowStart(context).toString()
        departureEnd = com.office.tracker.util.Prefs.getDepartureWindowEnd(context).toString()
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
