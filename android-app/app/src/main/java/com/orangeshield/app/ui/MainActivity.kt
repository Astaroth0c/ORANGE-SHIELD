package com.orangeshield.app.ui

import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.orangeshield.app.EngineProvider
import kotlinx.coroutines.launch

/**
 * ORANGE SHIELD — MainActivity (Jetpack Compose).
 * TopAppBar com a chave mestra de proteção + NavigationBar com Painel, Análise, Listas, WhatsApp e Observatório.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EngineProvider.init(this)
        enableEdgeToEdge()
        setContent {
            OrangeShieldTheme {
                OrangeShieldApp()
            }
        }
    }
}

private data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val NAV_ITEMS = listOf(
    NavItem("painel", "Painel", Icons.Filled.Shield),
    NavItem("inteligencia", "Radar", Icons.Filled.Radar),
    NavItem("analise", "Análise", Icons.Filled.PhoneInTalk),
    NavItem("listas", "Listas", Icons.Filled.Block),
    NavItem("whatsapp", "WhatsApp", Icons.Filled.Chat),
    NavItem("observatorio", "Observatório", Icons.Filled.Insights)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrangeShieldApp() {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val store = EngineProvider.settingsStore
    val settings by store.state.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route?.substringBefore("?") ?: "painel"

    // Versão do app lida do PackageManager (sem texto fixo no código).
    val context = LocalContext.current
    val appVersion = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (_: Exception) {
            "?"
        }
    }

    // Carrega as configurações persistidas (SharedPreferences) uma vez.
    LaunchedEffect(Unit) { store.load() }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    scrolledContainerColor = DarkSurface,
                    titleContentColor = LightOnDark
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ORANGE SHIELD",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = OrangePrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Motor anti-spoofing v$appVersion · regras ${settings.ruleVersion}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(start = 12.dp, end = 8.dp)
                        ) {
                            Text(
                                text = "Proteção",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                            Switch(
                                checked = settings.protectionActive,
                                onCheckedChange = { checked ->
                                    scope.launch {
                                        store.save(settings.copy(protectionActive = checked))
                                    }
                                }
                            )
                        }
                    }
                },
                actions = { OrangeUpdateButton() }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurface,
                contentColor = LightOnDark,
                tonalElevation = 12.dp
            ) {
                NAV_ITEMS.forEach { item ->
                    NavigationBarItem(
                        selected = currentRoute == item.route,
                        onClick = {
                            if (currentRoute != item.route) {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(imageVector = item.icon, contentDescription = item.label) },
                        label = { Text(item.label, maxLines = 1, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = OrangePrimary,
                            selectedTextColor = OrangeSecondary,
                            indicatorColor = Color(0xFF3A2115),
                            unselectedIconColor = MutedOnDark,
                            unselectedTextColor = MutedOnDark
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            DarkBackground,
                            Color(0xFF101013),
                            Color(0xFF17100D)
                        )
                    )
                )
        ) {
            NavHost(
                navController = navController,
                startDestination = "painel",
                modifier = Modifier.fillMaxSize()
            ) {
            composable("painel") { PainelScreen() }
            composable("inteligencia") { IntelligenceScreen(onAnalyzeNumber = { number -> navController.navigate("analise?number=${Uri.encode(number)}") }) }
            composable("analise?number={number}", arguments = listOf(navArgument("number") { type = NavType.StringType; defaultValue = "" })) { entry -> AnaliseScreen(initialNumber = entry.arguments?.getString("number").orEmpty()) }
            composable("listas") { ListasScreen() }
            composable("whatsapp") { WhatsAppScreen() }
            composable("observatorio") { ObservatoryScreen() }
            }
        }
    }
}
