use flate2::read::ZlibDecoder;
use memmap2::Mmap;
use std::collections::HashMap;
use std::fs::File;
use std::io::Read;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum PdfError {
    #[error("I/O error: {0}")]
    Io(#[from] std::io::Error),
    #[error("Invalid PDF header")]
    InvalidHeader,
}

#[derive(Debug, Clone)]
pub struct PdfMetadata {
    pub version: String,
    pub linearized: bool,
    pub object_count: usize,
    pub page_count: usize,
    pub title: String,
    pub author: String,
    pub creator: String,
    pub producer: String,
}

pub struct MappedPdfDocument {
    mmap: Mmap,
    xref: HashMap<u32, usize>,
    page_obj_ids: Vec<u32>,
    pub metadata: PdfMetadata,
}

impl MappedPdfDocument {
    pub fn open(file: File) -> Result<Self, PdfError> {
        let mmap = unsafe { Mmap::map(&file)? };
        if mmap.len() < 8 || !mmap.starts_with(b"%PDF-") {
            return Err(PdfError::InvalidHeader);
        }

        let version = std::str::from_utf8(&mmap[5..8.min(mmap.len())])
            .unwrap_or("1.7")
            .trim()
            .to_string();

        let head_slice = &mmap[..mmap.len().min(1024)];
        let linearized = find_bytes(head_slice, b"/Linearized").is_some();

        let xref = parse_classic_xref(&mmap);
        let page_obj_ids = discover_page_objects(&mmap, &xref);

        let metadata = PdfMetadata {
            version,
            linearized,
            object_count: xref.len(),
            page_count: page_obj_ids.len(),
            title: extract_info_field(&mmap, b"/Title"),
            author: extract_info_field(&mmap, b"/Author"),
            creator: extract_info_field(&mmap, b"/Creator"),
            producer: extract_info_field(&mmap, b"/Producer"),
        };

        Ok(Self {
            mmap,
            xref,
            page_obj_ids,
            metadata,
        })
    }

    pub fn page_count(&self) -> usize {
        self.page_obj_ids.len()
    }

    pub fn metadata_json(&self) -> String {
        format!(
            "{{\"version\":\"{}\",\"linearized\":{},\"objectCount\":{},\"pageCount\":{},\"title\":\"{}\",\"author\":\"{}\",\"creator\":\"{}\",\"producer\":\"{}\"}}",
            escape_json(&self.metadata.version),
            self.metadata.linearized,
            self.metadata.object_count,
            self.metadata.page_count,
            escape_json(&self.metadata.title),
            escape_json(&self.metadata.author),
            escape_json(&self.metadata.creator),
            escape_json(&self.metadata.producer),
        )
    }

    pub fn extract_page_text(&self, page_index: usize) -> String {
        let Some(&obj_id) = self.page_obj_ids.get(page_index) else {
            return String::new();
        };
        let Some(&offset) = self.xref.get(&obj_id) else {
            return String::new();
        };
        let obj_slice = slice_object(&self.mmap, offset);
        let content_refs = parse_content_refs(obj_slice);
        let mut out = String::new();
        for ref_id in content_refs {
            if let Some(&stream_off) = self.xref.get(&ref_id) {
                let raw = slice_object(&self.mmap, stream_off);
                if let Some(decoded) = decode_stream_bytes(raw) {
                    extract_text_operators(&decoded, &mut out);
                }
            }
        }
        out
    }
}

fn find_bytes(haystack: &[u8], needle: &[u8]) -> Option<usize> {
    if needle.is_empty() || needle.len() > haystack.len() {
        return None;
    }
    haystack.windows(needle.len()).position(|w| w == needle)
}

fn parse_classic_xref(data: &[u8]) -> HashMap<u32, usize> {
    let mut map = HashMap::new();
    let tail_start = data.len().saturating_sub(4096);
    let tail = &data[tail_start..];
    if let Some(pos) = find_bytes(tail, b"startxref") {
        let after = &tail[pos + 9..];
        if let Ok(s) = std::str::from_utf8(after) {
            if let Some(num_str) = s.split_whitespace().next() {
                if let Ok(xref_offset) = num_str.parse::<usize>() {
                    if xref_offset < data.len() && data[xref_offset..].starts_with(b"xref") {
                        parse_xref_section(&data[xref_offset..], &mut map);
                    }
                }
            }
        }
    }
    map
}

fn parse_xref_section(section: &[u8], map: &mut HashMap<u32, usize>) {
    let text = String::from_utf8_lossy(&section[..section.len().min(512 * 1024)]);
    let mut lines = text.lines();
    lines.next();
    while let Some(line) = lines.next() {
        let trimmed = line.trim();
        if trimmed.starts_with("trailer") {
            break;
        }
        let parts: Vec<&str> = trimmed.split_whitespace().collect();
        if parts.len() == 2 {
            if let (Ok(start_id), Ok(count)) = (parts[0].parse::<u32>(), parts[1].parse::<u32>()) {
                for idx in 0..count {
                    if let Some(entry) = lines.next() {
                        let eparts: Vec<&str> = entry.split_whitespace().collect();
                        if eparts.len() >= 3 && eparts[2] == "n" {
                            if let Ok(off) = eparts[0].parse::<usize>() {
                                map.insert(start_id + idx, off);
                            }
                        }
                    }
                }
            }
        }
    }
}

fn discover_page_objects(data: &[u8], xref: &HashMap<u32, usize>) -> Vec<u32> {
    let mut pages = Vec::new();
    let mut ids: Vec<u32> = xref.keys().copied().collect();
    ids.sort_unstable();
    for id in ids {
        if let Some(&off) = xref.get(&id) {
            let slice = slice_object(data, off);
            if find_bytes(slice, b"/Type /Page").is_some()
                && find_bytes(slice, b"/Type /Pages").is_none()
            {
                pages.push(id);
            }
        }
    }
    pages
}

fn slice_object(data: &[u8], offset: usize) -> &[u8] {
    if offset >= data.len() {
        return &[];
    }
    let window = &data[offset..data.len().min(offset + 256 * 1024)];
    match find_bytes(window, b"endobj") {
        Some(end) => &window[..end],
        None => window,
    }
}

fn parse_content_refs(page_dict: &[u8]) -> Vec<u32> {
    let mut refs = Vec::new();
    if let Some(pos) = find_bytes(page_dict, b"/Contents") {
        let tail = String::from_utf8_lossy(&page_dict[pos + 9..page_dict.len().min(pos + 256)]);
        let normalized = tail.replace('[', " ").replace(']', " ");
        let tokens: Vec<&str> = normalized.split_whitespace().collect();
        let mut i = 0;
        while i + 2 < tokens.len() {
            if tokens[i + 2] == "R" {
                if let Ok(id) = tokens[i].parse::<u32>() {
                    refs.push(id);
                }
                i += 3;
            } else if tokens[i].starts_with('/') {
                break;
            } else {
                i += 1;
            }
        }
    }
    refs
}

fn decode_stream_bytes(obj: &[u8]) -> Option<Vec<u8>> {
    let s_pos = find_bytes(obj, b"stream")?;
    let mut start = s_pos + 6;
    if start < obj.len() && obj[start] == b'\r' {
        start += 1;
    }
    if start < obj.len() && obj[start] == b'\n' {
        start += 1;
    }
    let end = find_bytes(&obj[start..], b"endstream")? + start;
    let payload = &obj[start..end];
    if find_bytes(&obj[..s_pos], b"/FlateDecode").is_some() {
        let mut decoder = ZlibDecoder::new(payload);
        let mut out = Vec::new();
        if decoder.read_to_end(&mut out).is_ok() {
            return Some(out);
        }
    }
    Some(payload.to_vec())
}

fn extract_text_operators(stream: &[u8], out: &mut String) {
    let mut i = 0;
    let len = stream.len();
    while i < len {
        if stream[i] == b'(' {
            i += 1;
            let mut depth = 1usize;
            while i < len && depth > 0 {
                let c = stream[i];
                if c == b'\\' && i + 1 < len {
                    i += 1;
                    match stream[i] {
                        b'n' => out.push('\n'),
                        b'r' => out.push('\r'),
                        b't' => out.push('\t'),
                        other => out.push(other as char),
                    }
                } else if c == b'(' {
                    depth += 1;
                    out.push('(');
                } else if c == b')' {
                    depth -= 1;
                    if depth > 0 {
                        out.push(')');
                    }
                } else if c.is_ascii_graphic() || c == b' ' {
                    out.push(c as char);
                }
                i += 1;
            }
            out.push(' ');
        } else {
            i += 1;
        }
    }
}

fn extract_info_field(data: &[u8], key: &[u8]) -> String {
    if let Some(pos) = find_bytes(data, key) {
        let rest = &data[pos + key.len()..data.len().min(pos + key.len() + 256)];
        if let Some(open) = rest.iter().position(|&b| b == b'(') {
            if let Some(close) = rest[open + 1..].iter().position(|&b| b == b')') {
                return String::from_utf8_lossy(&rest[open + 1..open + 1 + close]).to_string();
            }
        }
    }
    String::new()
}

fn escape_json(s: &str) -> String {
    s.replace('\\', "\\\\")
        .replace('"', "\\\"")
        .replace('\n', "\\n")
}
