package com.shogun.speedtest.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shogun.speedtest.debug.DebugLogEntry
import com.shogun.speedtest.debug.HiddenRadioDebugLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PanelCardColor = Color(0xFF1A1A1A)
private val PanelTextPrimary = Color(0xFFFFFFFF)
private val PanelTextSecondary = Color(0xFF9E9E9E)
private val PanelAccentBlue = Color(0xFF2196F3)
private val PanelAccentRed = Color(0xFFFF8A80)

@Composable
fun HiddenRadioDebugPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val entries = HiddenRadioDebugLog.entries
    val lastException = HiddenRadioDebugLog.lastException
    val rawDump = HiddenRadioDebugLog.lastRawDump ?: "未取得"
    val formatter = SimpleDateFormat("HH:mm:ss", Locale.JAPAN)
    val clipboardText = buildString {
        appendLine("[HiddenRadioDebugLog]")
        if (entries.isEmpty()) {
            appendLine("entries=0")
        } else {
            entries.forEach { entry ->
                appendLine(formatEntry(entry, formatter))
                entry.stacktrace?.let {
                    appendLine(it)
                }
            }
        }
        appendLine()
        appendLine("[LastException]")
        appendLine(lastException?.let { formatException(it, formatter) } ?: "none")
        appendLine()
        appendLine("[LastRawDump]")
        append(rawDump)
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = PanelCardColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Hidden Radio Debug",
                    color = PanelTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Button(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("hidden-radio-debug", clipboardText))
                }) {
                    Text("ログをコピー", fontSize = 11.sp)
                }
            }

            SectionCard(title = "直近ログ") {
                if (entries.isEmpty()) {
                    Text("ログなし", color = PanelTextSecondary, fontSize = 12.sp)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(entries.takeLast(20).reversed()) { entry ->
                            Surface(
                                color = Color(0xFF121212),
                                shape = MaterialTheme.shapes.medium,
                                tonalElevation = 0.dp
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                                    Text(
                                        text = formatEntry(entry, formatter),
                                        color = PanelTextPrimary,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    entry.stacktrace?.let {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = it,
                                            color = PanelAccentRed,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            SectionCard(title = "最後の例外") {
                Text(
                    text = lastException?.let { formatException(it, formatter) } ?: "例外なし",
                    color = if (lastException == null) PanelTextSecondary else PanelAccentRed,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            SectionCard(title = "PhysicalChannelConfig dump") {
                Text(
                    text = rawDump,
                    color = PanelTextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Text(
                text = "beta debug build 専用表示。個体識別子は伏字化済み。",
                color = PanelAccentBlue,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            color = PanelTextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Surface(
            color = Color(0xFF121212),
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 0.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                content()
            }
        }
    }
}

private fun formatEntry(entry: DebugLogEntry, formatter: SimpleDateFormat): String {
    return "${formatter.format(Date(entry.timestamp))} ${entry.event} ${entry.detail}"
}

private fun formatException(entry: DebugLogEntry, formatter: SimpleDateFormat): String {
    return buildString {
        append(formatEntry(entry, formatter))
        entry.stacktrace?.let {
            append('\n')
            append(it)
        }
    }
}
