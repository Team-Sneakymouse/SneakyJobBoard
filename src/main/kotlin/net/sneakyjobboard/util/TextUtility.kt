package net.sneakyjobboard.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage

object TextUtility {

    fun convertToComponent(message: String): Component {
        var convertedMessage =
                message.replace("&1", "<dark_blue>")
                        .replace("&2", "<dark_green>")
                        .replace("&3", "<dark_aqua>")
                        .replace("&4", "<dark_red>")
                        .replace("&5", "<dark_purple>")
                        .replace("&6", "<gold>")
                        .replace("&7", "<gray>")
                        .replace("&8", "<dark_gray>")
                        .replace("&9", "<blue>")
                        .replace("&0", "<black>")
                        .replace("&a", "<green>")
                        .replace("&b", "<aqua>")
                        .replace("&c", "<red>")
                        .replace("&d", "<light_purple>")
                        .replace("&e", "<yellow>")
                        .replace("&f", "<white>")
                        .replace("&k", "<obf>")
                        .replace("&l", "<b>")
                        .replace("&m", "<st>")
                        .replace("&n", "<u>")
                        .replace("&o", "<i>")
                        .replace("&r", "<reset>")
                        .replace("&#([A-Fa-f0-9]{6})".toRegex(), "<color:#$1>")

        return MiniMessage.miniMessage()
                .deserialize(convertedMessage)
                .decoration(TextDecoration.ITALIC, false)
    }

    fun splitIntoLines(text: String, maxLineLength: Int): List<String> {
        val words = text.split("\\s+".toRegex())
        val lines = mutableListOf<String>()

        // Calculate total symbol length of the text
        val totalSymbolLength = text.length

        // Calculate minimal amount of lines needed to fit the text
        val minLinesNeeded =
                (totalSymbolLength / maxLineLength) +
                        if (totalSymbolLength % maxLineLength != 0) 1 else 0

        // Calculate average symbol length per line
        val averageSymbolLengthPerLine = totalSymbolLength / minLinesNeeded

        // Distribute words evenly among lines
        var currentLine = StringBuilder()
        var currentSymbolLength = 0
        var remainingLines = minLinesNeeded
        for (word in words) {
            if (currentLine.isEmpty()) {
                currentLine.append(word)
                currentSymbolLength += word.length
            } else if (currentSymbolLength + word.length + 1 <= averageSymbolLengthPerLine ||
                            remainingLines == 1
            ) {
                currentLine.append(" ").append(word)
                currentSymbolLength += word.length + 1
            } else {
                lines.add(currentLine.toString())
                currentLine = StringBuilder(word)
                currentSymbolLength = word.length
                remainingLines--
            }
        }

        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }

        return lines
    }
}
