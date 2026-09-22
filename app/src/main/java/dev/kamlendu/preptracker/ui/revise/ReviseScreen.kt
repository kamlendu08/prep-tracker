package dev.kamlendu.preptracker.ui.revise

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.kamlendu.preptracker.revision.RevisionContent
import dev.kamlendu.preptracker.revision.Subject
import dev.kamlendu.preptracker.revision.SubjectSummary
import dev.kamlendu.preptracker.revision.Topic

/**
 * Quick revision: subject → topic → cards.
 *
 * Deliberately three flat levels with no search and no progress tracking. The screen exists for
 * the ten minutes before a mock test or in a queue, and every extra control is something to get
 * past before reading the thing you opened the app for.
 *
 * Navigation is two saved slugs rather than a nav graph — the app has no other multi-level
 * screen, and a NavHost for one would be more machinery than the feature is.
 */
@Composable
fun ReviseScreen(
    contentPadding: PaddingValues,
    viewModel: ReviseViewModel = viewModel(),
) {
    val subjects by viewModel.subjects.collectAsStateWithLifecycle()
    val remarks by viewModel.remarks.collectAsStateWithLifecycle()

    var subjectSlug by rememberSaveable { mutableStateOf<String?>(null) }
    var topicSlug by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(enabled = subjectSlug != null) {
        if (topicSlug != null) topicSlug = null else subjectSlug = null
    }

    var subject by remember { mutableStateOf<Subject?>(null) }
    LaunchedEffect(subjectSlug) {
        subject = subjectSlug?.let { viewModel.subject(it) }
    }

    val current = subject?.takeIf { it.slug == subjectSlug }
    val topic = topicSlug?.let { slug -> current?.topics?.firstOrNull { it.slug == slug } }

    when {
        current != null && topic != null -> TopicScreen(
            subject = current,
            topic = topic,
            remarks = remarks,
            contentPadding = contentPadding,
            onBack = { topicSlug = null },
            onSaveRemark = viewModel::saveRemark,
        )

        current != null -> TopicList(
            subject = current,
            remarks = remarks,
            contentPadding = contentPadding,
            onBack = { subjectSlug = null },
            onOpen = { topicSlug = it },
        )

        else -> SubjectList(
            subjects = subjects,
            contentPadding = contentPadding,
            onOpen = { subjectSlug = it },
        )
    }
}

@Composable
private fun SubjectList(
    subjects: List<SubjectSummary>,
    contentPadding: PaddingValues,
    onOpen: (String) -> Unit,
) {
    val widest = subjects.maxOfOrNull { it.sharePct } ?: 1.0

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 10.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text("Revise", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Concepts and formulas GATE EC actually asks. The weighting beside each " +
                        "subject is measured from 1,105 previous-year questions, not estimated.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
            }
        }

        items(subjects, key = { it.slug }) { s ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { onOpen(s.slug) }
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        s.subject,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${s.sharePct}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                // Where the marks are. Same bar, same scale, so subjects compare at a glance.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((s.sharePct / widest).toFloat().coerceIn(0f, 1f))
                            .fillMaxSize()
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "${s.topics.size} topics · ${s.cardCount} cards · ~${s.perPaper} questions a year",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TopicList(
    subject: Subject,
    remarks: Map<String, String>,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = subject.subject,
            subtitle = (subject.section?.let { "Section $it · " } ?: "") +
                "${subject.sharePct}% of the paper",
            topPadding = contentPadding.calculateTopPadding(),
            onBack = onBack,
        )

        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 6.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(subject.topics, key = { it.slug }) { t ->
                val noted = t.cards.count {
                    remarks.containsKey(RevisionContent.cardId(subject.slug, t.slug, it.slug))
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { onOpen(t.slug) }
                        .padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            t.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${t.cards.size} cards" + if (noted > 0) " · $noted with your remark" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    // The official scope, verbatim — so nothing on the screen is a guess about
                    // what is examinable.
                    Text(
                        t.syllabus,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopicScreen(
    subject: Subject,
    topic: Topic,
    remarks: Map<String, String>,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onSaveRemark: (String, String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val palette = remember(colors) {
        PagePalette(
            background = colors.background,
            surface = colors.surface,
            onSurface = colors.onSurface,
            onSurfaceVariant = colors.onSurfaceVariant,
            accent = colors.primary,
            warn = colors.secondary,
            outline = colors.outline,
        )
    }

    var page by remember(topic.slug) { mutableStateOf<RemarkPage?>(null) }
    var editing by remember { mutableStateOf<Pair<String, String>?>(null) }
    var draft by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState()

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = topic.title,
            subtitle = subject.subject,
            topPadding = contentPadding.calculateTopPadding(),
            onBack = onBack,
        )
        TopicPage(
            subjectSlug = subject.slug,
            topic = topic,
            remarks = remarks,
            palette = palette,
            onEditRemark = { cardId, cardTitle ->
                draft = remarks[cardId].orEmpty()
                editing = cardId to cardTitle
            },
            onReady = { page = it },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = contentPadding.calculateBottomPadding()),
        )
    }

    val target = editing
    if (target != null) {
        ModalBottomSheet(
            onDismissRequest = { editing = null },
            sheetState = sheetState,
        ) {
            Column(
                Modifier
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Your remark", style = MaterialTheme.typography.titleMedium)
                Text(
                    target.second,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 2000) draft = it },
                    placeholder = { Text("A trick that works for you, or what you keep forgetting.") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            onSaveRemark(target.first, draft)
                            page?.setRemark(target.first, draft.trim().ifBlank { null })
                            editing = null
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Save") }

                    if (remarks.containsKey(target.first)) {
                        OutlinedButton(
                            onClick = {
                                onSaveRemark(target.first, "")
                                page?.setRemark(target.first, null)
                                editing = null
                            },
                        ) { Text("Delete") }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(title: String, subtitle: String, topPadding: Dp, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding, start = 4.dp, end = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
