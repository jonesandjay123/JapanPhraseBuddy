package com.joneslab.japanphrasebuddy.wear

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import java.util.Locale
import org.json.JSONArray

private const val PHRASE_PREFS_NAME = "phrases"
private const val RECENT_PHRASES_KEY = "recent_phrases"
private const val PHRASE_CARDS_PATH = "/phrase_cards"
private const val REQUEST_PHRASE_CARDS_PATH = "/request_phrase_cards"
private const val PHRASE_CARDS_JSON_KEY = "cards_json"
private const val SHOW_FURIGANA_KEY = "wear_show_furigana"

class MainActivity : ComponentActivity(), DataClient.OnDataChangedListener {
    private var cards by mutableStateOf(emptyList<WearPhraseCard>())
    private var statusText by mutableStateOf("等待手機同步")
    private var showFurigana by mutableStateOf(false)
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cards = loadWearPhraseCards(this)
        showFurigana = getSharedPreferences(PHRASE_PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(SHOW_FURIGANA_KEY, false)
        if (cards.isNotEmpty()) {
            statusText = "已載入 ${cards.size} 張小卡"
        }

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.JAPAN)
                isTtsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }

        setContent {
            WearPhraseBuddyApp(
                cards = cards,
                statusText = statusText,
                showFurigana = showFurigana,
                onShowFuriganaChange = { enabled ->
                    showFurigana = enabled
                    getSharedPreferences(PHRASE_PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(SHOW_FURIGANA_KEY, enabled)
                        .apply()
                },
                onRequestSync = {
                    statusText = "正在向手機要求同步..."
                    requestPhoneSync()
                },
                onSpeak = { japanese ->
                    if (isTtsReady) {
                        tts?.speak(japanese, TextToSpeech.QUEUE_FLUSH, null, "wear-japanese-phrase")
                    } else {
                        Toast.makeText(this, "日文語音尚未準備好", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        requestPhoneSync()
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(this).addListener(this)
        requestPhoneSync()
    }

    override fun onPause() {
        Wearable.getDataClient(this).removeListener(this)
        super.onPause()
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.use { events ->
            events
                .filter { it.type == DataEvent.TYPE_CHANGED }
                .filter { it.dataItem.uri.path == PHRASE_CARDS_PATH }
                .forEach { event ->
                    val json = DataMapItem.fromDataItem(event.dataItem)
                        .dataMap
                        .getString(PHRASE_CARDS_JSON_KEY)
                        .orEmpty()
                    val updatedCards = parseWearPhraseCards(json)
                    cards = updatedCards
                    saveWearPhraseCards(this, updatedCards)
                    statusText = "已同步 ${updatedCards.size} 張小卡"
                }
        }
    }

    private fun requestPhoneSync() {
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            if (nodes.isEmpty()) {
                statusText = "找不到已連線手機"
            } else {
                nodes.forEach { node ->
                    Wearable.getMessageClient(this)
                        .sendMessage(node.id, REQUEST_PHRASE_CARDS_PATH, byteArrayOf())
                }
            }
        }.addOnFailureListener {
            statusText = "同步要求失敗"
        }
    }
}

data class WearPhraseCard(
    val id: Long,
    val chinese: String,
    val japanese: String,
    val rubySegments: List<WearRubySegment> = emptyList()
)

data class WearRubySegment(
    val text: String,
    val reading: String = ""
)

@Composable
private fun WearPhraseBuddyApp(
    cards: List<WearPhraseCard>,
    statusText: String,
    showFurigana: Boolean,
    onShowFuriganaChange: (Boolean) -> Unit,
    onRequestSync: () -> Unit,
    onSpeak: (String) -> Unit
) {
    MaterialTheme(
        colorScheme = wearDarkColorScheme
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Japan Phrase",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = onRequestSync) {
                            Text("同步")
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "假名",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Switch(
                                checked = showFurigana,
                                onCheckedChange = onShowFuriganaChange
                            )
                        }
                    }
                }

                if (cards.isEmpty()) {
                    item {
                        Text(
                            text = "先在手機新增小卡",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(cards, key = { it.id }) { card ->
                        WearPhraseCardItem(
                            card = card,
                            showFurigana = showFurigana,
                            onSpeak = onSpeak
                        )
                    }
                }
            }
        }
    }
}

private val wearDarkColorScheme = darkColorScheme(
    primary = Color(0xFF7DD8FF),
    onPrimary = Color(0xFF002F45),
    background = Color(0xFF050914),
    onBackground = Color(0xFFE7ECF5),
    surface = Color(0xFF050914),
    onSurface = Color(0xFFE7ECF5),
    surfaceVariant = Color(0xFF142033),
    onSurfaceVariant = Color(0xFFC4CDDA)
)

@Composable
private fun WearPhraseCardItem(
    card: WearPhraseCard,
    showFurigana: Boolean,
    onSpeak: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = card.chinese,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            WearJapaneseText(
                card = card,
                showFurigana = showFurigana
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = { onSpeak(card.japanese) },
                    enabled = card.japanese.isNotBlank()
                ) {
                    Text("播放")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WearJapaneseText(
    card: WearPhraseCard,
    showFurigana: Boolean
) {
    when {
        card.japanese.isBlank() -> {
            Text(
                text = "尚未翻譯",
                fontSize = 18.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        card.rubySegments.isEmpty() || !showFurigana -> {
            Text(
                text = card.japanese,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        else -> {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                card.rubySegments
                    .flatMap { it.toDisplaySegments() }
                    .forEach { segment ->
                        WearRubySegmentText(segment = segment)
                    }
            }
        }
    }
}

@Composable
private fun WearRubySegmentText(segment: WearRubySegment) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = Modifier.padding(end = 1.dp)
    ) {
        Text(
            text = segment.reading,
            fontSize = 8.sp,
            lineHeight = 9.sp,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1
        )
        Text(
            text = segment.text,
            fontSize = 18.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun loadWearPhraseCards(context: Context): List<WearPhraseCard> {
    val raw = context
        .getSharedPreferences(PHRASE_PREFS_NAME, Context.MODE_PRIVATE)
        .getString(RECENT_PHRASES_KEY, "[]")
        .orEmpty()
    return parseWearPhraseCards(raw)
}

private fun saveWearPhraseCards(context: Context, cards: List<WearPhraseCard>) {
    val array = JSONArray()
    cards.forEach { card ->
        array.put(
            org.json.JSONObject()
                .put("createdAt", card.id)
                .put("chinese", card.chinese)
                .put("japanese", card.japanese)
                .put("rubySegments", card.rubySegments.toJsonArray())
        )
    }
    context
        .getSharedPreferences(PHRASE_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(RECENT_PHRASES_KEY, array.toString())
        .apply()
}

private fun parseWearPhraseCards(raw: String): List<WearPhraseCard> =
    runCatching {
        val array = JSONArray(raw)
        List(array.length()) { index ->
            val item = array.getJSONObject(index)
            WearPhraseCard(
                id = item.optLong("createdAt", item.optLong("id", index.toLong())),
                chinese = item.optString("chinese"),
                japanese = item.optString("japanese"),
                rubySegments = normalizeRubySegments(
                    japanese = item.optString("japanese"),
                    segments = parseRubySegments(item.optJSONArray("rubySegments"))
                )
            )
        }
    }.getOrDefault(emptyList())

private fun parseRubySegments(array: JSONArray?): List<WearRubySegment> {
    if (array == null) return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val text = item.optString("text")
            if (text.isNotBlank()) {
                add(
                    WearRubySegment(
                        text = text,
                        reading = item.optString("reading").trim()
                    )
                )
            }
        }
    }
}

private fun List<WearRubySegment>.toJsonArray(): JSONArray {
    val array = JSONArray()
    forEach { segment ->
        array.put(
            org.json.JSONObject()
                .put("text", segment.text)
                .put("reading", segment.reading)
        )
    }
    return array
}

private fun normalizeRubySegments(
    japanese: String,
    segments: List<WearRubySegment>
): List<WearRubySegment> {
    if (japanese.isBlank()) return emptyList()
    if (segments.isEmpty()) return listOf(WearRubySegment(text = japanese))
    val joinedText = segments.joinToString(separator = "") { it.text }
    return if (joinedText == japanese) {
        segments
    } else {
        listOf(WearRubySegment(text = japanese))
    }
}

private fun WearRubySegment.toDisplaySegments(): List<WearRubySegment> {
    if (text.isBlank()) return emptyList()
    if (reading.isNotBlank()) return listOf(this)

    return text.codePoints()
        .toArray()
        .map { codePoint ->
            WearRubySegment(
                text = String(Character.toChars(codePoint)),
                reading = ""
            )
        }
}
