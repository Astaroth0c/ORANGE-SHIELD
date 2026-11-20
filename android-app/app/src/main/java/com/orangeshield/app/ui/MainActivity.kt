package com.orangeshield.app.ui

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EngineProvider.init(this)
        enableEdgeToEdge()
        setContent { OrangeShieldTheme { OrangeShieldApp() } }
    }
}

private data class NavItem(val route: String, val label: String, val icon: ImageVector)

private val NAV_ITEMS = listOf(
    NavItem("painel", "Painel", Icons.Filled.Shield),
    NavItem("chamadas", "Chamadas", Icons.Filled.History),
    NavItem("inteligencia", "Radar", Icons.Filled.Radar),
    NavItem("analise", "Análise", Icons.Filled.Insights),
    NavItem("listas", "Listas", Icons.Filled.Block),
    NavItem("whatsapp", "WhatsApp", Icons.Filled.Chat),
    NavItem("observatorio", "Observatório", Icons.Filled.Insights),
    NavItem("configuracoes", "Configurações", Icons.Filled.Settings)
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
    val context = LocalContext.current
    var ready by remember { mutableStateOf(false) }
    val appVersion = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?" }.getOrDefault("?")
    }

    LaunchedEffect(Unit) {
        store.load()
        com.orangeshield.app.voice.RingingCallState.voiceEnabled = store.current.voiceAssistant
        launch {
            runCatching { com.orangeshield.app.osint.OrangeLens.syncCommunityFeed(context.applicationContext) }
        }
        launch {
            runCatching {
                com.orangeshield.app.chain.ChainNode.ensureGenesis(context.applicationContext)
                com.orangeshield.app.chain.ChainTransport.pull(context.applicationContext)
            }
        }
        delay(1450)
        ready = true
    }
    LaunchedEffect(settings.voiceAssistant) {
        com.orangeshield.app.voice.RingingCallState.voiceEnabled = settings.voiceAssistant
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground, scrolledContainerColor = DarkSurface, titleContentColor = LightOnDark),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("ORANGE SHIELD", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = OrangePrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Proteção inteligente · v$appVersion", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(start = 8.dp, end = 4.dp)) {
                            Text("Proteção", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Switch(checked = settings.protectionActive, onCheckedChange = { checked -> scope.launch { store.save(settings.copy(protectionActive = checked)) } })
                        }
                    }
                },
                actions = { OrangeUpdateButton() }
            )
        },
        bottomBar = {
            Surface(color = DarkSurface.copy(alpha = 0.96f), tonalElevation = 8.dp) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    NAV_ITEMS.forEach { item ->
                        val selected = currentRoute == item.route
                        Column(
                            Modifier.width(76.dp).clickable {
                                if (!selected) navController.navigate(item.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }.padding(vertical = 5.dp, horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(item.icon, contentDescription = item.label, tint = if (selected) OrangePrimary else MutedOnDark)
                            Text(item.label, fontSize = 9.sp, maxLines = 1, color = if (selected) OrangeSecondary else MutedOnDark, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            Modifier.padding(padding).fillMaxSize().background(
                Brush.verticalGradient(listOf(DarkBackground, Color(0xFF101013), Color(0xFF17100D)))
            )
        ) {
            AmbientGlow(Modifier.fillMaxSize())
            NavHost(
                navController = navController,
                startDestination = "painel",
                modifier = Modifier.fillMaxSize()
            ) {
                composable("painel") { PainelScreen() }
                composable("chamadas") { CallsScreen() }
                composable("inteligencia") { IntelligenceScreen(onAnalyzeNumber = { number -> navController.navigate("analise?number=${Uri.encode(number)}") }) }
                composable("analise?number={number}", arguments = listOf(navArgument("number") { type = NavType.StringType; defaultValue = "" })) { entry -> AnaliseScreen(initialNumber = entry.arguments?.getString("number").orEmpty()) }
                composable("listas") { ListasScreen() }
                composable("whatsapp") { WhatsAppScreen() }
                composable("observatorio") { ObservatoryScreen() }
                composable("configuracoes") { SettingsScreen() }
            }
            if (!ready) {
                Box(Modifier.fillMaxSize().background(DarkBackground)) { OrangeSplashScreen() }
            }
        }
    }
}
