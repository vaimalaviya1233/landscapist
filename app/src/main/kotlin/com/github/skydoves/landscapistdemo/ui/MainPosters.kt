/*
 * Designed and developed by 2020-2023 skydoves (Jaewoong Eum)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.skydoves.landscapistdemo.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.skydoves.landscapistdemo.R
import com.github.skydoves.landscapistdemo.model.MockUtil
import com.github.skydoves.landscapistdemo.model.Poster
import com.github.skydoves.landscapistdemo.theme.DisneyComposeTheme
import com.github.skydoves.landscapistdemo.theme.background
import com.kmpalette.palette.graphics.Palette
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.animation.circular.CircularRevealPlugin
import com.skydoves.landscapist.animation.crossfade.CrossfadePlugin
import com.skydoves.landscapist.coil3.CoilImage
import com.skydoves.landscapist.components.rememberImageComponent
import com.skydoves.landscapist.fresco.FrescoImage
import com.skydoves.landscapist.glide.GlideImage
import com.skydoves.landscapist.palette.PalettePlugin
import com.skydoves.landscapist.palette.rememberPaletteState
import com.skydoves.landscapist.placeholder.shimmer.Shimmer
import com.skydoves.landscapist.placeholder.shimmer.ShimmerPlugin
import kotlinx.coroutines.coroutineScope
import kotlin.math.abs

@Composable
fun DisneyPosters(
  posters: List<Poster>,
  paddingValues: PaddingValues,
  vm: MainViewModel,
) {
  val poster: Poster by vm.poster

  Column(
    Modifier
      .verticalScroll(rememberScrollState())
      .padding(paddingValues),
  ) {
    LazyRow {
      item {
        Box(
          modifier = Modifier.padding(
            start = 16.dp,
            bottom = 16.dp,
            top = 16.dp,
            end = 8.dp,
          ),
        ) {
          Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = "Favorite",
            modifier = Modifier.size(50.dp),
            tint = Color.Red,
          )
        }
      }
      items(items = posters, key = { it.id }) { poster ->
        PosterItem(poster, vm)
      }
    }
    SelectedPoster(poster)
  }
}

@Composable
private fun PosterItem(
  poster: Poster,
  vm: MainViewModel,
) {
  Card(modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp)) {
    FrescoImage(
      imageUrl = poster.image,
      modifier = Modifier
        .size(50.dp)
        .clickable { vm.poster.value = poster },
      imageOptions = ImageOptions(contentScale = ContentScale.Crop),
      component = rememberImageComponent {
        +CrossfadePlugin()
      },
      previewPlaceholder = painterResource(id = R.drawable.poster),
    )
  }
}

/**
 * Time period inside which two taps are registered as double tap.
 */
private const val DoubleTapTimeoutMs: Long = 500L

/**
 * Maximum scale that can be applied to the image.
 */
private const val MaxZoomScale: Float = 3f

/**
 * Middle scale value that can be applied to image.
 */
private const val MidZoomScale: Float = 2f

/**
 * Default (min) value that can be applied to image.
 */
private const val DefaultZoomScale: Float = 1f

private fun calculateMaxOffset(imageSize: Size, scale: Float, parentSize: Size): Offset {
  val maxTranslationY = calculateMaxOffsetPerAxis(imageSize.height, scale, parentSize.height)
  val maxTranslationX = calculateMaxOffsetPerAxis(imageSize.width, scale, parentSize.width)
  return Offset(maxTranslationX, maxTranslationY)
}

private fun calculateMaxOffsetPerAxis(axisSize: Float, scale: Float, parentAxisSize: Float): Float {
  return (axisSize * scale - parentAxisSize).coerceAtLeast(0f) / 2
}

@Composable
private fun SelectedPoster(
  poster: Poster,
) {
  var palette by rememberPaletteState(null)

  var imageSize by remember { mutableStateOf(Size(0f, 0f)) }
  var currentScale by remember { mutableStateOf(DefaultZoomScale) }
  var translation by remember { mutableStateOf(Offset(0f, 0f)) }
  val scale by animateFloatAsState(targetValue = currentScale, label = "zoomable")

  BoxWithConstraints(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.Center,
  ) {
    val density = LocalDensity.current
    val parentSize = Size(density.run { maxWidth.toPx() }, density.run { maxHeight.toPx() })

    CoilImage(
      imageModel = { poster.image },
      modifier = Modifier
        .aspectRatio(0.8f)
        .graphicsLayer(
          scaleY = scale,
          scaleX = scale,
          translationX = translation.x,
          translationY = translation.y,
        )
        .onGloballyPositioned {
          imageSize = Size(it.size.width.toFloat(), it.size.height.toFloat())
        }
        .pointerInput(Unit) {
          coroutineScope {
            awaitEachGesture {
              awaitFirstDown(requireUnconsumed = true)
              do {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)

                val zoom = event.calculateZoom()
                currentScale = (zoom * currentScale).coerceAtMost(MaxZoomScale)

                val maxTranslation = calculateMaxOffset(
                  imageSize = imageSize,
                  scale = currentScale,
                  parentSize = parentSize,
                )

                val offset = event.calculatePan()
                val newTranslationX = translation.x + offset.x * currentScale
                val newTranslationY = translation.y + offset.y * currentScale

                translation = Offset(
                  newTranslationX.coerceIn(-maxTranslation.x, maxTranslation.x),
                  newTranslationY.coerceIn(-maxTranslation.y, maxTranslation.y),
                )

                if (abs(newTranslationX) < calculateMaxOffsetPerAxis(
                    imageSize.width,
                    currentScale,
                    parentSize.width,
                  ) || zoom != DefaultZoomScale
                ) {
                  event.changes.forEach { it.consume() }
                }
              } while (event.changes.any { it.pressed })

              if (currentScale < DefaultZoomScale) {
                currentScale = DefaultZoomScale
              }
            }
          }
        }
        .pointerInput(Unit) {
          coroutineScope {
            awaitEachGesture {
              awaitFirstDown()
              withTimeoutOrNull(DoubleTapTimeoutMs) {
                awaitFirstDown()
                currentScale = when {
                  currentScale == MaxZoomScale -> DefaultZoomScale
                  currentScale >= MidZoomScale -> MaxZoomScale
                  else -> MidZoomScale
                }

                if (currentScale == DefaultZoomScale) {
                  translation = Offset(0f, 0f)
                }
              }
            }
          }
        },
      component = rememberImageComponent {
        +ShimmerPlugin(
          Shimmer.Resonate(
            baseColor = if (isSystemInDarkTheme()) {
              background
            } else {
              Color.White
            },
            highlightColor = Color.LightGray,
          ),
        )
        +CircularRevealPlugin()
        +PalettePlugin { palette = it }
      },
      previewPlaceholder = painterResource(id = R.drawable.poster),
    )
  }

  ColorPalettes(palette)

  Text(
    text = poster.name,
    style = MaterialTheme.typography.h2,
    textAlign = TextAlign.Center,
    modifier = Modifier.padding(8.dp),
  )

  Text(
    text = poster.description,
    style = MaterialTheme.typography.body1,
    textAlign = TextAlign.Start,
    modifier = Modifier.padding(8.dp),
  )

  Text(
    text = "Gif",
    style = MaterialTheme.typography.h2,
    textAlign = TextAlign.Center,
    modifier = Modifier.padding(8.dp),
  )

  GlideImage(
    imageModel = { poster.gif },
    modifier = Modifier
      .fillMaxWidth()
      .padding(8.dp)
      .clip(RoundedCornerShape(8.dp)),
    previewPlaceholder = painterResource(id = R.drawable.poster),
  )

  Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun ColorPalettes(palette: Palette?) {
  val colorList: List<Int> = palette.paletteColorList()

  LazyRow(
    modifier = Modifier
      .padding(horizontal = 8.dp, vertical = 16.dp),
  ) {
    items(colorList) { color ->
      Crossfade(
        targetState = color,
        modifier = Modifier
          .padding(horizontal = 8.dp)
          .size(45.dp),
        label = "ColorPalettes",
      ) {
        Box(
          modifier = Modifier
            .background(color = Color(it))
            .fillMaxSize(),
        )
      }
    }
  }
}

@Preview
@Composable
private fun SelectedPosterPreview() {
  DisneyComposeTheme(darkTheme = false) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState()),
    ) {
      SelectedPoster(poster = MockUtil.getMockPoster())
    }
  }
}
