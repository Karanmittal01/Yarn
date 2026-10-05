package app.yarn.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.yarn.R
import app.yarn.work.JobKind
import app.yarn.work.JobProgress

/** "Re-sorting all messages · 1,240 of 5,600" with a bar; indeterminate until the total is known. */
@Composable
fun JobProgressBar(job: JobProgress, modifier: Modifier = Modifier) {
    val fmt = java.text.NumberFormat.getIntegerInstance()
    Column(modifier.fillMaxWidth()) {
        Row {
            Text(
                stringResource(if (job.kind == JobKind.RESORTING) R.string.job_resorting else R.string.job_sorting),
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (job.total > 0) {
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.job_count, fmt.format(job.done), fmt.format(job.total)),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        val bar = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
        if (job.total > 0) LinearProgressIndicator(progress = { job.fraction }, modifier = bar) else LinearProgressIndicator(bar)
    }
}
