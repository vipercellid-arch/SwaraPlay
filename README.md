# SwaraPlay v4.0.0 — Swara Monochrome OLED Cinema (Mobile/Tablet & Android TV)

Aplikasi nonton Android bertema monokrom OLED murni (`#000000`) seirama dengan **Swara** untuk **HP, Tablet, dan Android TV** — 100% gratis tanpa VIP/langganan, tanpa login, dan tanpa TMDB API key.

## Pembaruan Utama v4.0.0
- **UI Monokrom OLED Asli Swara:** Latar `#000000`, kartu matte `#141416`, aksen putih `#E0E0E0`, floating bottom pill ramping di tengah, `ProfileScreen` split-screen (Kartu Profil user + Total Waktu Menonton + Versi + Pengaturan modal), dan `DonateSheet` ala Swara (maskot monokrom + ring melingkar + QRIS ViperCell).
- **Perbaikan Total Player & Episode:** Server IMDb/AniList native (`VidSrc.icu`, `VidSrc.me`, `2Embed.cc`, `MoviesAPI.club`, `AutoEmbed`, `MultiEmbed`, `SmashyStream`), auto-fallback saat error 5xx / konten kosong, pengayaan episode Series/Drama via TVMaze API, dan episode Anime ongoing lengkap via AniList `nextAiringEpisode`.
- **Player Split-Screen Tablet/Landscape & TV:** Kiri 62% video (dengan True Fullscreen Landscape), kanan 38% grid kotak nomor episode, server cepat, dan subtitle Wyzie.
- **Multi-Filter Daftar Konten:** Filter Tipe, Area/Negara, Genre, dan Urutkan (Populer/Terbaru/Skor Tinggi).
- **Pre-release CI/CD & Notifikasi Update:** Otomatis membangun `SwaraPlay-v4.0.0.apk` dan `SwaraPlay-TV-v4.0.0.apk` sebagai Pre-release + notifikasi sistem & banner dalam aplikasi.
