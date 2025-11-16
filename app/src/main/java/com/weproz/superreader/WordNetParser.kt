// WordNetParser.kt
package com.weproz.superreader

import android.content.Context
import java.util.Locale

class WordNetParser(private val context: Context) {

    suspend fun parseWordNetData(): List<DictionaryWord> {
        val words = mutableListOf<DictionaryWord>()

        println("🔄 Starting WordNet parsing...")

        val nouns = parseWordNetFile("data.noun", "Noun")
        println("📚 Nouns: ${nouns.size} words")

        // FIX: use the correct verbs file
        val verbs = parseWordNetFile("data.verb", "Verb")
        println("📚 Verbs: ${verbs.size} words")

        val adjectives = parseWordNetFile("data.adj", "Adjective")
        println("📚 Adjectives: ${adjectives.size} words")

        val adverbs = parseWordNetFile("data.adv", "Adverb")
        println("📚 Adverbs: ${adverbs.size} words")

        words.addAll(nouns)
        words.addAll(verbs)
        words.addAll(adjectives)
        words.addAll(adverbs)

        val uniqueWords = words.distinctBy { it.word.lowercase(Locale.getDefault()) }
            .sortedBy { it.word.lowercase(Locale.getDefault()) }  // Sort alphabetically
        println("🎯 Total unique words: ${uniqueWords.size}")
        println("📊 Words with examples: ${uniqueWords.count { it.example.isNotEmpty() }}")

        return uniqueWords
    }

    private fun parseWordNetFile(filename: String, partOfSpeech: String): List<DictionaryWord> {
        val words = mutableListOf<DictionaryWord>()
        var lineCount = 0
        var wordCount = 0

        try {
            context.assets.open("wordnet/$filename").bufferedReader().use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    lineCount++
                    line?.let { currentLine ->
                        // Skip comment/blank/metadata lines
                        if (currentLine.isNotBlank() && !currentLine.startsWith(" ")) {
                            val word = parseWordNetLine(currentLine, partOfSpeech)
                            if (word != null) {
                                words.add(word)
                                wordCount++
                            }
                        }
                    }
                    if (lineCount % 10000 == 0) {
                        println("📖 $filename: Processed $lineCount lines, found $wordCount words")
                    }
                }
            }
            println("✅ $filename: Processed $lineCount lines, extracted $wordCount words")
        } catch (e: Exception) {
            e.printStackTrace()
            println("❌ Error reading $filename: ${e.message}")
        }

        return words
    }

    private fun parseWordNetLine(line: String, partOfSpeech: String): DictionaryWord? {
        try {
            val pipeIndex = line.indexOf('|')
            if (pipeIndex == -1) return null

            val parts = line.substring(0, pipeIndex).split(" ")
            if (parts.size < 5) return null

            val wordForm = parts.getOrNull(4) ?: return null
            val fullDefinition = line.substring(pipeIndex + 1).trim()

            var definition = fullDefinition

            // Extract a quoted (or otherwise) example if present
            var example = extractExample(fullDefinition)
            example = cleanExample(example)

            if (example.isNotEmpty()) {
                definition = removeExampleFromDefinition(fullDefinition, example)
            }

            definition = cleanDefinition(definition)

            if (definition.isNotEmpty() && wordForm.isNotEmpty()) {
                return DictionaryWord(
                    word = wordForm.replace('_', ' ')
                        .lowercase(Locale.getDefault())
                        .replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase(Locale.getDefault())
                            else it.toString()
                        },
                    meaning = definition,
                    example = example, // may be empty if not found in data
                    partOfSpeech = partOfSpeech,
                    isCustom = false,
                    dateAdded = ""
                )
            }
        } catch (e: Exception) {
            println("⚠️ Error parsing WordNet line: ${e.message}")
        }
        return null
    }

    // --- helpers for extracting/cleaning definition + example ---

    private fun extractExample(fullDefinition: String): String {
        var example = ""

        // "Quoted" example
        val quoteIndex = fullDefinition.indexOf('"')
        if (quoteIndex != -1) {
            val afterQuote = fullDefinition.substring(quoteIndex + 1)
            val closingQuoteIndex = afterQuote.indexOf('"')
            if (closingQuoteIndex != -1) {
                example = afterQuote.substring(0, closingQuoteIndex).trim()
                return cleanExample(example)
            }
        }

        // ex: marker (rare)
        val exIndex = fullDefinition.indexOf("ex:")
        if (exIndex != -1) {
            val afterEx = fullDefinition.substring(exIndex + 3).trim()
            val semicolonIndex = afterEx.indexOf(';')
            example = if (semicolonIndex != -1) {
                afterEx.substring(0, semicolonIndex).trim()
            } else {
                afterEx
            }
            return cleanExample(example)
        }

        // Parenthetical content (avoid syn/ant/see)
        val parenStart = fullDefinition.indexOf('(')
        if (parenStart != -1) {
            val parenEnd = fullDefinition.indexOf(')', parenStart)
            if (parenEnd != -1) {
                val content = fullDefinition.substring(parenStart + 1, parenEnd).trim()
                if (content.length > 10 && !content.startsWith("ant:") &&
                    !content.startsWith("syn:") && !content.startsWith("see")
                ) {
                    example = content
                    return cleanExample(example)
                }
            }
        }

        // After semicolon might be free text usable as example
        val semicolonIndex = fullDefinition.indexOf(';')
        if (semicolonIndex != -1) {
            val afterSemicolon = fullDefinition.substring(semicolonIndex + 1).trim()
            if (afterSemicolon.isNotEmpty() && afterSemicolon.first().isLowerCase()) {
                example = afterSemicolon
                return cleanExample(example)
            }
        }

        return ""
    }

    private fun removeExampleFromDefinition(fullDefinition: String, example: String): String {
        var definition = fullDefinition

        // remove with quotes if present
        if (definition.contains("\"$example\"")) {
            definition = definition.replace("\"$example\"", "").trim()
        }

        // remove markers like ex:...
        if (definition.contains("ex:$example")) {
            definition = definition.replace("ex:$example", "").trim()
        } else if (definition.contains("ex: $example")) {
            definition = definition.replace("ex: $example", "").trim()
        }

        // remove parenthetical example
        if (definition.contains("($example)")) {
            definition = definition.replace("($example)", "").trim()
        }

        definition = definition.trim()
            .removeSuffix(";")
            .removeSuffix(":")
            .removeSuffix(",")
            .replace("\\s+".toRegex(), " ")
            .trim()

        return definition
    }

    private fun cleanDefinition(definition: String): String {
        return definition.trim()
            .removeSuffix(";")
            .removeSuffix(":")
            .removeSuffix(",")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    private fun cleanExample(s: String): String {
        return s.trim()
            .trim('"', '“', '”', '‘', '’')
            .replace("\\s+".toRegex(), " ")
            .trim()
    }
}