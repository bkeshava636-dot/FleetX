package com.college.bustracker.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.college.bustracker.CollegeBusApp
import com.college.bustracker.data.model.UserProfile
import com.college.bustracker.data.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {

    private val authRepo = CollegeBusApp.instance.authRepository

    val currentUser: StateFlow<UserProfile?> = authRepo.currentProfile

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        viewModelScope.launch {
            authRepo.getUserProfileFlow().collect {
                // Profile flow updates authRepo.currentProfile internally
            }
        }
    }

    fun loginWithId(
        userId: String,
        pass: String,
        expectedRole: UserRole,
        onSuccess: (UserProfile) -> Unit
    ) {
        _errorMessage.value = null
        _isLoading.value = true

        viewModelScope.launch {
            val result = authRepo.loginWithId(userId, pass, expectedRole)
            _isLoading.value = false
            result.fold(
                onSuccess = { profile ->
                    onSuccess(profile)
                },
                onFailure = { ex ->
                    _errorMessage.value = ex.message ?: "Authentication failed. Check ID and Password."
                }
            )
        }
    }

    private val _isResetLoading = MutableStateFlow(false)
    val isResetLoading: StateFlow<Boolean> = _isResetLoading.asStateFlow()

    fun sendAdminPasswordReset(
        adminIdOrEmail: String,
        onComplete: (Result<Unit>) -> Unit
    ) {
        _isResetLoading.value = true
        viewModelScope.launch {
            val result = authRepo.sendAdminPasswordReset(adminIdOrEmail)
            _isResetLoading.value = false
            onComplete(result)
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun logout() {
        authRepo.logout()
        _errorMessage.value = null
    }
}

