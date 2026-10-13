package com.college.bustracker.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.college.bustracker.data.model.Bus
import com.college.bustracker.data.model.BusSwap
import com.college.bustracker.data.model.StopRequest
import com.college.bustracker.data.model.TemporaryAssignment
import com.college.bustracker.data.model.UserProfile
import com.college.bustracker.ui.theme.CollegePrimary
import com.college.bustracker.ui.theme.CollegeSurface
import com.college.bustracker.ui.theme.StatusActiveGreen
import com.college.bustracker.ui.theme.StatusWarningOrange

@Composable
fun AdminOverviewScreen(
    buses: List<Bus>,
    allDrivers: List<UserProfile>,
    activeDrivers: List<UserProfile>,
    temporaryAssignments: List<TemporaryAssignment>,
    swaps: List<BusSwap>,
    pendingStopRequests: List<StopRequest>,
    onNavigateToOperations: (subTab: String) -> Unit,
    onNavigateToRoutes: (subTab: String) -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigateToAnnouncements: () -> Unit = {}
) {
    val now = System.currentTimeMillis()
    val activeReplacements = temporaryAssignments.filter { it.isActive(now) }
    val activeSwaps = swaps.filter { it.status == "ACTIVE" }
    val activeBusesCount = buses.count { it.active }

    val hasAttentionItems = activeReplacements.isNotEmpty() ||
            pendingStopRequests.isNotEmpty() ||
            activeSwaps.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CollegeSurface)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // 1. Compact Fleet Statistics
        AdminStatSummary(
            busesCount = buses.size,
            driversCount = allDrivers.size,
            activeBusesCount = activeBusesCount,
            replacementsCount = activeReplacements.size
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 2. ATTENTION SECTION
        AdminSectionTitle(title = "ATTENTION")

        if (hasAttentionItems) {
            // Active Temporary Replacements
            activeReplacements.forEach { replacement ->
                val permName = replacement.permanentDriverName.ifBlank { "Permanent Driver" }
                val tempName = replacement.replacementDriverName.ifBlank { "Temporary Driver" }
                AdminAttentionCard(
                    icon = Icons.Default.Cached,
                    iconTint = Color(0xFF2563EB),
                    containerColor = Color(0xFFEFF6FF),
                    borderColor = Color(0xFFBFDBFE),
                    title = "Active Replacement • ${replacement.busId}",
                    subtitle = "$permName → $tempName",
                    detail = "Temporary route operator active now",
                    onViewClick = { onNavigateToOperations("replacements") }
                )
            }

            // Pending Stop Requests
            if (pendingStopRequests.isNotEmpty()) {
                val count = pendingStopRequests.size
                AdminAttentionCard(
                    icon = Icons.Default.Place,
                    iconTint = StatusWarningOrange,
                    containerColor = Color(0xFFFFFBEB),
                    borderColor = Color(0xFFFDE68A),
                    title = "Stop Requests ($count)",
                    subtitle = "$count proposed stop${if (count > 1) "s" else ""} pending review",
                    detail = "Submitted by drivers awaiting approval",
                    onViewClick = { onNavigateToRoutes("stop_requests") }
                )
            }

            // Active Bus Swaps
            activeSwaps.forEach { swap ->
                AdminAttentionCard(
                    icon = Icons.Default.SwapHoriz,
                    iconTint = Color(0xFFD97706),
                    containerColor = Color(0xFFFEF3C7),
                    borderColor = Color(0xFFFCD34D),
                    title = "Active Bus Swap • ${swap.originalBusId}",
                    subtitle = "${swap.originalBusId} → ${swap.replacementBusId}",
                    detail = swap.message.ifBlank { "Temporary bus swap in progress" },
                    onViewClick = { onNavigateToOperations("swaps") }
                )
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE2E8F0))
                )
            ) {
                AdminEmptyPlaceholder(
                    icon = Icons.Default.CheckCircle,
                    title = "Everything is up to date",
                    subtitle = "No urgent fleet actions or pending reviews required."
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 3. QUICK HUBS OVERVIEW
        AdminSectionTitle(title = "MANAGEMENT HUBS")

        AdminMenuRow(
            icon = Icons.Default.DirectionsBus,
            title = "Operations",
            subtitle = "${buses.size} Buses • ${allDrivers.size} Drivers • ${activeReplacements.size} Replacements",
            badgeCount = activeReplacements.size,
            badgeColor = Color(0xFF2563EB),
            onClick = { onNavigateToOperations("buses") }
        )

        AdminMenuRow(
            icon = Icons.Default.Route,
            title = "Routes & Stops",
            subtitle = "Route pathways, stops, and ${pendingStopRequests.size} stop requests",
            badgeCount = pendingStopRequests.size,
            badgeColor = StatusWarningOrange,
            onClick = { onNavigateToRoutes("routes") }
        )

        AdminMenuRow(
            icon = Icons.Default.DateRange,
            title = "Calendar",
            subtitle = "Working days, holidays, and schedule overrides",
            onClick = onNavigateToCalendar
        )

        AdminMenuRow(
            icon = Icons.Default.Campaign,
            title = "Announcements",
            subtitle = "Campus transit alerts, bus timing updates, and notices",
            onClick = onNavigateToAnnouncements
        )

        Spacer(modifier = Modifier.height(16.dp))

        // FleetX Updates & About Section
        com.college.bustracker.update.ui.UpdateCard()

        Spacer(modifier = Modifier.height(16.dp))
    }
}
