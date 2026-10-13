package com.college.bustracker.ui.auth

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import com.college.bustracker.data.model.UserProfile
import com.college.bustracker.data.model.UserRole
import com.college.bustracker.ui.theme.CollegePrimary
import com.college.bustracker.ui.theme.CollegeSurface
import com.college.bustracker.ui.theme.StatusDangerRed

@Composable
fun StaffLoginScreen(
    authViewModel: AuthViewModel,
    onLoginSuccess: (UserProfile) -> Unit,
    onNavigateBack: () -> Unit
) {
    val isLoading by authViewModel.isLoading.collectAsState()
    val errorMessage by authViewModel.errorMessage.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()
    val focusManager = LocalFocusManager.current

    androidx.compose.runtime.LaunchedEffect(currentUser) {
        val user = currentUser
        if (user != null) {
            onLoginSuccess(user)
        }
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) } // 0: Driver, 1: Admin
    var userIdInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    // Admin Password Recovery State
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var forgotPasswordInput by remember { mutableStateOf("") }
    var resetFeedbackMessage by remember { mutableStateOf<String?>(null) }
    var resetFeedbackIsError by remember { mutableStateOf(false) }
    val isResetLoading by authViewModel.isResetLoading.collectAsState()

    val currentRole = if (selectedTabIndex == 0) UserRole.DRIVER else UserRole.ADMIN

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CollegeSurface)
            .statusBarsPadding()
    ) {
        // Pinned Header: Back arrow, Title, and Language Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Student View",
                        tint = Color(0xFF1E293B)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Staff Authentication",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
            }

            androidx.compose.material3.OutlinedButton(
                onClick = { showLanguageDialog = true },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0F766E)),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = "Language",
                    modifier = Modifier.size(13.dp),
                    tint = Color(0xFF0F766E)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = if (com.college.bustracker.util.LocaleHelper.isKannada()) "ಕನ್ನಡ" else "EN",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Scrollable content area with imePadding for soft keyboard support
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // University Staff Badge Icon (Centered)
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .background(CollegePrimary.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (selectedTabIndex == 0) Icons.Default.Badge else Icons.Default.Security,
                    contentDescription = null,
                    tint = CollegePrimary,
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Console Title (Centered)
            Text(
                text = if (selectedTabIndex == 0) "Driver Access Console" else "Transit Administrator",
                fontSize = 21.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF0F172A),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Console Subtitle (Centered)
            Text(
                text = if (selectedTabIndex == 0)
                    "Sign in with your assigned staff ID to begin route tracking"
                else
                    "Sign in with administrator credentials to manage transit fleet",
                fontSize = 13.sp,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Login Card (Centered with equal width elements)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Equal-width Tab Selector
                    TabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = Color(0xFFF1F5F9),
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                color = CollegePrimary,
                                height = 3.dp
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                    ) {
                        Tab(
                            selected = selectedTabIndex == 0,
                            onClick = {
                                selectedTabIndex = 0
                                authViewModel.clearError()
                            },
                            text = {
                                Text(
                                    text = "Bus Driver",
                                    fontSize = 14.sp,
                                    fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedTabIndex == 0) CollegePrimary else Color(0xFF64748B),
                                    textAlign = TextAlign.Center
                                )
                            }
                        )
                        Tab(
                            selected = selectedTabIndex == 1,
                            onClick = {
                                selectedTabIndex = 1
                                authViewModel.clearError()
                            },
                            text = {
                                Text(
                                    text = "Administrator",
                                    fontSize = 14.sp,
                                    fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedTabIndex == 1) CollegePrimary else Color(0xFF64748B),
                                    textAlign = TextAlign.Center
                                )
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Error Banner
                    if (errorMessage != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2))
                        ) {
                            Text(
                                text = errorMessage ?: "",
                                color = StatusDangerRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    // ID Field
                    Text(
                        text = if (selectedTabIndex == 0) "Driver Staff ID" else "Administrator ID",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF475569),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = userIdInput,
                        onValueChange = {
                            userIdInput = it
                            authViewModel.clearError()
                        },
                        placeholder = {
                            Text(
                                if (selectedTabIndex == 0) "Enter driver ID" else "Enter admin username",
                                color = Color(0xFF94A3B8),
                                fontSize = 14.sp
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, tint = CollegePrimary)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CollegePrimary,
                            unfocusedBorderColor = Color(0xFFCBD5E1)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Password Field
                    Text(
                        text = "Password",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF475569),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = {
                            passwordInput = it
                            authViewModel.clearError()
                        },
                        placeholder = {
                            Text("Enter password", color = Color(0xFF94A3B8), fontSize = 14.sp)
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF94A3B8))
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                    tint = Color(0xFF94A3B8)
                                )
                            }
                        },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                authViewModel.loginWithId(userIdInput, passwordInput, currentRole, onLoginSuccess)
                            }
                        ),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CollegePrimary,
                            unfocusedBorderColor = Color(0xFFCBD5E1)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Forgot Password option (Admin only)
                    if (selectedTabIndex == 1) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    forgotPasswordInput = userIdInput.trim()
                                    resetFeedbackMessage = null
                                    resetFeedbackIsError = false
                                    showForgotPasswordDialog = true
                                }
                            ) {
                                Text(
                                    text = "Forgot Password?",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = CollegePrimary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    } else {
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    // Login Submit Button: Identical width to text fields
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            authViewModel.loginWithId(userIdInput, passwordInput, currentRole, onLoginSuccess)
                        },
                        enabled = !isLoading && userIdInput.isNotBlank() && passwordInput.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CollegePrimary
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Text(
                                text = if (selectedTabIndex == 0) "SIGN IN AS DRIVER" else "SIGN IN AS ADMINISTRATOR",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Return to Public Student Portal Button (Centered)
            TextButton(
                onClick = onNavigateBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Return to Public Student Portal",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF475569),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Admin Password Recovery Dialog
        if (showForgotPasswordDialog) {
            AlertDialog(
                onDismissRequest = {
                    if (!isResetLoading) {
                        showForgotPasswordDialog = false
                        resetFeedbackMessage = null
                    }
                },
                title = {
                    Text(
                        text = "Reset Administrator Password",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Enter your Administrator ID or registered email address. We will send a secure password reset link to your registered email.",
                            fontSize = 13.sp,
                            color = Color(0xFF475569),
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedTextField(
                            value = forgotPasswordInput,
                            onValueChange = {
                                forgotPasswordInput = it
                                resetFeedbackMessage = null
                            },
                            placeholder = {
                                Text("Enter your Admin ID", fontSize = 13.sp, color = Color(0xFF94A3B8))
                            },
                            label = { Text("Enter Admin ID", fontSize = 12.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (resetFeedbackMessage != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = resetFeedbackMessage ?: "",
                                fontSize = 12.sp,
                                color = if (resetFeedbackIsError) StatusDangerRed else Color(0xFF15803D),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            authViewModel.sendAdminPasswordReset(forgotPasswordInput) { result ->
                                result.fold(
                                    onSuccess = {
                                        resetFeedbackIsError = false
                                        resetFeedbackMessage = "Password reset email sent. Please check your inbox and spam folder."
                                    },
                                    onFailure = { ex ->
                                        resetFeedbackIsError = true
                                        resetFeedbackMessage = ex.message ?: "Could not process password reset."
                                    }
                                )
                            }
                        },
                        enabled = !isResetLoading && forgotPasswordInput.isNotBlank(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
                    ) {
                        if (isResetLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Send Reset Link", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showForgotPasswordDialog = false
                            resetFeedbackMessage = null
                        },
                        enabled = !isResetLoading
                    ) {
                        Text("Close", fontSize = 13.sp, color = Color(0xFF64748B))
                    }
                },
                shape = RoundedCornerShape(16.dp),
                containerColor = Color.White
            )
        }

        if (showLanguageDialog) {
            com.college.bustracker.ui.common.LanguageSelectionDialog(
                onDismiss = { showLanguageDialog = false }
            )
        }
    }
}
