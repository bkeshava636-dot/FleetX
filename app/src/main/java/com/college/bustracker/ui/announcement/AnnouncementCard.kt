package com.college.bustracker.ui.announcement

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.college.bustracker.ui.theme.CollegePrimary
import com.college.bustracker.ui.theme.StatusActiveGreen
import com.college.bustracker.ui.theme.StatusDangerRed
import com.college.bustracker.ui.theme.StatusWarningOrange
import com.college.bustracker.util.LocaleHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Mobile-friendly announcement card displaying category, priority, localized text, and target info.
 */
@Composable
fun AnnouncementCard(
    announcement: Announcement,
    isUnread: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isKn = LocaleHelper.isKannada()
    val displayTitle = announcement.getDisplayTitle(isKn)
    val displayMessage = announcement.getDisplayMessage(isKn)

    val priorityColor = when (announcement.priority) {
        Announcement.PRIORITY_URGENT -> StatusDangerRed
        Announcement.PRIORITY_IMPORTANT -> StatusWarningOrange
        else -> CollegePrimary
    }

    val priorityBg = when (announcement.priority) {
        Announcement.PRIORITY_URGENT -> Color(0xFFFEE2E2)
        Announcement.PRIORITY_IMPORTANT -> Color(0xFFFEF3C7)
        else -> Color(0xFFEFF6FF)
    }

    val categoryLabel = getCategoryLabel(announcement.category, isKn)
    val priorityLabel = getPriorityLabel(announcement.priority, isKn)
    val formattedTime = formatTimestamp(announcement.createdAt)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isUnread) Color(0xFFF8FAFC) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isUnread) 2.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = if (isUnread) 1.5.dp else 1.dp,
                    color = if (isUnread) CollegePrimary.copy(alpha = 0.4f) else Color(0xFFE2E8F0),
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(14.dp)
        ) {
            // Category, Priority, and Unread Dot Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Category chip
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFF1F5F9)
                    ) {
                        Text(
                            text = categoryLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF475569),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }

                    // Priority badge (if Important or Urgent)
                    if (announcement.priority != Announcement.PRIORITY_NORMAL) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = priorityBg
                        ) {
                            Text(
                                text = priorityLabel,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = priorityColor,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isUnread) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = CollegePrimary
                        ) {
                            Text(
                                text = stringResource(R.string.unread_badge),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = formattedTime,
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Announcement Title
            Text(
                text = displayTitle,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Message Body preview
            Text(
                text = displayMessage,
                fontSize = 13.sp,
                color = Color(0xFF475569),
                lineHeight = 18.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            // Target or timing change metadata footer if present
            if (announcement.targetAudience != Announcement.AUDIENCE_ALL || !announcement.timingBusId.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val targetText = when (announcement.targetAudience) {
                        Announcement.AUDIENCE_STUDENTS_ONLY -> if (isKn) "ವಿದ್ಯಾರ್ಥಿಗಳಿಗೆ" else "Students Only"
                        Announcement.AUDIENCE_DRIVERS_ONLY -> if (isKn) "ಚಾಲಕರಿಗೆ" else "Drivers Only"
                        Announcement.AUDIENCE_SELECTED_BUS -> if (isKn) "ಬಸ್: ${announcement.targetId}" else "Bus: ${announcement.targetId}"
                        Announcement.AUDIENCE_SELECTED_ROUTE -> if (isKn) "ಮಾರ್ಗ: ${announcement.targetId}" else "Route: ${announcement.targetId}"
                        Announcement.AUDIENCE_SELECTED_STOP -> if (isKn) "ನಿಲ್ದಾಣ: ${announcement.targetId}" else "Stop: ${announcement.targetId}"
                        else -> ""
                    }
                    if (targetText.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFE2E8F0).copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = targetText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF334155),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (announcement.expiresAt != null && announcement.expiresAt > 0) {
                        Text(
                            text = "${if (isKn) "ಮುಕ್ತಾಯ" else "Expires"}: ${formatDateShort(announcement.expiresAt)}",
                            fontSize = 10.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }
    }
}

fun getCategoryLabel(category: String, isKn: Boolean): String {
    return when (category) {
        Announcement.CATEGORY_TIMING_CHANGE -> if (isKn) "ಸಮಯ ಬದಲಾವಣೆ" else "Timing Change"
        Announcement.CATEGORY_BUS_DELAY -> if (isKn) "ವಿಳಂಬ" else "Delay"
        Announcement.CATEGORY_BUS_CANCELLATION -> if (isKn) "ರದ್ದತಿ" else "Cancellation"
        Announcement.CATEGORY_ROUTE_CHANGE -> if (isKn) "ಮಾರ್ಗ ಬದಲಾವಣೆ" else "Route Change"
        Announcement.CATEGORY_BUS_SWAP -> if (isKn) "ಬಸ್ ಬದಲಾವಣೆ" else "Bus Swap"
        Announcement.CATEGORY_HOLIDAY -> if (isKn) "ರಜೆ" else "Holiday"
        Announcement.CATEGORY_EMERGENCY -> if (isKn) "ತುರ್ತು" else "Emergency"
        else -> if (isKn) "ಸಾಮಾನ್ಯ ಪ್ರಕಟಣೆ" else "General"
    }
}

fun getPriorityLabel(priority: String, isKn: Boolean): String {
    return when (priority) {
        Announcement.PRIORITY_URGENT -> if (isKn) "ತುರ್ತು" else "Urgent"
        Announcement.PRIORITY_IMPORTANT -> if (isKn) "ಮುಖ್ಯ" else "Important"
        else -> if (isKn) "ಸಾಮಾನ್ಯ" else "Normal"
    }
}

private fun formatTimestamp(timestamp: Long): String {
    if (timestamp <= 0) return ""
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    if (diff < 60_000) return "Just now"
    if (diff < 3600_000) return "${diff / 60_000}m ago"
    if (diff < 86400_000) return "${diff / 3600_000}h ago"
    val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun formatDateShort(timestamp: Long): String {
    if (timestamp <= 0) return ""
    val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
