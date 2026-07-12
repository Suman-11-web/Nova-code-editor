package com.example.ui.editor

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import java.util.regex.Pattern

object SyntaxHighlighter {

    // Common standard keyword groups
    private val PYTHON_KEYWORDS = setOf(
        "def", "class", "import", "from", "as", "if", "elif", "else", "while", "for", "in", 
        "return", "try", "except", "finally", "raise", "with", "lambda", "pass", "break", 
        "continue", "assert", "global", "nonlocal", "and", "or", "not", "is", "True", "False", "None"
    )

    private val C_CPP_KEYWORDS = setOf(
        "int", "float", "double", "char", "void", "struct", "class", "public", "private", 
        "protected", "if", "else", "while", "for", "switch", "case", "break", "continue", 
        "return", "include", "define", "using", "namespace", "static", "const", "extern", 
        "template", "new", "delete", "typedef", "virtual", "override", "bool", "true", "false"
    )

    private val JAVA_KEYWORDS = setOf(
        "package", "import", "class", "interface", "enum", "extends", "implements", "public", 
        "private", "protected", "static", "final", "void", "int", "float", "double", "char", 
        "boolean", "if", "else", "while", "for", "switch", "case", "break", "continue", 
        "return", "try", "catch", "finally", "throw", "throws", "new", "this", "super", 
        "true", "false", "null", "abstract", "synchronized", "volatile", "transient"
    )

    private val JS_KEYWORDS = setOf(
        "let", "const", "var", "function", "class", "import", "export", "from", "default", 
        "if", "else", "while", "for", "in", "of", "return", "try", "catch", "finally", 
        "throw", "new", "this", "typeof", "instanceof", "async", "await", "yield", "break", 
        "continue", "switch", "case", "null", "undefined", "true", "false"
    )

    private val HTML_KEYWORDS = setOf(
        "html", "head", "title", "body", "h1", "h2", "h3", "h4", "h5", "h6", "p", "br", "hr", 
        "a", "img", "div", "span", "table", "tr", "td", "th", "thead", "tbody", "ul", "ol", 
        "li", "form", "input", "button", "textarea", "select", "option", "script", "style", 
        "link", "meta", "header", "footer", "section", "article", "aside", "nav", "main"
    )

    private val CSS_KEYWORDS = setOf(
        "color", "background", "background-color", "width", "height", "margin", "padding", 
        "border", "display", "position", "top", "bottom", "left", "right", "flex", "grid", 
        "align-items", "justify-content", "font-family", "font-size", "font-weight", 
        "text-align", "overflow", "opacity", "transition", "animation", "box-shadow"
    )

    fun highlight(text: String, language: String, theme: EditorTheme): AnnotatedString {
        if (text.isEmpty()) return AnnotatedString("")

        val lang = language.lowercase()
        return buildAnnotatedString {
            // Start with base text
            append(text)

            // Setup tracking lists for styles (style, start, end)
            val styles = mutableListOf<Triple<SpanStyle, Int, Int>>()

            // 1. Comments and Strings (Higher priority to prevent highlighting inside them)
            var commentPattern: Pattern? = null
            var stringPattern: Pattern? = null
            var numberPattern: Pattern? = null
            var functionPattern: Pattern? = null
            var operatorPattern: Pattern? = null
            var keywordPattern: Pattern? = null

            // Build regular expressions based on language
            when (lang) {
                "python" -> {
                    commentPattern = Pattern.compile("#.*|\\\"\\\"\\\"[\\s\\S]*?\\\"\\\"\\\"|'\\'\\'[\\s\\S]*?'\\'\\'")
                    stringPattern = Pattern.compile("\\\"[^\\\"\\\\\\r\\n]*(?:\\\\.[^\\\"\\\\\\r\\n]*)*\\\"|'[^'\\\\\\r\\n]*(?:\\\\.[^'\\\\\\r\\n]*)*'")
                    numberPattern = Pattern.compile("\\b\\d+(\\.\\d+)?\\b")
                    functionPattern = Pattern.compile("\\b\\w+(?=\\s*\\()")
                    operatorPattern = Pattern.compile("[+\\-*/%=<>!&|^~]")
                    val kws = PYTHON_KEYWORDS.joinToString("|") { "\\b$it\\b" }
                    keywordPattern = Pattern.compile(kws)
                }
                "java" -> {
                    commentPattern = Pattern.compile("//.*|/\\*[\\s\\S]*?\\*/")
                    stringPattern = Pattern.compile("\\\"[^\\\"\\\\\\r\\n]*(?:\\\\.[^\\\"\\\\\\r\\n]*)*\\\"|'[^'\\\\\\r\\n]*(?:\\\\.[^'\\\\\\r\\n]*)*'")
                    numberPattern = Pattern.compile("\\b\\d+(\\.\\d+)?\\b")
                    functionPattern = Pattern.compile("\\b\\w+(?=\\s*\\()")
                    operatorPattern = Pattern.compile("[+\\-*/%=<>!&|^~]")
                    val kws = JAVA_KEYWORDS.joinToString("|") { "\\b$it\\b" }
                    keywordPattern = Pattern.compile(kws)
                }
                "javascript" -> {
                    commentPattern = Pattern.compile("//.*|/\\*[\\s\\S]*?\\*/")
                    stringPattern = Pattern.compile("\\\"[^\\\"\\\\\\r\\n]*(?:\\\\.[^\\\"\\\\\\r\\n]*)*\\\"|'[^'\\\\\\r\\n]*(?:\\\\.[^'\\\\\\r\\n]*)*'|`[\\s\\S]*?`")
                    numberPattern = Pattern.compile("\\b\\d+(\\.\\d+)?\\b")
                    functionPattern = Pattern.compile("\\b\\w+(?=\\s*\\()")
                    operatorPattern = Pattern.compile("[+\\-*/%=<>!&|^~]")
                    val kws = JS_KEYWORDS.joinToString("|") { "\\b$it\\b" }
                    keywordPattern = Pattern.compile(kws)
                }
                "c", "cpp", "c++" -> {
                    commentPattern = Pattern.compile("//.*|/\\*[\\s\\S]*?\\*/|#\\s*include\\s*<.*>|#\\s*define\\s+\\w+")
                    stringPattern = Pattern.compile("\\\"[^\\\"\\\\\\r\\n]*(?:\\\\.[^\\\"\\\\\\r\\n]*)*\\\"|'[^'\\\\\\r\\n]*(?:\\\\.[^'\\\\\\r\\n]*)*'")
                    numberPattern = Pattern.compile("\\b\\d+(\\.\\d+)?\\b")
                    functionPattern = Pattern.compile("\\b\\w+(?=\\s*\\()")
                    operatorPattern = Pattern.compile("[+\\-*/%=<>!&|^~]")
                    val kws = C_CPP_KEYWORDS.joinToString("|") { "\\b$it\\b" }
                    keywordPattern = Pattern.compile(kws)
                }
                "html" -> {
                    commentPattern = Pattern.compile("<!--[\\s\\S]*?-->")
                    stringPattern = Pattern.compile("\\\"[^\\\"]*?\\\"|'[^']*?'")
                    // HTML tags
                    keywordPattern = Pattern.compile("<\\/?[a-zA-Z0-9]+(\\s|>|\\/)")
                    operatorPattern = Pattern.compile("[=<>]")
                }
                "css" -> {
                    commentPattern = Pattern.compile("/\\*[\\s\\S]*?\\*/")
                    stringPattern = Pattern.compile("\\\"[^\\\"]*?\\\"|'[^']*?'")
                    numberPattern = Pattern.compile("\\b\\d+(px|em|rem|%|s|ms|deg|vh|vw)?\\b")
                    // Properties
                    val kws = CSS_KEYWORDS.joinToString("|") { "\\b$it\\b" }
                    keywordPattern = Pattern.compile(kws)
                    // Selectors & curly brackets
                    operatorPattern = Pattern.compile("[{}:;.,#.]")
                }
                "json" -> {
                    stringPattern = Pattern.compile("\\\"[^\\\"\\\\\\r\\n]*(?:\\\\.[^\\\"\\\\\\r\\n]*)*\\\"")
                    numberPattern = Pattern.compile("\\b(true|false|null|\\d+(\\.\\d+)?)\\b")
                    operatorPattern = Pattern.compile("[{}:,\\[\\]]")
                }
                "markdown" -> {
                    // Headers
                    keywordPattern = Pattern.compile("^#+.*$", Pattern.MULTILINE)
                    // Bold / Italic
                    stringPattern = Pattern.compile("\\*\\*.*?\\*\\*|\\*.*?\\*")
                    // Code blocks
                    commentPattern = Pattern.compile("`.*?`|```[\\s\\S]*?```")
                    // Lists / links
                    operatorPattern = Pattern.compile("^\\s*[-*+]|\\b\\[.*?\\]\\(.*?\\)")
                }
            }

            // Create masks so we don't double-highlight keywords inside strings/comments
            val masked = BooleanArray(text.length) { false }

            // Apply comments (Highest priority)
            commentPattern?.let {
                val matcher = it.matcher(text)
                while (matcher.find()) {
                    val start = matcher.start()
                    val end = matcher.end()
                    styles.add(Triple(SpanStyle(color = theme.commentColor), start, end))
                    for (i in start until end) masked[i] = true
                }
            }

            // Apply strings
            stringPattern?.let {
                val matcher = it.matcher(text)
                while (matcher.find()) {
                    val start = matcher.start()
                    val end = matcher.end()
                    // Check if already masked by comment
                    if (!masked[start]) {
                        styles.add(Triple(SpanStyle(color = theme.stringColor), start, end))
                        for (i in start until end) masked[i] = true
                    }
                }
            }

            // Apply keywords
            keywordPattern?.let {
                val matcher = it.matcher(text)
                while (matcher.find()) {
                    val start = matcher.start()
                    val end = matcher.end()
                    if (start < masked.size && !masked[start]) {
                        styles.add(Triple(SpanStyle(color = theme.keywordColor, fontWeight = FontWeight.Bold), start, end))
                        for (i in start until end) masked[i] = true
                    }
                }
            }

            // Apply functions
            functionPattern?.let {
                val matcher = it.matcher(text)
                while (matcher.find()) {
                    val start = matcher.start()
                    val end = matcher.end()
                    if (start < masked.size && !masked[start]) {
                        styles.add(Triple(SpanStyle(color = theme.functionColor), start, end))
                        for (i in start until end) masked[i] = true
                    }
                }
            }

            // Apply numbers
            numberPattern?.let {
                val matcher = it.matcher(text)
                while (matcher.find()) {
                    val start = matcher.start()
                    val end = matcher.end()
                    if (start < masked.size && !masked[start]) {
                        styles.add(Triple(SpanStyle(color = theme.numberColor), start, end))
                        for (i in start until end) masked[i] = true
                    }
                }
            }

            // Apply operators
            operatorPattern?.let {
                val matcher = it.matcher(text)
                while (matcher.find()) {
                    val start = matcher.start()
                    val end = matcher.end()
                    if (start < masked.size && !masked[start]) {
                        styles.add(Triple(SpanStyle(color = theme.operatorColor), start, end))
                    }
                }
            }

            // Apply all sorted styles to the annotated string
            for (styleTriple in styles) {
                addStyle(styleTriple.first, styleTriple.second, styleTriple.third)
            }
        }
    }

    enum class DiffType { UNCHANGED, ADDED, DELETED }
    data class DiffLine(val type: DiffType, val text: String)

    fun computeLineDiff(oldText: String, newText: String): List<DiffLine> {
        val oldLines = oldText.split("\n")
        val newLines = newText.split("\n")
        
        val diff = mutableListOf<DiffLine>()
        var o = 0
        var n = 0
        while (o < oldLines.size || n < newLines.size) {
            if (o < oldLines.size && n < newLines.size) {
                val oldL = oldLines[o]
                val newL = newLines[n]
                if (oldL == newL) {
                    diff.add(DiffLine(type = DiffType.UNCHANGED, text = oldL))
                    o++
                    n++
                } else {
                    val nextMatchInNew = newLines.subList(n, newLines.size).indexOf(oldL)
                    if (nextMatchInNew > 0 && nextMatchInNew < 15) { // search limit to keep it extremely fast
                        for (i in 0 until nextMatchInNew) {
                            diff.add(DiffLine(type = DiffType.ADDED, text = newLines[n + i]))
                        }
                        n += nextMatchInNew
                    } else {
                        val nextMatchInOld = oldLines.subList(o, oldLines.size).indexOf(newL)
                        if (nextMatchInOld > 0 && nextMatchInOld < 15) { // search limit to keep it extremely fast
                            for (i in 0 until nextMatchInOld) {
                                diff.add(DiffLine(type = DiffType.DELETED, text = oldLines[o + i]))
                            }
                            o += nextMatchInOld
                        } else {
                            diff.add(DiffLine(type = DiffType.DELETED, text = oldL))
                            diff.add(DiffLine(type = DiffType.ADDED, text = newL))
                            o++
                            n++
                        }
                    }
                }
            } else if (o < oldLines.size) {
                diff.add(DiffLine(type = DiffType.DELETED, text = oldLines[o]))
                o++
            } else if (n < newLines.size) {
                diff.add(DiffLine(type = DiffType.ADDED, text = newLines[n]))
                n++
            }
        }
        return diff
    }
}
