# TV kids mode time locker

Android TV 的開機帳號桌面。小朋友在平日 8:00–21:00、週六日 8:00–22:00 才能看。每一輪預設 30 或 60 分鐘，時間只在別的 App 或 HDMI 前面才倒數。時間到回到帳號頁，不關電視。同一天剩下的時間可以接著用，過了午夜就重新給一輪。家長可以加時，或給當天不限分鐘，但仍然受上面的時段限制。

這份是公開用的乾淨程式。家裡正在用的那份不在這個資料夾。

## 資料夾

- `launcher/`：電視 App，套件 `app.tvkids.timelocker`。
- `cloud/`：手機網頁用的 HTTPS 轉送。電視主動連出來。憑證和 `state.json` 在第一次執行時產生，不要提交。

## 使用前要自己填

- `launcher/.../Gate.java` 的 `CLOUD_HOST`，以及 `cloud/cloud_proto.py` 的 `DEFAULT_HOST`。留空就不會連任何主機。
- 小朋友帳號沒有預先建立。開機先設家長密碼，再在家長設定裡新增。
- 訊號源只顯示 HDMI 1、HDMI 2、HDMI 3。
- 天氣預設關閉。要顯示時，在 `Weather.java` 填 `PLACE`、`FORECAST_URL`、`AIR_URL`。

## 建置

需要 JDK 17、Android SDK、Gradle 8.9。設定 `JAVA_HOME` 和 `ANDROID_HOME` 後，在 `launcher/` 執行：

```
gradle test assembleDebug
```

Debug APK 在 `launcher/app/build/outputs/apk/debug/app-debug.apk`。裝上電視之後，還要把這個 App 設成 Home，並打開懸浮窗和無障礙守衛。停用原廠桌面前請先確認可以還原。
