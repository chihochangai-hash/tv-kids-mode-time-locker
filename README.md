# TV kids mode time locker

Android TV 的開機帳號桌面。小朋友在平日 8:00–21:00、週六日 8:00–22:00 才能看。每一輪預設 30 或 60 分鐘，時間只在別的 App 或 HDMI 前面才倒數。時間到回到帳號頁，不關電視。同一天剩下的時間可以接著用，過了午夜就重新給一輪。家長可以加時，或給當天不限分鐘，但仍然受上面的時段限制。

這份是公開用的乾淨程式。家裡正在用的那份不在這個資料夾。

An Android TV home screen with separate accounts. Children can watch on weekdays from 8:00 to 21:00, and on Saturday and Sunday from 8:00 to 22:00. Each round starts at 30 or 60 minutes. The clock runs only while another app or an HDMI input is in front. When time is up, the TV returns to the account page and stays on. Time left the same day can be resumed. After local midnight the next login starts a fresh round. A parent can add minutes, or allow unlimited minutes for that day. Unlimited use is still limited to the hours above.

This folder is the cleaned public source. It is not the copy running in one particular home.

## 資料夾

- `launcher/`：電視 App，套件 `app.tvkids.timelocker`。
- `cloud/`：手機網頁用的 HTTPS 轉送。電視主動連出來。憑證和 `state.json` 在第一次執行時產生，不要提交。

## Folders

- `launcher/` is the TV app. Its application id is `app.tvkids.timelocker`.
- `cloud/` is the HTTPS relay for the phone page. The TV connects outward. The certificate and `state.json` are created on first run. Do not commit them.

## 使用前要自己填

- `launcher/.../Gate.java` 的 `CLOUD_HOST`，以及 `cloud/cloud_proto.py` 的 `DEFAULT_HOST`。留空就不會連任何主機。
- 小朋友帳號沒有預先建立。開機先設家長密碼，再在家長設定裡新增。
- 訊號源只顯示 HDMI 1、HDMI 2、HDMI 3。
- 天氣預設關閉。要顯示時，在 `Weather.java` 填 `PLACE`、`FORECAST_URL`、`AIR_URL`。

## Before you use it

- Set `CLOUD_HOST` in `launcher/.../Gate.java` and `DEFAULT_HOST` in `cloud/cloud_proto.py`. While they are empty, the app does not connect to a host.
- No child accounts are created in advance. Set the parent PIN first, then add children in parent settings.
- The input list shows only HDMI 1, HDMI 2, and HDMI 3.
- Weather is off. To show it, fill `PLACE`, `FORECAST_URL`, and `AIR_URL` in `Weather.java`.

## 建置

需要 JDK 17、Android SDK、Gradle 8.9。設定 `JAVA_HOME` 和 `ANDROID_HOME` 後，在 `launcher/` 執行：

```
gradle test assembleDebug
```

Debug APK 在 `launcher/app/build/outputs/apk/debug/app-debug.apk`。裝上電視之後，還要把這個 App 設成 Home，並打開懸浮窗和無障礙守衛。停用原廠桌面前請先確認可以還原。

## Build

JDK 17, the Android SDK, and Gradle 8.9 are required. Set `JAVA_HOME` and `ANDROID_HOME`, then run this in `launcher/`:

```
gradle test assembleDebug
```

The debug APK is `launcher/app/build/outputs/apk/debug/app-debug.apk`. After installing it, set this app as Home, and allow the overlay and the accessibility guard. Confirm that you can restore the stock launcher before you disable it.
