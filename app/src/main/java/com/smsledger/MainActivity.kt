package com.smsledger

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smsledger.data.Row
import com.smsledger.data.SmsScanner
import com.smsledger.parser.Direction
import com.smsledger.ui.*
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Blue header runs under the status bar, so the status bar icons are white.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Brand, background = Ground, surface = Ground)) {
                App()
            }
        }
    }
}

/** Bottom tabs: Overview, Over ₹10k (>10K), Under ₹10k (<10K), Dashboard. */
@Composable
private fun TabIcon(i: Int) {
    when (i) {
        0 -> Icon(painterResource(R.drawable.ic_tab_overview), contentDescription = null)
        1 -> Text(">10K", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        2 -> Text("<10K", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        else -> Icon(painterResource(R.drawable.ic_tab_dashboard), contentDescription = null)
    }
}

@Composable
private fun App() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var granted by remember { mutableStateOf(SmsScanner.hasPermission(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = SmsScanner.hasPermission(context)
    }
    if (!granted) {
        PermissionScreen { launcher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)) }
        return
    }
    val vm: LedgerViewModel = viewModel()
    // Re-read the inbox every time the app comes to the front.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) vm.refresh() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var categoryFor by remember { mutableStateOf<Row?>(null) }
    var entry by remember { mutableStateOf<Row?>(null) }        // row being edited
    var adding by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var rangeSheet by remember { mutableStateOf(false) }
    var range by remember { mutableStateOf<Pair<LocalDate, LocalDate>?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val actions = RowActions(
        onDelete = { r ->
            vm.delete(r)
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val sign = if (r.direction == Direction.DEBIT) "−" else "+"
                val name = if (r.manual) r.party else displayName(r.party)
                val res = snackbar.showSnackbar("Deleted $sign${inr(r.amount)} · $name", actionLabel = "Undo", duration = SnackbarDuration.Long)
                if (res == SnackbarResult.ActionPerformed) vm.undo(r)
            }
        },
        onCategory = { categoryFor = it },
        onEdit = { entry = it },
    )
    BackHandler(enabled = range != null) { range = null }

    Scaffold(
        containerColor = Ground,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (tab == 1 || tab == 2 || range != null) {
                ExtendedFloatingActionButton(
                    onClick = { adding = true }, containerColor = Brand, contentColor = Color.White,
                    shape = RoundedCornerShape(18.dp), icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add", fontWeight = FontWeight.Bold) },
                )
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                listOf("Overview", "Over ₹10k", "Under ₹10k", "Dashboard").forEachIndexed { i, label ->
                    NavigationBarItem(
                        selected = tab == i && range == null, onClick = { range = null; tab = i },
                        icon = { TabIcon(i) }, label = { Text(label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = BrandDark, selectedTextColor = BrandDark, indicatorColor = BrandLight,
                            unselectedIconColor = Muted, unselectedTextColor = Muted,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val r = range
            when {
                r != null -> RangeScreen(vm, r.first, r.second, actions) { range = null }
                tab == 0 -> OverviewScreen(vm, onSettings = { settings = true }, onCustom = { rangeSheet = true })
                tab == 1 -> TxnListScreen(vm, over = true, actions = actions, onCustom = { rangeSheet = true })
                tab == 2 -> TxnListScreen(vm, over = false, actions = actions, onCustom = { rangeSheet = true })
                else -> DashboardScreen(vm)
            }
        }
    }
    categoryFor?.let { row ->
        CategorySheet(row, onDismiss = { categoryFor = null }) { cat, forAll ->
            vm.setCategory(row, cat, forAll); categoryFor = null
        }
    }
    if (adding || entry != null) {
        EntrySheet(entry, vm.accounts, onDismiss = { adding = false; entry = null }) { e ->
            vm.saveManual(e)
            val wasEdit = entry != null
            adding = false; entry = null
            scope.launch { snackbar.showSnackbar(if (wasEdit) "Changes saved" else "Added ${inr(e.amount)} · ${e.party}") }
        }
    }
    if (rangeSheet) RangeSheet(onDismiss = { rangeSheet = false }) { from, to -> range = from to to; rangeSheet = false }
    if (settings) SettingsDialog(vm) { settings = false }
}

@Composable
private fun SettingsDialog(vm: LedgerViewModel, onClose: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(vm.ownerName) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (confirm) "Clear all data?" else "Settings") },
        text = {
            if (confirm) {
                Text("This removes every saved transaction (including ones you added), deletion and category choice from this phone. Your SMS inbox is not touched. The app will read your SMS again from 1 Aug 2026.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Your name as banks write it. Used only to label transfers between your own accounts as \"Own account (likely)\". Stays on this phone.", fontSize = 13.sp)
                    OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Your name") })
                    Text("Data stays on this phone. Only transaction details are saved, never SMS text.", fontSize = 12.sp, color = Muted)
                    TextButton(onClick = { confirm = true }, contentPadding = PaddingValues(0.dp)) { Text("Clear all data…", color = Color(0xFFC42B1C)) }
                }
            }
        },
        confirmButton = {
            if (confirm) TextButton(onClick = { vm.clearAll(); vm.refresh(); onClose() }) { Text("Clear all data", color = Color(0xFFC42B1C)) }
            else TextButton(onClick = { if (name.trim() != vm.ownerName) vm.saveOwnerName(name) else vm.refresh(); onClose() }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

@Composable
private fun PermissionScreen(onAllow: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Ground).systemBarsPadding().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Expense tracker", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(12.dp))
        Text(
            "To track your debits and credits, allow the app to read your SMS. It only reads bank, card and meal-card " +
                "transaction messages from 1 Aug 2026, ignores everything else, never changes your inbox and keeps all data on this phone.",
            fontSize = 15.sp, color = Muted, lineHeight = 22.sp,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAllow, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Brand)) { Text("Allow SMS access") }
    }
}
