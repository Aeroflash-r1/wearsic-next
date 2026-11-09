//! InnerTube client — builds typed requests and hands them to the shared
//! Phase 3 network engine. No HTTP stack of its own.

pub mod context;
pub mod parser;

use network::{CancellationToken, HttpClient, NetworkError, Request};

use context::{BrowseBody, ContinuationBody, SearchBody, SuggestBody};

/// Errors specific to InnerTube interaction. Reuses [`NetworkError`] for
/// transport-level failures and adds service-level errors.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum InnerTubeError {
    Network(NetworkError),
    /// The response was not valid JSON, or lacked any recognizable content.
    InvalidResponse(String),
    /// The service returned an error envelope (e.g. "Precondition check failed").
    ServiceError(String),
}

impl From<NetworkError> for InnerTubeError {
    fn from(e: NetworkError) -> Self {
        InnerTubeError::Network(e)
    }
}

impl std::fmt::Display for InnerTubeError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            InnerTubeError::Network(e) => write!(f, "network: {e}"),
            InnerTubeError::InvalidResponse(m) => write!(f, "invalid response: {m}"),
            InnerTubeError::ServiceError(m) => write!(f, "service error: {m}"),
        }
    }
}

/// Browse pages (long playlists, artist discographies) legitimately exceed
/// the network engine's 1 MiB default. The client clamps this to its own
/// hard ceiling.
const BROWSE_MAX_RESPONSE_BYTES: usize = 4 * 1024 * 1024;

/// A thin client over the Phase 3 shared HTTP client. One instance is cheap
/// (it only borrows the shared client).
#[derive(Clone)]
pub struct InnerTubeClient {
    http: HttpClient,
}

impl InnerTubeClient {
    pub fn new(http: HttpClient) -> Self {
        Self { http }
    }

    /// POSTs a typed body to an InnerTube endpoint and returns the raw JSON.
    pub async fn post_json(
        &self,
        url: &str,
        body: &impl serde::Serialize,
        token: &CancellationToken,
    ) -> Result<serde_json::Value, InnerTubeError> {
        self.post_json_capped(url, body, None, token).await
    }

    /// Like [Self::post_json] with an optional response-size override.
    async fn post_json_capped(
        &self,
        url: &str,
        body: &impl serde::Serialize,
        max_response_bytes: Option<usize>,
        token: &CancellationToken,
    ) -> Result<serde_json::Value, InnerTubeError> {
        let payload = serde_json::to_string(body)
            .map_err(|e| InnerTubeError::InvalidResponse(format!("serialize: {e}")))?;

        let mut request = Request::post(url, payload.into_bytes())
            .with_header("content-type", "application/json")
            .with_header("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:88.0) Gecko/20100101 Firefox/88.0");
        if let Some(limit) = max_response_bytes {
            request = request.with_max_response_bytes(limit);
        }

        let response = self
            .http
            .execute(request, token)
            .await
            .map_err(InnerTubeError::from)?;

        let text = response.text().map_err(|e| InnerTubeError::Network(e))?;
        serde_json::from_str(&text)
            .map_err(|e| InnerTubeError::InvalidResponse(format!("parse: {e}")))
    }

    /// POSTs a raw request (for player requests that need custom headers)
    /// and returns the parsed JSON.
    pub async fn post_raw(
        &self,
        request: &Request,
        token: &CancellationToken,
    ) -> Result<serde_json::Value, InnerTubeError> {
        let response = self
            .http
            .execute(request.clone(), token)
            .await
            .map_err(InnerTubeError::from)?;
        let text = response.text().map_err(|e| InnerTubeError::Network(e))?;
        serde_json::from_str(&text)
            .map_err(|e| InnerTubeError::InvalidResponse(format!("parse: {e}")))
    }

    /// Search with an optional filter param.
    pub async fn search(
        &self,
        query: &str,
        params: Option<&str>,
        token: &CancellationToken,
    ) -> Result<serde_json::Value, InnerTubeError> {
        let body = SearchBody {
            context: context::InnerTubeContext::web_remix(),
            query: query.to_string(),
            params: params.map(str::to_string),
        };
        self.post_json(&context::search_url(), &body, token).await
    }

    /// Fetch search suggestions for `input` (the live "did you mean" rows
    /// shown while typing).
    pub async fn suggestions(
        &self,
        input: &str,
        token: &CancellationToken,
    ) -> Result<serde_json::Value, InnerTubeError> {
        let body = SuggestBody {
            context: context::InnerTubeContext::web_remix(),
            input: input.to_string(),
        };
        self.post_json(&context::suggest_url(), &body, token).await
    }

    /// Fetch a continuation page.
    pub async fn continuation(
        &self,
        continuation: &str,
        token: &CancellationToken,
    ) -> Result<serde_json::Value, InnerTubeError> {
        let body = ContinuationBody {
            context: context::InnerTubeContext::web_remix(),
            continuation: continuation.to_string(),
        };
        self.post_json(&context::search_url(), &body, token).await
    }

    /// Fetch a browse page: album (`MPREb…`), playlist (`VL…`), artist
    /// (`UC…`) or radio (`RDAMVM…`).
    pub async fn browse(
        &self,
        browse_id: &str,
        params: Option<&str>,
        token: &CancellationToken,
    ) -> Result<serde_json::Value, InnerTubeError> {
        let body = BrowseBody {
            context: context::InnerTubeContext::web_remix(),
            browse_id: browse_id.to_string(),
            params: params.map(str::to_string),
        };
        self.post_json_capped(
            &context::browse_url(),
            &body,
            Some(BROWSE_MAX_RESPONSE_BYTES),
            token,
        )
        .await
    }

    /// Fetch the next page of a browse result (album track list, playlist).
    pub async fn browse_continuation(
        &self,
        continuation: &str,
        token: &CancellationToken,
    ) -> Result<serde_json::Value, InnerTubeError> {
        let body = ContinuationBody {
            context: context::InnerTubeContext::web_remix(),
            continuation: continuation.to_string(),
        };
        self.post_json_capped(
            &context::browse_url(),
            &body,
            Some(BROWSE_MAX_RESPONSE_BYTES),
            token,
        )
        .await
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn search_body_serializes_with_defaults() {
        // The original server noted that encodeDefaults is REQUIRED: without
        // it, clientName/hl/gl dropped and the service 400'd. serde always
        // serializes non-optional fields, so this is safe by construction.
        let body = SearchBody {
            context: context::InnerTubeContext::web_remix(),
            query: "test".into(),
            params: Some("EgWKAQIIAWoMEA4QChADEAQQCRAF".into()),
        };
        let s = serde_json::to_string(&body).unwrap();
        assert!(s.contains("\"clientName\":\"WEB_REMIX\""));
        assert!(s.contains("\"hl\":\"en\""));
        assert!(s.contains("\"gl\":\"US\""));
        assert!(s.contains("\"params\":\"EgWKAQIIAWoMEA4QChADEAQQCRAF\""));
    }

    #[test]
    fn search_body_allows_missing_params() {
        let body = SearchBody {
            context: context::InnerTubeContext::web_remix(),
            query: "test".into(),
            params: None,
        };
        let s = serde_json::to_string(&body).unwrap();
        assert!(!s.contains("\"params\""));
    }

    #[test]
    fn browse_body_serializes_browse_id_and_omits_params() {
        let body = BrowseBody {
            context: context::InnerTubeContext::web_remix(),
            browse_id: "MPREb_album".into(),
            params: None,
        };
        let s = serde_json::to_string(&body).unwrap();
        assert!(s.contains("\"browseId\":\"MPREb_album\""));
        assert!(!s.contains("\"params\""));
    }
}
