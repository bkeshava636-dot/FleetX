package com.college.bustracker.ui.announcement

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.college.bustracker.R
import com.college.bustracker.data.model.Announcement
import com.college.bustracker.ui.theme.CollegePrimary
import com.college.bustracker.ui.theme.StatusDangerRed
import com.college.bustracker.ui.theme.StatusWarningOrange
import com.college.bustracker.util.LocaleHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Detailed modal dialog presenting the full announcement content.
 */
@Composable
fun AnnouncementDetailDialog(
    announcement: Announcement,
    onDismiss: () -> Unit,
    onMarkAsRead: () -> Unit
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

    AlertDialog(
        onDismissRequest = {
            onMarkAsRead()
            onDismiss()
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = priorityBg
                    ) {
                        Text(
                            text = getPriorityLabel(announcement.priority, isKn),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = priorityColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF1F5F9)
                    ) {
                        Text(
                            text = getCategoryLabel(announcement.category, isKn),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF475569),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                IconButton(
                    onClick = {
                        onMarkAsRead()
                        onDismiss()
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp)
            ) {
                // Main Title
                Text(
                    text = displayTitle,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0F172A),
                    lineHeight = 24.sp
                )

                // Secondary Language Title (if both languages provided)
                val alternateTitle = if (isKn) announcement.titleEn else announcement.titleKn
                if (alternateTitle.isNotBlank() && alternateTitle != displayTitle) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = alternateTitle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF64748B)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFFE2E8F0))
                Spacer(modifier = Modifier.height(14.dp))

                // Timing change structured card if present
                if (!announcement.timingBusId.isNullOrBlank()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, CollegePrimary.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                        color = Color(0xFFF0FDF4),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = CollegePrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isKn) "ಸಮಯ ಬದಲಾವಣೆ ವಿವರಗಳು" else "Schedule Change Details",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CollegePrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Bus: ${announcement.timingBusId} • ${announcement.timingChangeType ?: "Departure"}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF0F172A)
                            )
                            if (!announcement.previousTime.isNullOrBlank() && !announcement.newTime.isNullOrBlank()) {
                                Text(
                                    text = "${if (isKn) "ಹಿಂದಿನ ಸಮಯ" else "Previous"}: ${announcement.previousTime} ➔ ${if (isKn) "ಹೊಸ ಸಮಯ" else "New"}: ${announcement.newTime}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF166534)
                                )
                            }
                            if (!announcement.effectiveDate.isNullOrBlank()) {
                                Text(
                                    text = "${if (isKn) "ಜಾರಿಗೆ ಬರುವ ದಿನಾಂಕ" else "Effective"}: ${announcement.effectiveDate}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF475569)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Message Body
                Text(
                    text = displayMessage,
                    fontSize = 14.sp,
                    color = Color(0xFF334155),
                    lineHeight = 22.sp
                )

                // Secondary Language Message (if available and different)
                val alternateMessage = if (isKn) announcement.messageEn else announcement.messageKn
                if (alternateMessage.isNotBlank() && alternateMessage != displayMessage) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = alternateMessage,
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        lineHeight = 18.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFFF1F5F9))
                Spacer(modifier = Modifier.height(12.dp))

                // Metadata Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${stringResource(R.string.published_at)}: ${formatDateFull(announcement.createdAt)}",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                    if (announcement.expiresAt != null && announcement.expiresAt > 0) {
                        Text(
                            text = "${stringResource(R.string.expires_at)}: ${formatDateFull(announcement.expiresAt)}",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onMarkAsRead()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(stringResource(R.string.close), fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(16.dp),
        containerColor = Color.White
    )
}

private fun formatDateFull(timestamp: Long): String {
    if (timestamp <= 0) return ""
    val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
