package com.college.bustracker.ui.common

import android.widget.Toast
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.college.bustracker.R
import com.college.bustracker.ui.theme.CollegePrimary
import com.college.bustracker.util.LocaleHelper

/**
 * In-app language selection dialog allowing instant switching between English and Kannada.
 */
@Composable
fun LanguageSelectionDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currentLang by LocaleHelper.currentLanguage.collectAsState()
    var selectedLang by remember { mutableStateOf(currentLang) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(CollegePrimary.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = CollegePrimary,
                    modifier = Modifier.size(26.dp)
                )
            }
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Language / ಭಾಷೆ",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Select your preferred language",
                    fontSize = 13.sp,
                    color = Color(0xFF64748B)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LanguageOptionCard(
                    title = "English",
                    subtitle = "English",
                    isSelected = selectedLang == LocaleHelper.LANGUAGE_EN,
                    onClick = {
                        selectedLang = LocaleHelper.LANGUAGE_EN
                        LocaleHelper.setLanguage(context, LocaleHelper.LANGUAGE_EN)
                        Toast.makeText(context, "Language changed to English", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                )

                LanguageOptionCard(
                    title = "ಕನ್ನಡ",
                    subtitle = "Kannada",
                    isSelected = selectedLang == LocaleHelper.LANGUAGE_KN,
                    onClick = {
                        selectedLang = LocaleHelper.LANGUAGE_KN
                        LocaleHelper.setLanguage(context, LocaleHelper.LANGUAGE_KN)
                        Toast.makeText(context, "ಭಾಷೆಯನ್ನು ಕನ್ನಡಕ್ಕೆ ಬದಲಾಯಿಸಲಾಗಿದೆ", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close), color = CollegePrimary, fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(16.dp),
        containerColor = Color.White
    )
}

@Composable
private fun LanguageOptionCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) CollegePrimary else Color(0xFFE2E8F0)
    val bgColor = if (isSelected) CollegePrimary.copy(alpha = 0.06f) else Color(0xFFF8FAFC)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = if (isSelected) 2.dp else 1.dp, color = borderColor, shape = RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) CollegePrimary else Color(0xFF1E293B)
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(CollegePrimary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .border(1.5.dp, Color(0xFF94A3B8), CircleShape)
                )
            }
        }
    }
}
