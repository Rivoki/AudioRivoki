package com.example.audiobooks.ui

object BookNameFormatter {
    fun toCyrillicAnalog(input: String): String {
        val builder = StringBuilder()
        var index = 0
        while (index < input.length) {
            val current = input[index]
            val next = input.getOrNull(index + 1)
            val afterNext = input.getOrNull(index + 2)

            if (current == '-') {
                builder.append(' ')
                index++
                continue
            }

            if ((current == 'c' || current == 'C') && (next == 'e' || next == 'E') && isWordBoundary(afterNext)) {
                builder.append(if (current.isUpperCase() || next?.isUpperCase() == true) 'Ц' else 'ц')
                index += 2
                continue
            }

            if ((current == 'n' || current == 'N') && (next == 'c' || next == 'C') && isWordBoundary(afterNext)) {
                builder.append(if (current.isUpperCase()) 'Н' else 'н')
                builder.append(if (next?.isUpperCase() == true) 'Ц' else 'ц')
                index += 2
                continue
            }

            if ((current == 'i' || current == 'I') && (next == 'i' || next == 'I')) {
                builder.append(if (current.isUpperCase()) 'И' else 'и')
                builder.append(if (next?.isUpperCase() == true) 'Й' else 'й')
                index += 2
                continue
            }

            builder.append(mapChar(current))
            index++
        }
        return builder.toString()
    }

    private fun mapChar(char: Char): String {
        val upper = char.isUpperCase()
        val c = char.lowercaseChar()
        val mapped = when (c) {
            'a' -> "а"
            'b' -> "б"
            'c' -> "с"
            'd' -> "д"
            'e' -> "е"
            'f' -> "ф"
            'g' -> "г"
            'h' -> "х"
            'i' -> "и"
            'j' -> "й"
            'k' -> "к"
            'l' -> "л"
            'm' -> "м"
            'n' -> "н"
            'o' -> "о"
            'p' -> "п"
            'q' -> "к"
            'r' -> "р"
            's' -> "с"
            't' -> "т"
            'u' -> "у"
            'v' -> "в"
            'w' -> "в"
            'x' -> "кс"
            'y' -> "ы"
            'z' -> "з"
            else -> char.toString()
        }
        return if (upper) mapped.uppercase() else mapped
    }

    private fun isWordBoundary(char: Char?): Boolean {
        return char == null || !char.isLetterOrDigit()
    }
}
