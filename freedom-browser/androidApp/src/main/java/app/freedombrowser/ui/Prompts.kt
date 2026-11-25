package app.freedombrowser.ui

import android.Manifest
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.freedombrowser.R
import app.freedombrowser.browser.BrowserViewModel
import app.freedombrowser.browser.Prompt
import app.freedombrowser.shared.AiAssist
import app.freedombrowser.shared.Formatters
import app.freedombrowser.shared.SitePermission

/** Asks Android for runtime permissions; result is true only if all were granted. */
fun interface RuntimePermissions {
    fun request(permissions: Array<String>, onResult: (Boolean) -> Unit)
}

fun androidPermissionsFor(p: Set<SitePermission>): Array<String> = buildList {
    if (SitePermission.CAMERA in p) add(Manifest.permission.CAMERA)
    if (SitePermission.MICROPHONE in p) add(Manifest.permission.RECORD_AUDIO)
    if (SitePermission.LOCATION in p) { add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.ACCESS_COARSE_LOCATION) }
}.toTypedArray()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptHost(prompt: Prompt?, vm: BrowserViewModel, actions: PageActions, runtime: RuntimePermissions) {
    when (prompt) {
        null -> Unit
        is Prompt.Permission -> PermissionPrompt(prompt, vm, runtime)
        is Prompt.Download -> DownloadPrompt(prompt, vm)
        is Prompt.HttpAuth -> HttpAuthPrompt(prompt, vm)
        is Prompt.ExternalApp -> AlertDialog(
            onDismissRequest = vm::dismissPrompt,
            title = { Text(stringResource(R.string.open_external_title)) },
            text = { Text(stringResource(R.string.open_external_body, prompt.url.take(200))) },
            confirmButton = { TextButton(onClick = { vm.dismissPrompt(); actions.openExternal(prompt.url) }) { Text(stringResource(R.string.open)) } },
            dismissButton = { TextButton(onClick = vm::dismissPrompt) { Text(stringResource(R.string.cancel)) } },
        )
        is Prompt.LongPress -> ModalBottomSheet(onDismissRequest = vm::dismissPrompt) {
            val link = prompt.target.linkUrl
            val image = prompt.target.imageUrl
            Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
                Text((link ?: image).orEmpty(), maxLines = 2, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                fun done(a: () -> Unit) { vm.dismissPrompt(); a() }
                val isPrivate = vm.tabs.tab(prompt.tabId)?.isPrivate == true
                if (link != null) {
                    MenuItem(Icons.Filled.Tab, R.string.open_in_new_tab) { done { vm.openNewTab(link, isPrivate = isPrivate, afterCurrent = true) } }
                    MenuItem(Icons.Filled.OpenInNew, R.string.open_in_background_tab) { done { vm.openNewTab(link, isPrivate = isPrivate, background = true, afterCurrent = true) } }
                    if (!isPrivate) MenuItem(Icons.Filled.VisibilityOff, R.string.open_in_private_tab) { done { vm.openNewTab(link, isPrivate = true) } }
                    MenuItem(Icons.Filled.ContentCopy, R.string.copy_link) { done { actions.copy(link) } }
                    MenuItem(Icons.Filled.Share, R.string.share_link) { done { actions.share(link, "") } }
                    MenuItem(Icons.Filled.Download, R.string.download_link) { done { vm.downloadUrl(link, prompt.tabId) } }
                }
                if (image != null) {
                    MenuItem(Icons.Filled.Tab, R.string.open_image_new_tab) { done { vm.openNewTab(image, isPrivate = isPrivate, afterCurrent = true) } }
                    MenuItem(Icons.Filled.Download, R.string.download_image) { done { vm.downloadUrl(image, prompt.tabId) } }
                    MenuItem(Icons.Filled.ContentCopy, R.string.copy_image_link) { done { actions.copy(image) } }
                }
            }
        }
        is Prompt.Translate -> AlertDialog(
            onDismissRequest = vm::dismissPrompt,
            title = { Text(stringResource(R.string.translate_page)) },
            text = { Text(stringResource(R.string.translate_disclosure, prompt.providerHost)) },
            confirmButton = { TextButton(onClick = { vm.confirmTranslate(prompt.targetUrl) }) { Text(stringResource(R.string.translate_continue)) } },
            dismissButton = { TextButton(onClick = vm::dismissPrompt) { Text(stringResource(R.string.cancel)) } },
        )
        is Prompt.AiConsent -> AiConsentPrompt(prompt, vm)
        is Prompt.AiResult -> ModalBottomSheet(onDismissRequest = vm::dismissPrompt) {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp).heightIn(min = 160.dp)) {
                Text(stringResource(R.string.ai_result_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                when {
                    prompt.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.padding(end = 12.dp))
                        Text(stringResource(R.string.ai_working))
                    }
                    prompt.error != null -> Text(stringResource(R.string.ai_failed, prompt.error), color = LocalFreedomColors.current.danger)
                    else -> SelectionContainer(Modifier.verticalScroll(rememberScrollState())) { Text(prompt.text.orEmpty(), style = MaterialTheme.typography.bodyMedium) }
                }
                if (!prompt.loading && prompt.text != null) TextButton(onClick = { actions.copy(prompt.text) }) { Text(stringResource(R.string.copy)) }
                Text(stringResource(R.string.ai_result_disclaimer), style = MaterialTheme.typography.labelSmall, color = LocalFreedomColors.current.textSecondary)
            }
        }
    }
}

@Composable
private fun PermissionPrompt(prompt: Prompt.Permission, vm: BrowserViewModel, runtime: RuntimePermissions) {
    // Grants a site request only if Android also granted the matching runtime permission.
    fun grantWithRuntime(remember: Boolean) {
        val perms = androidPermissionsFor(prompt.permissions)
        if (perms.isEmpty()) return vm.answerPermission(prompt, true, remember, prompt.permissions, prompt.protectedMedia)
        runtime.request(perms) { ok ->
            vm.answerPermission(prompt, ok, remember && ok, if (ok) prompt.permissions else emptySet(), prompt.protectedMedia)
        }
    }
    if (prompt.preApproved) {
        LaunchedEffect(prompt) { grantWithRuntime(remember = false) }
        return
    }
    var rememberChoice by remember { mutableStateOf(true) }
    val names = prompt.permissions.map {
        stringResource(when (it) {
            SitePermission.CAMERA -> R.string.perm_camera
            SitePermission.MICROPHONE -> R.string.perm_microphone
            SitePermission.LOCATION -> R.string.perm_location
            SitePermission.NOTIFICATIONS -> R.string.perm_notifications
            SitePermission.POPUPS -> R.string.perm_popups
        })
    } + if (prompt.protectedMedia) listOf(stringResource(R.string.perm_protected_media)) else emptyList()
    AlertDialog(
        onDismissRequest = { vm.answerPermission(prompt, false, false, emptySet(), false) },
        title = { Text(stringResource(R.string.permission_title, prompt.site)) },
        text = {
            Column {
                Text(stringResource(R.string.permission_body, names.joinToString(", ")))
                if (prompt.permissions.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = rememberChoice, onCheckedChange = { rememberChoice = it })
                    Text(stringResource(R.string.remember_for_site))
                }
            }
        },
        confirmButton = { TextButton(onClick = { grantWithRuntime(rememberChoice) }) { Text(stringResource(R.string.decision_allow)) } },
        dismissButton = { TextButton(onClick = { vm.answerPermission(prompt, false, rememberChoice, emptySet(), false) }) { Text(stringResource(R.string.decision_block)) } },
    )
}

@Composable
private fun DownloadPrompt(prompt: Prompt.Download, vm: BrowserViewModel) {
    val p = prompt.pending
    var name by remember(p) { mutableStateOf(p.fileName) }
    AlertDialog(
        onDismissRequest = vm::dismissPrompt,
        title = { Text(stringResource(if (p.dangerous) R.string.download_dangerous_title else R.string.download_confirm_title)) },
        text = {
            Column {
                if (p.dangerous) Text(stringResource(R.string.download_dangerous_body), color = LocalFreedomColors.current.warning)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text(stringResource(R.string.file_name)) }, modifier = Modifier.fillMaxWidth())
                if (p.contentLength > 0) Text(Formatters.bytes(p.contentLength), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
            }
        },
        confirmButton = { TextButton(onClick = { vm.confirmDownload(p, name) }) { Text(stringResource(R.string.download)) } },
        dismissButton = { TextButton(onClick = vm::dismissPrompt) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun HttpAuthPrompt(prompt: Prompt.HttpAuth, vm: BrowserViewModel) {
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { prompt.cancel(); vm.dismissPrompt() },
        title = { Text(stringResource(R.string.auth_title)) },
        text = {
            Column {
                Text(stringResource(R.string.auth_body, prompt.host, prompt.realm))
                OutlinedTextField(user, { user = it }, singleLine = true, label = { Text(stringResource(R.string.username)) })
                OutlinedTextField(pass, { pass = it }, singleLine = true, label = { Text(stringResource(R.string.password)) },
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            }
        },
        confirmButton = { TextButton(onClick = { prompt.proceed(user, pass); vm.dismissPrompt() }) { Text(stringResource(R.string.sign_in)) } },
        dismissButton = { TextButton(onClick = { prompt.cancel(); vm.dismissPrompt() }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun AiConsentPrompt(prompt: Prompt.AiConsent, vm: BrowserViewModel) {
    var question by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = vm::dismissPrompt,
        title = { Text(stringResource(if (prompt.task == AiAssist.Task.ASK) R.string.ai_ask else R.string.ai_summarize)) },
        text = {
            Column {
                Text(stringResource(R.string.ai_disclosure, prompt.endpointHost))
                if (prompt.task == AiAssist.Task.ASK) OutlinedTextField(question, { question = it }, label = { Text(stringResource(R.string.ai_question)) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(enabled = prompt.task != AiAssist.Task.ASK || question.isNotBlank(), onClick = { vm.runAi(prompt.task, question.takeIf { it.isNotBlank() }) }) {
                Text(stringResource(R.string.ai_send))
            }
        },
        dismissButton = { TextButton(onClick = vm::dismissPrompt) { Text(stringResource(R.string.cancel)) } },
    )
}
