package fr.epure.launcher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Saut de la liste vers une lettre de l'alphabet : en-tête à hauteur du doigt. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w393dp-h852dp-xhdpi")
class ScrollToLetterTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var state: LazyListState
    private lateinit var scope: CoroutineScope

    private fun showList() {
        compose.setContent {
            state = rememberLazyListState()
            scope = rememberCoroutineScope()
            LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
                items(300) { Box(Modifier.fillMaxWidth().height(40.dp)) }
            }
        }
        compose.waitForIdle()
    }

    private fun offsetOf(index: Int): Int = state.layoutInfo.visibleItemsInfo.first { it.index == index }.offset

    @Test
    fun `la liste glisse jusqu a la lettre`() {
        showList()
        compose.runOnUiThread { scope.launch { state.scrollToLetter(150, 300, glide = true) } }
        compose.waitForIdle()
        assertEquals(300, offsetOf(150))
    }

    @Test
    fun `saut immediat en vitesse rapide`() {
        showList()
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { scope.launch { state.scrollToLetter(150, 300, glide = false) } }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertEquals(300, offsetOf(150))
    }

    @Test
    fun `la lettre change pendant le glissement`() {
        showList()
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { scope.launch { state.scrollToLetter(200, 300, glide = true) } }
        compose.mainClock.advanceTimeBy(64)
        // Nouvelle lettre avant la fin : l'animation repart vers elle.
        compose.runOnUiThread { scope.launch { state.scrollToLetter(60, 500, glide = true) } }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(500, offsetOf(60))
    }
}
