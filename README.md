# Screen Recorder
Oleh **SenzaProject**

- Resolusi: 480p, 720p, 1080p (30 / 60 / 90 / 120 fps) dan 2K (120 fps)
- 2K otomatis muncul hanya jika perangkat mendukung
- Suara perangkat (internal) dan suara luar (mikrofon) bisa dihidupkan/dimatikan sendiri-sendiri, atau dicampur
- Hasil di `Movies/ScreenRecorder`

## Build APK
Push ke GitHub, lalu buka tab **Actions** > **Build APK** > unduh artifact `Screen-Recorder-APK`.

## Pill dinamis
Pill hitam (titik merah + timer) otomatis sejajar dengan lubang kamera depan. Ketuk pill untuk membuka kartu "Merekam layar" dengan tombol Pause dan Stop.
Butuh izin "Tampil di atas aplikasi lain". Matikan "Ketuk pill untuk Pause / Stop" jika ingin pill hanya sebagai indikator.

### Agar pill bisa disentuh
Status bar sebagian perangkat (mis. ColorOS) menelan sentuhan di area kamera. Aktifkan **Pengaturan > Aksesibilitas > Screen Recorder** (tombol "Aktifkan sentuh pill" di aplikasi). Layanan ini kosong dan hanya dipakai untuk menampilkan pill di atas status bar. Di Android 13+ mungkin perlu: Info aplikasi > menu titik tiga > Izinkan setelan terbatas.
