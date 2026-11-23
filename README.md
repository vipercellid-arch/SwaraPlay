# SwaraPlay v1.0.0

Aplikasi nonton Android OLED gelap bergaya **Swara** untuk Film, Series, Drama Asia, dan Anime — tanpa perlu input TMDB API key, dilengkapi Subtitle Wyzie, pemblokir iklan bawaan, dan Auto-Updater langsung dari GitHub Releases.

## Fitur Utama v1.0.0
- **UI OLED Hitam Pekat (`#0A0A0C`)** dengan aksen merah sinema (`#E11D48`) dan ikon Opsi 2.
- **Katalog Tanpa TMDB API Key**: Film & Series (Stremio Cinemeta), Drama Asia (K-Drama / C-Drama / J-Drama), dan Anime (AniList GraphQL).
- **Multi-Server AdBlock Player & Media3 ExoPlayer**: Pemblokir iklan & popup bawaan, dukungan Stremio Addons kustom, serta pemilihan server cepat.
- **Subtitle Wyzie (`sub.wyzie.io`)**: Pencarian subtitle otomatis (prioritas Bahasa Indonesia & Inggris) bebas iklan.
- **Lanjut Nonton (Resume), Favorit & Riwayat**: Tersimpan otomatis di perangkat melalui DataStore.
- **Auto-Update GitHub Releases**: Cek & pasang pembaruan langsung dari `vipercellid-arch/SwaraPlay` tanpa token.
- **Donasi QRIS ViperCell**: Dukungan zoom + simpan ke galeri, serta tautan developer (`vipercellid-arch` & `@t3ves_`).

## Cara Build APK via GitHub Actions
1. Push seluruh isi proyek ini ke branch `main` di `https://github.com/vipercellid-arch/SwaraPlay`.
2. Workflow **Build SwaraPlay APK** akan otomatis berjalan dan menghasilkan artefak `SwaraPlay-v1.0.0.apk`.
3. Buat dan push tag `v1.0.0` (`git tag v1.0.0 && git push origin v1.0.0`) untuk menerbitkan `SwaraPlay-v1.0.0.apk` di halaman GitHub Releases melalui workflow **Release SwaraPlay APK**.

> Catatan Aset QRIS: Ganti file `app/src/main/res/drawable/qris_vipercell.png` dengan gambar QRIS ViperCell resmi Anda sebelum rilis publik.
