package com.md4a.highlight

/**
 * Lightweight, allocation-friendly syntax highlighter for code blocks.
 *
 * Design goals: zero dependencies, single regex pass per language, good-enough
 * fidelity for README viewing (keywords / strings / comments / numbers).
 * Not a compiler frontend — it never throws and always terminates.
 */
object CodeHighlighter {

    enum class TokenType { KEYWORD, STRING, COMMENT, NUMBER }

    data class Span(val start: Int, val end: Int, val type: TokenType)

    private val KEYWORDS: Map<String, Set<String>> = mapOf(
        "kotlin|kt|kts" to setOf(
            "package", "import", "class", "object", "interface", "fun", "val", "var", "if", "else",
            "for", "while", "when", "return", "is", "in", "as", "null", "true", "false", "this",
            "super", "private", "public", "internal", "protected", "override", "open", "abstract",
            "suspend", "data", "sealed", "enum", "companion", "lateinit", "by", "try", "catch",
            "finally", "throw", "do", "break", "continue", "typealias", "init", "const", "vararg",
        ),
        "java" to setOf(
            "package", "import", "class", "interface", "enum", "extends", "implements", "public",
            "private", "protected", "static", "final", "void", "new", "return", "if", "else", "for",
            "while", "switch", "case", "break", "continue", "try", "catch", "finally", "throw",
            "throws", "this", "super", "null", "true", "false", "abstract", "synchronized", "record",
        ),
        "python|py" to setOf(
            "def", "class", "import", "from", "return", "if", "elif", "else", "for", "while", "in",
            "not", "and", "or", "None", "True", "False", "try", "except", "finally", "raise", "with",
            "as", "lambda", "yield", "pass", "break", "continue", "global", "async", "await", "self",
        ),
        "javascript|js|jsx|node" to setOf(
            "const", "let", "var", "function", "class", "extends", "return", "if", "else", "for",
            "while", "switch", "case", "break", "continue", "new", "this", "null", "undefined",
            "true", "false", "try", "catch", "finally", "throw", "async", "await", "import", "export",
            "default", "typeof", "instanceof", "of", "in",
        ),
        "typescript|ts|tsx" to setOf(
            "const", "let", "var", "function", "class", "interface", "type", "enum", "extends",
            "implements", "return", "if", "else", "for", "while", "switch", "case", "break",
            "continue", "new", "this", "null", "undefined", "true", "false", "try", "catch",
            "finally", "throw", "async", "await", "import", "export", "default", "typeof", "keyof",
            "readonly", "public", "private", "protected", "static", "as", "of", "in",
        ),
        "go" to setOf(
            "package", "import", "func", "return", "if", "else", "for", "range", "switch", "case",
            "default", "break", "continue", "go", "defer", "chan", "select", "struct", "interface",
            "map", "nil", "true", "false", "var", "const", "type", "new", "make",
        ),
        "rust|rs" to setOf(
            "fn", "let", "mut", "const", "static", "struct", "enum", "impl", "trait", "pub", "use",
            "mod", "match", "if", "else", "for", "while", "loop", "return", "break", "continue",
            "self", "Self", "super", "crate", "where", "async", "await", "move", "dyn", "true", "false",
        ),
        "c|cpp|c++|h|hpp|objc" to setOf(
            "int", "char", "float", "double", "void", "long", "short", "unsigned", "signed", "bool",
            "struct", "enum", "union", "class", "public", "private", "protected", "virtual", "const",
            "static", "return", "if", "else", "for", "while", "switch", "case", "break", "continue",
            "new", "delete", "true", "false", "nullptr", "NULL", "sizeof", "template", "typename",
            "namespace", "using", "auto", "try", "catch", "throw",
        ),
        "csharp|cs|c#" to setOf(
            "using", "namespace", "class", "struct", "interface", "enum", "public", "private",
            "protected", "static", "void", "var", "new", "return", "if", "else", "for", "foreach",
            "while", "switch", "case", "break", "continue", "try", "catch", "finally", "throw",
            "null", "true", "false", "async", "await", "this", "base", "override", "readonly",
        ),
        "swift" to setOf(
            "import", "class", "struct", "enum", "protocol", "func", "var", "let", "return", "if",
            "else", "guard", "for", "while", "switch", "case", "break", "continue", "nil", "true",
            "false", "self", "init", "extension", "static", "override", "try", "catch", "throw",
            "async", "await",
        ),
        "ruby|rb" to setOf(
            "def", "end", "class", "module", "if", "elsif", "else", "unless", "while", "until", "for",
            "in", "do", "return", "yield", "begin", "rescue", "ensure", "raise", "nil", "true",
            "false", "self", "require", "attr_accessor", "puts", "new",
        ),
        "shell|sh|bash|zsh" to setOf(
            "if", "then", "else", "elif", "fi", "for", "while", "do", "done", "case", "esac",
            "function", "return", "export", "local", "echo", "exit", "source", "set", "cd", "sudo",
            "apt", "npm", "git", "curl", "docker",
        ),
        "yaml|yml|json|toml|ini|properties" to setOf("true", "false", "null", "yes", "no"),
        "dart" to setOf(
            "import", "class", "extends", "implements", "final", "const", "var", "late", "void",
            "return", "if", "else", "for", "while", "switch", "case", "break", "continue", "new",
            "null", "true", "false", "this", "super", "static", "async", "await", "try", "catch",
            "factory", "get", "set", "override",
        ),
        "php" to setOf(
            "php", "function", "return", "if", "else", "elseif", "foreach", "for", "while", "class",
            "public", "private", "protected", "static", "new", "null", "true", "false", "echo",
            "use", "namespace", "try", "catch", "finally", "throw", "extends", "implements", "const",
        ),
    )

    private val cache = HashMap<String, Regex>()

    private fun regexFor(language: String?): Regex {
        val lang = language?.lowercase()?.trim().orEmpty()
        synchronized(cache) { cache[lang]?.let { return it } }

        val keywords = KEYWORDS.entries
            .firstOrNull { entry -> entry.key.split('|').any { it == lang } }
            ?.value ?: emptySet()

        val keywordAlt = if (keywords.isEmpty()) {
            null
        } else {
            keywords.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
        }

        // Order matters: comments → strings → numbers → keywords.
        val parts = mutableListOf(
            """<!--.*?-->""",                  // HTML comments
            """/\*[\s\S]*?\*/""",              // block comments
            """//[^\n]*""",                    // line comments (C-like)
            """#[^\n]*""",                     // line comments (# langs; also YAML comments)
            """"(?:\\.|[^"\\\n])*"""",         // double-quoted strings
            """'(?:\\.|[^'\\\n])*'""",         // single-quoted strings
            """`(?:\\.|[^`\\])*`""",           // template literals
            """\b0x[0-9a-fA-F_]+\b""",         // hex (before decimal so 0x1A2B wins)
            """\b\d[\d_]*(?:\.\d+)?(?:[eE][+-]?\d+)?\b""", // numbers
        )
        if (keywordAlt != null) parts += keywordAlt

        val regex = Regex(parts.joinToString("|"), setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
        synchronized(cache) { cache[lang] = regex }
        return regex
    }

    private val HASH_LANGS = setOf("python", "py", "shell", "sh", "bash", "zsh", "yaml", "yml",
        "toml", "ini", "properties", "ruby", "rb", "makefile", "perl", "r", "dockerfile", "conf")

    /** Tokenize [code] (in language [language]) into non-overlapping spans. */
    fun tokenize(code: String, language: String?): List<Span> {
        val lang = language?.lowercase()?.trim().orEmpty()
        val regex = regexFor(language)
        val spans = mutableListOf<Span>()

        // Classify each match, then drop overlapping lower-priority spans.
        val candidates = regex.findAll(code).map { m ->
            val value = m.value
            val type = when {
                value.startsWith("<!--") -> TokenType.COMMENT
                value.startsWith("/*") -> TokenType.COMMENT
                value.startsWith("//") && lang !in HASH_LANGS -> TokenType.COMMENT
                value.startsWith("#") && (lang in HASH_LANGS || lang.isEmpty()) -> TokenType.COMMENT
                value.startsWith("#") -> TokenType.NUMBER.takeIf { value matches Regex("#[0-9a-fA-F]{3,8}") } ?: TokenType.COMMENT
                value.startsWith("\"") || value.startsWith("'") || value.startsWith("`") -> TokenType.STRING
                value.startsWith("0x") || value.first().isDigit() -> TokenType.NUMBER
                else -> TokenType.KEYWORD
            }
            Span(m.range.first, m.range.last + 1, type)
        }.sortedBy { it.start }

        var lastEnd = 0
        for (span in candidates) {
            if (span.start < lastEnd) continue
            spans.add(span)
            lastEnd = span.end
        }
        return spans
    }
}
