package com.orangeshield.app.chain

/**
 * ORANGE CHAIN — codec JSON determinístico e JVM-safe (sem org.json).
 *
 * Usado pelo nó (persistência/transporte), pelos testes de consenso e é
 * compatível com o validador comunitário em Python (JSON padrão).
 */
object ChainCodec {

    fun encodeBlock(block: ChainBlock): String {
        val sb = StringBuilder()
        sb.append("{\"index\":").append(block.index)
            .append(",\"timestamp\":").append(block.timestamp)
            .append(",\"prevHash\":\"").append(block.prevHash)
            .append("\",\"merkleRoot\":\"").append(block.merkleRoot)
            .append("\",\"publisher\":\"").append(escape(block.publisher)).append("\",\"entries\":[")
        block.entries.forEachIndexed { i, e ->
            if (i > 0) sb.append(",")
            sb.append("{\"e164\":\"").append(escape(e.e164))
                .append("\",\"classification\":\"").append(escape(e.classification))
                .append("\",\"reportedAt\":").append(e.reportedAt)
                .append(",\"reporterId\":\"").append(escape(e.reporterId))
                .append("\",\"pubKey\":\"").append(escape(e.pubKey))
                .append("\",\"signature\":\"").append(escape(e.signature)).append("\"}")
        }
        sb.append("]}")
        return sb.toString()
    }

    fun decodeBlock(json: String): ChainBlock {
        val obj = Parser(json).parseObject()
        val entries = mutableListOf<ChainEntry>()
        val arr = obj["entries"] as? List<*> ?: emptyList<Any>()
        for (item in arr) {
            @Suppress("UNCHECKED_CAST")
            val e = item as Map<String, Any>
            entries.add(
                ChainEntry(
                    e164 = e["e164"] as String,
                    classification = e["classification"] as String,
                    reportedAt = (e["reportedAt"] as Number).toLong(),
                    reporterId = e["reporterId"] as String,
                    pubKey = e["pubKey"] as String,
                    signature = e["signature"] as String
                )
            )
        }
        return ChainBlock(
            index = (obj["index"] as Number).toLong(),
            timestamp = (obj["timestamp"] as Number).toLong(),
            prevHash = obj["prevHash"] as String,
            merkleRoot = obj["merkleRoot"] as String,
            publisher = obj["publisher"] as String,
            entries = entries
        )
    }

    /** Extrai os blocos de um chain.json completo {"blocks":[...]}: */
    fun decodeChain(body: String): List<ChainBlock> {
        val obj = Parser(body).parseObject()
        val arr = obj["blocks"] as? List<*> ?: return emptyList()
        return arr.map { @Suppress("UNCHECKED_CAST") encodeMapToString(it as Map<String, Any>) }.map { decodeBlock(it) }
    }

    private fun encodeMapToString(map: Map<String, Any>): String {
        val sb = StringBuilder("{")
        map.entries.forEachIndexed { i, (k, v) ->
            if (i > 0) sb.append(",")
            sb.append("\"").append(escape(k)).append("\":")
            when (v) {
                is Number -> sb.append(v)
                is Boolean -> sb.append(v)
                is String -> sb.append("\"").append(escape(v)).append("\"")
                is List<*> -> {
                    sb.append("[")
                    v.forEachIndexed { j, item ->
                        if (j > 0) sb.append(",")
                        @Suppress("UNCHECKED_CAST")
                        sb.append(encodeMapToString(item as Map<String, Any>))
                    }
                    sb.append("]")
                }
                else -> sb.append("\"").append(escape(v.toString())).append("\"")
            }
        }
        sb.append("}")
        return sb.toString()
    }

    private fun escape(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

    /** Parser JSON mínimo (objetos, arrays, strings, números, booleans, null). */
    private class Parser(private val src: String) {
        private var pos = 0

        fun parseObject(): Map<String, Any> {
            skipWs()
            expect('{')
            val map = LinkedHashMap<String, Any>()
            skipWs()
            if (peek() == '}') { pos++; return map }
            while (true) {
                skipWs()
                expect('"')
                val key = parseString()
                skipWs(); expect(':'); skipWs()
                map[key] = parseValue()
                skipWs()
                when (peek()) {
                    ',' -> { pos++ }
                    '}' -> { pos++; return map }
                    else -> error("JSON inválido na posição $pos")
                }
            }
        }

        private fun parseValue(): Any {
            skipWs()
            return when (val c = peek()) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> { expect('"'); parseString() }
                't' -> { expectWord("true"); true }
                'f' -> { expectWord("false"); false }
                'n' -> { expectWord("null"); "null" }
                else -> {
                    if (c == '-' || c.isDigit()) parseNumber()
                    else error("JSON inválido na posição $pos")
                }
            }
        }

        private fun parseArray(): List<Any> {
            skipWs(); expect('[')
            val list = ArrayList<Any>()
            skipWs()
            if (peek() == ']') { pos++; return list }
            while (true) {
                list.add(parseValue())
                skipWs()
                when (peek()) {
                    ',' -> { pos++ }
                    ']' -> { pos++; return list }
                    else -> error("JSON inválido na posição $pos")
                }
            }
        }

        private fun parseString(): String {
            val sb = StringBuilder()
            while (true) {
                val c = src[pos++]
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> {
                        when (val esc = src[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                val hex = src.substring(pos, pos + 4); pos += 4
                                sb.append(hex.toInt(16).toChar())
                            }
                            else -> error("escape inválido: \\$esc")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun parseNumber(): Any {
            val start = pos
            if (peek() == '-') pos++
            while (pos < src.length && (src[pos].isDigit() || src[pos] in ".eE+-")) pos++
            val text = src.substring(start, pos)
            return if (text.contains('.') || text.contains('e') || text.contains('E'))
                text.toDouble() else text.toLong()
        }

        private fun expectWord(word: String) {
            require(src.regionMatches(pos, word, 0, word.length)) { "JSON inválido na posição $pos" }
            pos += word.length
        }

        private fun expect(c: Char) {
            check(peek() == c) { "JSON inválido na posição $pos (esperado '$c')" }
            pos++
        }

        private fun peek(): Char {
            check(pos < src.length) { "JSON truncado" }
            return src[pos]
        }

        private fun skipWs() {
            while (pos < src.length && src[pos].isWhitespace()) pos++
        }
    }
}
