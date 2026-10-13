package com.college.bustracker.update.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.college.bustracker.ui.theme.CollegePrimary
import com.college.bustracker.ui.theme.StatusActiveGreen
import com.college.bustracker.ui.theme.StatusDangerRed
import com.college.bustracker.update.AppUpdateManager
import com.college.bustracker.update.model.AppReleaseInfo
import com.college.bustracker.update.model.DownloadState
import com.college.bustracker.update.service.AppUpdateInstaller
import java.io.File
import java.util.Locale

@Composable
fun UpdateDialog(
    releaseInfo: AppReleaseInfo,
    installedVersionName: String,
    installedVersionCode: Long,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updateManager = remember { AppUpdateManager.instance }
    val downloadState by updateManager.downloadState.collectAsState()

    var showPermissionGuidance by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = {
        if (downloadState !is DownloadState.Downloading) {
            onDismiss()
        }
    }) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(CollegePrimary.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = CollegePrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Update Available",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "FleetX v${releaseInfo.versionName}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    if (downloadState !is DownloadState.Downloading) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Version Comparison Badge Box
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "CURRENT VERSION",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF94A3B8),
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = "v$installedVersionName ($installedVersionCode)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF475569),
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier
                                .padding(horizontal = 6.dp)
                                .size(16.dp)
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = "NEW VERSION",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = CollegePrimary,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = "v${releaseInfo.versionName} (${releaseInfo.versionCode})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = CollegePrimary,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Metadata Details (Size + Checksum status)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val sizeMb = if (releaseInfo.apkAsset.sizeBytes > 0) {
                        String.format(Locale.US, "%.1f MB", releaseInfo.apkAsset.sizeBytes / (1024.0 * 1024.0))
                    } else {
                        "Standard APK"
                    }
                    Text(
                        text = "Download size: $sizeMb",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF64748B),
                        maxLines = 1
                    )

                    if (!releaseInfo.sha256Checksum.isNullOrBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = StatusActiveGreen,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Verified SHA-256",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = StatusActiveGreen,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Release Notes
                Text(
                    text = "Release Notes",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )
                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 140.dp)
                        .background(Color(0xFFF1F5F9), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = releaseInfo.releaseNotes.ifBlank { "Performance improvements and bug fixes." },
                        fontSize = 12.sp,
                        color = Color(0xFF334155),
                        lineHeight = 16.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Download & Verification Status / Controls
                when (val state = downloadState) {
                    is DownloadState.Idle -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    updateManager.dismissVersion(context, releaseInfo.versionCode)
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(vertical = 10.dp)
                            ) {
                                Text("Later", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF64748B))
                            }

                            Button(
                                onClick = {
                                    updateManager.startDownload(context, releaseInfo, scope)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary),
                                contentPadding = PaddingValues(vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Update Now", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    is DownloadState.Downloading -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (state.progressPercent >= 0) "Downloading update (${state.progressPercent}%)" else "Downloading update...",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = CollegePrimary
                                )
                                if (state.totalBytes > 0) {
                                    val downloadedMb = state.bytesDownloaded / (1024.0 * 1024.0)
                                    val totalMb = state.totalBytes / (1024.0 * 1024.0)
                                    Text(
                                        text = String.format(Locale.US, "%.1f / %.1f MB", downloadedMb, totalMb),
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (state.progressPercent >= 0) {
                                LinearProgressIndicator(
                                    progress = { state.progressPercent / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp),
                                    color = CollegePrimary,
                                    trackColor = Color(0xFFE2E8F0)
                                )
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp),
                                    color = CollegePrimary,
                                    trackColor = Color(0xFFE2E8F0)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedButton(
                                onClick = { updateManager.cancelDownload() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Cancel", fontSize = 12.sp, color = Color(0xFF64748B))
                            }
                        }
                    }

                    is DownloadState.Verifying -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFEFF6FF), RoundedCornerShape(10.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = CollegePrimary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = state.message,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF1E40AF)
                            )
                        }
                    }

                    is DownloadState.ReadyToInstall -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF0FDF4), RoundedCornerShape(10.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = StatusActiveGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Update verified and ready to install.",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF166534)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    if (!AppUpdateInstaller.canInstallPackages(context)) {
                                        showPermissionGuidance = true
                                    } else {
                                        updateManager.installUpdate(context, state.apkFile)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = StatusActiveGreen),
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                Text("Install Update", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }

                    is DownloadState.Error -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFFEF2F2), RoundedCornerShape(10.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = StatusDangerRed,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = state.message,
                                    fontSize = 12.sp,
                                    color = Color(0xFF991B1B),
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        updateManager.resetDownloadState()
                                        onDismiss()
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Dismiss", fontSize = 12.sp)
                                }

                                Button(
                                    onClick = {
                                        updateManager.resetDownloadState()
                                        updateManager.startDownload(context, releaseInfo, scope)
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
                                ) {
                                    Text("Retry", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Permission Guidance Dialog if user needs to enable "Install unknown apps"
    if (showPermissionGuidance) {
        AlertDialog(
            onDismissRequest = { showPermissionGuidance = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = CollegePrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Permission Required", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Text(
                    text = "To install the update, Android requires you to allow FleetX to install apps. Please toggle 'Allow from this source' on the settings screen that opens next.",
                    fontSize = 13.sp,
                    color = Color(0xFF334155),
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionGuidance = false
                        val intent = AppUpdateInstaller.createManageUnknownAppSourcesIntent(context)
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
                ) {
                    Text("Open Settings", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionGuidance = false }) {
                    Text("Cancel", color = Color(0xFF64748B))
                }
            }
        )
    }
}
