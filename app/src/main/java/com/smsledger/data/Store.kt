package com.smsledger.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.smsledger.parser.Direction
import com.smsledger.parser.Kind
import com.smsledger.parser.Txn
import com.smsledger.parser.merchantKey
import java.time.LocalDateTime
import java.time.ZoneOffset

/** A transaction as stored and shown. No SMS text is ever stored. */
data class Row(
    val id: String,
    val time: LocalDateTime,
    val amount: Double,
    val direction: Direction,
    val account: String,
    val bank: String,
    val party: String,
    val kind: Kind,
    val category: String,
    val smsCount: Int,
) {
    val over10k get() = amount > 10_000.0
    val counted get() = kind != Kind.CARD_PAYMENT
}

/**
 * On-phone database:
 *  txn               - one row per transaction (details only, no SMS text)
 *  deleted           - row ids the user deleted (so re-scans never bring them back)
 *  row_category      - category the user picked for one row
 *  merchant_category - category the user picked for every row of a merchant
 */
class Store(context: Context) : SQLiteOpenHelper(context, "ledger.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE txn(id TEXT PRIMARY KEY, time INTEGER, amount REAL, direction TEXT, account TEXT,
               bank TEXT, party TEXT, kind TEXT, auto_category TEXT, sms_count INTEGER, merged_ids TEXT)"""
        )
        db.execSQL("CREATE TABLE deleted(id TEXT PRIMARY KEY)")
        db.execSQL("CREATE TABLE row_category(id TEXT PRIMARY KEY, category TEXT)")
        db.execSQL("CREATE TABLE merchant_category(merchant TEXT PRIMARY KEY, category TEXT)")
        db.execSQL("CREATE TABLE meta(k TEXT PRIMARY KEY, v TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    /** Saves parsed transactions. Rows that were later merged into another row are removed. */
    fun upsert(txns: List<Txn>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (t in txns) {
                db.insertWithOnConflict("txn", null, ContentValues().apply {
                    put("id", t.id)
                    put("time", t.time.toEpochSecond(ZoneOffset.UTC))
                    put("amount", t.amount)
                    put("direction", t.direction.name)
                    put("account", t.account)
                    put("bank", t.bank)
                    put("party", t.party)
                    put("kind", t.kind.name)
                    put("auto_category", t.category)
                    put("sms_count", t.smsCount)
                    put("merged_ids", t.merged.joinToString(",") { it.id })
                }, SQLiteDatabase.CONFLICT_REPLACE)
                for (m in t.merged) db.delete("txn", "id = ?", arrayOf(m.id))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun setMeta(k: String, v: String) {
        writableDatabase.insertWithOnConflict("meta", null, ContentValues().apply { put("k", k); put("v", v) },
            SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun meta(k: String): String? =
        readableDatabase.rawQuery("SELECT v FROM meta WHERE k = ?", arrayOf(k)).use { if (it.moveToFirst()) it.getString(0) else null }

    /** All rows the user has not deleted, with the user's categories applied. */
    fun rows(): List<Row> {
        val db = readableDatabase
        val deleted = db.rawQuery("SELECT id FROM deleted", null).use { c -> buildSet { while (c.moveToNext()) add(c.getString(0)) } }
        val rowCat = db.rawQuery("SELECT id, category FROM row_category", null)
            .use { c -> buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1)) } }
        val merchantCat = db.rawQuery("SELECT merchant, category FROM merchant_category", null)
            .use { c -> buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1)) } }
        val out = mutableListOf<Row>()
        db.rawQuery("SELECT id, time, amount, direction, account, bank, party, kind, auto_category, sms_count, merged_ids FROM txn", null).use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                val merged = c.getString(10).split(",").filter { it.isNotEmpty() }
                if (id in deleted || merged.any { it in deleted }) continue
                val party = c.getString(6)
                out += Row(
                    id = id,
                    time = LocalDateTime.ofEpochSecond(c.getLong(1), 0, ZoneOffset.UTC),
                    amount = c.getDouble(2),
                    direction = Direction.valueOf(c.getString(3)),
                    account = c.getString(4),
                    bank = c.getString(5),
                    party = party,
                    kind = Kind.valueOf(c.getString(7)),
                    category = rowCat[id] ?: merchantCat[merchantKey(party)] ?: c.getString(8),
                    smsCount = c.getInt(9),
                )
            }
        }
        return out
    }

    fun delete(id: String) {
        writableDatabase.insertWithOnConflict("deleted", null, ContentValues().apply { put("id", id) }, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun undelete(id: String) {
        writableDatabase.delete("deleted", "id = ?", arrayOf(id))
    }

    fun setCategory(row: Row, category: String, forMerchant: Boolean) {
        val db = writableDatabase
        if (forMerchant) {
            db.insertWithOnConflict("merchant_category", null,
                ContentValues().apply { put("merchant", merchantKey(row.party)); put("category", category) },
                SQLiteDatabase.CONFLICT_REPLACE)
            db.delete("row_category", "id = ?", arrayOf(row.id))
        } else {
            db.insertWithOnConflict("row_category", null,
                ContentValues().apply { put("id", row.id); put("category", category) }, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun clearAll() {
        val db = writableDatabase
        for (t in listOf("txn", "deleted", "row_category", "merchant_category", "meta")) db.delete(t, null, null)
    }
}
