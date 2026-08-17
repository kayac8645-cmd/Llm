package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CodeBlockBg
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.OledBorder
import com.example.ui.theme.OledBorderSubtle
import com.example.ui.theme.OledSurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun FormattedMessageContent(
    content: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Extract thinking blocks if present (e.g. <think> ... </think>)
    val thinkRegex = Regex("<think>([\\s\\S]*?)(?:</think>|$)", RegexOption.IGNORE_CASE)
    val thinkMatch = thinkRegex.find(content)

    var mainContent = content
    var thinkingText: String? = null

    if (thinkMatch != null) {
        thinkingText = thinkMatch.groupValues.getOrNull(1)?.trim()
        mainContent = content.replace(thinkRegex, "").trim()
    }

    Column(modifier = modifier) {
        // Thinking Process Accordion
        if (!thinkingText.isNullOrBlank()) {
            ThinkingBlock(
                thinkingContent = thinkingText,
                isOngoing = content.contains("<think>") && !content.contains("</think>")
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Render main markdown & code blocks
        if (mainContent.isNotBlank()) {
            RenderMarkdownBlocks(mainContent, context)
        }
    }
}

@Composable
private fun ThinkingBlock(
    thinkingContent: String,
    isOngoing: Boolean
) {
    var expanded by remember { mutableStateOf(isOngoing) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0F172A).copy(alpha = 0.7f))
            .border(1.dp, NeonViolet.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = NeonViolet,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isOngoing) "Düşünce Süreci (Çıkarım Yapılıyor...)" else "Düşünce Süreci (Reasoning)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NeonViolet
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Kapat" else "Aç",
                tint = TextMuted,
                modifier = Modifier.size(18.dp)
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 6.dp)) {
                Text(
                    text = thinkingContent,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun RenderMarkdownBlocks(content: String, context: Context) {
    // Split by code blocks ```lang ... ```
    val codeBlockRegex = Regex("```([a-zA-Z0-9_]*)\n?([\\s\\S]*?)```")
    var lastIndex = 0

    val matches = codeBlockRegex.findAll(content).toList()

    if (matches.isEmpty()) {
        SimpleRichText(content)
        return
    }

    for (match in matches) {
        val startIndex = match.range.first
        if (startIndex > lastIndex) {
            val textBefore = content.substring(lastIndex, startIndex)
            if (textBefore.isNotBlank()) {
                SimpleRichText(textBefore)
                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        val language = match.groupValues.getOrNull(1)?.ifBlank { "code" } ?: "code"
        val codeSnippet = match.groupValues.getOrNull(2) ?: ""

        CodeBlockCard(language = language, code = codeSnippet, context = context)
        Spacer(modifier = Modifier.height(6.dp))

        lastIndex = match.range.last + 1
    }

    if (lastIndex < content.length) {
        val remaining = content.substring(lastIndex)
        if (remaining.isNotBlank()) {
            SimpleRichText(remaining)
        }
    }
}

@Composable
fun CodeBlockCard(
    language: String,
    code: String,
    context: Context
) {
    var copied by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CodeBlockBg)
            .border(1.dp, OledBorder, RoundedCornerShape(8.dp))
    ) {
        // Code Block Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(OledSurfaceCard)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = language.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CyanPrimary,
                fontFamily = FontFamily.Monospace
            )

            IconButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Code", code.trim())
                    clipboard.setPrimaryClip(clip)
                    copied = true
                    Toast.makeText(context, "Kod kopyalandı", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = "Kodu Kopyala",
                    tint = if (copied) CyanPrimary else TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // Code Snippet
        Text(
            text = code.trim(),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = TextPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        )
    }
}

@Composable
fun SimpleRichText(text: String, modifier: Modifier = Modifier) {
    val annotated = buildAnnotatedString {
        val lines = text.split("\n")
        for (i in lines.indices) {
            val line = lines[i]
            when {
                line.startsWith("### ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = CyanPrimary)) {
                        append(line.removePrefix("### "))
                    }
                }
                line.startsWith("## ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = CyanPrimary)) {
                        append(line.removePrefix("## "))
                    }
                }
                line.startsWith("# ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp, color = CyanPrimary)) {
                        append(line.removePrefix("# "))
                    }
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    withStyle(SpanStyle(color = CyanPrimary, fontWeight = FontWeight.Bold)) {
                        append("  • ")
                    }
                    appendInlineFormatted(line.substring(2))
                }
                else -> {
                    appendInlineFormatted(line)
                }
            }
            if (i < lines.size - 1) append("\n")
        }
    }

    Text(
        text = annotated,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        color = TextPrimary,
        modifier = modifier
    )
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendInlineFormatted(text: String) {
    val boldRegex = Regex("\\*\\*(.*?)\\*\\*")
    var lastIndex = 0
    val matches = boldRegex.findAll(text).toList()

    if (matches.isEmpty()) {
        append(text)
        return
    }

    for (match in matches) {
        val start = match.range.first
        if (start > lastIndex) {
            append(text.substring(lastIndex, start))
        }
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = TextPrimary)) {
            append(match.groupValues[1])
        }
        lastIndex = match.range.last + 1
    }

    if (lastIndex < text.length) {
        append(text.substring(lastIndex))
    }
}
