# JapanPhraseBuddy

一個給台灣旅人在日本現場溝通用的輕量 Android App。

這不是大型片語庫，也不做分類管理。它的定位很單純：把你臨時想到、等等可能要對真人說的中文先存成小卡，需要時再翻成自然日文、播放或複製給對方看。

## 目前功能

- 中文文字輸入
- Android 原生中文語音輸入，回填前會轉成繁體中文
- 一句中文先存成一張小卡，不需要等 Gemini 成功
- 每張小卡可個別翻譯、重翻、播放、複製、刪除
- Android 原生日文 Text-to-Speech 播放
- 小卡可用左側拖曳把手調整順序
- 小卡會存在本機，下次打開仍保留
- Gemini API 429 或額度用完時，可匯出 prompt 給外部 LLM 翻譯，再匯入 JSON 補回小卡

## 使用方式

1. 在上方輸入中文，或按麥克風用中文語音輸入。
2. 按「新增小卡」，先把腦中想到的句子存下來。
3. 對需要的卡片按「翻譯」，用 Gemini 產生日文。
4. 翻譯完成後可按「播放」或「複製」。
5. 若日文不滿意，可按「重翻」。
6. 用左側拖曳把手排列小卡順序。

小卡按鈕順序刻意排成：

```text
播放 → 複製 → 翻譯/重翻 → 刪除
```

## Gemini 設定

在專案根目錄的 `local.properties` 加上：

```properties
GEMINI_API_KEY=你的_api_key
```

`local.properties` 已被 `.gitignore` 排除，不要把 API key commit 進 repo。

目前模型使用：

```text
gemini-2.5-flash-lite
```

## LLM 備援流程

如果 Gemini API 額度用完或遇到 429，可以改走外部 LLM：

1. 在 App 小卡清單按「匯出」。
2. App 會把翻譯 prompt 和小卡 JSON 複製到剪貼簿。
3. 貼到 ChatGPT、Gemini 網頁版、Claude 等外部 LLM。
4. 將 LLM 回傳的 JSON 複製回 App 的「匯入」對話框。
5. App 會用 `id` 對應既有小卡，補上 `japanese` 欄位。

匯入格式核心如下：

```json
[
  {
    "id": 123456789,
    "chinese": "請問這班車會到東京車站嗎？",
    "japanese": "すみません、この電車は東京駅まで行きますか？"
  }
]
```

## 執行

```bash
./gradlew :app:assembleDebug
```

Debug APK 會產在：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 技術概況

- Kotlin
- Jetpack Compose
- Android TextToSpeech
- Android RecognizerIntent
- SharedPreferences + JSON 儲存小卡
- Gemini REST API

## 目前刻意不做的事

- 不做分類
- 不做預載片語
- 不做英文、泰文、多語言
- 不做登入、Firebase、上架流程
- 不做 Wear OS，等手機版確認好用後再加
