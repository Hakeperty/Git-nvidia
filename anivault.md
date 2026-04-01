Build a local Crunchyroll-style Android APK (Anime & Manga Library App)
Create a fully functional Android application that acts as a personal, offline-first anime and manga library. The app fetches content from open sources and stores everything locally on the device — no subscription, no account required.
Core Concept
The user pastes a URL from a supported site (e.g. hianime.to, mangadex.org, mangakatana.com) and the app scrapes, downloads, and permanently stores that anime or manga to local device storage. It functions like a personal media library — think Crunchyroll + Tachiyomi, self-hosted on your own phone.
Supported Source Sites
Anime: aniwatchtv (aniwatchtv.to), and similar streaming sites
Manga: MangaDex, MangaKatana, and similar readers
The scraper layer should be modular so new sources can be added easily
Features
Content Import
Paste any supported anime or manga URL into a search/import bar
App auto-detects whether it's anime or manga and pulls all metadata (title, cover art, description, genre, episode/chapter list)
User selects what to download: full series, specific seasons, specific episodes/chapters, or "download as I watch"
Anime Playback
Both dubbed and subbed versions available where the source provides them — user can toggle dub/sub per-show or per-episode
Episode list is clearly laid out: Season → Episode number + title + thumbnail
Progress tracking: episodes marked as Watched / In Progress / Unwatched
Built-in video player with subtitle support (.vtt / .srt), playback speed control, skip intro button
Manga Reader
Chapter list clearly displayed with chapter number, title, and release date
Chapters marked as Read / Unread
Reader supports vertical scroll (webtoon) and paginated (left-to-right / right-to-left) modes
Pages cached/downloaded for fully offline reading
Library Management
Home screen shows all saved anime and manga with cover art in a clean grid
One-tap delete to remove any title and free up storage — with a confirmation prompt showing how much space will be freed
Filter/sort library by: type (anime/manga), genre, watch status, recently added, A–Z
Storage usage indicator per title and total
UI/UX
Dark mode by default (AMOLED-friendly)
Crunchyroll-inspired layout: bottom nav bar with Library, Browse/Import, Downloads, Settings
"Continue Watching / Reading" row on the home screen
Download queue with progress bars for active downloads
Settings
Default download quality (480p / 720p / 1080p)
Default language preference (sub/dub)
Storage location picker (internal vs SD card)
Auto-delete after finishing a series toggle
Tech Stack (suggested)
Framework: React Native (Expo) or Flutter for cross-platform, or native Android (Kotlin) for best performance
Scraping: Cheerio / Puppeteer (Node.js backend sidecar) or native Kotlin scraper using Jsoup
Local storage: SQLite (via Drizzle or Room) for metadata, device filesystem for media files
Video player: ExoPlayer (Android native) or react-native-video
Manga renderer: Custom FlatList-based reader or react-native-image-zoom-viewer
Constraints
All content stored locally — no cloud sync, no external accounts
App must work fully offline once content is downloaded
No placeholders — every screen, component, and feature must be fully implemented
Clean, production-quality code with no TODOs left in
