package com.m3.app.feature.fuel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3.core.data.StoredGroceryItem
import com.m3.core.designsystem.component.DoubleRule
import com.m3.core.designsystem.component.HairRule
import com.m3.core.designsystem.component.Kicker
import com.m3.core.designsystem.component.M3CheckRow
import com.m3.core.designsystem.component.M3OutlineButton
import com.m3.core.designsystem.component.M3Snackbar
import com.m3.core.designsystem.component.M3SquareButton
import com.m3.core.designsystem.component.M3TextField
import com.m3.core.designsystem.component.SectionHeader
import com.m3.core.designsystem.component.Skeleton
import com.m3.core.designsystem.component.TextAction
import com.m3.core.designsystem.theme.M3
import com.m3.core.model.Grocery
import com.m3.core.model.GrocerySection
import kotlinx.coroutines.launch

/** This week's shop, made from the owner's targets, plus their own items. Opened from Fuel. */
@Composable
fun GroceryScreen(onBack: () -> Unit, viewModel: GroceryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable { mutableStateOf("") }
    val canAdd = Grocery.validateName(draft) == null
    val add = {
        if (canAdd) {
            viewModel.add(draft) { added ->
                if (added) draft = "" else scope.launch { snackbar.showSnackbar("Already on your list") }
            }
        }
    }
    val remove = { item: StoredGroceryItem ->
        viewModel.remove(item)
        scope.launch {
            val result = snackbar.showSnackbar(message = "Removed ${item.name}", actionLabel = "Undo")
            if (result == SnackbarResult.ActionPerformed) viewModel.restore(item)
        }
        Unit
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = M3.spacing.gutter, vertical = M3.spacing.m),
            verticalArrangement = Arrangement.spacedBy(M3.spacing.l),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(M3.spacing.xs)) {
                TextAction(text = "← Fuel", onClick = onBack)
                Kicker(text = "This week's shop", color = M3.colors.accent)
                Text(text = "Grocery list", style = M3.typography.display, color = M3.colors.ink)
                Spacer(Modifier.size(M3.spacing.xs))
                DoubleRule()
            }
            when (val s = state) {
                GroceryState.Loading -> Skeleton(widths = listOf(0.5f, 1f, 0.8f, 1f))
                GroceryState.Failed ->
                    Text(text = "Couldn't load your list.", style = M3.typography.body, color = M3.colors.accent)
                is GroceryState.Ready -> {
                    if (s.items.isEmpty()) {
                        Text(
                            text = if (s.hasTargets) {
                                "Nothing on the list yet: refresh to build this week's shop from your targets, or add your own items."
                            } else {
                                "Set up your profile to get a shop from your targets; until then you can add your own items."
                            },
                            style = M3.typography.body,
                            color = M3.colors.muted,
                        )
                    }
                    GrocerySection.entries.forEach { section ->
                        val items = s.items.filter { it.section == section }
                        if (items.isNotEmpty()) {
                            Column {
                                SectionHeader(title = section.displayName)
                                items.forEach { item ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        M3CheckRow(
                                            label = item.name,
                                            supportingText = item.amount.ifBlank { null },
                                            checked = item.checked,
                                            onCheckedChange = { viewModel.setChecked(item, it) },
                                            modifier = Modifier.weight(1f),
                                        )
                                        M3SquareButton(description = "Remove ${item.name}", onClick = { remove(item) }, size = 44.dp) {
                                            Text(text = "×", style = M3.typography.numeric, color = M3.colors.muted)
                                        }
                                    }
                                    HairRule()
                                }
                            }
                        }
                    }
                    M3TextField(
                        value = draft,
                        onValueChange = { draft = it.take(Grocery.MAX_NAME_CHARS + 1) },
                        label = "Add your own item",
                        error = "Keep it under ${Grocery.MAX_NAME_CHARS + 1} characters.".takeIf { draft.trim().length > Grocery.MAX_NAME_CHARS },
                        imeAction = ImeAction.Done,
                        keyboardActions = KeyboardActions(onDone = { add() }),
                    )
                    M3OutlineButton(text = "Add item", onClick = { add() }, enabled = canAdd, modifier = Modifier.fillMaxWidth())
                    M3OutlineButton(
                        text = "Refresh from my targets",
                        onClick = viewModel::refresh,
                        enabled = s.hasTargets,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextAction(text = "Clear ticked", onClick = viewModel::clearTicked, enabled = s.items.any { it.checked })
                }
            }
        }
        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter)) { data -> M3Snackbar(data) }
    }
}
