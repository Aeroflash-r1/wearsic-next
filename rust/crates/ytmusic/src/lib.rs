//! YouTube Music discovery (Phase 4): InnerTube-based search.

pub mod mapper;
pub mod models;
pub mod service;

pub use mapper::SearchFilter;
pub use models::{
    Album, AlbumResult, Artist, ArtistResult, MusicSearchResult, PlaylistResult, SearchPage,
    Thumbnail, Track,
};
pub use service::YtMusicService;
