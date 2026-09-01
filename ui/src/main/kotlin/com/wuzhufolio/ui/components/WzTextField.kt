package com.wuzhufolio.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.wuzhufolio.ui.theme.WzTheme

/**
 * 输入框（design-tokens §4.2）：描边式、聚焦 accent 高亮；
 * 错误态 loss 描边 + 下方 loss 错误文案。
 */
@Composable
fun WzTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    error: String? = null,
    singleLine: Boolean = true,
    testTag: String? = null,
) {
    val colors = WzTheme.colors
    Column(modifier = modifier) {
        Text(text = label, color = colors.ink2, style = WzTheme.typography.caption)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
            placeholder = { Text(placeholder, color = colors.ink3) },
            isError = error != null,
            singleLine = singleLine,
            shape = RoundedCornerShape(7.dp),
            textStyle = WzTheme.typography.body,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.line,
                errorBorderColor = colors.loss,
                focusedTextColor = colors.ink,
                unfocusedTextColor = colors.ink,
                cursorColor = colors.accent,
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                errorContainerColor = colors.surface,
            ),
        )
        if (error != null) {
            Text(
                text = error,
                color = colors.loss,
                style = WzTheme.typography.caption,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
