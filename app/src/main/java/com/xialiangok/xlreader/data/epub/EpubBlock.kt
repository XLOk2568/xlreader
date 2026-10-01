package com.xialiangok.xlreader.data.epub

import androidx.compose.runtime.Immutable
import java.io.File

/**
 * 正文里的一个块。
 *
 * 之前正文只有纯文本，图片会被直接丢掉；现在图片也作为独立块参与排版，
 * 位置与原文一致。
 */
@Immutable
sealed interface EpubBlock {

    /** 一段文字。 */
    @Immutable
    data class Text(val text: String) : EpubBlock

    /** 一张可以直接解码的位图（jpg / png / gif / webp 等）。 */
    @Immutable
    data class Image(val file: File) : EpubBlock

    /**
     * 认得出是资源、但本应用渲染不了的东西。
     *
     * 目前主要是 SVG：`BitmapFactory` 完全不能解矢量图，
     * 而 EPUB3 的封面常常就是 `<svg><image href="cover.jpg"/></svg>` 这种写法，
     * 所以需要一个明确的占位提示，而不是静默漏掉。
     */
    @Immutable
    data class Unsupported(val name: String, val reason: String) : EpubBlock
}
