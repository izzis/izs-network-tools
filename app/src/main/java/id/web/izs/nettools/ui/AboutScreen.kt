package id.web.izs.nettools.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri

private data class Credit(val name: String, val note: String, val url: String)

private val CREDITS = listOf(
    Credit(
        "Shodan InternetDB",
        "Passive open-port + vuln data for Global Ports. Weekly scan, free for non-commercial use, no key.",
        "https://internetdb.shodan.io/"
    ),
    Credit(
        "Globalping",
        "Worldwide probes for Global Ping/Trace. Free, no key (250 tests/hour anonymous); optional token raises limits.",
        "https://globalping.io/"
    ),
    Credit(
        "dnsjava",
        "DNS client behind the Dig tool.",
        "https://github.com/ibauersachs/dnsjava"
    )
)

const val ABOUT_REPO_URL = "https://github.com/izzis/izs-network-tools"

/** About: version, source link, third-party credits, license. No donation links. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val pkg = remember {
        val pm = context.packageManager
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
        }.getOrNull()
    }
    val versionCode: Long = remember(pkg) {
        if (pkg == null) -1L
        else if (Build.VERSION.SDK_INT >= 28) pkg.longVersionCode
        else @Suppress("DEPRECATION") pkg.versionCode.toLong()
    }
    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("izs Network Tools", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "v${pkg?.versionName ?: "?"} ($versionCode) · ${context.packageName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Ping, DNS, traceroute, whois, ports, certs and more — local engines plus global sources, long-press a tool to switch scope or server.",
                style = MaterialTheme.typography.bodyMedium
            )
            ListItem(
                headlineContent = { Text("Source code on GitHub") },
                supportingContent = { Text(ABOUT_REPO_URL) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                modifier = Modifier.clickable { open(ABOUT_REPO_URL) }
            )
            Text("Data & services", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            CREDITS.forEach { c ->
                ListItem(
                    headlineContent = { Text(c.name) },
                    supportingContent = { Text(c.note) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { open(c.url) }
                )
            }
            Text("License", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "MIT © 2026 izs. Free and open-source; global data sources belong to their respective owners and follow their own terms.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
