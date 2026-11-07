//! Wearsic Next stream resolution (Phase 5).

pub mod error;
pub mod format;
pub mod models;
pub mod player_response;
pub mod resolver;
pub mod validation;

pub use error::{ResolveError, ResolveErrorKind};
pub use models::{AudioFormat, StreamDescriptor};
pub use resolver::StreamResolver;
