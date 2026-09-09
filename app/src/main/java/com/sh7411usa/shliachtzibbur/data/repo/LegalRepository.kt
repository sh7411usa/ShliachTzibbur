package com.sh7411usa.shliachtzibbur.data.repo

import com.sh7411usa.shliachtzibbur.core.model.LegalDocument
import com.sh7411usa.shliachtzibbur.core.model.LegalKind
import com.sh7411usa.shliachtzibbur.core.net.TzibburApi
import com.sh7411usa.shliachtzibbur.core.result.ApiResult
import com.sh7411usa.shliachtzibbur.core.result.apiCatching

/** Privacy / terms documents, cached in memory for the session. */
class LegalRepository(private val api: TzibburApi) {

    private val cache = mutableMapOf<LegalKind, LegalDocument>()

    suspend fun document(kind: LegalKind): ApiResult<LegalDocument> = apiCatching {
        cache[kind] ?: api.getLegal(kind).also { cache[kind] = it }
    }
}
