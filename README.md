# JapanPhraseBuddy

一個給台灣旅人在日本現場溝通用的輕量 Android App。

核心流程很單純：

1. 輸入中文，或按麥克風用中文語音輸入
2. 先存成小卡，需要時再用 Gemini 產生自然、禮貌、適合現場使用的日文
3. 直接播放日文 TTS，或複製文字給對方看
4. Gemini API 額度用完時，可匯出 prompt 給外部 LLM 翻譯，再把 JSON 匯入補回小卡

## 設定 Gemini API Key

在專案根目錄的 `local.properties` 加上：

```properties
GEMINI_API_KEY=你的_api_key
```

`local.properties` 已被 `.gitignore` 排除，不要把 API key commit 進 repo。

目前模型使用：

```text
gemini-2.5-flash-lite
```

## API 額度用完時

App 內的小卡清單有「匯出」和「匯入」：

- 匯出：把翻譯 prompt 和小卡 JSON 複製到剪貼簿
- 匯入：貼上外部 LLM 回傳的 JSON，App 會用 `id` 對應小卡並補上 `japanese`

## 執行

```bash
./gradlew :app:assembleDebug
```

Debug APK 會產在：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 目前刻意不做的事

- 不做分類
- 不做預載片語
- 不做英文、泰文、多語言
- 不做登入、Firebase、上架流程
- 不做 Wear OS，等手機版確認好用後再加
