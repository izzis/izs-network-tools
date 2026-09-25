package id.web.izs.nettools.ui

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import id.web.izs.nettools.R

/** Console monospace for output text. `FontFamily.Monospace` is not guaranteed
 *  to be monospace — Samsung ships a proportional replacement, which silently
 *  breaks column alignment (e.g. the WiFi 3-row display where line 3 must sit
 *  on line 2's `ch` column). Bundled Source Code Pro is always 1 cell per
 *  char — the same font izs-ssh-android and Tabby use for their terminals. */
val TermMono: FontFamily = FontFamily(Font(R.font.source_code_pro))
