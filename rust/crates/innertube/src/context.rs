//! InnerTube client contexts and request payloads.
//!
//! Mirrors the original Wearsic server's YTM client (WEB_REMIX) and the
//! documented InnerTube search endpoints. Public key and client version are
//! derived the same way the web app does — no credentials, no cookies
//! required.

use serde::Serialize;
use time::macros::format_description;
use time::OffsetDateTime;

/// Publisher of the `clientVersion` used in InnerTube requests, e.g.
/// `1.20260101.01.00` (same scheme as the original server).
pub fn client_version_now() -> String {
    let now = OffsetDateTime::now_utc();
    let format = format_description!("[year][month][day]");
    let yyyymmdd = now.format(&format).unwrap_or_else(|_| "20260101".into());
    format!("1.{yyyymmdd}.01.00")
}

/// InnerTube `client` context object.
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct InnerTubeClient {
    #[serde(rename = "clientName")]
    pub client_name: String,
    #[serde(rename = "clientVersion")]
    pub client_version: String,
    pub hl: String,
    pub gl: String,
}

impl InnerTubeClient {
    pub fn web_remix() -> Self {
        Self {
            client_name: "WEB_REMIX".into(),
            client_version: client_version_now(),
            hl: "en".into(),
            gl: "US".into(),
        }
    }
}

/// The `context` object sent with every InnerTube request.
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct InnerTubeContext {
    pub client: InnerTubeClient,
}

impl InnerTubeContext {
    pub fn web_remix() -> Self {
        Self { client: InnerTubeClient::web_remix() }
    }
}

/// Minimal request body for a search.
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SearchBody {
    pub context: InnerTubeContext,
    pub query: String,
    /// Optional filter parameter (e.g. songs). Omit for all-typed search.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub params: Option<String>,
}

/// Request body for a pagination (continuation) call.
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ContinuationBody {
    pub context: InnerTubeContext,
    pub continuation: String,
}

/// Request body for a browse call (album / playlist / artist pages).
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct BrowseBody {
    pub context: InnerTubeContext,
    pub browse_id: String,
    /// Optional page params (e.g. `wAEB` for radio). Omitted when absent.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub params: Option<String>,
}

/// Standard InnerTube search endpoint + public key (same as the YTM web
/// app / original Wearsic server; no authentication).
pub const YTM_SEARCH_URL: &str = "https://music.youtube.com/youtubei/v1/search";
pub const YTM_BROWSE_URL: &str = "https://music.youtube.com/youtubei/v1/browse";
pub const YTM_API_KEY: &str = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30";

pub fn search_url() -> String {
    format!("{YTM_SEARCH_URL}?alt=json&key={YTM_API_KEY}")
}

pub fn browse_url() -> String {
    format!("{YTM_BROWSE_URL}?alt=json&key={YTM_API_KEY}")
}
