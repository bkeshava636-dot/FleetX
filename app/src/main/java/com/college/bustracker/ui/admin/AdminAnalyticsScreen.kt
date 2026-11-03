package com.college.bustracker.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.college.bustracker.ui.theme.CollegePrimary
import com.college.bustracker.ui.theme.StatusActiveGreen
import com.college.bustracker.util.KolkataTimeFormatter
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class DailyAnalyticsData(
    val date: String = "",
    val dauCount: Long = 0L,
    val newInstallations: Long = 0L,
    val returningUsers: Long = 0L,
    val totalEvents: Long = 0L,
    val events: Map<String, Long> = emptyMap(),
    val languages: Map<String, Long> = emptyMap(),
    val versions: Map<String, Long> = emptyMap()
)

@Composable
fun AdminAnalyticsScreen() {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var totalInstallations by remember { mutableStateOf(0L) }
    var dailyList by remember { mutableStateOf<List<DailyAnalyticsData>>(emptyList()) }

    suspend fun loadAnalytics() {
        isLoading = true
        try {
            val firestore = FirebaseFirestore.getInstance()
            // 1. Total installations observed
            val installSnap = firestore.collection("student_installations").get().await()
            totalInstallations = installSnap.size().toLong()

            // 2. Last 30 daily docs
            val dailySnap = firestore.collection("student_analytics_daily")
                .orderBy("date", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(30)
                .get()
                .await()

            val list = dailySnap.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                val eventsMap = (data["events"] as? Map<*, *>)?.mapNotNull { (k, v) ->
                    val key = k?.toString() ?: return@mapNotNull null
                    val count = (v as? Number)?.toLong() ?: 0L
                    key to count
                }?.toMap() ?: emptyMap()

                val langMap = (data["languages"] as? Map<*, *>)?.mapNotNull { (k, v) ->
                    val key = k?.toString() ?: return@mapNotNull null
                    val count = (v as? Number)?.toLong() ?: 0L
                    key to count
                }?.toMap() ?: emptyMap()

                val verMap = (data["versions"] as? Map<*, *>)?.mapNotNull { (k, v) ->
                    val key = k?.toString() ?: return@mapNotNull null
                    val count = (v as? Number)?.toLong() ?: 0L
                    key to count
                }?.toMap() ?: emptyMap()

                DailyAnalyticsData(
                    date = doc.id,
                    dauCount = (data["dauCount"] as? Number)?.toLong() ?: 0L,
                    newInstallations = (data["newInstallations"] as? Number)?.toLong() ?: 0L,
                    returningUsers = (data["returningUsers"] as? Number)?.toLong() ?: 0L,
                    totalEvents = (data["totalEvents"] as? Number)?.toLong() ?: 0L,
                    events = eventsMap,
                    languages = langMap,
                    versions = verMap
                )
            }
            dailyList = list
        } catch (ignored: Exception) {
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadAnalytics()
    }

    val todayStr = remember { KolkataTimeFormatter.getTodayIso() }
    val todayData = remember(dailyList, todayStr) { dailyList.find { it.date == todayStr } }

    val todayDau = todayData?.dauCount ?: 0L
    val past7DaysDau = remember(dailyList) { dailyList.take(7).sumOf { it.dauCount } }
    val past30DaysDau = remember(dailyList) { dailyList.sumOf { it.dauCount } }

    val wauEstimated = remember(past7DaysDau, totalInstallations) {
        past7DaysDau.coerceAtMost(totalInstallations.coerceAtLeast(past7DaysDau))
    }
    val mauEstimated = remember(past30DaysDau, totalInstallations) {
        past30DaysDau.coerceAtMost(totalInstallations.coerceAtLeast(past30DaysDau))
    }

    // Aggregated feature events
    val aggregatedEvents = remember(dailyList) {
        val map = mutableMapOf<String, Long>()
        dailyList.forEach { daily ->
            daily.events.forEach { (ev, count) ->
                map[ev] = (map[ev] ?: 0L) + count
            }
        }
        map.entries.sortedByDescending { it.value }
    }

    // Aggregated languages
    val (kannadaCount, englishCount) = remember(dailyList) {
        var kn = 0L
        var en = 0L
        dailyList.forEach { daily ->
            kn += daily.languages["kn"] ?: 0L
            en += daily.languages["en"] ?: 0L
        }
        kn to en
    }

    // Aggregated versions
    val aggregatedVersions = remember(dailyList) {
        val map = mutableMapOf<String, Long>()
        dailyList.forEach { daily ->
            daily.versions.forEach { (v, count) ->
                map[v] = (map[v] ?: 0L) + count
            }
        }
        map.entries.sortedByDescending { it.value }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF8FAFC))) {
        // Top Header
        Surface(color = Color.White, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Student App Usage Analytics", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    Text("Anonymous telemetry • No student login required", fontSize = 12.sp, color = Color(0xFF64748B))
                }
                IconButton(onClick = { scope.launch { loadAnalytics() } }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = CollegePrimary)
                }
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CollegePrimary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Privacy Banner
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFBBF7D0), RoundedCornerShape(12.dp))
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = StatusActiveGreen, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "100% Anonymous: No student phone numbers, names, passwords, or personal location data are ever collected. Analytics rely exclusively on a locally generated random installation UUID.",
                                fontSize = 11.sp,
                                color = Color(0xFF166534),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // Core Metrics Row: Installations, DAU, WAU, MAU
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricStatCard(
                            modifier = Modifier.weight(1f),
                            title = "Total Installs",
                            value = "${totalInstallations.coerceAtLeast(1)}",
                            subtext = "Observed app copies",
                            icon = Icons.Default.Download,
                            color = CollegePrimary
                        )
                        MetricStatCard(
                            modifier = Modifier.weight(1f),
                            title = "Active Today (DAU)",
                            value = "$todayDau",
                            subtext = "Unique opens today",
                            icon = Icons.Default.Group,
                            color = StatusActiveGreen
                        )
                    }
                }

                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricStatCard(
                            modifier = Modifier.weight(1f),
                            title = "Active (7d WAU)",
                            value = "$wauEstimated",
                            subtext = "Weekly active users",
                            icon = Icons.Default.Timeline,
                            color = Color(0xFF8B5CF6)
                        )
                        MetricStatCard(
                            modifier = Modifier.weight(1f),
                            title = "Active (30d MAU)",
                            value = "$mauEstimated",
                            subtext = "Monthly active users",
                            icon = Icons.Default.Analytics,
                            color = Color(0xFFF59E0B)
                        )
                    }
                }

                // Language Preferences: English vs Kannada
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Language, contentDescription = null, tint = CollegePrimary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Language Preference Distribution", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            val totalLang = (kannadaCount + englishCount).coerceAtLeast(1L)
                            val knPct = ((kannadaCount * 100) / totalLang).toInt()
                            val enPct = 100 - knPct

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("English: $englishCount ($enPct%)", fontSize = 12.sp, color = Color(0xFF334155))
                                Text("Kannada: $kannadaCount ($knPct%)", fontSize = 12.sp, color = Color(0xFF334155), fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { enPct / 100f },
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                                color = CollegePrimary,
                                trackColor = Color(0xFFF59E0B)
                            )
                        }
                    }
                }

                // Feature Usage Frequency
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Most Frequently Accessed Features", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                            Spacer(modifier = Modifier.height(10.dp))

                            if (aggregatedEvents.isEmpty()) {
                                Text("No feature telemetry collected yet.", fontSize = 12.sp, color = Color(0xFF94A3B8))
                            } else {
                                val maxCount = (aggregatedEvents.firstOrNull()?.value ?: 1L).toFloat()
                                aggregatedEvents.forEach { (event, count) ->
                                    val readableName = when (event) {
                                        "app_opened" -> "App Opened"
                                        "live_tracking_opened" -> "Live Tracking Map"
                                        "bus_details_viewed" -> "Bus Details Sheet"
                                        "stop_selected" -> "Bus Stop Selected"
                                        "announcement_viewed" -> "Campus Announcement Viewed"
                                        "bus_swap_alert_viewed" -> "Bus Swap Alert Viewed"
                                        "settings_opened" -> "Settings Opened"
                                        "update_available" -> "Update Prompt Viewed"
                                        "update_installed" -> "Update Installed"
                                        else -> event.replace("_", " ").replaceFirstChar { it.uppercase() }
                                    }
                                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(readableName, fontSize = 12.sp, color = Color(0xFF334155))
                                            Text("$count events", fontSize = 11.sp, color = Color(0xFF64748B), fontWeight = FontWeight.SemiBold)
                                        }
                                        Spacer(modifier = Modifier.height(3.dp))
                                        LinearProgressIndicator(
                                            progress = { (count / maxCount).coerceIn(0f, 1f) },
                                            modifier = Modifier.fillMaxWidth().height(6.dp),
                                            color = CollegePrimary,
                                            trackColor = Color(0xFFF1F5F9)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // App Version Distribution
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Installed App Version Distribution", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                            Spacer(modifier = Modifier.height(10.dp))

                            if (aggregatedVersions.isEmpty()) {
                                Text("1.0.35 (100%)", fontSize = 12.sp, color = Color(0xFF334155))
                            } else {
                                aggregatedVersions.forEach { (ver, count) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("FleetX v$ver", fontSize = 12.sp, color = Color(0xFF334155))
                                        Text("$count active pings", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CollegePrimary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricStatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtext: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Card(
        modifier = modifier.border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(color.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF475569))
            Text(subtext, fontSize = 10.sp, color = Color(0xFF94A3B8))
        }
    }
}
