//! Structured response.

use std::collections::BTreeMap;

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct Response {
    pub status: u16,
    pub headers: BTreeMap<String, String>,
    pub body: Vec<u8>,
}

impl Response {
    pub fn text(&self) -> Result<String, crate::NetworkError> {
        String::from_utf8(self.body.clone()).map_err(|_| {
            crate::NetworkError::new(
                crate::NetworkErrorKind::InvalidResponse,
                "response body was not valid UTF-8",
            )
        })
    }

    pub fn json<T: serde::de::DeserializeOwned>(&self) -> Result<T, crate::NetworkError> {
        serde_json::from_slice(&self.body).map_err(|e| {
            crate::NetworkError::new(
                crate::NetworkErrorKind::InvalidResponse,
                format!("invalid JSON: {e}"),
            )
        })
    }

    pub fn is_success(&self) -> bool {
        (200..300).contains(&self.status)
    }
}
