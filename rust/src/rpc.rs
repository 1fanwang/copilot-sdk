//! JSON-RPC request/response types and typed namespace builders.
//!
//! Types are auto-generated from the Copilot CLI protocol schemas, with
//! handwritten convenience methods where needed.
//! This module is the stable public access point — the underlying
//! crate-private modules where the types are defined are an
//! implementation detail whose layout may change.
//!
//! Use the [`crate::Client::rpc`] and [`crate::session::Session::rpc`] helper
//! methods to obtain a typed view over the protocol surface.

pub use crate::generated::api_types::*;
pub use crate::generated::rpc::*;

impl SendRequest {
    /// Set the message provenance tag without changing billing or delivery options.
    ///
    /// See [`crate::types::MessageOptions::source`] for accepted forms and
    /// remote-delivery limitations. When unset, the tag is omitted.
    pub fn with_source(mut self, source: impl Into<String>) -> Self {
        self.source = Some(source.into());
        self
    }
}
