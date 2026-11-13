package com.quadplayer.feature.filebrowser

import java.text.CollationKey
import java.text.Collator
import java.util.Locale

/** Prepared once per name so sorting does not repeatedly allocate case-folded strings. */
internal class NaturalNameOrder(locale: Locale) {
    private val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }

    internal sealed class Part {
        class Text(val key: CollationKey) : Part()
        class Number(val significant: String, val length: Int) : Part()
    }

    fun key(name: String): List<Part> {
        val parts = mutableListOf<Part>()
        var start = 0
        while (start < name.length) {
            val isNumber = name[start] in '0'..'9'
            var end = start + 1
            while (end < name.length && (name[end] in '0'..'9') == isNumber) end++
            val chunk = name.substring(start, end)
            parts += if (isNumber) {
                Part.Number(chunk.trimStart('0').ifEmpty { "0" }, chunk.length)
            } else {
                Part.Text(collator.getCollationKey(chunk))
            }
            start = end
        }
        return parts
    }

    fun compare(left: List<Part>, right: List<Part>): Int {
        for (i in 0 until minOf(left.size, right.size)) {
            val a = left[i]
            val b = right[i]
            val result = when {
                a is Part.Number && b is Part.Number ->
                    compareValues(a.significant.length, b.significant.length).takeIf { it != 0 }
                        ?: a.significant.compareTo(b.significant).takeIf { it != 0 }
                        ?: compareValues(a.length, b.length)
                a is Part.Text && b is Part.Text -> a.key.compareTo(b.key)
                a is Part.Number -> -1
                else -> 1
            }
            if (result != 0) return result
        }
        return compareValues(left.size, right.size)
    }
}
