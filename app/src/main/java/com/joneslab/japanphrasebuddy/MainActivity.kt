package com.joneslab.japanphrasebuddy

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.icu.text.Transliterator
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joneslab.japanphrasebuddy.ui.theme.JapanPhraseBuddyTheme
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.JAPAN)
                isTtsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }

        setContent {
            JapanPhraseBuddyTheme {
                JapanPhraseBuddyApp(
                    onSpeak = { text ->
                        if (isTtsReady) {
                            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "japanese-phrase")
                        } else {
                            Toast.makeText(this, "日文語音尚未準備好", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }
}

data class PhraseRecord(
    val chinese: String,
    val japanese: String,
    val createdAt: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JapanPhraseBuddyApp(onSpeak: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("phrases", Context.MODE_PRIVATE) }
    var chineseText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var records by remember { mutableStateOf(loadPhraseRecords(prefs)) }
    var translatingIds by remember { mutableStateOf(emptySet<Long>()) }
    var showImportDialog by remember { mutableStateOf(false) }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.toTraditionalChinese()
                .orEmpty()
            if (spokenText.isNotBlank()) {
                chineseText = spokenText
                message = "已填入語音辨識文字"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Japan Phrase Buddy")
                        Text(
                            text = "中文現場轉自然日文",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .imePadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                InputPanel(
                    chineseText = chineseText,
                    message = message,
                    onChineseChange = { chineseText = it },
                    onMicClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                            )
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-Hant-TW")
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "zh-Hant-TW")
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "請用中文說出想轉成日文的內容")
                        }
                        speechLauncher.launch(intent)
                    },
                    onAddClick = {
                        val input = chineseText.trim()
                        if (input.isEmpty()) {
                            message = "先輸入或說一句中文"
                            return@InputPanel
                        }
                        val newRecord = PhraseRecord(input, "")
                        val updatedRecords = listOf(newRecord) + records
                        records = updatedRecords
                        savePhraseRecords(prefs, updatedRecords)
                        chineseText = ""
                        message = "已新增小卡，之後可再翻譯"
                    },
                )
            }

            if (records.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "小卡清單",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = {
                                    copyText(
                                        context = context,
                                        text = buildLlmExportPrompt(records),
                                        label = "Japan Phrase Buddy export"
                                    )
                                    message = "已複製匯出 prompt"
                                }
                            ) {
                                Icon(Icons.Default.FileDownload, contentDescription = null)
                                Text("匯出")
                            }
                            TextButton(onClick = { showImportDialog = true }) {
                                Icon(Icons.Default.FileUpload, contentDescription = null)
                                Text("匯入")
                            }
                        }
                    }
                }

                itemsIndexed(records, key = { _, record -> record.createdAt }) { index, record ->
                    PhraseRecordCard(
                        record = record,
                        index = index,
                        lastIndex = records.lastIndex,
                        isTranslating = record.createdAt in translatingIds,
                        onSelect = {
                            chineseText = record.chinese
                            message = "已載入中文，可重新產生日文"
                        },
                        onSpeak = {
                            if (record.japanese.isBlank()) {
                                message = "這張小卡還沒有日文"
                            } else {
                                onSpeak(record.japanese)
                            }
                        },
                        onCopy = {
                            if (record.japanese.isBlank()) {
                                message = "這張小卡還沒有日文"
                            } else {
                                copyText(context, record.japanese)
                                message = "已複製日文"
                            }
                        },
                        onDelete = {
                            val updatedRecords = records.filterNot { it.createdAt == record.createdAt }
                            records = updatedRecords
                            savePhraseRecords(prefs, updatedRecords)
                            message = "已刪除小卡"
                        },
                        onMove = { fromIndex, toIndex ->
                            val updatedRecords = records.move(fromIndex, toIndex)
                            records = updatedRecords
                            savePhraseRecords(prefs, updatedRecords)
                        },
                        onTranslate = {
                            scope.launch {
                                translatingIds = translatingIds + record.createdAt
                                message = "Gemini 正在翻譯這張小卡..."
                                val result = generateJapanesePhrase(record.chinese)
                                translatingIds = translatingIds - record.createdAt
                                result
                                    .onSuccess { japanese ->
                                        val updatedRecords = records.map {
                                            if (it.createdAt == record.createdAt) {
                                                it.copy(japanese = japanese)
                                            } else {
                                                it
                                            }
                                        }
                                        records = updatedRecords
                                        savePhraseRecords(prefs, updatedRecords)
                                        message = "已補上日文"
                                    }
                                    .onFailure { error ->
                                        message = error.message ?: "產生日文失敗"
                                    }
                            }
                        }
                    )
                }
            }
        }
    }

    if (showImportDialog) {
        ImportJsonDialog(
            onDismiss = { showImportDialog = false },
            onImport = { jsonText ->
                importTranslatedRecords(jsonText, records)
                    .onSuccess { result ->
                        records = result.records
                        savePhraseRecords(prefs, result.records)
                        showImportDialog = false
                        message = "已更新 ${result.updatedCount} 張小卡"
                    }
                    .onFailure { error ->
                        message = error.message ?: "匯入失敗"
                    }
            }
        )
    }
}

@Composable
private fun InputPanel(
    chineseText: String,
    message: String,
    onChineseChange: (String) -> Unit,
    onMicClick: () -> Unit,
    onAddClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = chineseText,
                onValueChange = onChineseChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("新增小卡") },
                placeholder = { Text("例如：請問這班車會到東京車站嗎？") },
                minLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onMicClick) {
                    Icon(Icons.Default.Mic, contentDescription = "語音輸入")
                }
                Button(
                    onClick = onAddClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("新增小卡")
                }
            }

            if (message.isNotBlank()) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun PhraseRecordCard(
    record: PhraseRecord,
    index: Int,
    lastIndex: Int,
    isTranslating: Boolean,
    onSelect: () -> Unit,
    onSpeak: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    onTranslate: () -> Unit
) {
    val dragThreshold = with(LocalDensity.current) { 72.dp.toPx() }
    var dragOffset by remember(record.createdAt) { mutableStateOf(0f) }
    var dragIndex by remember(record.createdAt) { mutableStateOf(index) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        onClick = onSelect
    ) {
        Row(
            modifier = Modifier.padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .pointerInput(record.createdAt, index, lastIndex) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                dragOffset = 0f
                                dragIndex = index
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                dragOffset += dragAmount

                                if (dragOffset > dragThreshold && dragIndex < lastIndex) {
                                    onMove(dragIndex, dragIndex + 1)
                                    dragIndex += 1
                                    dragOffset = 0f
                                } else if (dragOffset < -dragThreshold && dragIndex > 0) {
                                    onMove(dragIndex, dragIndex - 1)
                                    dragIndex -= 1
                                    dragOffset = 0f
                                }
                            },
                            onDragEnd = {
                                dragOffset = 0f
                            },
                            onDragCancel = {
                                dragOffset = 0f
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "拖曳排序",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = record.chinese,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = record.japanese.ifBlank { "尚未翻譯" },
                    fontSize = 22.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (record.japanese.isBlank()) {
                        MaterialTheme.colorScheme.outline
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                if (isTranslating) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            text = "Gemini 正在整理成自然日文...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(
                        onClick = onSpeak,
                        enabled = record.japanese.isNotBlank() && !isTranslating
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Text("播放")
                    }
                    TextButton(
                        onClick = onCopy,
                        enabled = record.japanese.isNotBlank() && !isTranslating
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Text("複製")
                    }
                    TextButton(
                        onClick = onTranslate,
                        enabled = !isTranslating
                    ) {
                        Icon(Icons.Default.Translate, contentDescription = null)
                        Text(if (record.japanese.isBlank()) "翻譯" else "重翻")
                    }
                    TextButton(
                        onClick = onDelete,
                        enabled = !isTranslating
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Text("刪除")
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportJsonDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit
) {
    var jsonText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("匯入 LLM 翻譯結果") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "貼上 LLM 回傳的 JSON。App 會用 id 對應小卡，只補上 japanese 欄位。",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = jsonText,
                    onValueChange = { jsonText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("JSON") },
                    minLines = 8,
                    maxLines = 12
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(jsonText) },
                enabled = jsonText.isNotBlank()
            ) {
                Text("匯入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

private fun List<PhraseRecord>.move(fromIndex: Int, toIndex: Int): List<PhraseRecord> {
    if (fromIndex !in indices || toIndex !in indices || fromIndex == toIndex) return this
    return toMutableList().apply {
        add(toIndex, removeAt(fromIndex))
    }
}

private data class ImportResult(
    val records: List<PhraseRecord>,
    val updatedCount: Int
)

private fun buildLlmExportPrompt(records: List<PhraseRecord>): String {
    val exportArray = JSONArray()
    records.forEach { record ->
        exportArray.put(record.toJsonObject())
    }

    return """
        你是台灣旅人在日本旅行時的現場口譯助手。
        請把下方 JSON 中每個 chinese 翻成自然、禮貌、適合直接對日本店員、站務、飯店櫃檯或路人說的日文。
        可以稍微潤飾成更自然的日文，但不要新增中文沒有提到的事實。
        請保留 id、chinese 和順序，只填寫或更新 japanese。
        只回傳合法 JSON 陣列，不要 Markdown，不要解釋，不要包在 ``` 裡。

        JSON:
        ${exportArray.toString(2)}
    """.trimIndent()
}

private fun importTranslatedRecords(
    jsonText: String,
    currentRecords: List<PhraseRecord>
): Result<ImportResult> = runCatching {
    val importedArray = JSONArray(extractJsonArrayText(jsonText))
    val importedJapaneseById = mutableMapOf<Long, String>()

    for (index in 0 until importedArray.length()) {
        val item = importedArray.getJSONObject(index)
        val id = item.optLong("id", Long.MIN_VALUE)
        val japanese = item.optString("japanese").trim()
        if (id != Long.MIN_VALUE && japanese.isNotBlank()) {
            importedJapaneseById[id] = japanese.trim('"')
        }
    }

    require(importedJapaneseById.isNotEmpty()) {
        "找不到可匯入的 japanese 結果"
    }

    var updatedCount = 0
    val updatedRecords = currentRecords.map { record ->
        val importedJapanese = importedJapaneseById[record.createdAt]
        if (importedJapanese.isNullOrBlank()) {
            record
        } else {
            updatedCount += 1
            record.copy(japanese = importedJapanese)
        }
    }

    require(updatedCount > 0) {
        "JSON 裡的 id 沒有對應到目前小卡"
    }

    ImportResult(updatedRecords, updatedCount)
}

private fun extractJsonArrayText(rawText: String): String {
    val trimmed = rawText.trim()
        .removePrefix("```json")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()
    val start = trimmed.indexOf('[')
    val end = trimmed.lastIndexOf(']')
    require(start >= 0 && end > start) {
        "請貼上 JSON 陣列"
    }
    return trimmed.substring(start, end + 1)
}

private fun PhraseRecord.toJsonObject(): JSONObject =
    JSONObject()
        .put("id", createdAt)
        .put("chinese", chinese)
        .put("japanese", japanese)

private fun String.toTraditionalChinese(): String =
    Transliterator.getInstance("Simplified-Traditional").transliterate(this)

private suspend fun generateJapanesePhrase(chineseText: String): Result<String> =
    withContext(Dispatchers.IO) {
        runCatching {
            val apiKey = BuildConfig.GEMINI_API_KEY
            require(apiKey.isNotBlank()) {
                "local.properties 尚未設定 GEMINI_API_KEY"
            }

            val prompt = """
                你是台灣旅人在日本旅行時的現場口譯助手。
                請把下面這句中文轉成自然、禮貌、適合直接對日本店員、站務、飯店櫃檯或路人說的日文。
                可以稍微潤飾成更自然的日文，但不要新增中文沒有提到的事實。
                只輸出日文句子本身，不要解釋，不要 Markdown，不要加引號。

                中文：$chineseText
            """.trimIndent()

            val requestBody = JSONObject()
                .put(
                    "contents",
                    JSONArray().put(
                        JSONObject().put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", prompt))
                        )
                    )
                )
                .put(
                    "generationConfig",
                    JSONObject()
                        .put("temperature", 0.35)
                        .put("maxOutputTokens", 256)
                )
                .toString()

            val url = URL(
                "https://generativelanguage.googleapis.com/v1beta/models/" +
                    "gemini-2.5-flash-lite:generateContent?key=$apiKey"
            )
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connectTimeout = 15_000
                readTimeout = 30_000
                doOutput = true
            }

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(requestBody)
            }

            val responseCode = connection.responseCode
            val responseText = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }

            connection.disconnect()

            if (responseCode !in 200..299) {
                error("Gemini API 失敗：HTTP $responseCode")
            }

            JSONObject(responseText)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()
                .trim('"')
                .ifBlank { error("Gemini 沒有回傳日文") }
        }
    }

private fun copyText(
    context: Context,
    text: String,
    label: String = "Japanese phrase"
) {
    if (text.isBlank()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "已複製", Toast.LENGTH_SHORT).show()
}

private fun loadPhraseRecords(prefs: android.content.SharedPreferences): List<PhraseRecord> {
    val raw = prefs.getString("recent_phrases", "[]").orEmpty()
    return runCatching {
        val array = JSONArray(raw)
        List(array.length()) { index ->
            val item = array.getJSONObject(index)
            PhraseRecord(
                chinese = item.getString("chinese"),
                japanese = item.getString("japanese"),
                createdAt = item.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }.getOrDefault(emptyList())
}

private fun savePhraseRecords(
    prefs: android.content.SharedPreferences,
    records: List<PhraseRecord>
) {
    val array = JSONArray()
    records.forEach { record ->
        array.put(
            JSONObject()
                .put("chinese", record.chinese)
                .put("japanese", record.japanese)
                .put("createdAt", record.createdAt)
        )
    }
    prefs.edit().putString("recent_phrases", array.toString()).apply()
}

@Preview(showBackground = true)
@Composable
fun JapanPhraseBuddyPreview() {
    JapanPhraseBuddyTheme {
        Box(Modifier.fillMaxSize()) {
            InputPanel(
                chineseText = "請問這班車會到東京車站嗎？",
                message = "會先新增成小卡，之後再翻譯",
                onChineseChange = {},
                onMicClick = {},
                onAddClick = {}
            )
        }
    }
}
