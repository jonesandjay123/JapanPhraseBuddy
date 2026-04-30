package com.joneslab.japanphrasebuddy

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
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
    var japaneseText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var records by remember { mutableStateOf(loadPhraseRecords(prefs)) }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
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
                    japaneseText = japaneseText,
                    message = message,
                    isGenerating = isGenerating,
                    onChineseChange = { chineseText = it },
                    onMicClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                            )
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-TW")
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "請用中文說出想轉成日文的內容")
                        }
                        speechLauncher.launch(intent)
                    },
                    onGenerateClick = {
                        val input = chineseText.trim()
                        if (input.isEmpty()) {
                            message = "先輸入或說一句中文"
                            return@InputPanel
                        }
                        scope.launch {
                            isGenerating = true
                            message = "Gemini 正在整理成日文..."
                            val result = generateJapanesePhrase(input)
                            isGenerating = false
                            result
                                .onSuccess { japanese ->
                                    japaneseText = japanese
                                    val updatedRecords = (listOf(PhraseRecord(input, japanese)) + records)
                                        .distinctBy { it.chinese.trim() to it.japanese.trim() }
                                        .take(6)
                                    records = updatedRecords
                                    savePhraseRecords(prefs, updatedRecords)
                                    message = "完成，可以播放或複製"
                                }
                                .onFailure { error ->
                                    message = error.message ?: "產生日文失敗"
                                }
                        }
                    },
                    onSpeak = { onSpeak(japaneseText) },
                    onCopy = {
                        copyText(context, japaneseText)
                        message = "已複製日文"
                    }
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
                            text = "最近使用",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        TextButton(
                            onClick = {
                                records = emptyList()
                                savePhraseRecords(prefs, records)
                                message = "已清空最近紀錄"
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Text("清空")
                        }
                    }
                }

                items(records, key = { it.createdAt }) { record ->
                    PhraseRecordCard(
                        record = record,
                        onSelect = {
                            chineseText = record.chinese
                            japaneseText = record.japanese
                            message = "已載入這句"
                        },
                        onSpeak = { onSpeak(record.japanese) },
                        onCopy = {
                            copyText(context, record.japanese)
                            message = "已複製日文"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun InputPanel(
    chineseText: String,
    japaneseText: String,
    message: String,
    isGenerating: Boolean,
    onChineseChange: (String) -> Unit,
    onMicClick: () -> Unit,
    onGenerateClick: () -> Unit,
    onSpeak: () -> Unit,
    onCopy: () -> Unit
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
                label = { Text("你想跟對方說什麼") },
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
                    onClick = onGenerateClick,
                    enabled = !isGenerating,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.padding(4.dp))
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                    }
                    Text("產生日文")
                }
            }

            if (message.isNotBlank()) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            if (japaneseText.isNotBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = japaneseText,
                            fontSize = 26.sp,
                            lineHeight = 34.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onSpeak) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Text("播放")
                            }
                            Button(onClick = onCopy) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null)
                                Text("複製")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhraseRecordCard(
    record: PhraseRecord,
    onSelect: () -> Unit,
    onSpeak: () -> Unit,
    onCopy: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        onClick = onSelect
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = record.chinese,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = record.japanese,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = onSpeak) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text("播放")
                }
                TextButton(onClick = onCopy) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Text("複製")
                }
            }
        }
    }
}

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
                    "gemini-3-flash-preview:generateContent?key=$apiKey"
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

private fun copyText(context: Context, text: String) {
    if (text.isBlank()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Japanese phrase", text))
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
                japaneseText = "すみません、この電車は東京駅まで行きますか？",
                message = "完成，可以播放或複製",
                isGenerating = false,
                onChineseChange = {},
                onMicClick = {},
                onGenerateClick = {},
                onSpeak = {},
                onCopy = {}
            )
        }
    }
}
