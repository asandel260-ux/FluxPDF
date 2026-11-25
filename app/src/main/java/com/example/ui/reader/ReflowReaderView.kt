package com.example.ui.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.ReadingColorMode
import com.example.ui.theme.EditorialSerifFamily

/**
 * Accessible Text Reflow reading view: renders extracted PDF prose in crisp Newsreader serif
 * typography with selectable text, dynamic font scaling, and optical theme synchronization.
 */
@Composable
fun ReflowReaderView(
    pageIndex: Int,
    pageCount: Int,
    extractedText: String,
    fontScale: Float,
    readingColorMode: ReadingColorMode,
    onTapCanvas: () -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onCopiedMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val (bgColor, fgColor, mutedColor) = when (readingColorMode) {
        ReadingColorMode.ORIGINAL -> Triple(Color(0xFFFDFBF7), Color(0xFF17181C), Color(0xFF60626B))
        ReadingColorMode.SEPIA -> Triple(Color(0xFFF3EAD8), Color(0xFF2D2418), Color(0xFF6E5C45))
        ReadingColorMode.MIDNIGHT -> Triple(Color(0xFF131822), Color(0xFFE4E8F0), Color(0xFF8C97AB))
        ReadingColorMode.OLED_DARK -> Triple(Color(0xFF000000), Color(0xFFEBEBEB), Color(0xFF919191))
        ReadingColorMode.HIGH_CONTRAST -> Triple(Color(0xFF000000), Color(0xFFFFFFFF), Color(0xFFFFD54F))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTapCanvas,
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 88.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = fgColor.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, fgColor.copy(alpha = 0.14f)),
                ) {
                    Text(
                        text = "REFLOW  •  PAGE ${pageIndex + 1} / $pageCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = mutedColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }

                if (extractedText.isNotBlank()) {
                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(extractedText))
                            onCopiedMessage("Copied page ${pageIndex + 1} text to clipboard")
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy page text",
                            tint = mutedColor,
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            if (extractedText.isBlank()) {
                Text(
                    text = stringResource(R.string.reflow_no_text),
                    style = MaterialTheme.typography.bodyLarge,
                    color = mutedColor,
                )
            } else {
                val baseFontSize = (18f * fontScale).sp
                val baseLineHeight = (29f * fontScale).sp
                SelectionContainer {
                    Text(
                        text = extractedText,
                        style = TextStyle(
                            fontFamily = EditorialSerifFamily,
                            fontSize = baseFontSize,
                            lineHeight = baseLineHeight,
                            color = fgColor,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(36.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                OutlinedButton(
                    onClick = onPreviousPage,
                    enabled = pageIndex > 0,
                ) {
                    Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = null)
                    Text("Previous")
                }
                OutlinedButton(
                    onClick = onNextPage,
                    enabled = pageIndex < pageCount - 1,
                ) {
                    Text("Next Page")
                    Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null)
                }
            }
        }
    }
}
