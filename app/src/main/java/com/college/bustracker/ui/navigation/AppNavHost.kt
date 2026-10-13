package com.college.bustracker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.college.bustracker.CollegeBusApp
import com.college.bustracker.data.model.UserRole
import com.college.bustracker.ui.admin.AdminDashboardScreen
import com.college.bustracker.ui.admin.AdminViewModel
import com.college.bustracker.ui.auth.AuthViewModel
import com.college.bustracker.ui.auth.StaffLoginScreen
import com.college.bustracker.ui.driver.DriverHomeScreen
import com.college.bustracker.ui.driver.DriverSwapScreen
import com.college.bustracker.ui.driver.DriverViewModel
import com.college.bustracker.ui.student.StudentHomeScreen
import com.college.bustracker.ui.student.StudentMapScreen
import com.college.bustracker.ui.student.StudentViewModel

sealed class Screen(val route: String) {
    data object StudentHome : Screen("student_home")
    data object StudentMap : Screen("student_map")
    data object StaffLogin : Screen("staff_login")
    data object DriverHome : Screen("driver_home")
    data object DriverSwap : Screen("driver_swap")
    data object AdminDashboard : Screen("admin_dashboard")
    data object BusPass : Screen("bus_pass")
    data object GpsDiagnostics : Screen("gps_diagnostics")
}

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    authViewModel: AuthViewModel = viewModel(),
    driverViewModel: DriverViewModel = viewModel(),
    studentViewModel: StudentViewModel = viewModel(),
    adminViewModel: AdminViewModel = viewModel()
) {
    val currentUser by authViewModel.currentUser.collectAsState()

    // Determine startup destination directly from active restored session
    val initialProfile = CollegeBusApp.instance.authRepository.getActiveProfile()
    val startDestination = remember {
        when (initialProfile?.role) {
            UserRole.DRIVER -> Screen.DriverHome.route
            UserRole.ADMIN -> Screen.AdminDashboard.route
            else -> Screen.StudentHome.route
        }
    }

    // Reactive handler: if user logs out or is deactivated while on a protected screen, pop back to StudentHome
    LaunchedEffect(currentUser) {
        val user = currentUser
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        if (user == null) {
            if (currentRoute == Screen.DriverHome.route ||
                currentRoute == Screen.DriverSwap.route ||
                currentRoute == Screen.AdminDashboard.route
            ) {
                navController.navigate(Screen.StudentHome.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // 1. Student Screens (Default Entry Point - NO LOGIN REQUIRED)
        composable(Screen.StudentHome.route) {
            StudentHomeScreen(
                viewModel = studentViewModel,
                onNavigateToMap = { navController.navigate(Screen.StudentMap.route) },
                onNavigateToStaffLogin = { navController.navigate(Screen.StaffLogin.route) },
                onNavigateToBusPass = { navController.navigate(Screen.BusPass.route) }
            )
        }

        composable(Screen.StudentMap.route) {
            StudentMapScreen(
                viewModel = studentViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.BusPass.route) {
            val busPassViewModel: com.college.bustracker.pass.ui.BusPassViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            com.college.bustracker.pass.ui.BusPassScreen(
                viewModel = busPassViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.GpsDiagnostics.route) {
            com.college.bustracker.diagnostics.ui.GpsDiagnosticsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 2. Staff Login (Admin & Driver ID + Password Authentication)
        composable(Screen.StaffLogin.route) {
            com.college.bustracker.ui.auth.StaffLoginScreen(
                authViewModel = authViewModel,
                onLoginSuccess = { profile ->
                    when (profile.role) {
                        UserRole.DRIVER -> {
                            navController.navigate(Screen.DriverHome.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                        UserRole.ADMIN -> {
                            navController.navigate(Screen.AdminDashboard.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                        UserRole.STUDENT -> {
                            navController.navigate(Screen.StudentHome.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // 3. Driver Screens (Protected)
        composable(Screen.DriverHome.route) {
            DriverHomeScreen(
                viewModel = driverViewModel,
                onNavigateToSwap = { navController.navigate(Screen.DriverSwap.route) },
                onNavigateToDiagnostics = { navController.navigate(Screen.GpsDiagnostics.route) },
                onLogout = {
                    driverViewModel.onLogout()
                    driverViewModel.stopTracking()
                    authViewModel.logout()
                    navController.navigate(Screen.StudentHome.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.DriverSwap.route) {
            DriverSwapScreen(
                viewModel = driverViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 4. Admin Screens (Protected)
        composable(Screen.AdminDashboard.route) {
            AdminDashboardScreen(
                viewModel = adminViewModel,
                onNavigateToDiagnostics = { navController.navigate(Screen.GpsDiagnostics.route) },
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(Screen.StudentHome.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
