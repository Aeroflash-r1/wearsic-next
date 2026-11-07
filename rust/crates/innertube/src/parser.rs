//! Safe JSON traversal helpers for InnerTube responses.
//!
//! InnerTube responses are deeply nested and change over time. All access is
//! total (never panics on missing fields / wrong types), and individual rows
//! with incomplete metadata are skipped rather than failing a whole search.

use serde_json::Value;

/// Returns the `str` value of `key` if it exists and is a JSON string.
pub fn get_str<'a>(obj: &'a Value, key: &str) -> Option<&'a str> {
    obj.get(key).and_then(Value::as_str)
}

/// Returns the i64 value of `key` if it exists and is an integer.
pub fn get_i64(obj: &Value, key: &str) -> Option<i64> {
    obj.get(key).and_then(Value::as_i64)
}

pub fn get_array(obj: &Value, key: &str) -> Vec<Value> {
    obj.get(key).and_then(Value::as_array).cloned().unwrap_or_default()
}

pub fn get_object(obj: &Value, key: &str) -> Option<Value> {
    obj.get(key).and_then(Value::as_object).map(|m| Value::Object(m.clone()))
}

/// Depth-first search for the first occurrence of `key` as a string, anywhere
/// under `value`. Used for continuation tokens (which appear in several
/// container shapes).
pub fn find_string_deep(value: &Value, key: &str) -> Option<String> {
    match value {
        Value::Object(map) => {
            for (k, v) in map {
                if k == key {
                    if let Some(s) = v.as_str() {
                        return Some(s.to_string());
                    }
                }
                if let Some(found) = find_string_deep(v, key) {
                    return Some(found);
                }
            }
            None
        }
        Value::Array(items) => {
            for item in items {
                if let Some(found) = find_string_deep(item, key) {
                    return Some(found);
                }
            }
            None
        }
        _ => None,
    }
}

/// Walks the tree and collects every object that has `renderer_key` set.
/// This mirrors the original recursive `collectSongRenderers`: it is
/// layout-agnostic (musicShelfRenderer / musicCardShelfRenderer / future
/// renames all work).
pub fn collect_renderers<'a>(
    value: &'a Value,
    renderer_key: &str,
    out: &mut Vec<&'a Value>,
) {
    match value {
        Value::Object(map) => {
            if let Some(inner) = map.get(renderer_key) {
                out.push(inner);
            }
            for v in map.values() {
                collect_renderers(v, renderer_key, out);
            }
        }
        Value::Array(items) => {
            for item in items {
                collect_renderers(item, renderer_key, out);
            }
        }
        _ => {}
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn get_str_handles_missing() {
        let v = json!({"a": "x"});
        assert_eq!(get_str(&v, "a"), Some("x"));
        assert_eq!(get_str(&v, "b"), None);
        assert_eq!(get_str(&json!({"a": 5}), "a"), None);
    }

    #[test]
    fn find_string_deep_finds_continuation() {
        let v = json!({
            "contents": {
                "tabbedSearchResultsRenderer": {
                    "tabs": [{
                        "tabRenderer": {
                            "content": {
                                "sectionListRenderer": {
                                    "contents": [{
                                        "continuationItemRenderer": {
                                            "continuationEndpoint": {
                                                "continuationCommand": {
                                                    "token": "MY_TOKEN_123"
                                                }
                                            }
                                        }
                                    }]
                                }
                            }
                        }
                    }]
                }
            }
        });
        assert_eq!(find_string_deep(&v, "token"), Some("MY_TOKEN_123".into()));
    }

    #[test]
    fn collect_renderers_finds_all_rows() {
        let v = json!({
            "contents": [{
                "musicShelfRenderer": {
                    "contents": [
                        {"musicResponsiveListItemRenderer": {"a": 1}},
                        {"musicResponsiveListItemRenderer": {"a": 2}}
                    ]
                }
            }, {
                "musicCardShelfRenderer": {
                    "contents": [
                        {"musicResponsiveListItemRenderer": {"a": 3}}
                    ]
                }
            }]
        });
        let mut out = vec![];
        collect_renderers(&v, "musicResponsiveListItemRenderer", &mut out);
        assert_eq!(out.len(), 3);
    }

    #[test]
    fn collect_renderers_handles_non_object() {
        let v = json!([1, 2, "x"]);
        let mut out = vec![];
        collect_renderers(&v, "musicResponsiveListItemRenderer", &mut out);
        assert!(out.is_empty());
    }
}
