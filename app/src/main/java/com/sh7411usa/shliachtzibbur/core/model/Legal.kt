package com.sh7411usa.shliachtzibbur.core.model

enum class LegalKind(val slug: String) {
    PRIVACY("privacy"),
    TERMS("terms"),
}

/** A legal document from `GET /v1/legal/{privacy|terms}`. [text] is Markdown source. */
data class LegalDocument(
    val key: String,
    val text: String,
    val checksum: String,
)
