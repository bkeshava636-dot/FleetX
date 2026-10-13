package com.college.bustracker.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.college.bustracker.R
import com.college.bustracker.data.model.Announcement
import com.college.bustracker.ui.announcement.AnnouncementCard
import com.college.bustracker.ui.announcement.getCategoryLabel
import com.college.bustracker.ui.announcement.getPriorityLabel
import com.college.bustracker.ui.common.EmptyStateCard
import com.college.bustracker.ui.theme.CollegePrimary
import com.college.bustracker.ui.theme.StatusDangerRed
import com.college.bustracker.ui.theme.StatusWarningOrange
import com.college.bustracker.util.LocaleHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Administrative console screen for managing campus transit announcements.
 */
@Composable
fun AdminAnnouncementScreen(
    published: List<Announcement>,
    drafts: List<Announcement>,
    archived: List<Announcement>,
    onAddAnnouncementClick: () -> Unit,
    onTimingChangeClick: () -> Unit,
    onEditAnnouncementClick: (Announcement) -> Unit,
    onPublishAnnouncementClick: (Announcement) -> Unit,
    onArchiveAnnouncementClick: (Announcement) -> Unit,
    onDeleteAnnouncementClick: (Announcement) -> Unit,
    modifier: Modifier = Modifier
) {
    val isKn = LocaleHelper.isKannada()
    var selectedTab by remember { mutableStateOf(0) } // 0: Published, 1: Drafts, 2: Archived

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Quick Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onAddAnnouncementClick,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.create_announcement), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }

            OutlinedButton(
                onClick = onTimingChangeClick,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD97706)),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFD97706))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.bus_timing_change), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Subtabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.White,
            contentColor = CollegePrimary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = CollegePrimary,
                    height = 3.dp
                )
            }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Text(
                        text = "${stringResource(R.string.published_tab)} (${published.size})",
                        fontSize = 12.sp,
                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Text(
                        text = "${stringResource(R.string.drafts_tab)} (${drafts.size})",
                        fontSize = 12.sp,
                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = {
                    Text(
                        text = "${stringResource(R.string.archived_tab)} (${archived.size})",
                        fontSize = 12.sp,
                        fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        val currentList = when (selectedTab) {
            0 -> published
            1 -> drafts
            else -> archived
        }

        if (currentList.isEmpty()) {
            EmptyStateCard(
                title = stringResource(R.string.no_announcements),
                description = stringResource(R.string.no_announcements_desc),
                icon = Icons.Default.Campaign
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(currentList, key = { it.id }) { item ->
                    AdminAnnouncementItemCard(
                        announcement = item,
                        isKn = isKn,
                        onEdit = { onEditAnnouncementClick(item) },
                        onPublish = { onPublishAnnouncementClick(item) },
                        onArchive = { onArchiveAnnouncementClick(item) },
                        onDelete = { onDeleteAnnouncementClick(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminAnnouncementItemCard(
    announcement: Announcement,
    isKn: Boolean,
    onEdit: () -> Unit,
    onPublish: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    val displayTitle = announcement.getDisplayTitle(isKn)
    val displayMessage = announcement.getDisplayMessage(isKn)

    val priorityColor = when (announcement.priority) {
        Announcement.PRIORITY_URGENT -> StatusDangerRed
        Announcement.PRIORITY_IMPORTANT -> StatusWarningOrange
        else -> CollegePrimary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFF1F5F9)
                    ) {
                        Text(
                            text = getCategoryLabel(announcement.category, isKn),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF475569),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = priorityColor.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = getPriorityLabel(announcement.priority, isKn),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = priorityColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(announcement.createdAt)),
                    fontSize = 10.sp,
                    color = Color(0xFF94A3B8)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = displayTitle,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = displayMessage,
                fontSize = 12.sp,
                color = Color(0xFF64748B),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Audience badge
                val audienceText = when (announcement.targetAudience) {
                    Announcement.AUDIENCE_SELECTED_BUS -> "Bus: ${announcement.targetId}"
                    Announcement.AUDIENCE_SELECTED_ROUTE -> "Route: ${announcement.targetId}"
                    Announcement.AUDIENCE_STUDENTS_ONLY -> "Students Only"
                    Announcement.AUDIENCE_DRIVERS_ONLY -> "Drivers Only"
                    else -> "All Students & Drivers"
                }
                Text(
                    text = audienceText,
                    fontSize = 11.sp,
                    color = Color(0xFF475569),
                    fontWeight = FontWeight.Medium
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF475569), modifier = Modifier.size(16.dp))
                    }

                    if (announcement.status == Announcement.STATUS_DRAFT) {
                        IconButton(onClick = onPublish, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Publish, contentDescription = "Publish", tint = CollegePrimary, modifier = Modifier.size(16.dp))
                        }
                    }

                    if (announcement.status == Announcement.STATUS_PUBLISHED) {
                        IconButton(onClick = onArchive, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Archive, contentDescription = "Archive", tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                        }
                    }

                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = StatusDangerRed, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
