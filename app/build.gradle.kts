plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.panicpilot"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.panicpilot"
        minSdk = 31
        targetSdk = 36
        versionCode = 35
        // 2026-10-24 v2.3.15: 損益・騰落率の色（プラス緑・マイナス赤）を手直し前の仕様に戻した（ユーザー指示）
        // 2026-10-15 v2.3.10: 日次チェックを毎日16:30のアラーム（setExactAndAllowWhileIdle の自己連鎖）で起動。
        // 24時間周期は前回の実行から数えるので遅れが翌日へ持ち越されていた（18:01→19:30）。周期ジョブは保険として残す
        // 2026-10-15 v2.3.9: 日次チェックから通信の条件を外す（裏の通信を止められると起動せず失敗通知も黙るため・LESSON-262）／
        // 履歴タブに電池最適化の除外カード／取得失敗の通知で未除外なら除外を促す
        // 2026-10-05 v2.3.8: nikkei225jp.com がデータファイルの置き場所を変え（10-04）、日経平均・騰落レシオ・日経VIの
        // 取得が404で止まっていたのを修正。控えURLを新しい場所へ直し、404のときは参照元ページの <script src> から
        // ファイル名で置き場所を探して取り直す（次にフォルダが変わっても追従）
        // 2026-09-26 v2.3.7: 点灯・買い増し・高値圏の通知を「状態が続く間は1回だけ」に（毎営業日の同じ通知をやめる・LESSON-211）
        // 2026-09-25 v2.3.6: 取得が戻ったら取得失敗通知を消す／保存ファイルが壊れていたら別名へ退避してから初期化
        // 2026-10-25 v2.3.17: 損益・増減の色を＋赤・−青に（APP_RULES.md B3）
        // 2026-11-01 v2.3.19: 見出しの左にアプリアイコン、見出しの後ろに Gemini で描いた絵（案3-3・UiKit の HeaderBackdrop／HeaderAppIcon）
        versionName = "2.3.19" // 2026-10-24: 見出しと中身の境目に1本の線（v2.3.15: 損益・騰落率の色を手直し前に戻した／v2.3.14: 見た目を表示の決まり UiKit に）

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // WorkManager: background task scheduler (no foreground service needed)
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
