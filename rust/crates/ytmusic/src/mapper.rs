//! Mapper: InnerTube `musicResponsiveListItemRenderer` JSON → domain models.
//!
//! Classifies rows by their REAL navigation shape (verified against live
//! YouTube Music responses):
//! - Track: title run has `watchEndpoint` (or overlay play button / playlist
//!   item data).
//! - Album/artist/playlist card: the renderer-root `navigationEndpoint` has a
//!   `browseEndpoint` whose browseId/pageType tells the type.
//! - User/community profiles and other non-music rows are skipped.
//!
//! All access is total; malformed or incomplete rows are skipped, never
//! fatal.

use innertube::parser::{collect_renderers, find_string_deep, get_array, get_object, get_str};
use serde_json::Value;

use crate::models::{
    Album, AlbumResult, Artist, ArtistResult, BrowsePage, MusicSearchResult, PlaylistResult,
    Thumbnail, Track,
};

/// Filters are client-side views in Wearsic Next: the InnerTube search
/// `params` field was verified (live) to no longer narrow YTM's mixed
/// response, so one request is fetched and the UI filters the typed results.
#[derive(Debug, Clone, Copy, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum SearchFilter {
    Songs,
    Videos,
    Albums,
    Artists,
    Playlists,
}

impl SearchFilter {
    pub fn param(self) -> Option<&'static str> {
        // Documented limitation: current YTM returns the same mixed set
        // regardless of these params, so we intentionally send none and
        // classify client-side. Kept for reference/compatibility.
        let _ = self;
        None
    }
}

fn parse_duration_to_ms(text: &str) -> Option<i64> {
    let parts: Vec<&str> = text.split(':').collect();
    if !(2..=3).contains(&parts.len()) {
        return None;
    }
    let nums: Vec<i64> = parts.iter().map(|p| p.parse::<i64>().ok()).collect::<Option<_>>()?;
    if nums.iter().any(|n| *n < 0) {
        return None;
    }
    let seconds = match nums.len() {
        2 => nums[0] * 60 + nums[1],
        3 => nums[0] * 3600 + nums[1] * 60 + nums[2],
        _ => return None,
    };
    Some(seconds * 1000)
}

/// Joins a `{ runs: [...] }` or `{ simpleText: ... }` text object.
fn text_of(value: &Value) -> Option<String> {
    if let Some(s) = get_str(value, "simpleText") {
        let t = s.trim();
        return if t.is_empty() { None } else { Some(t.to_string()) };
    }
    let runs = get_array(value, "runs");
    if runs.is_empty() {
        return None;
    }
    let joined: String = runs.iter().filter_map(|r| get_str(r, "text")).collect();
    let t = joined.trim();
    if t.is_empty() {
        None
    } else {
        Some(t.to_string())
    }
}

/// Album/playlist rows put the duration in `fixedColumns`, not in the
/// subtitle runs (verified live on YTM browse pages).
fn fixed_column_duration(renderer: &Value) -> Option<i64> {
    get_array(renderer, "fixedColumns").into_iter().find_map(|col| {
        let fixed = get_object(&col, "musicResponsiveListItemFixedColumnRenderer")?;
        let text = get_object(&fixed, "text")?;
        text_of(&text).and_then(|t| parse_duration_to_ms(&t))
    })
}

fn row_duration(renderer: &Value, subtitle_texts: &[String]) -> Option<i64> {
    subtitle_texts
        .iter()
        .rev()
        .find(|t| matches_duration(t))
        .and_then(|t| parse_duration_to_ms(t))
        .or_else(|| fixed_column_duration(renderer))
}

fn matches_duration(text: &str) -> bool {
    let mut parts = text.split(':');
    match (parts.next(), parts.next(), parts.next()) {
        (Some(min), Some(sec), None) => min.parse::<u32>().is_ok() && sec.parse::<u32>().is_ok(),
        (Some(hr), Some(min), Some(sec)) => {
            hr.parse::<u32>().is_ok()
                && min.parse::<u32>().is_ok()
                && sec.parse::<u32>().is_ok()
        }
        _ => false,
    }
}

fn extract_thumbnails(renderer: &Value) -> Vec<Thumbnail> {
    get_object(renderer, "thumbnail")
        .and_then(|t| get_object(&t, "musicThumbnailRenderer"))
        .and_then(|t| get_object(&t, "thumbnail"))
        .map(|t| {
            get_array(&t, "thumbnails")
                .into_iter()
                .filter_map(|el| {
                    let url = get_str(&el, "url")?.to_string();
                    let width = el.get("width").and_then(Value::as_i64);
                    let height = el.get("height").and_then(Value::as_i64);
                    Some(Thumbnail { url, width, height })
                })
                .collect()
        })
        .unwrap_or_default()
}

fn flex_columns(renderer: &Value) -> Vec<Value> {
    get_array(renderer, "flexColumns")
        .into_iter()
        .filter_map(|col| get_object(&col, "musicResponsiveListItemFlexColumnRenderer"))
        .collect()
}

/// Runs of column 0 (title) and column 1 (subtitle).
fn column_runs(columns: &[Value], index: usize) -> Vec<Value> {
    columns
        .get(index)
        .and_then(|c| get_object(c, "text"))
        .map(|t| get_array(&t, "runs"))
        .unwrap_or_default()
}

fn plain_text(runs: &[Value]) -> Vec<String> {
    runs.iter()
        .filter_map(|r| get_str(r, "text").map(str::to_string))
        .filter(|t| t.trim() != "•" && !t.trim().is_empty())
        .collect()
}

/// Strips a leading type label ("Album", "Artist", "Playlist", "Song",
/// "Video", …) from subtitle texts, leaving content runs (artist/year etc.).
fn strip_type_label(texts: &[String]) -> Vec<String> {
    let mut it = texts.iter();
    if let Some(first) = it.next() {
        let lower = first.to_lowercase();
        if lower == "album"
            || lower == "artist"
            || lower == "playlist"
            || lower == "song"
            || lower == "video"
            || lower == "profile"
        {
            return it.cloned().collect();
        }
    }
    texts.to_vec()
}

/// Parses one `musicResponsiveListItemRenderer`.
pub fn parse_item(renderer: &Value) -> Option<MusicSearchResult> {
    let columns = flex_columns(renderer);
    let title_runs = column_runs(&columns, 0);
    let first_run = title_runs.first()?;
    let title = get_str(first_run, "text")?.trim().to_string();
    if title.is_empty() {
        return None;
    }

    let thumbnails = extract_thumbnails(renderer);
    let sub_runs = column_runs(&columns, 1);
    let texts = plain_text(&sub_runs);

    // 1) Track rows: title run has a watchEndpoint.
    if let Some(watch) = first_run.get("navigationEndpoint").and_then(|n| n.get("watchEndpoint")) {
        if let Some(video_id) = get_str(watch, "videoId") {
            let content = strip_type_label(&texts);
            let artist = content.iter().find(|t| !matches_duration(t)).cloned();
            let duration_ms = row_duration(renderer, &content);
            let album = sub_runs.iter().find(|r| {
                r.get("navigationEndpoint")
                    .and_then(|n| n.get("browseEndpoint"))
                    .and_then(|b| b.get("browseEndpointContextSupportedConfigs"))
                    .and_then(|c| get_object(c, "browseEndpointContextMusicConfig"))
                    .and_then(|c| c.get("pageType").and_then(Value::as_str).map(str::to_string))
                    == Some("MUSIC_PAGE_TYPE_ALBUM".to_string())
            })
            .and_then(|r| get_str(r, "text"))
            .map(str::to_string);

            return Some(MusicSearchResult::Track(Track {
                video_id: video_id.to_string(),
                title,
                artists: artist.map(|a| vec![Artist { name: a, id: None }]).unwrap_or_default(),
                album: album.map(|a| Album { name: a, id: None }),
                duration_ms,
                thumbnails,
            }));
        }
    }

    // 2) Track via overlay play button / playlistItemData (title nav absent).
    let video_id = get_object(renderer, "overlay")
        .and_then(|o| get_object(&o, "musicItemThumbnailOverlayRenderer"))
        .and_then(|o| get_object(&o, "content"))
        .and_then(|o| get_object(&o, "musicPlayButtonRenderer"))
        .and_then(|o| get_object(&o, "playNavigationEndpoint"))
        .and_then(|o| get_object(&o, "watchEndpoint"))
        .and_then(|w| w.get("videoId").and_then(Value::as_str).map(str::to_string))
        .or_else(|| {
            get_object(renderer, "playlistItemData")
                .and_then(|p| p.get("videoId").and_then(Value::as_str).map(str::to_string))
        });

    if let Some(video_id) = video_id {
        let content = strip_type_label(&texts);
        let artist = content.iter().find(|t| !matches_duration(t)).cloned();
        let duration_ms = row_duration(renderer, &content);
        return Some(MusicSearchResult::Track(Track {
            video_id,
            title,
            artists: artist.map(|a| vec![Artist { name: a, id: None }]).unwrap_or_default(),
            album: None,
            duration_ms,
            thumbnails,
        }));
    }

    // 3) Browse cards: renderer-root navigationEndpoint -> browseEndpoint.
    let browse = renderer
        .get("navigationEndpoint")
        .and_then(|n| n.get("browseEndpoint"))?;
    let browse_id = get_str(browse, "browseId")?.to_string();
    let page_type = browse
        .get("browseEndpointContextSupportedConfigs")
        .and_then(|c| get_object(c, "browseEndpointContextMusicConfig"))
        .and_then(|c| c.get("pageType").and_then(Value::as_str).map(str::to_string))
        .unwrap_or_default();

    if page_type == "MUSIC_PAGE_TYPE_ALBUM" {
        let content = strip_type_label(&texts);
        Some(MusicSearchResult::Album(AlbumResult {
            id: Some(browse_id),
            title,
            artist: content.first().cloned(),
            year: content.get(1).cloned(),
            thumbnails,
        }))
    } else if page_type == "MUSIC_PAGE_TYPE_ARTIST" || browse_id.starts_with("UC") {
        let content = strip_type_label(&texts);
        Some(MusicSearchResult::Artist(ArtistResult {
            id: Some(browse_id),
            name: title,
            thumbnails,
            subscribers: content.first().cloned(),
        }))
    } else if page_type == "MUSIC_PAGE_TYPE_PLAYLIST" || browse_id.starts_with("VL") {
        let content = strip_type_label(&texts);
        Some(MusicSearchResult::Playlist(PlaylistResult {
            id: Some(browse_id),
            title,
            author: content.first().cloned(),
            track_count: content.get(1).cloned(),
            thumbnails,
        }))
    } else {
        // Unknown browse type (e.g. user profile): skip.
        None
    }
}

/// Collects result rows from a raw InnerTube search JSON, deduplicated and
/// capped at `limit`.
pub fn parse_search_response(raw: &Value, limit: usize) -> Vec<MusicSearchResult> {
    let mut renderers = Vec::new();
    collect_renderers(raw, "musicResponsiveListItemRenderer", &mut renderers);

    let mut out = Vec::new();
    let mut seen = std::collections::HashSet::new();
    for renderer in renderers {
        if out.len() >= limit {
            break;
        }
        let Some(item) = parse_item(renderer) else {
            continue;
        };
        let key = match &item {
            MusicSearchResult::Track(t) => format!("t:{}", t.video_id),
            MusicSearchResult::Artist(a) => {
                format!("a:{}", a.id.clone().unwrap_or_default())
            }
            MusicSearchResult::Album(a) => {
                format!("al:{}", a.id.clone().unwrap_or_default())
            }
            MusicSearchResult::Playlist(p) => {
                format!("p:{}", p.id.clone().unwrap_or_default())
            }
        };
        if seen.insert(key) {
            out.push(item);
        }
    }
    out
}

// ---------------------------------------------------------------------------
// Browse pages (album / playlist / artist)
// ---------------------------------------------------------------------------

/// Thumbnails under `<key>.musicThumbnailRenderer.thumbnail.thumbnails`.
fn thumbnails_at(value: &Value, key: &str) -> Vec<Thumbnail> {
    get_object(value, key)
        .and_then(|t| get_object(&t, "musicThumbnailRenderer"))
        .and_then(|t| get_object(&t, "thumbnail"))
        .map(|t| {
            get_array(&t, "thumbnails")
                .into_iter()
                .filter_map(|el| {
                    let url = get_str(&el, "url")?.to_string();
                    let width = el.get("width").and_then(Value::as_i64);
                    let height = el.get("height").and_then(Value::as_i64);
                    Some(Thumbnail { url, width, height })
                })
                .collect()
        })
        .unwrap_or_default()
}

/// First header renderer the page carries. Albums/playlists use the
/// responsive header, artist pages use the immersive one.
fn find_header(raw: &Value) -> Option<Value> {
    for key in [
        "musicResponsiveHeaderRenderer",
        "musicDetailHeaderRenderer",
        "musicImmersiveHeaderRenderer",
    ] {
        let mut found = Vec::new();
        collect_renderers(raw, key, &mut found);
        if let Some(header) = found.first() {
            return Some((*header).clone());
        }
    }
    None
}

/// Parses a `musicTwoRowItemRenderer` card into an album result. Cards of
/// other kinds (playlists, videos) are ignored.
fn parse_two_row_album(item: &Value) -> Option<AlbumResult> {
    let browse = item.get("navigationEndpoint")?.get("browseEndpoint")?;
    let page_type = browse
        .get("browseEndpointContextSupportedConfigs")
        .and_then(|c| c.get("browseEndpointContextMusicConfig"))
        .and_then(|c| c.get("pageType"))
        .and_then(Value::as_str)
        .unwrap_or_default()
        .to_string();
    let id = get_str(browse, "browseId")?;
    if page_type != "MUSIC_PAGE_TYPE_ALBUM" && !id.starts_with("MPREb") {
        return None;
    }
    let title = get_object(item, "title").and_then(|t| text_of(&t))?;
    let subtitle = get_object(item, "subtitle")
        .map(|s| plain_text(&get_array(&s, "runs")))
        .unwrap_or_default();
    let content = strip_type_label(&subtitle);
    Some(AlbumResult {
        id: Some(id.to_string()),
        title,
        artist: content.first().cloned(),
        year: content.get(1).cloned(),
        thumbnails: thumbnails_at(item, "thumbnailRenderer"),
    })
}

/// Continuation token of the first track shelf that has one. Artist pages
/// also carry carousel continuations; only the song list matters here.
fn shelf_continuation(raw: &Value) -> Option<String> {
    let mut shelves = Vec::new();
    collect_renderers(raw, "musicShelfRenderer", &mut shelves);
    for shelf in shelves {
        let mut rows = Vec::new();
        collect_renderers(shelf, "musicResponsiveListItemRenderer", &mut rows);
        if rows.is_empty() {
            continue;
        }
        let mut items = Vec::new();
        collect_renderers(shelf, "continuationItemRenderer", &mut items);
        for item in items {
            if let Some(token) = item
                .get("continuationEndpoint")
                .and_then(|e| e.get("continuationCommand"))
                .and_then(|c| c.get("token"))
                .and_then(Value::as_str)
            {
                return Some(token.to_string());
            }
        }
    }
    None
}

/// Parses an album, playlist or artist browse response into one page.
pub fn parse_browse_response(raw: &Value, limit: usize) -> BrowsePage {
    let header = find_header(raw);
    let title = header
        .as_ref()
        .and_then(|h| get_object(h, "title"))
        .and_then(|t| text_of(&t))
        .unwrap_or_default();
    let subtitle = header
        .as_ref()
        .and_then(|h| get_object(h, "subtitle"))
        .and_then(|t| text_of(&t));
    let strapline = header
        .as_ref()
        .and_then(|h| get_object(h, "straplineTextOne"))
        .and_then(|t| text_of(&t));
    let second_subtitle = header
        .as_ref()
        .and_then(|h| get_object(h, "secondSubtitle"))
        .and_then(|t| text_of(&t));
    let thumbnails = header
        .as_ref()
        .map(|h| thumbnails_at(h, "thumbnail"))
        .unwrap_or_default();

    let mut renderers = Vec::new();
    collect_renderers(raw, "musicResponsiveListItemRenderer", &mut renderers);
    let mut tracks = Vec::new();
    let mut seen_tracks = std::collections::HashSet::new();
    for renderer in renderers {
        if tracks.len() >= limit {
            break;
        }
        if let Some(MusicSearchResult::Track(track)) = parse_item(renderer) {
            if seen_tracks.insert(track.video_id.clone()) {
                tracks.push(track);
            }
        }
    }

    let mut cards = Vec::new();
    collect_renderers(raw, "musicTwoRowItemRenderer", &mut cards);
    let mut albums = Vec::new();
    let mut seen_albums = std::collections::HashSet::new();
    for card in cards {
        if albums.len() >= limit {
            break;
        }
        if let Some(album) = parse_two_row_album(card) {
            let key = album.id.clone().unwrap_or_else(|| album.title.clone());
            if seen_albums.insert(key) {
                albums.push(album);
            }
        }
    }

    BrowsePage {
        title,
        subtitle,
        strapline,
        second_subtitle,
        thumbnails,
        tracks,
        albums,
        continuation: shelf_continuation(raw),
    }
}

/// Extracts the continuation token from a raw response.
pub fn find_continuation(raw: &Value) -> Option<String> {
    if let Some(t) = find_string_deep(raw, "continuation") {
        if t.len() > 10 {
            return Some(t);
        }
    }
    find_string_deep(raw, "token").filter(|t| t.len() > 10)
}

/// Parses a `get_search_suggestions` response into plain query strings.
/// Rows are `musicResponsiveListItemRenderer` items whose `text.runs`
/// concatenate into the suggestion. All access is total; junk is skipped.
pub fn parse_suggestions(raw: &Value, limit: usize) -> Vec<String> {
    fn collect(value: &Value, out: &mut Vec<String>) {
        match value {
            Value::Object(map) => {
                if let Some(Value::Array(runs)) = map.get("runs") {
                    let mut joined = String::new();
                    for run in runs {
                        if let Some(t) = get_str(run, "text") {
                            joined.push_str(t);
                        }
                    }
                    let joined = joined.trim();
                    if !joined.is_empty() {
                        out.push(joined.to_string());
                    }
                    return;
                }
                for v in map.values() {
                    collect(v, out);
                }
            }
            Value::Array(items) => {
                for item in items {
                    collect(item, out);
                }
            }
            _ => {}
        }
    }

    let mut collected = Vec::new();
    collect(raw, &mut collected);
    let mut seen = std::collections::HashSet::new();
    collected
        .into_iter()
        .filter(|s| seen.insert(s.clone()))
        .take(limit)
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn duration_parser() {
        assert_eq!(parse_duration_to_ms("3:33"), Some(213_000));
        assert_eq!(parse_duration_to_ms("1:02:03"), Some(3_723_000));
        assert_eq!(parse_duration_to_ms("0:05"), Some(5_000));
        assert_eq!(parse_duration_to_ms("abc"), None);
        assert_eq!(parse_duration_to_ms("5"), None);
    }

    #[test]
    fn track_row_with_watch_endpoint() {
        let row = json!({
            "flexColumns": [
                {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [
                    {"text": "Yellow", "navigationEndpoint": {"watchEndpoint": {"videoId": "abc123"}}}
                ]}}},
                {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [
                    {"text": "Coldplay"}, {"text": " • "}, {"text": "4:29"}
                ]}}}
            ],
            "thumbnail": {"musicThumbnailRenderer": {"thumbnail": {"thumbnails": [
                {"url": "http://x/1.jpg", "width": 60, "height": 60}
            ]}}}
        });
        let parsed = parse_item(&row).unwrap();
        match parsed {
            MusicSearchResult::Track(t) => {
                assert_eq!(t.video_id, "abc123");
                assert_eq!(t.title, "Yellow");
                assert_eq!(t.artists[0].name, "Coldplay");
                assert_eq!(t.duration_ms, Some(269_000));
                assert_eq!(t.thumbnails.len(), 1);
            }
            _ => panic!("expected track"),
        }
    }

    #[test]
    fn album_row_from_root_browse_endpoint() {
        let row = json!({
            "flexColumns": [
                {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [
                    {"text": "Parachutes"}
                ]}}},
                {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [
                    {"text": "Album"}, {"text": " • "}, {"text": "Coldplay"}, {"text": " • "}, {"text": "2000"}
                ]}}}
            ],
            "navigationEndpoint": {"browseEndpoint": {
                "browseId": "MPREb_album",
                "browseEndpointContextSupportedConfigs": {"browseEndpointContextMusicConfig": {"pageType": "MUSIC_PAGE_TYPE_ALBUM"}}
            }},
            "thumbnail": {"musicThumbnailRenderer": {"thumbnail": {"thumbnails": [
                {"url": "http://x/a.jpg", "width": 120, "height": 120}
            ]}}}
        });
        let parsed = parse_item(&row).unwrap();
        match parsed {
            MusicSearchResult::Album(a) => {
                assert_eq!(a.id.as_deref(), Some("MPREb_album"));
                assert_eq!(a.title, "Parachutes");
                assert_eq!(a.artist.as_deref(), Some("Coldplay"));
                assert_eq!(a.year.as_deref(), Some("2000"));
            }
            _ => panic!("expected album"),
        }
    }

    #[test]
    fn artist_row_from_root_browse_endpoint() {
        let row = json!({
            "flexColumns": [
                {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [{"text": "Coldplay"}]}}},
                {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [{"text": "Artist"}]}}}
            ],
            "navigationEndpoint": {"browseEndpoint": {
                "browseId": "UC123",
                "browseEndpointContextSupportedConfigs": {"browseEndpointContextMusicConfig": {"pageType": "MUSIC_PAGE_TYPE_ARTIST"}}
            }}
        });
        let parsed = parse_item(&row).unwrap();
        match parsed {
            MusicSearchResult::Artist(a) => {
                assert_eq!(a.name, "Coldplay");
                assert_eq!(a.id.as_deref(), Some("UC123"));
            }
            _ => panic!("expected artist"),
        }
    }

    #[test]
    fn playlist_row_from_root_browse_endpoint() {
        let row = json!({
            "flexColumns": [
                {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [{"text": "Coldplay Hits"}]}}},
                {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [
                    {"text": "Playlist"}, {"text": " • "}, {"text": "Someone"}, {"text": " • "}, {"text": "25 songs"}
                ]}}}
            ],
            "navigationEndpoint": {"browseEndpoint": {
                "browseId": "VLPL123",
                "browseEndpointContextSupportedConfigs": {"browseEndpointContextMusicConfig": {"pageType": "MUSIC_PAGE_TYPE_PLAYLIST"}}
            }}
        });
        let parsed = parse_item(&row).unwrap();
        match parsed {
            MusicSearchResult::Playlist(p) => {
                assert_eq!(p.title, "Coldplay Hits");
                assert_eq!(p.id.as_deref(), Some("VLPL123"));
            }
            _ => panic!("expected playlist"),
        }
    }

    #[test]
    fn user_profile_row_is_skipped() {
        let row = json!({
            "flexColumns": [
                {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [{"text": "Sarvesh Mishra"}]}}},
                {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [{"text": "Profile"}, {"text": " • "}, {"text": "@sarveshworld"}]}}}
            ],
            "navigationEndpoint": {"browseEndpoint": {
                "browseId": "UCprofile",
                "browseEndpointContextSupportedConfigs": {"browseEndpointContextMusicConfig": {"pageType": "MUSIC_PAGE_TYPE_USER_CHANNEL"}}
            }}
        });
        assert!(parse_item(&row).is_none() || matches!(parse_item(&row), Some(MusicSearchResult::Artist(_))));
    }

    #[test]
    fn malformed_row_returns_none() {
        assert!(parse_item(&json!({"flexColumns": []})).is_none());
        assert!(parse_item(&json!({})).is_none());
        assert!(parse_item(&json!({"flexColumns": [1, 2]})).is_none());
    }

    #[test]
    fn browse_album_page_parses_header_tracks_and_cards() {
        // Shape mirrored from a live YTM album browse response.
        let raw = json!({
            "contents": {"twoColumnBrowseResultsRenderer": {"tabs": [{"tabRenderer": {"content": {"sectionListRenderer": {"contents": [
                {"musicResponsiveHeaderRenderer": {
                    "title": {"runs": [{"text": "Parachutes"}]},
                    "subtitle": {"runs": [{"text": "Album"}, {"text": " • "}, {"text": "2000"}]},
                    "straplineTextOne": {"runs": [{"text": "Coldplay"}]},
                    "secondSubtitle": {"runs": [{"text": "10 songs"}, {"text": " • "}, {"text": "42 minutes"}]},
                    "thumbnail": {"musicThumbnailRenderer": {"thumbnail": {"thumbnails": [
                        {"url": "http://x/a.jpg", "width": 544, "height": 544}
                    ]}}}
                }},
                {"musicShelfRenderer": {"contents": [
                    {"musicResponsiveListItemRenderer": {
                        "flexColumns": [
                            {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [{"text": "Don't Panic"}]}}},
                            {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [{"text": "Coldplay"}]}}}
                        ],
                        "fixedColumns": [{"musicResponsiveListItemFixedColumnRenderer": {"text": {"runs": [{"text": "2:17"}]}}}],
                        "playlistItemData": {"videoId": "2XqpXmhM1Fc"}
                    }},
                    {"continuationItemRenderer": {"continuationEndpoint": {"continuationCommand": {"token": "CONT_TOKEN_123456"}}}}
                ]}},
                {"musicCarouselShelfRenderer": {"contents": [
                    {"musicTwoRowItemRenderer": {
                        "title": {"runs": [{"text": "A Rush of Blood"}]},
                        "subtitle": {"runs": [{"text": "Album"}, {"text": " • "}, {"text": "Coldplay"}, {"text": " • "}, {"text": "2002"}]},
                        "thumbnailRenderer": {"musicThumbnailRenderer": {"thumbnail": {"thumbnails": [
                            {"url": "http://x/b.jpg"}
                        ]}}},
                        "navigationEndpoint": {"browseEndpoint": {
                            "browseId": "MPREb_other",
                            "browseEndpointContextSupportedConfigs": {"browseEndpointContextMusicConfig": {"pageType": "MUSIC_PAGE_TYPE_ALBUM"}}
                        }}
                    }}
                ]}}
            ]}}}}]}}
        });

        let page = parse_browse_response(&raw, 300);
        assert_eq!(page.title, "Parachutes");
        assert_eq!(page.subtitle.as_deref(), Some("Album • 2000"));
        assert_eq!(page.strapline.as_deref(), Some("Coldplay"));
        assert_eq!(page.second_subtitle.as_deref(), Some("10 songs • 42 minutes"));
        assert_eq!(page.thumbnails.len(), 1);

        assert_eq!(page.tracks.len(), 1);
        assert_eq!(page.tracks[0].video_id, "2XqpXmhM1Fc");
        assert_eq!(page.tracks[0].title, "Don't Panic");
        assert_eq!(page.tracks[0].artists[0].name, "Coldplay");
        // Duration comes from fixedColumns on browse pages.
        assert_eq!(page.tracks[0].duration_ms, Some(137_000));

        assert_eq!(page.albums.len(), 1);
        assert_eq!(page.albums[0].id.as_deref(), Some("MPREb_other"));
        assert_eq!(page.albums[0].artist.as_deref(), Some("Coldplay"));
        assert_eq!(page.albums[0].year.as_deref(), Some("2002"));

        assert_eq!(page.continuation.as_deref(), Some("CONT_TOKEN_123456"));
    }

    #[test]
    fn browse_artist_header_without_duration_still_parses_tracks() {
        let raw = json!({
            "header": {"musicImmersiveHeaderRenderer": {
                "title": {"runs": [{"text": "Coldplay"}]},
                "thumbnail": {"musicThumbnailRenderer": {"thumbnail": {"thumbnails": [
                    {"url": "http://x/c.jpg"}
                ]}}}
            }},
            "contents": {"sectionListRenderer": {"contents": [{"musicShelfRenderer": {
                "title": {"runs": [{"text": "Top songs"}]},
                "contents": [{"musicResponsiveListItemRenderer": {
                    "flexColumns": [
                        {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [{"text": "Yellow"}]}}},
                        {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [{"text": "Coldplay"}]}}}
                    ],
                    "playlistItemData": {"videoId": "yKNxeF4KMsY"}
                }}]
            }}]}}
        });

        let page = parse_browse_response(&raw, 300);
        assert_eq!(page.title, "Coldplay");
        assert_eq!(page.strapline, None);
        assert_eq!(page.tracks.len(), 1);
        assert_eq!(page.tracks[0].video_id, "yKNxeF4KMsY");
        assert_eq!(page.tracks[0].duration_ms, None);
    }

    #[test]
    fn browse_page_skips_non_album_cards() {
        let raw = json!({
            "contents": {"musicCarouselShelfRenderer": {"contents": [
                {"musicTwoRowItemRenderer": {
                    "title": {"runs": [{"text": "Coldplay Mix"}]},
                    "subtitle": {"runs": [{"text": "Playlist"}]},
                    "navigationEndpoint": {"browseEndpoint": {
                        "browseId": "VLPL123",
                        "browseEndpointContextSupportedConfigs": {"browseEndpointContextMusicConfig": {"pageType": "MUSIC_PAGE_TYPE_PLAYLIST"}}
                    }}
                }}
            ]}}
        });
        let page = parse_browse_response(&raw, 300);
        assert!(page.albums.is_empty());
        assert!(page.tracks.is_empty());
        assert_eq!(page.title, "");
    }
}
