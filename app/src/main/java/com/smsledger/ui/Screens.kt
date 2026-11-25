package com.smsledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smsledger.data.ManualEntry
import com.smsledger.data.Row
import com.smsledger.parser.CREDIT_CATEGORIES
import com.smsledger.parser.DEBIT_CATEGORIES
import com.smsledger.parser.Direction
import com.smsledger.parser.Kind
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

private val DAY = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH)
private val FULL_DAY = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
private val HM = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private val BANK_ORDER = listOf("DCB", "HDFC", "IndusInd", "IOB", "Pluxee", "SBI", "SBI Card")

private fun bankRank(r: Row) = if (r.bank == "Cash") -1 else BANK_ORDER.indexOf(r.bank).let { if (it < 0) 99 else it }

// ---------------------------------------------------------------- header
@Composable
private fun PickerBox(value: String, options: List<String>, onPick: (Int) -> Unit, modifier: Modifier, description: String) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp)).background(Color.White)
                .clickable { open = true }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(value, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1)
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Choose $description", tint = Ink)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { i, o -> DropdownMenuItem(text = { Text(o) }, onClick = { open = false; onPick(i) }) }
        }
    }
}

/** Blue header: title, optional right button, Month / Year / Custom, live line. */
@Composable
fun Header(
    title: String,
    vm: LedgerUi,
    showMonth: Boolean = true,
    onCustom: (() -> Unit)? = null,
    right: (@Composable () -> Unit)? = null,
) {
    val months = Month.entries.map { it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }
    val years = vm.yearsAvailable()
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
            .background(Brush.verticalGradient(listOf(Brand, BrandDark)))
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
            right?.invoke()
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            if (showMonth) Column(Modifier.weight(1f)) {
                SmallLabel("MONTH")
                PickerBox(months[vm.month - 1], months, { vm.month = it + 1 }, Modifier.fillMaxWidth(), "month")
            }
            Column(Modifier.weight(1f)) {
                SmallLabel("YEAR")
                PickerBox(vm.year.toString(), years.map { it.toString() }, { vm.year = years[it] }, Modifier.fillMaxWidth(), "year")
            }
            if (onCustom != null) {
                Row(
                    Modifier.height(48.dp).clip(RoundedCornerShape(12.dp)).background(Color.White)
                        .clickable(onClick = onCustom).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null, tint = BrandDark, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Custom", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = BrandDark)
                }
            }
        }
        LiveLine(vm)
    }
}

@Composable
private fun SmallLabel(t: String) {
    Text(t, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFC9D0FF), letterSpacing = 0.5.sp,
        modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
fun SettingsButton(onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.14f))
            .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Settings", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun LiveLine(vm: LedgerUi) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF7CF0A8)))
        Spacer(Modifier.width(8.dp))
        val t = vm.lastUpdated
        Text(
            when {
                vm.scanning -> "Reading SMS…"
                t != null -> "Reading new SMS automatically · updated ${t.format(HM).lowercase()}"
                else -> "Reading new SMS automatically"
            }, fontSize = 12.sp, color = Color(0xFFDDE2FF),
        )
    }
}

// ---------------------------------------------------------------- shared pieces
@Composable
private fun CardBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White)
            .border(1.dp, Line, RoundedCornerShape(16.dp)).padding(16.dp),
        content = content,
    )
}

@Composable
private fun Dot(c: Color) = Box(Modifier.size(8.dp).clip(CircleShape).background(c))

/** Debits / Credits total tile; tinted orange or green. Clickable when used as a toggle. */
@Composable
private fun SideTile(
    side: Direction, amount: Double, count: Int, modifier: Modifier,
    selected: Boolean = false, onClick: (() -> Unit)? = null,
) {
    val debit = side == Direction.DEBIT
    val color = if (debit) Spend else Credit
    val bg = if (debit) DebitTile else CreditTile
    val border = when {
        selected -> color
        debit -> DebitTileBorder
        else -> CreditTileBorder
    }
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(bg)
            .border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onClick != null) {
                if (selected) Icon(Icons.Default.Check, null, tint = color, modifier = Modifier.size(15.dp))
            } else Dot(color)
            if (selected || onClick == null) Spacer(Modifier.width(6.dp))
            Text(if (debit) "Debits" else "Credits", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = if (onClick != null) color else Muted)
        }
        Spacer(Modifier.height(4.dp))
        Text(inr(amount, paise = onClick != null), fontSize = 21.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        Text("$count transactions", fontSize = 12.sp, color = Muted)
    }
}

@Composable
private fun Note(text: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(NoteBg).padding(12.dp)) {
        Icon(Icons.Default.Info, null, tint = NoteFg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 13.sp, color = NoteFg, lineHeight = 18.sp)
    }
}

@Composable
private fun CategoryBars(title: String, totals: List<Pair<String, Double>>, colorOf: (String) -> Color) {
    if (totals.isEmpty()) return
    val sum = totals.sumOf { it.second }
    val max = totals.maxOf { it.second }
    CardBox {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Spacer(Modifier.height(12.dp))
        totals.forEach { (name, v) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(colorOf(name)))
                Spacer(Modifier.width(10.dp))
                Text(name, Modifier.weight(1f), fontSize = 14.sp, color = Ink)
                Text(inr(v, paise = false), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Text(" · ${"%.0f".format(if (sum > 0) v / sum * 100 else 0.0)}%", fontSize = 13.sp, color = Muted)
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.padding(start = 20.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFFEEF0F3))) {
                Box(Modifier.fillMaxWidth((v / max).toFloat().coerceAtLeast(0.015f)).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(colorOf(name)))
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private fun totals(rows: List<Row>) = rows.groupBy { it.category }.map { it.key to it.value.sumOf { r -> r.amount } }.sortedByDescending { it.second }

/** Tiles + category bars + card-bill note, for a month or a custom range. */
@Composable
private fun Summary(rows: List<Row>) {
    val debits = rows.filter { it.direction == Direction.DEBIT && it.counted }
    val credits = rows.filter { it.direction == Direction.CREDIT }
    val bills = rows.filter { it.kind == Kind.CARD_PAYMENT }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SideTile(Direction.DEBIT, debits.sumOf { it.amount }, debits.size, Modifier.weight(1f))
        SideTile(Direction.CREDIT, credits.sumOf { it.amount }, credits.size, Modifier.weight(1f))
    }
    if (rows.isEmpty()) Note("No transactions for this period yet.")
    CategoryBars("Debits by category", totals(debits), ::categoryColor)
    CategoryBars("Credits by type", totals(credits)) { Credit }
    if (bills.isNotEmpty()) {
        Note("${bills.size} credit card bill payment${if (bills.size > 1) "s" else ""} (${inr(bills.sumOf { it.amount }, false)}) " +
            "${if (bills.size > 1) "are" else "is"} listed but not added to debits.")
    }
}

// ---------------------------------------------------------------- 1. Overview
@Composable
fun OverviewScreen(vm: LedgerUi, onSettings: () -> Unit, onCustom: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header("Overview", vm, onCustom = onCustom, right = { SettingsButton(onSettings) })
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { Summary(vm.monthRows()) }
    }
}

// ---------------------------------------------------------------- 2 & 3. Over / Under ₹10k
data class RowActions(val onDelete: (Row) -> Unit, val onCategory: (Row) -> Unit, val onEdit: (Row) -> Unit)

@Composable
fun TxnListScreen(vm: LedgerUi, over: Boolean, actions: RowActions, onCustom: () -> Unit) {
    var side by rememberSaveable(over) { mutableStateOf(Direction.DEBIT) }
    val rows = vm.monthRows().filter { it.over10k == over }
    TxnList(rows, side, { side = it }, actions) {
        Header(if (over) "Over ₹10,000" else "Under ₹10,000", vm, onCustom = onCustom)
    }
}

/** Debits/Credits toggle and the chosen side's rows grouped by bank and account. Page tint follows the side. */
@Composable
private fun TxnList(
    rows: List<Row>, side: Direction, onSide: (Direction) -> Unit, actions: RowActions,
    extraTop: (@Composable () -> Unit)? = null, header: @Composable () -> Unit,
) {
    val debits = rows.filter { it.direction == Direction.DEBIT }
    val credits = rows.filter { it.direction == Direction.CREDIT }
    val shown = (if (side == Direction.DEBIT) debits else credits)
        .sortedWith(compareBy<Row>({ bankRank(it) }, { it.account }, { it.time }))
    LazyColumn(
        Modifier.fillMaxSize().background(if (side == Direction.DEBIT) DebitPage else CreditPage),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        item { header() }
        if (extraTop != null) item { Box(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) { extraTop() } }
        item {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SideTile(Direction.DEBIT, debits.filter { it.counted }.sumOf { it.amount }, debits.size, Modifier.weight(1f),
                    selected = side == Direction.DEBIT) { onSide(Direction.DEBIT) }
                SideTile(Direction.CREDIT, credits.filter { it.counted }.sumOf { it.amount }, credits.size, Modifier.weight(1f),
                    selected = side == Direction.CREDIT) { onSide(Direction.CREDIT) }
            }
        }
        if (shown.isEmpty()) item {
            Text("No ${if (side == Direction.DEBIT) "debits" else "credits"} here.", Modifier.padding(horizontal = 20.dp), color = Muted, fontSize = 14.sp)
        }
        for ((account, items) in shown.groupBy { it.account }) {
            item(key = "$side-$account") {
                Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                    Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                        Text(account.ifEmpty { "Other" }, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3A404C))
                        Text(inr(items.filter { it.counted }.sumOf { it.amount }), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Muted)
                    }
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, Line, RoundedCornerShape(16.dp))) {
                        items.forEachIndexed { i, r ->
                            if (i > 0) HorizontalDivider(color = Color(0xFFEEF0F3))
                            TxnRow(r, actions)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TxnRow(r: Row, a: RowActions) {
    val bill = r.kind == Kind.CARD_PAYMENT
    val color = when {
        bill -> Color(0xFF8B919C)
        r.direction == Direction.DEBIT -> Spend
        else -> Credit
    }
    Row(
        Modifier.then(if (r.manual) Modifier.clickable { a.onEdit(r) } else Modifier)
            .padding(start = 14.dp, end = 2.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(if (r.manual) r.party else displayName(r.party), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                color = if (bill) Muted else Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(r.time.format(DAY), fontSize = 12.sp, color = Muted, modifier = Modifier.align(Alignment.CenterVertically))
                if (bill) Pill("Card bill · not counted", Color(0xFFEEF0F3), Muted)
                else CategoryChip(r.category) { a.onCategory(r) }
                if (r.manual) Pill(if (r.account == "Cash") "Cash · tap to edit" else "Added by you · tap to edit", Color(0xFFE3F3E8), Color(0xFF1F6A3A))
                if (r.smsCount > 1) Pill("${r.smsCount} SMS → 1", NoteBg, NoteFg)
            }
        }
        Text((if (r.direction == Direction.DEBIT) "−" else "+") + inr(r.amount), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color)
        IconButton(onClick = { a.onDelete(r) }) {
            Icon(Icons.Default.Delete, contentDescription = "Delete ${displayName(r.party)}", tint = Color(0xFF9AA0AB), modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun Pill(text: String, bg: Color, fg: Color) {
    Text(text, Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp),
        fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = fg)
}

/** Category bubble in the category's own colour. */
@Composable
private fun CategoryChip(category: String, onClick: () -> Unit) {
    val c = categoryColor(category)
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(c.copy(alpha = 0.10f)).border(1.dp, c.copy(alpha = 0.25f), RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(category, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c)
        Icon(Icons.Default.ArrowDropDown, contentDescription = "Change category", tint = c, modifier = Modifier.size(16.dp))
    }
}

// ---------------------------------------------------------------- custom date range
@Composable
fun RangeScreen(vm: LedgerUi, from: LocalDate, to: LocalDate, actions: RowActions, onBack: () -> Unit) {
    var side by rememberSaveable { mutableStateOf(Direction.DEBIT) }
    val rows = vm.rowsBetween(from, to)
    TxnList(rows, side, { side = it }, actions, extraTop = {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val debits = rows.filter { it.direction == Direction.DEBIT && it.counted }
            CategoryBars("Debits by category", totals(debits).take(6), ::categoryColor)
        }
    }) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
                .background(Brush.verticalGradient(listOf(Brand, BrandDark))).statusBarsPadding().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.14f))) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("${from.format(DAY)} – ${to.format(FULL_DAY)}", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Custom dates · ${rows.size} transactions", fontSize = 12.sp, color = Color(0xFFDDE2FF))
            }
        }
    }
}

private fun LocalDate.toMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.toDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, date: LocalDate, onPick: (LocalDate) -> Unit, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF3A404C))
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, Color(0xFFD5D9E0), RoundedCornerShape(12.dp))
                .clickable { open = true }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(date.format(FULL_DAY), Modifier.weight(1f), fontSize = 15.sp, color = Ink)
            Icon(Icons.Default.DateRange, contentDescription = "Pick $label date", tint = Muted, modifier = Modifier.size(18.dp))
        }
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.toMillis())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { onPick(it.toDate()) }; open = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RangeSheet(onDismiss: () -> Unit, onShow: (LocalDate, LocalDate) -> Unit) {
    val today = LocalDate.now()
    var from by remember { mutableStateOf(today.withDayOfMonth(1)) }
    var to by remember { mutableStateOf(today) }
    var quick by remember { mutableStateOf("This month") }
    val quicks = listOf(
        "Last 7 days" to (today.minusDays(6) to today),
        "Last 30 days" to (today.minusDays(29) to today),
        "This month" to (today.withDayOfMonth(1) to today),
        "Last 3 months" to (today.minusMonths(3).plusDays(1) to today),
    )
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Custom dates", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                quicks.forEach { (name, r) ->
                    FilterChip(selected = quick == name, onClick = { quick = name; from = r.first; to = r.second }, label = { Text(name) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BrandLight, selectedLabelColor = BrandDark))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DateField("From", from, { from = it; quick = "" }, Modifier.weight(1f))
                DateField("To", to, { to = it; quick = "" }, Modifier.weight(1f))
            }
            if (from.isAfter(to)) Text("“From” must be on or before “To”.", color = Color(0xFFC42B1C), fontSize = 13.sp)
            Button(
                onClick = { onShow(from, to) }, enabled = !from.isAfter(to), modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Brand), shape = RoundedCornerShape(14.dp),
            ) { Text("Show transactions") }
        }
    }
}

// ---------------------------------------------------------------- add / edit a manual transaction
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntrySheet(existing: Row?, accounts: List<String>, onDismiss: () -> Unit, onSave: (ManualEntry) -> Unit) {
    var dir by remember { mutableStateOf(existing?.direction ?: Direction.DEBIT) }
    var amountText by remember { mutableStateOf(existing?.amount?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "") }
    var date by remember { mutableStateOf(existing?.time?.toLocalDate() ?: LocalDate.now()) }
    var account by remember { mutableStateOf(existing?.account ?: "Cash") }
    var desc by remember { mutableStateOf(existing?.party ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: "Other") }
    fun catsFor(d: Direction) = if (d == Direction.DEBIT) DEBIT_CATEGORIES.filter { it != "Card bill payment" } else CREDIT_CATEGORIES
    val cats = catsFor(dir)
    val amount = amountText.replace(",", "").toDoubleOrNull()
    val valid = amount != null && amount > 0 && desc.isNotBlank()

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(if (existing == null) "Add transaction" else "Edit transaction", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFEEF0F3)).padding(4.dp)) {
                listOf(Direction.DEBIT to "Debit (money out)", Direction.CREDIT to "Credit (money in)").forEach { (d, label) ->
                    val on = dir == d
                    Box(
                        Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(9.dp)).background(if (on) Color.White else Color.Transparent)
                            .clickable {
                                dir = d
                                if (category !in catsFor(d)) category = if (d == Direction.DEBIT) "Other" else "Other credit"
                            }, contentAlignment = Alignment.Center,
                    ) {
                        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            color = if (!on) Muted else if (d == Direction.DEBIT) Spend else Credit)
                    }
                }
            }
            OutlinedTextField(
                value = amountText, onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Amount") }, prefix = { Text("₹ ") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = LocalTextStyle.current.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                DateField("Date", date, { date = it }, Modifier.weight(1f))
                Column(Modifier.weight(1f)) {
                    Text(if (dir == Direction.DEBIT) "Paid with" else "Received in", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF3A404C))
                    Spacer(Modifier.height(6.dp))
                    SimpleDropdown(account, listOf("Cash") + accounts) { account = it }
                }
            }
            OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), placeholder = { Text("e.g. Vegetable market") })
            Column {
                Text("Category", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF3A404C))
                Spacer(Modifier.height(6.dp))
                SimpleDropdown(category, cats) { category = it }
            }
            if (amount != null) {
                Text(
                    if (amount > 10_000) "Over ₹10,000, so it will show on Over ₹10k." else "₹10,000 or less, so it will show on Under ₹10k.",
                    fontSize = 12.sp, color = Muted,
                )
            }
            Button(
                enabled = valid,
                onClick = {
                    val time = if (existing != null && existing.time.toLocalDate() == date) existing.time else date.atTime(12, 0)
                    onSave(ManualEntry(existing?.id ?: "m-" + UUID.randomUUID(), time, amount!!, dir, account, desc.trim(), category))
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Brand), shape = RoundedCornerShape(14.dp),
            ) { Text(if (existing == null) "Save transaction" else "Save changes") }
        }
    }
}

@Composable
private fun SimpleDropdown(value: String, options: List<String>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, Color(0xFFD5D9E0), RoundedCornerShape(12.dp))
                .clickable { open = true }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(value, Modifier.weight(1f), fontSize = 15.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Muted)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { open = false; onPick(o) }) }
        }
    }
}

// ---------------------------------------------------------------- category picker
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySheet(row: Row, onDismiss: () -> Unit, onSave: (String, Boolean) -> Unit) {
    val options = if (row.direction == Direction.DEBIT) DEBIT_CATEGORIES.filter { it != "Card bill payment" } else CREDIT_CATEGORIES
    var picked by remember { mutableStateOf(row.category) }
    var forAll by remember { mutableStateOf(!row.manual) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text("Change category", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("${if (row.manual) row.party else displayName(row.party)} · ${row.time.format(DAY)} · ${inr(row.amount)}", fontSize = 13.sp, color = Muted)
            Spacer(Modifier.height(12.dp))
            options.chunked(2).forEach { pair ->
                Row {
                    pair.forEach { c ->
                        Row(Modifier.weight(1f).heightIn(min = 44.dp).clickable { picked = c }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = picked == c, onClick = { picked = c },
                                colors = RadioButtonDefaults.colors(selectedColor = categoryColor(c)))
                            Text(c, fontSize = 14.sp, color = Ink)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            if (!row.manual) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ground).clickable { forAll = !forAll }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = forAll, onCheckedChange = { forAll = it })
                    Text("Use this category for all ${displayName(row.party)} transactions", fontSize = 14.sp, color = Ink)
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onSave(picked, forAll && !row.manual) }, modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Brand), shape = RoundedCornerShape(14.dp),
            ) { Text("Save") }
        }
    }
}

// ---------------------------------------------------------------- 4. Dashboard
@Composable
fun DashboardScreen(vm: LedgerUi) {
    val perMonth = (1..12).associateWith { m ->
        vm.rows.filter { it.time.year == vm.year && it.time.monthValue == m && it.direction == Direction.DEBIT && it.counted }.sumOf { it.amount }
    }
    val tracked = perMonth.filterValues { it > 0 }
    val total = tracked.values.sum()
    val max = tracked.values.maxOrNull() ?: 0.0
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header("Dashboard", vm, showMonth = false)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Debits in ${vm.year}", inr(total, false), "${tracked.size} months with data", Spend, Modifier.weight(1f))
                StatCard("Monthly average", inr(if (tracked.isEmpty()) 0.0 else total / tracked.size, false), "across those months", Ink, Modifier.weight(1f))
            }
            CardBox {
                Text("Debits per month", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth().height(190.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
                    (1..12).forEach { m ->
                        val v = perMonth.getValue(m)
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                            if (v > 0) {
                                Text("%.1fL".format(v / 100000), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1)
                                Spacer(Modifier.height(3.dp))
                                Box(Modifier.fillMaxWidth().height((150 * v / max).dp.coerceAtLeast(3.dp))
                                    .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)).background(Spend))
                            } else Box(Modifier.fillMaxWidth().height(3.dp).background(Line))
                        }
                    }
                }
                HorizontalDivider(color = Color(0xFFD5D9E0))
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Month.entries.forEach { Text(it.name.take(1), Modifier.weight(1f), fontSize = 11.sp, color = Muted, textAlign = TextAlign.Center) }
                }
                Spacer(Modifier.height(10.dp))
                Text("Card bill payments are not included. Tracking starts 1 Aug 2026.", fontSize = 12.sp, color = Muted)
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, Line, RoundedCornerShape(16.dp))) {
                (12 downTo 1).forEachIndexed { i, m ->
                    if (i > 0) HorizontalDivider(color = Color(0xFFEEF0F3))
                    val v = perMonth.getValue(m)
                    Row(Modifier.fillMaxWidth().clickable { vm.month = m }.padding(horizontal = 14.dp, vertical = 14.dp)) {
                        Text(Month.of(m).getDisplayName(TextStyle.FULL, Locale.ENGLISH), Modifier.weight(1f), fontSize = 14.sp, color = Ink)
                        if (v > 0) Text(inr(v, false), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Spend)
                        else Text(if (vm.year == 2026 && m < 8) "Before tracking" else "No data", fontSize = 13.sp, color = Muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, sub: String, color: Color, modifier: Modifier) {
    CardBox(modifier) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Muted)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        Text(sub, fontSize = 12.sp, color = Muted)
    }
}
