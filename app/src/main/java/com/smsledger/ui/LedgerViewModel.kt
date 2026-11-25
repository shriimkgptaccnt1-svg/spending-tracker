package com.smsledger.ui

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.smsledger.data.Row
import com.smsledger.data.SmsScanner
import com.smsledger.data.Store
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.Executors

/** What the screens read. Implemented by the real ViewModel and by screenshot tests. */
interface LedgerUi {
    val rows: List<Row>
    var year: Int
    var month: Int
    val lastUpdated: LocalDateTime?
    val scanning: Boolean

    fun monthRows(): List<Row> = rows.filter { it.time.year == year && it.time.monthValue == month }

    fun yearsAvailable(): List<Int> {
        val now = LocalDate.now().year
        val first = minOf(2026, rows.minOfOrNull { it.time.year } ?: now)
        return (first..maxOf(now, 2026)).toList()
    }
}

class LedgerViewModel(app: Application) : AndroidViewModel(app), LedgerUi {
    private val store = Store(app)
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    override var rows by mutableStateOf<List<Row>>(emptyList()); private set
    override var year by mutableStateOf(LocalDate.now().year)
    override var month by mutableStateOf(LocalDate.now().monthValue)
    override var lastUpdated by mutableStateOf<LocalDateTime?>(null); private set
    override var scanning by mutableStateOf(false); private set
    var ownerName by mutableStateOf(""); private set

    init {
        io.execute { val n = store.meta("owner_name") ?: ""; main.post { ownerName = n } }
    }

    /** Reads the inbox (if [rescan]), saves new transactions and reloads what is shown. */
    fun refresh(rescan: Boolean = true) {
        if (rescan) scanning = true
        io.execute {
            if (rescan) SmsScanner.scan(getApplication())
            val r = store.rows()
            val now = LocalDateTime.now()
            main.post {
                rows = r
                if (rescan) { lastUpdated = now; scanning = false }
            }
        }
    }

    fun delete(row: Row) { io.execute { store.delete(row.id) }; refresh(rescan = false) }
    fun undo(row: Row) { io.execute { store.undelete(row.id) }; refresh(rescan = false) }

    fun setCategory(row: Row, category: String, forMerchant: Boolean) {
        io.execute { store.setCategory(row, category, forMerchant) }
        refresh(rescan = false)
    }

    /** Your name as banks write it; only used to suggest "Own account (likely)". Re-reads the SMS. */
    fun saveOwnerName(name: String) {
        ownerName = name.trim()
        io.execute { store.setMeta("owner_name", name.trim()) }
        refresh()
    }

    fun clearAll() {
        io.execute { store.clearAll() }
        refresh(rescan = false)
    }

    override fun onCleared() {
        store.close()
        io.shutdown()
    }
}
