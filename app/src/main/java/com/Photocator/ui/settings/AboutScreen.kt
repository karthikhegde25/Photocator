package com.Photocator.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.Photocator.BuildConfig
import com.Photocator.R
import com.Photocator.Session
import com.Photocator.ui.C
import com.Photocator.ui.Card
import com.Photocator.ui.Host
import com.Photocator.ui.ScreenHeader
import com.Photocator.ui.T

private const val REPO = "https://github.com/karthikhegde25/Photocator"

@Composable
fun AboutScreen(session: Session, host: Host) {
    Column(Modifier.fillMaxSize().background(C.Black)) {
        ScreenHeader("About", onBack = { session.pop() })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Image(painterResource(R.drawable.logo), null, Modifier.padding(top = 16.dp).size(88.dp).clip(RoundedCornerShape(22.dp)))
            T("Photocator", 26f, weight = FontWeight.Bold)
            T("Version ${BuildConfig.VERSION_NAME}", 13f, C.TextDim)
            T("Developer: Karthik Hegde", 14f, weight = FontWeight.SemiBold)
            T(
                "Photocator is a camera that stamps every photo and video with where and when it was taken: coordinates, place name, address, a map thumbnail, the weather and compass heading.",
                14f, C.TextDim, align = TextAlign.Center,
            )
            Column(
                Modifier.clip(RoundedCornerShape(18.dp)).clickable { host.openUrl(REPO) }.padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                GithubMark(Modifier.size(56.dp))
                T("View source on GitHub", 13f, C.Blue, weight = FontWeight.SemiBold)
            }
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    T("Data credits", 15f, weight = FontWeight.SemiBold)
                    T("Map imagery: Esri and its data providers, © OpenStreetMap contributors, OpenTopoMap (CC-BY-SA).", 13f, C.TextDim)
                    T("Place search and addresses: © OpenStreetMap contributors (Nominatim, Photon).", 13f, C.TextDim)
                    T("Weather: Open-Meteo.", 13f, C.TextDim)
                }
            }
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    T("License", 15f, weight = FontWeight.SemiBold)
                    T("Photocator is free software licensed under the GNU General Public License v3.0 (GPL-3.0). You may use, study, share and modify it under the terms of that license. It comes with no warranty.", 13f, C.TextDim)
                    T("GPL-3.0 text and source: $REPO", 12f, C.Blue, Modifier.clickable { host.openUrl("$REPO/blob/main/LICENSE") })
                }
            }
            T("© 2026 Karthik Hegde", 12f, C.TextFaint, Modifier.padding(bottom = 24.dp), align = TextAlign.Center)
        }
    }
}

private const val GITHUB_PATH =
    "M12 .297c-6.63 0-12 5.373-12 12 0 5.303 3.438 9.8 8.205 11.385.6.113.82-.258.82-.577 0-.285-.01-1.04-.015-2.04-3.338.724-4.042-1.61-4.042-1.61C4.422 18.07 3.633 17.7 3.633 17.7c-1.087-.744.084-.729.084-.729 1.205.084 1.838 1.236 1.838 1.236 1.07 1.835 2.809 1.305 3.495.998.108-.776.417-1.305.76-1.605-2.665-.3-5.466-1.332-5.466-5.93 0-1.31.465-2.38 1.235-3.22-.135-.303-.54-1.523.105-3.176 0 0 1.005-.322 3.3 1.23.96-.267 1.98-.399 3-.405 1.02.006 2.04.138 3 .405 2.28-1.552 3.285-1.23 3.285-1.23.645 1.653.24 2.873.12 3.176.765.84 1.23 1.91 1.23 3.22 0 4.61-2.805 5.625-5.475 5.925.435.375.81 1.095.81 2.22 0 1.605-.015 2.895-.015 3.3 0 .315.225.69.825.57A12.02 12.02 0 0 0 24 12.297c0-6.627-5.373-12-12-12z"

/** The GitHub mark, drawn from its vector outline. */
@Composable
private fun GithubMark(modifier: Modifier) {
    val path = remember { PathParser().parsePathString(GITHUB_PATH).toPath() }
    Canvas(modifier) {
        val k = size.width / 24f
        withTransform({ scale(k, k, pivot = androidx.compose.ui.geometry.Offset.Zero) }) {
            drawPath(path, Color.White)
        }
    }
}
