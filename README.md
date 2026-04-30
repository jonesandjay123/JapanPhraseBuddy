# JapanPhraseBuddy

一個給台灣旅人在日本現場溝通用的輕量 Android App。

這不是大型片語庫，也不做分類管理。它的定位很單純：把你臨時想到、等等可能要對真人說的中文先存成小卡，需要時再翻成自然日文、播放或複製給對方看。

## 目前功能

- 中文文字輸入
- Android 原生中文語音輸入，回填前會轉成繁體中文
- 一句中文先存成一張小卡，不需要等 Gemini 成功
- 每張小卡可個別翻譯、重翻、播放、複製、刪除
- 手機版會在日文漢字上方顯示平假名讀音，輔助認字與發音
- 手機右上角可切換假名顯示開關，並會記住上次狀態
- Android 原生日文 Text-to-Speech 播放
- 小卡可用左側拖曳把手調整順序
- 小卡會存在本機，下次打開仍保留
- Gemini API 429 或額度用完時，可匯出 prompt 給外部 LLM 翻譯，再匯入 JSON 補回小卡
- Wear OS 版本可被動同步手機小卡，顯示同一份清單並播放日文 TTS
- 手錶版也有獨立「假名」開關，預設關閉，開啟後會嘗試顯示漢字上方假名

## 使用方式

1. 在上方輸入中文，或按麥克風用中文語音輸入。
2. 按「新增小卡」，先把腦中想到的句子存下來。
3. 對需要的卡片按「翻譯」，用 Gemini 產生日文。
4. 翻譯完成後，手機卡片會用 furigana/ruby 形式把假名顯示在漢字上方。
5. 可按「播放」或「複製」。
6. 若日文不滿意，可按「重翻」。
7. 可用右上角「假名」開關顯示或隱藏假名音標。
8. 用左側拖曳把手排列小卡順序。
9. 手錶端若需要看讀音，可在手錶 App 內開啟獨立的「假名」開關。

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
5. App 會用 `id` 對應既有小卡，補上 `japanese`、`furiganaReading` 和 `rubySegments` 欄位。

匯入格式核心如下：

```json
[
  {
    "id": 123456789,
    "chinese": "請問這班車會到東京車站嗎？",
    "japanese": "すみません、この電車は東京駅まで行きますか？",
    "furiganaReading": "すみません、このでんしゃはとうきょうえきまでいきますか？",
    "rubySegments": [
      { "text": "すみません、この", "reading": "" },
      { "text": "電車", "reading": "でんしゃ" },
      { "text": "は", "reading": "" },
      { "text": "東京駅", "reading": "とうきょうえき" },
      { "text": "まで", "reading": "" },
      { "text": "行", "reading": "い" },
      { "text": "きますか？", "reading": "" }
    ]
  }
]
```

## 資料相容性

目前小卡 JSON 主要欄位包含：

- `chinese`
- `japanese`
- `furiganaReading`
- `rubySegments`
- `createdAt`

舊版小卡只有 `chinese`、`japanese`、`createdAt` 也可以繼續使用、播放和同步；只是沒有 `rubySegments` 的舊卡不會顯示漢字上方假名。若想補上假名音標，對舊卡按「重翻」，或使用匯出/匯入讓外部 LLM 補回 `furiganaReading` 與 `rubySegments`。

手機端會檢查 `rubySegments` 串起來是否完全等於 `japanese`。如果不一致，會自動退回普通日文顯示，避免假名錯位。

furigana 顯示使用自製 Compose renderer：有讀音的漢字詞會維持為一個 ruby 片段；沒有讀音的長文字片段會在 UI 顯示時拆小，避免 `FlowRow` 因為整段太長而提早換行。

## 執行

```bash
./gradlew :app:assembleDebug :wear:assembleDebug
```

Debug APK 會產在：

```text
app/build/outputs/apk/debug/app-debug.apk
wear/build/outputs/apk/debug/wear-debug.apk
```

## Wear OS 同步

此 repo 內含兩個 module：

```text
app/   手機版
wear/  手錶版
```

目前手錶版固定使用 dark mode，是被動端，不打 Gemini、不編輯小卡，只負責：

- 接收手機同步的小卡 JSON
- 顯示中文與日文
- 播放日文 TTS
- 手動按「同步」向手機要求最新資料

手錶版有自己的「假名」開關，預設關閉；開啟後會用較小字級顯示漢字上方假名。這個設定獨立於手機端，會記在手錶本機。

同步方式使用 Wear OS Data Layer：

- 手機每次小卡新增、刪除、翻譯、匯入、排序後，會 push 整份小卡清單到 `/phrase_cards`
- 手錶啟動或按「同步」時，會送 `/request_phrase_cards` 給手機
- 手機收到 request 後，會再 push 一次最新 `/phrase_cards`

測試時需要：

1. 手機和手錶已完成 Wear OS / Pixel Watch 配對。
2. 手機和手錶都開啟 Developer options。
3. 手機開 USB debugging。
4. 手錶開 ADB debugging；若不是 USB 連線，開 Wireless debugging。
5. 手機安裝 `app-debug.apk`。
6. 手錶安裝 `wear-debug.apk`。
7. 兩邊 app 使用同一套 debug 簽名與 application id。
8. 手機新增或翻譯小卡後，打開手錶 app 按「同步」。

如果手錶走 Wi-Fi debugging，大致流程是：

1. 在手錶設定中開啟 Wireless debugging。
2. 記下手錶顯示的 IP、port、pairing code。
3. 在電腦執行配對：

```bash
adb pair 手錶_ip:pairing_port
```

4. 輸入手錶上的 pairing code。
5. 再連線到手錶：

```bash
adb connect 手錶_ip:adb_port
```

用 adb 安裝時，請先確認裝置：

```bash
adb devices
```

接著分別安裝到手機與手錶對應的 device serial：

```bash
adb -s 手機_serial install -r app/build/outputs/apk/debug/app-debug.apk
adb -s 手錶_serial install -r wear/build/outputs/apk/debug/wear-debug.apk
```

## 技術概況

- Kotlin
- Jetpack Compose
- Android TextToSpeech
- Android RecognizerIntent
- SharedPreferences + JSON 儲存小卡
- Gemini REST API
- Wear OS Data Layer
- 手機端自製 Compose ruby/furigana 顯示
- 手錶端自製輕量 ruby/furigana 顯示，預設關閉

## 目前刻意不做的事

- 不做分類
- 不做預載片語
- 不做英文、泰文、多語言
- 不做登入、Firebase、上架流程
- 手錶版不做 Gemini、不做輸入、不做編輯，只做同步顯示與播放；furigana 顯示由手錶端獨立開關控制
