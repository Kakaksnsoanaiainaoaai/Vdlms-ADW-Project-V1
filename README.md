# DLMS 2-Way — Virtual Digital Loudspeaker Management System (Android)

**DLMS 2-Way** adalah aplikasi Android Virtual Digital Loudspeaker Management System (DLMS) berdesain rack hardware audio processor profesional (mode utama Landscape 19" rack unit dan adaptif untuk Portrait).

## Fitur Utama

- **Arsitektur Audio 2-Input / 2-Output Terpisah**
  - `INPUT L + INPUT R → DSP Input Processing → 2-Way Crossover → DSP Output Processing → OUTPUT L + OUTPUT R`
  - **Input L & Input R**: Gain (`-24` s/d `+12 dB`), Polarity (`0° / 180°`), Mute, Delay (`0–50 ms` + konversi meter), Parametric EQ 5-Band dengan grafik respons frekuensi real-time, serta VU meter terpisah.
  - **Crossover 2-Way (LOW / HIGH)**: Filter **Butterworth** (`12 / 18 / 24 / 48 dB/oct`) dan **Linkwitz-Riley** (`12 / 24 / 48 dB/oct`), frekuensi `20 Hz – 20 kHz`, bypass per band/global, dan grafik respons frekuensi matematis.
  - **Output L & Output R**: Matrix routing (`IN L`, `IN R`, `L+R SUM`), band assignment (`LOW`, `HIGH`, `FULL`), Gain, Delay (`ms` & `meter`), Polarity, Mute, Parametric EQ 5-Band, dan Peak Limiter (`Threshold`, `Attack`, `Release`, `Make-up Gain`, indikator aktif + gain reduction meter).
- **Audio Engine Native Low-Latency**
  - Menggunakan `AudioRecord` + `DlmsDspProcessor` + `AudioTrack` (`PERFORMANCE_MODE_LOW_LATENCY` pada API 26+) dengan deteksi perangkat input/output dan sample rate aktual perangkat.
  - Penanganan status nyata (`AUDIO: READY`, `AUDIO: NOT CONNECTED`, `AUDIO: PERMISSION DENIED`) tanpa status atau sinyal palsu.
- **Kontrol Hardware Rack Virtual**
  - Tombol fisik virtual (`MENU`, `BACK`, `ENTER`, D-Pad `UP/DOWN/LEFT/RIGHT`, `MUTE`, pemilih channel `IN L`, `IN R`, `X-OVER`, `OUT L`, `OUT R`, `YT`), LED indikator, serta **Rotary Encoder** virtual (`TURN` untuk navigasi/ubah nilai, `PUSH = ENTER`).
- **Preset & Penyimpanan Lokal**
  - Save, Load, Rename, Delete, dan Reset ke preset netral `DEFAULT` menggunakan **Room Database** dan **DataStore Preferences**.
- **Media / YouTube Terintegrasi**
  - Terhubung langsung ke YouTube di dalam aplikasi (`m.youtube.com` & IFrame Player resmi) tanpa API key, tanpa downloader/ad-bypass, dengan pemisahan jalur audio sistem dari rantai DSP mikrofon internal.

---

## Struktur Repository

```text
.
├── .github/workflows/build-apk.yml   # GitHub Actions CI/CD build Debug & Release APK
├── .env.example                      # Template environment (tanpa secret)
├── .gitignore                        # Aturan ignore Android, Gradle, keystore, & secret
├── build.gradle.kts                  # Konfigurasi Gradle root
├── settings.gradle.kts               # Pengaturan project Gradle ("DLMS 2-Way")
├── gradle/
│   ├── libs.versions.toml            # Version catalog dependency Android
│   └── wrapper/gradle-wrapper.properties
└── app/
    ├── build.gradle.kts              # Konfigurasi modul app, SDK, & signingConfigs
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── res/                  # Font lokal, string, tema, & launcher icon adaptif
        │   └── java/com/example/
        │       ├── audio/            # AndroidAudioEngine & AudioDeviceEnumerator
        │       ├── dsp/              # BiquadFilter, Crossover, Delay, Limiter, Meter, DlmsDspProcessor
        │       ├── hardware/         # State machine tombol fisik & rotary encoder
        │       ├── storage/          # Room Database (Preset) & DataStore (Settings)
        │       ├── ui/               # RackChassis, RotaryEncoder, VuMeter, Graphs, & Layar LCD
        │       ├── viewmodel/        # DlmsViewModel
        │       ├── youtube/          # YouTubeEmbeddedWebView & URL parser
        │       ├── DlmsApplication.kt
        │       └── MainActivity.kt
        └── test/java/com/example/    # Unit test DSP & aplikasi
```

---

## 1. Clone Repository

```bash
git clone https://github.com/<username>/<nama-repo>.git
cd <nama-repo>
```

---

## 2. Build APK

### Prasyarat Lokal
- **JDK 17**
- **Android SDK** (compileSdk 36, targetSdk 36, minSdk 24)
- **Gradle 9.3.1** (atau buka langsung di **Android Studio**)

### Build via Command Line

1. Siapkan `debug.keystore` lokal jika belum ada (untuk build debug):
   ```bash
   keytool -genkeypair -v \
     -keystore debug.keystore \
     -storepass android \
     -alias androiddebugkey \
     -keypass android \
     -keyalg RSA -keysize 2048 -validity 10000 \
     -dname "CN=Android Debug,O=Android,C=US"
   ```
2. Jalankan unit test DSP:
   ```bash
   gradle :app:testDebugUnitTest --tests "com.example.DspProcessorTest"
   ```
3. Build **Debug APK** (untuk testing):
   ```bash
   gradle :app:assembleDebug
   ```
4. Build **Release APK** (untuk distribusi):
   Siapkan keystore release Anda (jangan pernah commit file `.jks` ke GitHub):
   ```bash
   export KEYSTORE_PATH="/path/ke/my-upload-key.jks"
   export STORE_PASSWORD="password_keystore_anda"
   export KEY_PASSWORD="password_key_anda"
   gradle :app:assembleRelease
   ```

### Build via Android Studio
1. Buka **Android Studio** → **Open** → pilih folder root repository ini.
2. Tunggu Gradle Sync selesai.
3. Pilih **Build → Build Bundle(s) / APK(s) → Build APK(s)**.

---

## 3. Mendapatkan APK Hasil Build

### Dari Build Lokal
- **Debug APK**: `app/build/outputs/apk/debug/app-debug.apk`
- **Release APK**: `app/build/outputs/apk/release/app-release.apk`

### Langsung dari GitHub Actions
Setiap push ke branch `main`/`master`, pull request, atau dijalankan manual melalui tab **Actions**:
1. Buka repository di GitHub → tab **Actions**.
2. Pilih workflow **Build DLMS 2-Way APK** pada run terbaru yang berstatus hijau (berhasil).
3. Scroll ke bagian **Artifacts** di halaman ringkasan run:
   - `dlms-2way-debug-apk` → berisi `dlms-2way-debug.apk`
   - `dlms-2way-release-apk` → berisi `dlms-2way-release.apk`
4. Klik nama artifact untuk mengunduh file `.zip` berisi APK siap instal.

---

## 4. Membuat GitHub Release (Distribusi APK Langsung)

Workflow `.github/workflows/build-apk.yml` mendukung pembuatan **GitHub Release** otomatis beserta file APK siap unduh setiap kali Anda mendorong tag versi `v*`:

### Langkah Opsional: Siapkan Signing Key Produksi di GitHub Secrets
Agar Release APK ditandatangani dengan keystore permanen Anda:
1. Encode file keystore `.jks` (dengan alias `upload`) menjadi Base64:
   ```bash
   base64 -w 0 my-upload-key.jks
   ```
2. Buka **GitHub Repository → Settings → Secrets and variables → Actions → New repository secret**, lalu tambahkan:
   - `RELEASE_KEYSTORE_BASE64`: hasil base64 keystore Anda
   - `STORE_PASSWORD`: password keystore
   - `KEY_PASSWORD`: password alias `upload`

*(Jika secret di atas belum diisi, GitHub Actions tetap dapat melakukan build release APK dengan keystore CI sementara agar proses pengujian workflow berjalan lancar.)*

### Publish Release dengan Git Tag
```bash
git tag v1.0.0
git push origin v1.0.0
```
Setelah workflow selesai, buka halaman **Releases** di repository GitHub untuk mengunduh `dlms-2way-debug.apk` dan `dlms-2way-release.apk` secara langsung.
