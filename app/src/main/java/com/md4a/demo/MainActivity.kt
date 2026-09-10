package com.md4a.demo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.md4a.render.Md4aDocument
import kotlinx.coroutines.launch

/**
 * Demo shell: a thin UI around the MD4a SDK.
 * Type a repo/URL, tap 随机, or read the built-in sample.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { DemoScreen() }
    }
}

private sealed interface DocState {
    data object Idle : DocState
    data object Loading : DocState
    data class Ready(val markdown: String, val baseUrl: String?, val title: String) : DocState
    data class Error(val message: String) : DocState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DemoScreen() {
    var input by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<DocState>(DocState.Ready(SAMPLE_MD, null, "内置示例")) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun load(target: String?) {
        scope.launch {
            state = DocState.Loading
            state = when (val result = ReadmeFetcher.fetch(target)) {
                is ReadmeFetcher.Result.Ok -> DocState.Ready(
                    result.doc.markdown,
                    result.doc.imageBaseUrl,
                    result.doc.markdown.lineSequence().firstOrNull { it.startsWith("# ") }?.removePrefix("# ")?.take(40) ?: "README",
                )
                is ReadmeFetcher.Result.Failed -> DocState.Error(result.message)
            }
        }
    }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("MD4a demo") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("owner/repo 或 README 链接") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { load(input) }),
                    )
                    OutlinedButton(onClick = { load(null) }) {
                        Text("🎲 随机")
                    }
                    Button(onClick = { load(input) }) {
                        Text("打开")
                    }
                }

                when (val s = state) {
                    is DocState.Loading -> Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) { CircularProgressIndicator() }

                    is DocState.Error -> Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("加载失败", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(8.dp))
                        Text(s.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }

                    is DocState.Ready -> Column(modifier = Modifier.fillMaxSize()) {
                        Text(
                            s.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                        Md4aDocument(
                            markdown = s.markdown,
                            modifier = Modifier.fillMaxSize(),
                            baseUrl = s.baseUrl,
                            onLinkClick = { url ->
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            },
                        )
                    }

                    DocState.Idle -> Unit
                }
            }
        }
    }
}
