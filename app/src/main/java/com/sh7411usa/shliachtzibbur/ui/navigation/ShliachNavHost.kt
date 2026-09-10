package com.sh7411usa.shliachtzibbur.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.sh7411usa.shliachtzibbur.core.model.LegalKind
import com.sh7411usa.shliachtzibbur.ui.AppViewModelFactory
import com.sh7411usa.shliachtzibbur.ui.appsettings.AppSettingsScreen
import com.sh7411usa.shliachtzibbur.ui.auth.AuthLandingScreen
import com.sh7411usa.shliachtzibbur.ui.auth.AuthViewModel
import com.sh7411usa.shliachtzibbur.ui.auth.CodeVerifyScreen
import com.sh7411usa.shliachtzibbur.ui.auth.PhoneEntryScreen
import com.sh7411usa.shliachtzibbur.ui.contacts.ContactsScreen
import com.sh7411usa.shliachtzibbur.ui.groups.CreateGroupScreen
import com.sh7411usa.shliachtzibbur.ui.groups.GroupsScreen
import com.sh7411usa.shliachtzibbur.ui.groupsettings.AddMembersScreen
import com.sh7411usa.shliachtzibbur.ui.groupsettings.GroupEncryptionScreen
import com.sh7411usa.shliachtzibbur.ui.groupsettings.GroupSettingsScreen
import com.sh7411usa.shliachtzibbur.ui.groupsettings.MembersScreen
import com.sh7411usa.shliachtzibbur.ui.messages.MessagesScreen
import com.sh7411usa.shliachtzibbur.ui.settings.SettingsHomeScreen
import com.sh7411usa.shliachtzibbur.ui.usersettings.DevicesScreen
import com.sh7411usa.shliachtzibbur.ui.usersettings.LegalScreen
import com.sh7411usa.shliachtzibbur.ui.usersettings.UserSettingsScreen

/**
 * Root navigation. The start graph is chosen by whether a session exists; the
 * auth flow and the main app never share a back stack. Sign-in / sign-out that
 * happens while the app is open is handled by [LaunchedEffect] below.
 */
@Composable
fun ShliachNavHost(
    isSignedIn: Boolean,
    initialGroupId: String? = null,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = if (isSignedIn) Routes.MAIN_GRAPH else Routes.AUTH_GRAPH,
    ) {
        authGraph(navController)
        mainGraph(navController)
    }

    LaunchedEffect(isSignedIn) {
        val entry = navController.currentBackStackEntry ?: return@LaunchedEffect
        val target = if (isSignedIn) Routes.MAIN_GRAPH else Routes.AUTH_GRAPH
        val alreadyThere = entry.destination.hierarchy.any { it.route == target }
        if (!alreadyThere) {
            navController.navigate(target) {
                popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
            }
        }
    }

    LaunchedEffect(initialGroupId, isSignedIn) {
        if (isSignedIn && !initialGroupId.isNullOrBlank() &&
            navController.currentBackStackEntry != null
        ) {
            navController.navigate(Routes.messages(initialGroupId))
        }
    }
}

private fun NavGraphBuilder.authGraph(navController: NavHostController) {
    navigation(startDestination = Routes.AUTH_LANDING, route = Routes.AUTH_GRAPH) {
        composable(Routes.AUTH_LANDING) {
            AuthLandingScreen(onContinueWithPhone = { navController.navigate(Routes.AUTH_PHONE) })
        }
        composable(Routes.AUTH_PHONE) {
            val vm = authViewModel(navController)
            val state by vm.state.collectAsStateWithLifecycle()
            PhoneEntryScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onDetectPhone = vm::onPhoneDetection,
                onChooseSim = vm::chooseSim,
                onSubmit = vm::submitPhone,
            )
            LaunchedEffect(state.challenge) {
                if (state.challenge != null) navController.navigate(Routes.AUTH_CODE)
            }
        }
        composable(Routes.AUTH_CODE) {
            val vm = authViewModel(navController)
            val state by vm.state.collectAsStateWithLifecycle()
            CodeVerifyScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onStartAutoDetect = vm::startSmsAutoDetect,
                onStopAutoDetect = vm::stopSmsAutoDetect,
                onVerify = { code, nickname -> vm.verify(code, nickname) },
                onResend = vm::resend,
            )
        }
    }
}

/** One [AuthViewModel] shared across the auth graph (phone + code screens). */
@Composable
private fun authViewModel(navController: NavHostController): AuthViewModel {
    val parentEntry = remember { navController.getBackStackEntry(Routes.AUTH_GRAPH) }
    return viewModel(viewModelStoreOwner = parentEntry, factory = AppViewModelFactory.Factory)
}

private fun NavGraphBuilder.mainGraph(navController: NavHostController) {
    navigation(startDestination = Routes.GROUPS, route = Routes.MAIN_GRAPH) {
        composable(Routes.GROUPS) {
            GroupsScreen(
                onOpenGroup = { id -> navController.navigate(Routes.messages(id)) },
                onCreateGroup = { navController.navigate(Routes.createGroup()) },
                onOpenContacts = { navController.navigate(Routes.CONTACTS) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS_HOME) },
                onOpenGroupSettings = { id -> navController.navigate(Routes.groupSettings(id)) },
                onOpenMembers = { id -> navController.navigate(Routes.members(id)) },
                onManageEncryption = { id -> navController.navigate(Routes.groupEncryption(id)) },
            )
        }
        composable(Routes.CONTACTS) {
            ContactsScreen(
                onBack = { navController.popBackStack() },
                onOpenGroup = { id -> navController.navigate(Routes.messages(id)) },
                onNewGroupWith = { phone -> navController.navigate(Routes.createGroup(phone)) },
            )
        }
        composable(Routes.SETTINGS_HOME) {
            SettingsHomeScreen(
                onBack = { navController.popBackStack() },
                onOpenAccount = { navController.navigate(Routes.USER_SETTINGS) },
                onOpenAppSettings = { navController.navigate(Routes.APP_SETTINGS) },
                onOpenLegal = { kind -> navController.navigate(Routes.legal(kind)) },
            )
        }
        composable(
            route = Routes.CREATE_GROUP,
            arguments = listOf(
                navArgument(Routes.ARG_MEMBER_PHONE) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            CreateGroupScreen(
                onBack = { navController.popBackStack() },
                onCreated = { id ->
                    navController.popBackStack()
                    navController.navigate(Routes.messages(id))
                },
            )
        }
        composable(
            route = Routes.MESSAGES,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
        ) {
            MessagesScreen(
                onBack = { navController.popBackStack() },
                onOpenSettings = { id -> navController.navigate(Routes.groupSettings(id)) },
            )
        }
        composable(
            route = Routes.GROUP_SETTINGS,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
        ) {
            GroupSettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenMembers = { id -> navController.navigate(Routes.members(id)) },
                onOpenEncryption = { id -> navController.navigate(Routes.groupEncryption(id)) },
                onLeftOrDeleted = { navController.popBackStack(Routes.GROUPS, inclusive = false) },
            )
        }
        composable(
            route = Routes.GROUP_ENCRYPTION,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
        ) {
            GroupEncryptionScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.MEMBERS,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
        ) {
            MembersScreen(
                onBack = { navController.popBackStack() },
                onAddMembers = { id -> navController.navigate(Routes.addMembers(id)) },
            )
        }
        composable(
            route = Routes.ADD_MEMBERS,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
        ) {
            AddMembersScreen(onDone = { navController.popBackStack() })
        }
        composable(Routes.USER_SETTINGS) {
            UserSettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenDevices = { navController.navigate(Routes.DEVICES) },
                onOpenLegal = { kind -> navController.navigate(Routes.legal(kind)) },
                onSignedOut = {},
            )
        }
        composable(Routes.APP_SETTINGS) {
            AppSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.DEVICES) {
            DevicesScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.LEGAL,
            arguments = listOf(navArgument("kind") { type = NavType.StringType }),
        ) { entry ->
            val kindArg = entry.arguments?.getString("kind")
            val kind = if (kindArg == LegalKind.TERMS.slug) LegalKind.TERMS else LegalKind.PRIVACY
            LegalScreen(kind = kind, onBack = { navController.popBackStack() })
        }
    }
}
