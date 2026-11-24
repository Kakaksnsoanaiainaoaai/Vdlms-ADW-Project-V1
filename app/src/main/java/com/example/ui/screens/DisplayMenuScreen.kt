package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hardware.DisplayPage
import com.example.hardware.mainMenuEntries
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalDlmsPalette

@Composable
fun DisplayMenuScreen(
    selectedIndex: Int,
    onSelectEntry: (DisplayPage) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDlmsPalette.current
    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex) {
        if (selectedIndex in mainMenuEntries.indices) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itemsIndexed(mainMenuEntries) { idx, entry ->
            val selected = idx == selectedIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (selected) palette.lcdPrimary.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.28f))
                    .border(
                        width = 1.dp,
                        color = if (selected) palette.lcdPrimary else palette.lcdGrid,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .clickable { onSelectEntry(entry.targetPage) }
                    .testTag("menu_item_${entry.targetPage.name.lowercase()}")
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${idx + 1}. ${entry.title}",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) palette.lcdPrimary else palette.lcdText
                    )
                    Text(
                        text = entry.subtitle,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 9.sp,
                        color = palette.lcdMutedText
                    )
                }
                Text(
                    text = if (selected) "ENTER ↵" else "›",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) palette.lcdSecondary else palette.lcdMutedText
                )
            }
        }
    }
}
