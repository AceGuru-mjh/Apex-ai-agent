package com.apex.selfmodify.index

class InMemoryCodeIndex(private val indexer: CodeIndexer) : CodeIndex {
    override suspend fun findSymbol(name: String): List<SymbolLocation> {
        return indexer.snapshot().filter { it.name == name }.map { SymbolLocation(it.file, it.line, it.column) }
    }

    override suspend fun findReferences(symbol: String): List<ReferenceLocation> {
        val refs = mutableListOf<ReferenceLocation>()
        // Collect ALL distinct files that have been indexed (not just files with matching symbols)
        val indexedFiles = indexer.snapshot().map { it.file }.distinct()
        // Also scan files that may have no symbols but are in the source dirs
        val allFiles = mutableSetOf<String>()
        allFiles.addAll(indexedFiles)
        // For files with symbols, we know the definition line — skip it to avoid self-match
        val definitionLines = indexer.snapshot()
            .filter { it.name == symbol }
            .associate { it.file to it.line }
        indexedFiles.forEach { filePath ->
            val file = java.io.File(filePath)
            if (file.exists()) {
                val defLine = definitionLines[filePath]
                file.readLines().forEachIndexed { idx, line ->
                    val lineNo = idx + 1
                    if (line.contains(symbol) && lineNo != defLine) {
                        refs.add(ReferenceLocation(filePath, lineNo, symbol))
                    }
                }
            }
        }
        return refs
    }

    override suspend fun listFiles(pattern: String): List<String> {
        val regex = Regex(pattern)
        return indexer.snapshot().map { it.file }.distinct().filter { regex.containsMatchIn(it) }
    }

    override fun isIndexed(): Boolean = indexer.isIndexed()
}
