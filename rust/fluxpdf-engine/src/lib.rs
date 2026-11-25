//! FluxPDF Native Rust Document Engine
//!
//! Zero-copy memory-mapped PDF structural parser, FlateDecode content stream
//! text extractor, and fast case-insensitive/whole-word search index exposed
//! to Android via JNI (`com.example.engine.FluxNativeBridge`).

pub mod parser;
pub mod search;

use jni::objects::{JClass, JString};
use jni::sys::{jint, jlong, jstring};
use jni::JNIEnv;
use once_cell::sync::Lazy;
use parking_lot::RwLock;
use std::collections::HashMap;
use std::fs::File;
use std::sync::atomic::{AtomicI64, Ordering};
use std::sync::Arc;

use crate::parser::MappedPdfDocument;
use crate::search::SearchOptions;

static NEXT_HANDLE: AtomicI64 = AtomicI64::new(1);
static DOCUMENTS: Lazy<RwLock<HashMap<i64, Arc<MappedPdfDocument>>>> =
    Lazy::new(|| RwLock::new(HashMap::new()));

#[no_mangle]
#[allow(unused_mut)]
pub extern "system" fn Java_com_example_engine_FluxNativeBridge_nativeEngineVersion<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jstring {
    env.new_string("fluxpdf-rust-0.1.0 (mmap + flate2)")
        .expect("Failed to allocate version string")
        .into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_example_engine_FluxNativeBridge_nativeOpenDocument<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    path: JString<'local>,
) -> jlong {
    let path_str: String = match env.get_string(&path) {
        Ok(s) => s.into(),
        Err(_) => return -1,
    };

    let file = match File::open(&path_str) {
        Ok(f) => f,
        Err(_) => return -1,
    };

    match MappedPdfDocument::open(file) {
        Ok(doc) => {
            let handle = NEXT_HANDLE.fetch_add(1, Ordering::Relaxed);
            DOCUMENTS.write().insert(handle, Arc::new(doc));
            handle
        }
        Err(_) => -1,
    }
}

#[no_mangle]
pub extern "system" fn Java_com_example_engine_FluxNativeBridge_nativeCloseDocument<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
) {
    DOCUMENTS.write().remove(&handle);
}

#[no_mangle]
#[allow(unused_mut)]
pub extern "system" fn Java_com_example_engine_FluxNativeBridge_nativeGetMetadataJson<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
) -> jstring {
    let doc = DOCUMENTS.read().get(&handle).cloned();
    let json = match doc {
        Some(d) => d.metadata_json(),
        None => "{}".to_string(),
    };
    env.new_string(json)
        .expect("Failed to allocate metadata JSON")
        .into_raw()
}

#[no_mangle]
#[allow(unused_mut)]
pub extern "system" fn Java_com_example_engine_FluxNativeBridge_nativeExtractPageText<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    page_index: jint,
) -> jstring {
    let doc = DOCUMENTS.read().get(&handle).cloned();
    let text = match doc {
        Some(d) => d.extract_page_text(page_index.max(0) as usize),
        None => String::new(),
    };
    env.new_string(text)
        .expect("Failed to allocate extracted text")
        .into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_example_engine_FluxNativeBridge_nativeSearchJson<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    query: JString<'local>,
    case_sensitive: jint,
    whole_word: jint,
) -> jstring {
    let query_str: String = match env.get_string(&query) {
        Ok(s) => s.into(),
        Err(_) => {
            return env
                .new_string("[]")
                .expect("Failed to allocate empty JSON")
                .into_raw()
        }
    };
    let doc = DOCUMENTS.read().get(&handle).cloned();
    let results = match doc {
        Some(d) => {
            let opts = SearchOptions {
                case_sensitive: case_sensitive != 0,
                whole_word: whole_word != 0,
                max_hits: 500,
            };
            search::search_document(&d, &query_str, opts)
        }
        None => "[]".to_string(),
    };
    env.new_string(results)
        .expect("Failed to allocate search JSON")
        .into_raw()
}

#[cfg(test)]
mod tests {
    use super::*;
    use flate2::write::ZlibEncoder;
    use flate2::Compression;
    use std::io::Write;

    #[test]
    fn parses_minimal_flate_pdf_and_searches() {
        let mut encoder = ZlibEncoder::new(Vec::new(), Compression::default());
        encoder
            .write_all(b"BT /F1 12 Tf 50 700 Td (FluxPDF Rust Engine Verified) Tj ET")
            .unwrap();
        let compressed = encoder.finish().unwrap();

        let mut pdf = Vec::new();
        pdf.extend_from_slice(b"%PDF-1.7\n");
        let off1 = pdf.len();
        pdf.extend_from_slice(b"1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
        let off2 = pdf.len();
        pdf.extend_from_slice(b"2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n");
        let off3 = pdf.len();
        pdf.extend_from_slice(
            b"3 0 obj\n<< /Type /Page /Parent 2 0 R /Contents 4 0 R >>\nendobj\n",
        );
        let off4 = pdf.len();
        pdf.extend_from_slice(
            format!(
                "4 0 obj\n<< /Filter /FlateDecode /Length {} >>\nstream\n",
                compressed.len()
            )
            .as_bytes(),
        );
        pdf.extend_from_slice(&compressed);
        pdf.extend_from_slice(b"\nendstream\nendobj\n");
        let xref = pdf.len();
        pdf.extend_from_slice(
            format!(
                "xref\n0 5\n0000000000 65535 f \n{:010} 00000 n \n{:010} 00000 n \n{:010} 00000 n \n{:010} 00000 n \ntrailer\n<< /Size 5 /Root 1 0 R >>\nstartxref\n{}\n%%EOF\n",
                off1, off2, off3, off4, xref
            )
            .as_bytes(),
        );

        let path = std::env::temp_dir().join("fluxpdf_rust_unit_test.pdf");
        std::fs::write(&path, &pdf).unwrap();
        let file = File::open(&path).unwrap();
        let doc = MappedPdfDocument::open(file).unwrap();
        assert_eq!(doc.page_count(), 1);
        let text = doc.extract_page_text(0);
        assert!(text.contains("FluxPDF Rust Engine Verified"), "got: {text}");
        let hits = search::search_document(
            &doc,
            "verified",
            SearchOptions {
                case_sensitive: false,
                whole_word: true,
                max_hits: 10,
            },
        );
        assert!(hits.contains("\"page\":0"), "hits: {hits}");
        let _ = std::fs::remove_file(&path);
    }
}
