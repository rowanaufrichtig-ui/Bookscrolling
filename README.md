# Bookscrolling

An Android app that turns any EPUB into a scrollable feed. Each paragraph is a post you can like or comment on, and the book's front cover is the first post.

## Features

- **Feed reader**: every paragraph and chapter heading is a post. Tap the green bookmark (or double-tap the text) to save a post. Swipe a post to the right, or tap 💬, to comment on it. The **Aa** button next to the progress percentage changes the text size. Your place in each book is saved.
- **Gutenberg tab**: browse the most popular Project Gutenberg books or search 70,000+ public-domain titles. Tap a book to download it and start scrolling.
- **Philosophy tab**: all ~1,800 articles in the [Stanford Encyclopedia of Philosophy](https://plato.stanford.edu). Search by topic or author, or press shuffle to open a random article. Each article opens as a feed of its preamble and main text, and common logic symbols are shown as Unicode.
- **My Books tab**: search your shelf, add your own `.epub` files with **+**, and long-press a book to remove it. You can also use "Open with → Bookscrolling" from a file manager.
- **Bookmarks tab**: every bookmarked passage and every comment, across all books and articles. Tap one to jump to that paragraph.
- A minimal black-and-white interface with a serif reading font. It follows the system light/dark setting.

Bookmarks, comments and books are stored on the device, so no account is needed.

SEP articles are copyrighted by their authors and the Metaphysics Research Lab at Stanford, so they're fetched on demand for personal reading. Don't redistribute their content.

## Install

Download [`dist/Bookscrolling.apk`](dist/Bookscrolling.apk) to your phone and open it. You may need to allow "Install unknown apps". The APK is signed with a debug key for sideloading. Use your own signing config before publishing it to a store.

## Build

Requires JDK 17+ and the Android SDK (platform 35).

```sh
./gradlew :app:assembleRelease     # -> app/build/outputs/apk/release/app-release.apk
./gradlew :app:testDebugUnitTest   # EPUB parser + Gutenberg feed tests
```

## How it works

- `data/EpubParser.kt` reads the OPF package (metadata, cover, spine) and splits each chapter's XHTML into paragraph and heading posts with jsoup. A table, such as a table of contents, becomes a single post.
- `data/Sep.kt` parses the SEP table of contents (cached for a week) and each article's `#preamble` and `#main-text`. `data/HtmlText.kt` is the shared HTML-to-posts splitter.
- `data/Gutenberg.kt` uses Project Gutenberg's public OPDS catalog for browsing and search. It downloads the `.epub.noimages` edition, which is small and still includes the cover.
- `data/BookRepository.kt` parses a book once and caches its posts to a compact binary file. `data/Db.kt` (SQLite) stores books, reading position, bookmarks and comments.
- The UI is Jetpack Compose (`ui/`).
