package com.jpcottin.composebreakout.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.jpcottin.composebreakout.engine.Game
import com.jpcottin.composebreakout.engine.Levels
import com.jpcottin.composebreakout.engine.World
import com.jpcottin.composebreakout.theme.SpaceBlack
import com.jpcottin.composebreakout.theme.TitleGold

/**
 * The ten walls side by side: every level's bricks as the engine lays them
 * out, drawn by the same code that draws the game. A preview and a
 * screenshot test, so a change to a level's layout is seen, not guessed.
 */
@Composable
fun LevelsGallery(modifier: Modifier = Modifier, columns: Int = 2) {
    val walls = remember { (1..Levels.COUNT).map { level -> level to wallOf(level) } }
    Column(modifier.background(MaterialTheme.colorScheme.background).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (row in walls.chunked(columns)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for ((level, game) in row) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "LEVEL $level",
                            style = MaterialTheme.typography.labelLarge,
                            color = TitleGold,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        Canvas(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(WALL_ASPECT)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SpaceBlack),
                        ) {
                            // The wall's band only. The cell is the top of a
                            // taller virtual canvas whose width holds the lane
                            // (half-width 0.63 plus a margin), and the world is
                            // shifted so the ceiling sits just under the cell's
                            // top edge.
                            val m = WorldToPixels(Size(size.width, 2f * size.width / LANE_SPAN))
                            for (b in game.bricks) if (b.alive) drawBrick(m, game.brickX(b), game.brickY(b) + WALL_SHIFT, b, 1f)
                        }
                    }
                }
            }
        }
    }
}

/** A game on [level] with the wall built and nothing in flight, on a lane
 *  wide enough for the full ten columns. */
private fun wallOf(level: Int): Game = Game().apply {
    setAspect(LANE_SPAN / 2f)
    debug.startLevel(level)
}

/** The cell's width for its height: nine rows and the margin above them. */
private const val WALL_ASPECT = 2.4f
/** World units across the virtual canvas: the lane's full width plus margins. */
private const val LANE_SPAN = 2f * (World.MAX_FIELD_HW + 0.07f)
/** Added to every brick's world y so the ceiling lands near the cell's top. */
private const val WALL_SHIFT = -1f + 0.05f - World.TOP_WALL_Y
