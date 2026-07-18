package com.scythebill.birdlist.android.ui.query

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate

@Composable
fun QueryScreen(
    viewModel: QueryViewModel,
    scrollToTaxonId: String? = null,
    onScrollHandled: () -> Unit = {},
) {
    var queryExpanded by remember { mutableStateOf(true) }
    val rotation by animateFloatAsState(targetValue = if (queryExpanded) 180f else 0f, label = "chevron")

    Column(modifier = Modifier.fillMaxSize()) {
        ListItem(
            modifier = Modifier.clickable { queryExpanded = !queryExpanded },
            headlineContent = { Text("Query") },
            trailingContent = {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.rotate(rotation),
                )
            },
        )
        if (queryExpanded) {
            QueryBuilderScreen(viewModel)
        }
        QueryResultsScreen(viewModel, scrollToTaxonId, onScrollHandled)
    }
}
