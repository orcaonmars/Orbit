package com.hydrogen.ai.model

/** A pending export owns its original content even if a different answer becomes visible. */
data class CodeExportSnapshot(val fileName: String, val code: String) {
    companion object {
        fun capture(block: MdBlock.CodeBlock): CodeExportSnapshot? {
            if (!block.closed || block.code.isBlank()) return null
            return CodeExportSnapshot(CodeFileName.suggest(block.language, block.code), block.code)
        }
    }
}

object ReplyLayoutPolicy {
    /** Stack code actions on compact surfaces or at large system font sizes. */
    fun stackHeader(widthDp: Float, fontScale: Float): Boolean =
        widthDp < 280f * fontScale.coerceAtLeast(1f)
}