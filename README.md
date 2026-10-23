# SP Photos

Aplikasi galeri Android (Kotlin + Jetpack Compose) bergaya **iOS 27 Photos · Liquid Glass**,
mengacu pada dokumen konsep `iOS27_Photos_Liquid_Glass_UIUX_Specification.docx`.

- Package: `com.senza.spphotos` · Nama aplikasi: **SP Photos**
- Min Android 10 (API 29) · Target 14 (API 34)
- Build lewat GitHub Actions (`.github/workflows/build.yml`) → APK debug di tab *Actions → Artifacts*

## Push ke GitHub lewat Termux

```bash
pkg install git unzip -y
termux-setup-storage            # sekali saja
cd ~
unzip /sdcard/Download/SPPhotos.zip -d SPPhotos
cd SPPhotos

git init
git add .
git commit -m "SP Photos v1.0"
git branch -M main
git remote add origin https://github.com/SenzaProject/SPPhotos.git
git push -u origin main
```

Buat dulu repo kosong `SPPhotos` di GitHub (tanpa README). Saat diminta password di Termux,
pakai **Personal Access Token** (bukan password akun).

Setelah push: buka repo → **Actions** → **Build SP Photos APK** → tunggu hijau →
unduh artifact **SP-Photos-debug-apk** → ekstrak → install `app-debug.apk`.

## Fitur v1.0

| Area | Isi |
|---|---|
| Library | Header mengecil saat scroll, kartu Featured/Recent Days (+slideshow), koleksi (Memories, Favorites, Videos, Screenshots), grid dikelompokkan Today/Yesterday/bulan, tile lebar bervariasi, pinch untuk ubah kepadatan, sort, filter, jump to date, Select |
| For You | Memories otomatis per bulan + "Hari ini di tahun lalu", slideshow |
| Albums | Album perangkat, album kustom (buat/ubah nama/hapus/tambah foto), Favorites, Videos, Recently Deleted (pulihkan / hapus permanen) |
| Search | Kata kunci bahasa natural sederhana (tanggal, bulan, album, jenis media, favorit), filter, recent searches, saran |
| Viewer | Swipe, pinch + double-tap zoom, video player, favorit, bagikan, info/metadata, edit, hapus |
| Editor | Non-destruktif: exposure, contrast, saturation, warmth, tint, 6 filter + intensitas, crop rasio, rotasi, compare (tahan), undo, revert, simpan sebagai salinan |
| Settings | Tema, intensitas kaca, kurangi efek (Reduce Motion), kolom grid, ringkasan penyimpanan, privasi |

## Belum ada (butuh model ML/layanan tambahan)

People & Pets, Places (GPS), visual search objek/teks, deteksi duplikat, shared albums,
cloud sync, trim video, Live Photos/RAW/HDR khusus, musik pada slideshow.

## v1.1 — mengikuti referensi iOS 26 Photos

- Tab bawah: pil kaca **Library | Collections** + tombol **Search** bulat terpisah
- Animasi **lensa kaca**: saat ikon ditekan/digeser, sorotan membesar melebihi bar, isinya diperbesar,
  mengikuti jari dengan pegas lalu menempel ke tab terdekat (sama untuk Years / Months / All)
- Library: header "Library" + jumlah item (berubah jadi rentang tanggal saat scroll), grid penuh 5 kolom,
  tombol filter bulat dan pil Select, ketuk tab Library lagi untuk mode Years / Months / All
- Collections: daftar Albums, Memories, Recent Days, Media Types, dan Utilities
- Tema gelap hitam pekat

## v1.2

- **Blur latar sungguhan** (Haze) di bilah atas Library dan seluruh permukaan kaca bilah bawah mengambang
- **Lensa kaca cair**: pembiasan & pembesaran isi (Android 13+), jelly mengikuti jari, riak saat bergerak cepat
- **Update tanpa bentrok**: APK kini ditandatangani keystore tetap (`app/spphotos.jks`), jadi update
  bisa langsung dipasang di atas versi sebelumnya. **Jangan hapus/ganti file keystore ini.**
  (Khusus pemasangan 1.2 pertama: uninstall dulu versi lama sekali, karena versi lama memakai kunci acak.)
- Logo baru dari gambar yang diberikan (adaptive icon + efek kaca + ikon bertema Android 13+)
- Perbaikan: teks "Months" tidak lagi terpotong, chip "Recently Deleted" muat

## v1.3

- Blur diganti sistem sendiri (tanpa library Haze): **blur progresif ala iOS** di tepi atas (header Library)
  dan tepi bawah (bilah mengambang) — paling kabur di tepi layar, memudar tajam ke arah isi, plus gradasi gelap tipis
- Blur dirender di resolusi kecil sehingga ringan di HP kelas menengah; bisa dimatikan di Settings
- **Scroll lebih mulus**: thumbnail memakai `MediaStore.loadThumbnail` (cache sistem) bukan decode foto penuh,
  crossfade dimatikan, cache memori diatur
- **Laporan crash**: jika aplikasi berhenti, jejaknya disimpan dan muncul dialog "Salin" saat dibuka lagi

## v1.4 — Editor video (Fase 1, gaya CapCut)

Buka video → ikon Edit. Timeline dengan playhead tengah, thumbnail klip, split, trim (handle putih),
kecepatan 0.25x–4x, mute, duplikat, hapus, undo/redo, filter + adjust, tambah klip lain, ekspor MP4 480/720/1080P
ke Movies/SP Photos. Teks, audio/musik, stiker, transisi, keyframe: belum (Fase berikutnya).

## v1.5

- **Editor foto ala iOS**: Adjust 15 alat (Exposure, Brilliance, Highlights, Shadows, Contrast, Brightness,
  Black Point, Saturation, Vibrance, Warmth, Tint, Sharpness, Definition, Noise Reduction, Vignette) dengan dial geser
  + cincin kuning, tombol Auto, 9 filter ala iOS dengan intensitas, Crop (straighten, perspektif vertical/horizontal,
  rotasi, flip, 9 rasio, kotak crop bisa digeser), tahan gambar untuk membandingkan, undo/redo, Revert,
  tab dengan lensa kaca cair. Hasil disimpan sebagai salinan (`Pictures/SP Photos`), file asli tidak diubah.
- **Perbaikan video layar hitam** (1080p60 – 4K60): pemutar diganti ExoPlayer dengan decoder cadangan
  (hardware → software) + tampilan TextureView; jika tetap tak bisa, ada tombol "Buka dengan aplikasi lain".

## v1.6

- **Editor video**: anti-freeze, layer teks & stiker (14 font), transisi di titik potong, ekspor 480P–8K
  (opsi yang tidak didukung encoder perangkat dinonaktifkan).

## v1.7

- **Watermark dihapus** dari hasil ekspor. Fitur SP Pro/verifikasi Telegram ikut dihapus (tidak ada lagi yang perlu dibuka),
  termasuk token bot di build.
- **Galeri default**: SP Photos kini terdaftar sebagai Galeri (`APP_GALLERY`), pembuka foto/video (`VIEW`), tinjau hasil kamera
  (`REVIEW`), editor (`EDIT`), dan pemilih foto/video untuk aplikasi lain (`PICK` / `GET_CONTENT`, termasuk pilih banyak).
  Membuka foto dari aplikasi lain langsung tampil di viewer; saat ditutup kembali ke aplikasi pemanggil.
  Settings → *Galeri default* menampilkan status dan tombol untuk menjadikannya default.
- **Refresh rate**: jika layar sedang di 90/120 Hz, aplikasi meminta mode tertinggi pada resolusi yang sama
  (sistem tetap membatasi sesuai pengaturan layar; di 60 Hz tidak ada permintaan).
- **Scroll lebih mulus**: blur dijeda saat menggulir (kembali halus setelah berhenti; bisa diaktifkan di Settings → *Blur saat scroll*),
  subtitle Library tidak dihitung tiap frame.

### Cara menjadikan SP Photos galeri default
1. Settings → Galeri default → **Jadikan default** → pilih SP Photos → **Selalu**.
2. Jika yang terbuka aplikasi lain: buka **Pengaturan** → Aplikasi default / Kelola aplikasi → galeri lama → hapus pengaturan default, lalu ulangi langkah 1.
3. Tekan **Periksa** untuk melihat statusnya.

### Direncanakan berikutnya
Detail (pertajam, noise, penghalus), warna ala Lightroom (HSL, color grading, tone curve, periset), latar transparan,
salin teks dari foto (tahan 2 detik), serta teks/stiker/bingkai/gambar di editor foto.
