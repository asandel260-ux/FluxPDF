use crate::parser::MappedPdfDocument;

pub struct SearchOptions {
    pub case_sensitive: bool,
    pub whole_word: bool,
    pub max_hits: usize,
}

fn floor_utf8_boundary(s: &str, mut index: usize) -> usize {
    if index >= s.len() {
        return s.len();
    }
    while index > 0 && !s.is_char_boundary(index) {
        index -= 1;
    }
    index
}

fn ceil_utf8_boundary(s: &str, mut index: usize) -> usize {
    if index >= s.len() {
        return s.len();
    }
    while index < s.len() && !s.is_char_boundary(index) {
        index += 1;
    }
    index
}

pub fn search_document(doc: &MappedPdfDocument, query: &str, opts: SearchOptions) -> String {
    if query.is_empty() {
        return "[]".to_string();
    }

    let needle = if opts.case_sensitive {
        query.to_string()
    } else {
        query.to_lowercase()
    };

    let mut entries = Vec::new();
    for page in 0..doc.page_count() {
        let text = doc.extract_page_text(page);
        let haystack = if opts.case_sensitive {
            text.clone()
        } else {
            text.to_lowercase()
        };

        let mut search_from = 0usize;
        while search_from < haystack.len() {
            let Some(rel) = haystack[search_from..].find(&needle) else {
                break;
            };
            let start = search_from + rel;
            let end = start + needle.len();
            let valid = if opts.whole_word {
                let left_ok = start == 0
                    || !haystack[..start]
                        .chars()
                        .next_back()
                        .map(|c| c.is_alphanumeric())
                        .unwrap_or(false);
                let right_ok = end >= haystack.len()
                    || !haystack[end..]
                        .chars()
                        .next()
                        .map(|c| c.is_alphanumeric())
                        .unwrap_or(false);
                left_ok && right_ok
            } else {
                true
            };

            if valid {
                let snip_start = floor_utf8_boundary(&text, start.saturating_sub(42));
                let snip_end = ceil_utf8_boundary(&text, (end + 56).min(text.len()));
                let snippet = text[snip_start..snip_end]
                    .replace('\\', "\\\\")
                    .replace('"', "\\\"")
                    .replace('\n', " ");
                entries.push(format!(
                    "{{\"page\":{},\"offset\":{},\"snippet\":\"{}\"}}",
                    page, start, snippet
                ));
                if entries.len() >= opts.max_hits {
                    return format!("[{}]", entries.join(","));
                }
            }
            search_from = ceil_utf8_boundary(&haystack, end.max(search_from + 1));
        }
    }

    format!("[{}]", entries.join(","))
}
