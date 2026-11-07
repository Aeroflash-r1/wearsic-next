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
