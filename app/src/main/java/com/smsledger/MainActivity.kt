package com.smsledger

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Ink, background = Ground, surface = Ground)) {
                App()
            }
        }
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
    var editing by remember { mutableStateOf<Row?>(null) }
    var settings by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val onDelete: (Row) -> Unit = { r ->
        vm.delete(r)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val sign = if (r.direction == Direction.DEBIT) "−" else "+"
            val res = snackbar.showSnackbar("Deleted $sign${inr(r.amount)} · ${displayName(r.party)}", actionLabel = "Undo", duration = SnackbarDuration.Long)
            if (res == SnackbarResult.ActionPerformed) vm.undo(r)
        }
    }

    Scaffold(
        containerColor = Ground,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                val items = listOf(
                    "Overview" to Icons.Default.Home, "Over ₹10k" to Icons.Default.KeyboardArrowUp,
                    "Under ₹10k" to Icons.Default.List, "Year" to Icons.Default.DateRange,
                )
                items.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icon, contentDescription = null) },
                        label = { Text(label, fontSize = 11.sp) })
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> OverviewScreen(vm) { settings = true }
                1 -> TxnListScreen(vm, over = true, onDelete = onDelete, onCategory = { editing = it })
                2 -> TxnListScreen(vm, over = false, onDelete = onDelete, onCategory = { editing = it })
                else -> YearScreen(vm)
            }
        }
    }
    editing?.let { r ->
        CategorySheet(r, onDismiss = { editing = null }) { cat, forAll ->
            vm.setCategory(r, cat, forAll); editing = null
        }
    }
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
                Text("This removes every saved transaction, deletion and category choice from this phone. Your SMS inbox is not touched. The app will read your SMS again from 1 Aug 2026.")
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
    Column(Modifier.fillMaxSize().background(Ground).padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Expense tracker", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(12.dp))
        Text(
            "To track your debits and credits, allow the app to read your SMS. It only reads bank, card and meal-card " +
                "transaction messages from 1 Aug 2026, ignores everything else, never changes your inbox and keeps all data on this phone.",
            fontSize = 15.sp, color = Muted, lineHeight = 22.sp,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAllow, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Ink)) { Text("Allow SMS access") }
    }
}
