package com.scythebill.birdlist.android.ui.query

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun QueryScreen(viewModel: QueryViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        QueryBuilderScreen(viewModel)
        QueryResultsScreen(viewModel)
    }
}
