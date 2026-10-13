package com.college.bustracker.ui.admin

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.college.bustracker.R
import com.college.bustracker.data.model.Announcement
import com.college.bustracker.data.model.Bus
import com.college.bustracker.data.model.Route
import com.college.bustracker.ui.theme.CollegePrimary
import com.college.bustracker.ui.theme.StatusActiveGreen
import com.college.bustracker.ui.theme.StatusDangerRed
import com.college.bustracker.ui.theme.StatusWarningOrange
import com.college.bustracker.util.LocaleHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAnnouncementDialog(
    announcementToEdit: Announcement? = null,
    buses: List<Bus>,
    routes: List<Route>,
    onDismiss: () -> Unit,
    onSave: (announcement: Announcement, publishImmediately: Boolean) -> Unit
) {
    val isKn = LocaleHelper.isKannada()

    var titleEn by remember { mutableStateOf(announcementToEdit?.titleEn ?: "") }
    var messageEn by remember { mutableStateOf(announcementToEdit?.messageEn ?: "") }
    var titleKn by remember { mutableStateOf(announcementToEdit?.titleKn ?: "") }
    var messageKn by remember { mutableStateOf(announcementToEdit?.messageKn ?: "") }

    var category by remember { mutableStateOf(announcementToEdit?.category ?: Announcement.CATEGORY_GENERAL) }
    var priority by remember { mutableStateOf(announcementToEdit?.priority ?: Announcement.PRIORITY_NORMAL) }
    var targetAudience by remember { mutableStateOf(announcementToEdit?.targetAudience ?: Announcement.AUDIENCE_ALL) }
    var targetId by remember { mutableStateOf(announcementToEdit?.targetId ?: "") }

    var categoryDropdownOpen by remember { mutableStateOf(false) }
    var audienceDropdownOpen by remember { mutableStateOf(false) }
    var targetDropdownOpen by remember { mutableStateOf(false) }

    var expiryOption by remember { mutableStateOf("24_HOURS") } // 24_HOURS, 48_HOURS, 7_DAYS, NEVER
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (announcementToEdit == null) stringResource(R.string.create_announcement) else stringResource(R.string.edit_announcement),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color(0xFF0F172A)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = StatusDangerRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // 1. English Title & Message
                Text(
                    text = "English Information",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = CollegePrimary
                )

                OutlinedTextField(
                    value = titleEn,
                    onValueChange = { titleEn = it; errorMessage = null },
                    label = { Text(stringResource(R.string.announcement_title_en)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = messageEn,
                    onValueChange = { messageEn = it; errorMessage = null },
                    label = { Text(stringResource(R.string.announcement_message_en)) },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(color = Color(0xFFE2E8F0))

                // 2. Kannada Title & Message
                Text(
                    text = "ಕನ್ನಡ ಮಾಹಿತಿ (Kannada)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = CollegePrimary
                )

                OutlinedTextField(
                    value = titleKn,
                    onValueChange = { titleKn = it },
                    label = { Text(stringResource(R.string.announcement_title_kn)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = messageKn,
                    onValueChange = { messageKn = it },
                    label = { Text(stringResource(R.string.announcement_message_kn)) },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(color = Color(0xFFE2E8F0))

                // 3. Category Dropdown
                Text(
                    text = stringResource(R.string.announcement_category),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )

                ExposedDropdownMenuBox(
                    expanded = categoryDropdownOpen,
                    onExpandedChange = { categoryDropdownOpen = !categoryDropdownOpen }
                ) {
                    OutlinedTextField(
                        value = getCategoryDisplayName(category, isKn),
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryDropdownOpen) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = categoryDropdownOpen,
                        onDismissRequest = { categoryDropdownOpen = false }
                    ) {
                        listOf(
                            Announcement.CATEGORY_GENERAL,
                            Announcement.CATEGORY_TIMING_CHANGE,
                            Announcement.CATEGORY_BUS_DELAY,
                            Announcement.CATEGORY_BUS_CANCELLATION,
                            Announcement.CATEGORY_ROUTE_CHANGE,
                            Announcement.CATEGORY_BUS_SWAP,
                            Announcement.CATEGORY_HOLIDAY,
                            Announcement.CATEGORY_EMERGENCY
                        ).forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(getCategoryDisplayName(cat, isKn)) },
                                onClick = {
                                    category = cat
                                    categoryDropdownOpen = false
                                }
                            )
                        }
                    }
                }

                // 4. Priority selection
                Text(
                    text = stringResource(R.string.announcement_priority),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        Announcement.PRIORITY_NORMAL to (if (isKn) "ಸಾಮಾನ್ಯ" else "Normal"),
                        Announcement.PRIORITY_IMPORTANT to (if (isKn) "ಮುಖ್ಯ" else "Important"),
                        Announcement.PRIORITY_URGENT to (if (isKn) "ತುರ್ತು" else "Urgent")
                    ).forEach { (p, label) ->
                        val isSelected = priority == p
                        val btnColor = when (p) {
                            Announcement.PRIORITY_URGENT -> StatusDangerRed
                            Announcement.PRIORITY_IMPORTANT -> StatusWarningOrange
                            else -> CollegePrimary
                        }
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) btnColor else Color(0xFFE2E8F0),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { priority = p },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) btnColor.copy(alpha = 0.1f) else Color.White
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) btnColor else Color(0xFF475569)
                                )
                            }
                        }
                    }
                }

                // 5. Target Audience
                Text(
                    text = stringResource(R.string.target_audience),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )

                ExposedDropdownMenuBox(
                    expanded = audienceDropdownOpen,
                    onExpandedChange = { audienceDropdownOpen = !audienceDropdownOpen }
                ) {
                    OutlinedTextField(
                        value = getAudienceDisplayName(targetAudience, isKn),
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = audienceDropdownOpen) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = audienceDropdownOpen,
                        onDismissRequest = { audienceDropdownOpen = false }
                    ) {
                        listOf(
                            Announcement.AUDIENCE_ALL,
                            Announcement.AUDIENCE_STUDENTS_ONLY,
                            Announcement.AUDIENCE_DRIVERS_ONLY,
                            Announcement.AUDIENCE_SELECTED_BUS,
                            Announcement.AUDIENCE_SELECTED_ROUTE
                        ).forEach { aud ->
                            DropdownMenuItem(
                                text = { Text(getAudienceDisplayName(aud, isKn)) },
                                onClick = {
                                    targetAudience = aud
                                    audienceDropdownOpen = false
                                    if (aud == Announcement.AUDIENCE_ALL ||
                                        aud == Announcement.AUDIENCE_STUDENTS_ONLY ||
                                        aud == Announcement.AUDIENCE_DRIVERS_ONLY
                                    ) {
                                        targetId = ""
                                    }
                                }
                            )
                        }
                    }
                }

                // Specific Bus Target Picker
                if (targetAudience == Announcement.AUDIENCE_SELECTED_BUS) {
                    ExposedDropdownMenuBox(
                        expanded = targetDropdownOpen,
                        onExpandedChange = { targetDropdownOpen = !targetDropdownOpen }
                    ) {
                        OutlinedTextField(
                            value = targetId.ifBlank { "Select Target Bus" },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Target Bus") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = targetDropdownOpen) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = targetDropdownOpen,
                            onDismissRequest = { targetDropdownOpen = false }
                        ) {
                            buses.forEach { bus ->
                                DropdownMenuItem(
                                    text = { Text("${bus.busNumber} (${bus.busId})") },
                                    onClick = {
                                        targetId = bus.busId
                                        targetDropdownOpen = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Specific Route Target Picker
                if (targetAudience == Announcement.AUDIENCE_SELECTED_ROUTE) {
                    ExposedDropdownMenuBox(
                        expanded = targetDropdownOpen,
                        onExpandedChange = { targetDropdownOpen = !targetDropdownOpen }
                    ) {
                        OutlinedTextField(
                            value = targetId.ifBlank { "Select Target Route" },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Target Route") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = targetDropdownOpen) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = targetDropdownOpen,
                            onDismissRequest = { targetDropdownOpen = false }
                        ) {
                            routes.forEach { r ->
                                DropdownMenuItem(
                                    text = { Text("${r.name} (${r.routeId})") },
                                    onClick = {
                                        targetId = r.routeId
                                        targetDropdownOpen = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Save as draft button
                OutlinedButton(
                    onClick = {
                        val valid = validateInputs(titleEn, messageEn, titleKn, messageKn, targetAudience, targetId)
                        if (valid.first) {
                            val announcement = buildAnnouncement(
                                announcementToEdit,
                                titleEn,
                                messageEn,
                                titleKn,
                                messageKn,
                                category,
                                priority,
                                targetAudience,
                                targetId,
                                expiryOption,
                                isPublished = false
                            )
                            onSave(announcement, false)
                        } else {
                            errorMessage = valid.second
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.save_as_draft), fontSize = 12.sp)
                }

                // Publish now button
                Button(
                    onClick = {
                        val valid = validateInputs(titleEn, messageEn, titleKn, messageKn, targetAudience, targetId)
                        if (valid.first) {
                            val announcement = buildAnnouncement(
                                announcementToEdit,
                                titleEn,
                                messageEn,
                                titleKn,
                                messageKn,
                                category,
                                priority,
                                targetAudience,
                                targetId,
                                expiryOption,
                                isPublished = true
                            )
                            onSave(announcement, true)
                        } else {
                            errorMessage = valid.second
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
                ) {
                    Text(stringResource(R.string.publish_now), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        shape = RoundedCornerShape(16.dp),
        containerColor = Color.White
    )
}

private fun validateInputs(
    titleEn: String,
    messageEn: String,
    titleKn: String,
    messageKn: String,
    targetAudience: String,
    targetId: String
): Pair<Boolean, String?> {
    if (titleEn.isBlank() && titleKn.isBlank()) {
        return false to "Please provide a title in English or Kannada"
    }
    if (messageEn.isBlank() && messageKn.isBlank()) {
        return false to "Please provide a message in English or Kannada"
    }
    if ((targetAudience == Announcement.AUDIENCE_SELECTED_BUS || targetAudience == Announcement.AUDIENCE_SELECTED_ROUTE) && targetId.isBlank()) {
        return false to "Please select a target bus or route"
    }
    return true to null
}

private fun buildAnnouncement(
    existing: Announcement?,
    titleEn: String,
    messageEn: String,
    titleKn: String,
    messageKn: String,
    category: String,
    priority: String,
    targetAudience: String,
    targetId: String,
    expiryOption: String,
    isPublished: Boolean
): Announcement {
    val now = System.currentTimeMillis()
    val expiresAt = when (expiryOption) {
        "24_HOURS" -> now + 24 * 3600_000L
        "48_HOURS" -> now + 48 * 3600_000L
        "7_DAYS" -> now + 7 * 86400_000L
        else -> null
    }

    return (existing ?: Announcement()).copy(
        titleEn = titleEn.trim().ifBlank { titleKn.trim() },
        messageEn = messageEn.trim().ifBlank { messageKn.trim() },
        titleKn = titleKn.trim().ifBlank { titleEn.trim() },
        messageKn = messageKn.trim().ifBlank { messageEn.trim() },
        category = category,
        priority = priority,
        targetAudience = targetAudience,
        targetId = targetId,
        status = if (isPublished) Announcement.STATUS_PUBLISHED else Announcement.STATUS_DRAFT,
        createdAt = existing?.createdAt ?: now,
        expiresAt = expiresAt,
        updatedAt = now
    )
}

fun getCategoryDisplayName(cat: String, isKn: Boolean): String {
    return when (cat) {
        Announcement.CATEGORY_TIMING_CHANGE -> if (isKn) "ಬಸ್ ಸಮಯ ಬದಲಾವಣೆ" else "Bus Timing Change"
        Announcement.CATEGORY_BUS_DELAY -> if (isKn) "ಬಸ್ ವಿಳಂಬ" else "Bus Delay"
        Announcement.CATEGORY_BUS_CANCELLATION -> if (isKn) "ಬಸ್ ರದ್ದತಿ" else "Bus Cancellation"
        Announcement.CATEGORY_ROUTE_CHANGE -> if (isKn) "ಮಾರ್ಗ ಬದಲಾವಣೆ" else "Route Change"
        Announcement.CATEGORY_BUS_SWAP -> if (isKn) "ಬಸ್ ಬದಲಾವಣೆ" else "Bus Replacement"
        Announcement.CATEGORY_HOLIDAY -> if (isKn) "ಕಾಲೇಜು ರಜೆ" else "College Holiday"
        Announcement.CATEGORY_EMERGENCY -> if (isKn) "ತುರ್ತು ಎಚ್ಚರಿಕೆ" else "Emergency Alert"
        else -> if (isKn) "ಸಾಮಾನ್ಯ ಪ್ರಕಟಣೆ" else "General Announcement"
    }
}

fun getAudienceDisplayName(aud: String, isKn: Boolean): String {
    return when (aud) {
        Announcement.AUDIENCE_STUDENTS_ONLY -> if (isKn) "ವಿದ್ಯಾರ್ಥಿಗಳಿಗೆ ಮಾತ್ರ" else "Students Only"
        Announcement.AUDIENCE_DRIVERS_ONLY -> if (isKn) "ಚಾಲಕರಿಗೆ ಮಾತ್ರ" else "Drivers Only"
        Announcement.AUDIENCE_SELECTED_BUS -> if (isKn) "ಆಯ್ದ ಬಸ್" else "Selected Bus"
        Announcement.AUDIENCE_SELECTED_ROUTE -> if (isKn) "ಆಯ್ದ ಮಾರ್ಗ" else "Selected Route"
        Announcement.AUDIENCE_SELECTED_STOP -> if (isKn) "ಆಯ್ದ ನಿಲ್ದಾಣ" else "Selected Stop"
        else -> if (isKn) "ಎಲ್ಲಾ ವಿದ್ಯಾರ್ಥಿಗಳು ಮತ್ತು ಚಾಲಕರು" else "All Students and Drivers"
    }
}
