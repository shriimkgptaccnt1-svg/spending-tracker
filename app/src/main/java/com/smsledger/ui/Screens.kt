package com.smsledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smsledger.data.Row
import com.smsledger.parser.CREDIT_CATEGORIES
import com.smsledger.parser.DEBIT_CATEGORIES
import com.smsledger.parser.Direction
import com.smsledger.parser.Kind
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val DAY = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH)
private val HM = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private val BANK_ORDER = listOf("DCB", "HDFC", "IndusInd", "IOB", "Pluxee", "SBI", "SBI Card")

// ---------------------------------------------------------------- shared pieces
@Composable
fun Title(text: String) = Text(text, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink)

@Composable
private fun PickerBox(label: String, value: String, options: List<String>, onPick: (Int) -> Unit, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Muted, letterSpacing = 0.5.sp)
        Spacer(Modifier.height(4.dp))
        Box {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                    .background(Color.White).border(1.dp, Color(0xFFD5D9E0), RoundedCornerShape(12.dp))
                    .clickable { open = true }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(value, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Choose $label", tint = Ink)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEachIndexed { i, o -> DropdownMenuItem(text = { Text(o) }, onClick = { open = false; onPick(i) }) }
            }
        }
    }
}

@Composable
fun MonthYearPickers(vm: LedgerUi, showMonth: Boolean = true) {
    val months = Month.entries.map { it.getDisplayName(TextStyle.FULL, Locale.ENGLISH) }
    val years = vm.yearsAvailable()
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (showMonth) PickerBox("Month", months[vm.month - 1], months, { vm.month = it + 1 }, Modifier.weight(1.4f))
        PickerBox("Year", vm.year.toString(), years.map { it.toString() }, { vm.year = years[it] }, Modifier.weight(1f))
    }
}

@Composable
fun LiveLine(vm: LedgerUi) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF1F8A4C)))
        Spacer(Modifier.width(8.dp))
        val t = vm.lastUpdated
        Text(
            when {
                vm.scanning -> "Reading SMS…"
                t != null -> "Reading new SMS automatically · updated ${t.format(HM).lowercase()}"
                else -> "Reading new SMS automatically"
            }, fontSize = 12.sp, color = Muted,
        )
    }
}

@Composable
private fun CardBox(modifier: Modifier = Modifier, bg: Color = Color.White, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bg)
            .border(1.dp, Line, RoundedCornerShape(16.dp)).padding(16.dp),
        content = content,
    )
}

@Composable
private fun Dot(c: Color) = Box(Modifier.size(8.dp).clip(CircleShape).background(c))

@Composable
private fun TotalCard(label: String, amount: Double, sub: String, color: Color, modifier: Modifier) {
    CardBox(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(color); Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Muted)
        }
        Spacer(Modifier.height(4.dp))
        Text(inr(amount, paise = false), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
        Text(sub, fontSize = 12.sp, color = Muted)
    }
}

@Composable
private fun Note(text: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(NoteBg).padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
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

// ---------------------------------------------------------------- 1. Overview
@Composable
fun OverviewScreen(vm: LedgerUi, onSettings: () -> Unit) {
    val rows = vm.monthRows()
    val debits = rows.filter { it.direction == Direction.DEBIT && it.counted }
    val credits = rows.filter { it.direction == Direction.CREDIT }
    val bills = rows.filter { it.kind == Kind.CARD_PAYMENT }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Title("Overview") }
            TextButton(onClick = onSettings) { Text("Settings", color = Ink) }
        }
        MonthYearPickers(vm)
        LiveLine(vm)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TotalCard("Debits", debits.sumOf { it.amount }, "${debits.size} transactions", Spend, Modifier.weight(1f))
            TotalCard("Credits", credits.sumOf { it.amount }, "${credits.size} transactions", Credit, Modifier.weight(1f))
        }
        if (bills.isNotEmpty()) {
            Note("${bills.size} credit card bill payment${if (bills.size > 1) "s" else ""} (${inr(bills.sumOf { it.amount }, false)}) listed but not added to debits.")
        }
        if (rows.isEmpty()) {
            Note("No transactions for this month yet.")
        }
        CategoryBars("Debits by category",
            debits.groupBy { it.category }.map { it.key to it.value.sumOf { r -> r.amount } }.sortedByDescending { it.second }, ::categoryColor)
        CategoryBars("Credits by type",
            credits.groupBy { it.category }.map { it.key to it.value.sumOf { r -> r.amount } }.sortedByDescending { it.second }) { Credit }
        Spacer(Modifier.height(8.dp))
    }
}

// ---------------------------------------------------------------- 2 & 3. Over / Under ₹10k
@Composable
fun TxnListScreen(vm: LedgerUi, over: Boolean, onDelete: (Row) -> Unit, onCategory: (Row) -> Unit) {
    val rows = vm.monthRows().filter { it.over10k == over }
    val sorted = rows.sortedWith(compareBy<Row>({ BANK_ORDER.indexOf(it.bank).let { i -> if (i < 0) 99 else i } }, { it.account }, { it.time }))
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Title(if (over) "Over ₹10,000" else "Under ₹10,000") }
        item { MonthYearPickers(vm) }
        item { LiveLine(vm) }
        if (over) item {
            Text("Sorted by bank and account to match your statements. Tap a category to change it, or the bin to delete.",
                fontSize = 13.sp, color = Muted, lineHeight = 18.sp)
        }
        for (dir in listOf(Direction.DEBIT, Direction.CREDIT)) {
            val list = sorted.filter { it.direction == dir }
            val color = if (dir == Direction.DEBIT) Spend else Credit
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Dot(color); Spacer(Modifier.width(8.dp))
                    Text(if (dir == Direction.DEBIT) "Debits" else "Credits", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    Text("  ${list.size}", fontSize = 13.sp, color = Muted, modifier = Modifier.weight(1f))
                    Text(inr(list.filter { it.counted }.sumOf { it.amount }), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
                }
            }
            if (list.isEmpty()) item { Text("None", fontSize = 14.sp, color = Muted) }
            val groups = list.groupBy { it.account }
            for ((account, items) in groups) {
                item(key = "$dir-$account") {
                    Column {
                        Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                            Text(account, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3A404C))
                            Text(inr(items.filter { it.counted }.sumOf { it.amount }), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Muted)
                        }
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, Line, RoundedCornerShape(16.dp))) {
                            items.forEachIndexed { i, r ->
                                if (i > 0) HorizontalDivider(color = Color(0xFFEEF0F3))
                                TxnRow(r, onDelete, onCategory)
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TxnRow(r: Row, onDelete: (Row) -> Unit, onCategory: (Row) -> Unit) {
    val bill = r.kind == Kind.CARD_PAYMENT
    val color = when {
        bill -> Color(0xFF8B919C)
        r.direction == Direction.DEBIT -> Spend
        else -> Credit
    }
    Row(Modifier.padding(start = 14.dp, end = 2.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(displayName(r.party), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (bill) Muted else Ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(r.time.format(DAY), fontSize = 12.sp, color = Muted, modifier = Modifier.align(Alignment.CenterVertically))
                if (bill) Pill("Card bill · not counted", Color(0xFFEEF0F3), Muted)
                else CategoryChip(r.category) { onCategory(r) }
                if (r.smsCount > 1) Pill("${r.smsCount} SMS → 1", NoteBg, NoteFg)
            }
        }
        Text((if (r.direction == Direction.DEBIT) "−" else "+") + inr(r.amount), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color)
        IconButton(onClick = { onDelete(r) }) {
            Icon(Icons.Default.Delete, contentDescription = "Delete ${displayName(r.party)}", tint = Color(0xFF9AA0AB), modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun Pill(text: String, bg: Color, fg: Color) {
    Text(text, Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp),
        fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = fg)
}

@Composable
private fun CategoryChip(category: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).border(1.dp, Color(0xFFD5D9E0), RoundedCornerShape(50)).clickable(onClick = onClick)
            .padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(category, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF3A404C))
        Icon(Icons.Default.ArrowDropDown, contentDescription = "Change category", tint = Color(0xFF3A404C), modifier = Modifier.size(16.dp))
    }
}

// ---------------------------------------------------------------- category picker
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySheet(row: Row, onDismiss: () -> Unit, onSave: (String, Boolean) -> Unit) {
    val options = if (row.direction == Direction.DEBIT) DEBIT_CATEGORIES.filter { it != "Card bill payment" } else CREDIT_CATEGORIES
    var picked by remember { mutableStateOf(row.category) }
    var forAll by remember { mutableStateOf(true) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text("Change category", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("${displayName(row.party)} · ${row.time.format(DAY)} · ${inr(row.amount)}", fontSize = 13.sp, color = Muted)
            Spacer(Modifier.height(12.dp))
            options.chunked(2).forEach { pair ->
                Row {
                    pair.forEach { c ->
                        Row(Modifier.weight(1f).heightIn(min = 44.dp).clickable { picked = c }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = picked == c, onClick = { picked = c })
                            Text(c, fontSize = 14.sp, color = Ink)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ground).clickable { forAll = !forAll }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = forAll, onCheckedChange = { forAll = it })
                Text("Use this category for all ${displayName(row.party)} transactions", fontSize = 14.sp, color = Ink)
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onSave(picked, forAll) }, modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink), shape = RoundedCornerShape(14.dp),
            ) { Text("Save") }
        }
    }
}

// ---------------------------------------------------------------- 4. Year dashboard
@Composable
fun YearScreen(vm: LedgerUi) {
    val perMonth = (1..12).associateWith { m ->
        vm.rows.filter { it.time.year == vm.year && it.time.monthValue == m && it.direction == Direction.DEBIT && it.counted }.sumOf { it.amount }
    }
    val tracked = perMonth.filterValues { it > 0 }
    val total = tracked.values.sum()
    val max = tracked.values.maxOrNull() ?: 0.0
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Title("${vm.year} debits")
        MonthYearPickers(vm, showMonth = false)
        LiveLine(vm)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TotalCard("Debits in ${vm.year}", total, "${tracked.size} months with SMS", Spend, Modifier.weight(1f))
            TotalCard("Monthly average", if (tracked.isEmpty()) 0.0 else total / tracked.size, "across those months", Ink, Modifier.weight(1f))
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
                            Box(Modifier.fillMaxWidth().height((150 * v / max).dp.coerceAtLeast(3.dp)).clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)).background(Spend))
                        } else {
                            Box(Modifier.fillMaxWidth().height(3.dp).background(Line))
                        }
                    }
                }
            }
            HorizontalDivider(color = Color(0xFFD5D9E0))
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Month.entries.forEach { Text(it.name.take(1), Modifier.weight(1f), fontSize = 11.sp, color = Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
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
                    else Text(if (vm.year == 2026 && m < 8) "Before tracking" else "No SMS", fontSize = 13.sp, color = Muted)
                }
            }
        }
    }
}
