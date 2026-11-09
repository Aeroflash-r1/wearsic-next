//! YouTube Music domain models — a common typed model for discovery results.
//!
//! Fields that a result type cannot have are simply omitted (Option::None);
//! no invented metadata is produced.

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct Thumbnail {
    pub url: String,
    pub width: Option<i64>,
    pub height: Option<i64>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct Artist {
    pub name: String,
    pub id: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct Album {
    pub name: String,
    pub id: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct Track {
    pub video_id: String,
    pub title: String,
    pub artists: Vec<Artist>,
    pub album: Option<Album>,
    pub duration_ms: Option<i64>,
    pub thumbnails: Vec<Thumbnail>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct ArtistResult {
    pub id: Option<String>,
    pub name: String,
    pub thumbnails: Vec<Thumbnail>,
    pub subscribers: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct AlbumResult {
    pub id: Option<String>,
    pub title: String,
    pub artist: Option<String>,
    pub year: Option<String>,
    pub thumbnails: Vec<Thumbnail>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct PlaylistResult {
    pub id: Option<String>,
    pub title: String,
    pub author: Option<String>,
    pub track_count: Option<String>,
    pub thumbnails: Vec<Thumbnail>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum MusicSearchResult {
    Track(Track),
    Artist(ArtistResult),
    Album(AlbumResult),
    Playlist(PlaylistResult),
}

/// One page of results plus an optional continuation token for the next page.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct SearchPage {
    pub results: Vec<MusicSearchResult>,
    pub continuation: Option<String>,
}

/// A browsable collection page: an album, a YouTube Music playlist, an
/// artist page, or a radio.
///
/// Only fields the response actually provides are populated — a playlist has
/// no `strapline` (artist), an album has no `album` carousel, etc.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct BrowsePage {
    /// Header title (album/playlist/artist name).
    pub title: String,
    /// Header subtitle line, e.g. "Album • 2000" or "Playlist • 2022".
    pub subtitle: Option<String>,
    /// Artist/owner line, e.g. the album artist.
    pub strapline: Option<String>,
    /// Secondary line, e.g. "10 songs • 42 minutes".
    pub second_subtitle: Option<String>,
    pub thumbnails: Vec<Thumbnail>,
    /// Playable tracks in page order (album/playlist songs, artist top songs).
    pub tracks: Vec<Track>,
    /// Albums shown on an artist page (empty elsewhere).
    pub albums: Vec<AlbumResult>,
    /// Token for the next page of tracks, when the page is paginated.
    pub continuation: Option<String>,
}
