package fr.epure.launcher.domain

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow

/**
 * Petite calculatrice de la barre de recherche : « 12×7 », « (3+4)/2 », « 2^10 »,
 * « 80*15% », virgule ou point pour les décimales.
 */
object Calculator {

    /** Résultat du calcul, ou null si le texte n'est pas un calcul (« 12 », « Netflix »…). */
    fun evaluate(input: String): Double? {
        val tokens = tokenize(input) ?: return null
        // Il faut au moins une opération : un nombre seul n'est pas un calcul.
        val hasOperation = tokens.withIndex().any { (i, token) ->
            token is Token.Percent ||
                (token is Token.Op && i > 0 && (tokens[i - 1] is Token.Num || tokens[i - 1] == Token.Close || tokens[i - 1] is Token.Percent))
        }
        if (!hasOperation) return null
        val parser = Parser(tokens)
        val value = parser.expression() ?: return null
        if (!parser.atEnd()) return null
        return value.takeIf { it.isFinite() }
    }

    /**
     * Résultat à la française : « 84 », « 3,5 », « 1 234,5 », « 0,3333333333 », « 1,2E20 ».
     * Sans [grouping] (pour copier le résultat) : « 1234,5 ».
     */
    fun format(value: Double, grouping: Boolean = true): String {
        val symbols = DecimalFormatSymbols(Locale.FRANCE)
        val magnitude = abs(value)
        val pattern = when {
            value == 0.0 -> "0"
            magnitude >= 1e15 || magnitude < 1e-6 -> "0.#########E0"
            grouping -> "#,##0.##########"
            else -> "0.##########"
        }
        // Espace insécable fine entre les milliers : le nombre ne se coupe pas en fin de ligne.
        return DecimalFormat(pattern, symbols).format(value).replace(' ', ' ')
    }

    private sealed interface Token {
        data class Num(val value: Double) : Token
        data class Op(val symbol: Char) : Token
        data object Open : Token
        data object Close : Token
        data object Percent : Token
    }

    private fun tokenize(input: String): List<Token>? {
        val text = input.trim()
        if (text.isEmpty() || text.none { it.isDigit() }) return null
        val tokens = ArrayList<Token>()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || ((c == ',' || c == '.') && i + 1 < text.length && text[i + 1].isDigit()) -> {
                    val start = i
                    var separators = 0
                    while (i < text.length && (text[i].isDigit() || text[i] == ',' || text[i] == '.')) {
                        if (!text[i].isDigit()) separators++
                        i++
                    }
                    if (separators > 1) return null
                    val number = text.substring(start, i).replace(',', '.').toDoubleOrNull() ?: return null
                    tokens += Token.Num(number)
                }
                c == '+' -> { tokens += Token.Op('+'); i++ }
                c == '-' || c == '−' || c == '–' -> { tokens += Token.Op('-'); i++ }
                c == '*' || c == '×' || c == '·' -> { tokens += Token.Op('*'); i++ }
                // « x » entre deux nombres : 3x4.
                (c == 'x' || c == 'X') && tokens.lastOrNull().let { it is Token.Num || it == Token.Close } -> {
                    tokens += Token.Op('*')
                    i++
                }
                c == '/' || c == '÷' -> { tokens += Token.Op('/'); i++ }
                c == '^' -> { tokens += Token.Op('^'); i++ }
                c == '%' -> { tokens += Token.Percent; i++ }
                c == '(' -> { tokens += Token.Open; i++ }
                c == ')' -> { tokens += Token.Close; i++ }
                else -> return null
            }
        }
        return tokens
    }

    /** Analyse descendante : addition < multiplication < puissance < signe < pourcentage. */
    private class Parser(private val tokens: List<Token>) {
        private var pos = 0

        fun atEnd() = pos == tokens.size

        private fun peek(): Token? = tokens.getOrNull(pos)

        fun expression(): Double? {
            var value = term() ?: return null
            while (true) {
                val op = peek() as? Token.Op ?: break
                if (op.symbol != '+' && op.symbol != '-') break
                pos++
                val right = term() ?: return null
                value = if (op.symbol == '+') value + right else value - right
            }
            return value
        }

        private fun term(): Double? {
            var value = power() ?: return null
            while (true) {
                val op = peek() as? Token.Op ?: break
                if (op.symbol != '*' && op.symbol != '/') break
                pos++
                val right = power() ?: return null
                if (op.symbol == '/' && right == 0.0) return null
                value = if (op.symbol == '*') value * right else value / right
            }
            return value
        }

        private fun power(): Double? {
            val base = unary() ?: return null
            val op = peek() as? Token.Op
            if (op?.symbol == '^') {
                pos++
                val exponent = power() ?: return null
                return base.pow(exponent)
            }
            return base
        }

        private fun unary(): Double? {
            val op = peek() as? Token.Op
            if (op != null && (op.symbol == '-' || op.symbol == '+')) {
                pos++
                val value = unary() ?: return null
                return if (op.symbol == '-') -value else value
            }
            return percent()
        }

        private fun percent(): Double? {
            var value = primary() ?: return null
            while (peek() == Token.Percent) {
                pos++
                value /= 100.0
            }
            return value
        }

        private fun primary(): Double? = when (val token = peek()) {
            is Token.Num -> {
                pos++
                token.value
            }
            Token.Open -> {
                pos++
                val value = expression()
                if (value == null || peek() != Token.Close) {
                    null
                } else {
                    pos++
                    value
                }
            }
            else -> null
        }
    }
}
