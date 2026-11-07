# Search Data Models

Rust (`ytmusic::models`) ↔ Kotlin (`SearchModels.kt`) — snake_case JSON.

| Rust | Kotlin | Fields |
|---|---|---|
| `Thumbnail` | `Thumbnail` | url, width?, height? |
| `Artist` | `Artist` | name, id? |
| `Album` | `Album` | name, id? |
| `Track` | `TrackDto` | video_id, title, artists[], album?, duration_ms?, thumbnails[] |
| `ArtistResult` | `ArtistDto` | id?, name, thumbnails[], subscribers? |
| `AlbumResult` | `AlbumDto` | id?, title, artist?, year?, thumbnails[] |
| `PlaylistResult` | `PlaylistDto` | id?, title, author?, track_count?, thumbnails[] |
| `MusicSearchResult` (enum) | `MusicSearchResultDto` (externally-tagged) → sealed `MusicSearchResult` | exactly one variant present |
| `SearchPage` | `SearchEnvelope` | results[], continuation? |

The externally-tagged enum serializes as `{"track":{…}}` / `{"artist":{…}}`
/ `{"album":{…}}` / `{"playlist":{…}}`; the Kotlin DTO holds all four
nullable fields and converts to a sealed interface.

Principles:

- No invented metadata: absent fields are `null`/empty.
- Unknown/malformed rows are skipped (a single bad row never fails a page).
- Identifiers and URLs are validated by consumers before use (Phase 5+).
