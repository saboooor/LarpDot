package ca.saboor.larpdot.ui.overlay

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Document
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser

private val markdownParser = Parser.builder().maxOpenBlockParsers(32).build()
private val headingSizes = listOf(28, 24, 21, 18, 16, 14)
private val codeStyle = SpanStyle(fontFamily = FontFamily.Monospace)

internal fun renderCustomIslandMarkdown(template: String, values: Map<String, String>): AnnotatedString =
    buildAnnotatedString {
        appendMarkdown(markdownParser.parse(template), values)
    }

private fun AnnotatedString.Builder.appendMarkdown(node: Node, values: Map<String, String>, listDepth: Int = 0) {
    fun children(separator: String = "") {
        var child = node.firstChild
        while (child != null) {
            appendMarkdown(child, values, listDepth)
            child = child.next
            if (child != null) append(separator)
        }
    }

    // Substitute live values after parsing so notification and song text stay literal.
    fun literal(value: String) = append(customIslandText(value, values))

    when (node) {
        is Document, is ListItem -> children("\n")
        is Heading -> {
            val size = headingSizes[node.level - 1]
            withStyle(ParagraphStyle(lineHeight = (size + 4).sp)) {
                withStyle(SpanStyle(fontSize = size.sp, fontWeight = FontWeight.Bold)) { children() }
            }
        }
        is StrongEmphasis -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { children() }
        is Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { children() }
        is Link -> withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { children() }
        is BlockQuote -> {
            append("│ ")
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { children("\n") }
        }
        is BulletList, is OrderedList -> {
            var number = (node as? OrderedList)?.markerStartNumber ?: 1
            var entry = node.firstChild
            while (entry != null) {
                append("  ".repeat(listDepth))
                append(if (node is OrderedList) "${number++}. " else "• ")
                appendMarkdown(entry, values, listDepth + 1)
                entry = entry.next
                if (entry != null) append("\n")
            }
        }
        is Code -> withStyle(codeStyle) { literal(node.literal) }
        is FencedCodeBlock -> withStyle(codeStyle) { literal(node.literal.removeSuffix("\n")) }
        is IndentedCodeBlock -> withStyle(codeStyle) { literal(node.literal.removeSuffix("\n")) }
        is Text -> literal(node.literal)
        is HtmlInline -> literal(node.literal)
        is HtmlBlock -> literal(node.literal.removeSuffix("\n"))
        is SoftLineBreak, is HardLineBreak -> append("\n")
        is ThematicBreak -> append("────")
        else -> children()
    }
}
