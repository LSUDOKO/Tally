package app.tally.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Receipts as a CSV anyone can open in a spreadsheet, with the explorer link per row. */
object Export {
    fun receiptsCsv(entries: List<LogEntry>, explorer: (String) -> String): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val rows = entries.map { e ->
            listOf(fmt.format(Date(e.at)), e.title, e.merchant, "%.2f".format(Locale.US, e.amountMicro / 1e6), e.outcome, if (e.disputed) "disputed" else "", e.signature?.let(explorer) ?: "", e.detail)
        }
        return (listOf(listOf("date", "item", "counter", "usdc", "outcome", "flags", "transaction", "detail")) + rows)
            .joinToString("\n") { row -> row.joinToString(",") { "\"" + it.replace("\"", "\"\"") + "\"" } }
    }

    /** Writes the CSV to app-private cache and opens the share sheet for it. */
    fun share(context: Context, csv: String, name: String = "tally-receipts.csv") {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, name).apply { writeText(csv) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
        val send = Intent(Intent.ACTION_SEND).setType("text/csv").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Export receipts"))
    }
}
