package com.scythebill.birdlist.android.ui.common

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.scythebill.birdlist.android.ui.query.ResultRow

/**
 * Single-character indicators for a sighting row: H (heard-only), I
 * (introduced), and P (photographed). When a photograph has an http(s) URL,
 * its P is a link that opens the photo; with more than one such URL, they're
 * numbered P1, P2, etc.
 */
@Composable
fun SightingIndicators(row: ResultRow, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(modifier = modifier) {
        if (row.heardOnly) {
            Text("H", modifier = Modifier.padding(start = 4.dp))
        }
        if (row.introduced) {
            Text("I", modifier = Modifier.padding(start = 4.dp))
        }
        if (row.photographed) {
            if (row.photoUrls.isEmpty()) {
                Text("P", modifier = Modifier.padding(start = 4.dp))
            } else {
                row.photoUrls.forEachIndexed { index, url ->
                    val label = if (row.photoUrls.size > 1) "P${index + 1}" else "P"
                    Text(
                        label,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .clickable {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            },
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                    )
                }
            }
        }
    }
}
