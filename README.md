# JapanPhraseBuddy

一個給台灣旅人在日本現場溝通用的輕量 Android App。

核心流程很單純：

1. 輸入中文，或按麥克風用中文語音輸入
2. 用 Gemini 產生自然、禮貌、適合現場使用的日文
3. 直接播放日文 TTS，或複製文字給對方看
4. App 會保留最近 6 筆，方便短時間內重複使用

## 設定 Gemini API Key

在專案根目錄的 `local.properties` 加上：

```properties
GEMINI_API_KEY=你的_api_key
```

`local.properties` 已被 `.gitignore` 排除，不要把 API key commit 進 repo。

目前模型使用：

```text
gemini-3-flash-preview
```

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
