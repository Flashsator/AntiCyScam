package com.anticyscam.app.ui.dormant

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anticyscam.app.R
import com.anticyscam.app.data.repository.TransferAccountRepository
import com.anticyscam.app.service.AppLauncher
import com.anticyscam.app.ui.theme.AlertYellow
import com.anticyscam.app.ui.theme.AntiCyScamTheme
import com.anticyscam.app.ui.theme.SurfaceBlack
import com.anticyscam.app.ui.theme.SurfaceDim
import com.anticyscam.app.ui.theme.TextPrimary
import com.anticyscam.app.ui.theme.TextSecondary
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One-time verification before reusing an account that has been dormant for
 * 90+ days (PRD § 3.4). Once the user confirms, the dormancy flag is consumed
 * via [TransferAccountRepository.consumeDormantVerification] and the launch
 * proceeds. The flag is single-use — if the account stays dormant another 90
 * days, it'll re-flag and require another confirmation.
 */
@AndroidEntryPoint
class DormantVerifyActivity : ComponentActivity() {

    private val viewModel: DormantVerifyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.bootstrap(
            accountId = intent.getLongExtra(EXTRA_ACCOUNT_ID, -1L),
            targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE),
            accountNumber = intent.getStringExtra(EXTRA_ACCOUNT_NUMBER).orEmpty()
        )
        setContent {
            AntiCyScamTheme {
                val state by viewModel.state.collectAsState()
                Screen(
                    state = state,
                    onToggleCheck = viewModel::toggleCheck,
                    onProceed = {
                        viewModel.confirmAndLaunch(this@DormantVerifyActivity)
                        finish()
                    },
                    onDismiss = ::finish
                )
            }
        }
    }

    companion object {
        private const val EXTRA_ACCOUNT_ID = "extra_account_id"
        private const val EXTRA_TARGET_PACKAGE = "extra_target_package"
        private const val EXTRA_ACCOUNT_NUMBER = "extra_account_number"

        fun newIntent(
            context: Context,
            accountId: Long,
            targetPackage: String?,
            accountNumber: String
        ): Intent = Intent(context, DormantVerifyActivity::class.java).apply {
            putExtra(EXTRA_ACCOUNT_ID, accountId)
            putExtra(EXTRA_TARGET_PACKAGE, targetPackage)
            putExtra(EXTRA_ACCOUNT_NUMBER, accountNumber)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }
}

@HiltViewModel
class DormantVerifyViewModel @Inject constructor(
    private val transferRepo: TransferAccountRepository,
    private val appLauncher: AppLauncher
) : ViewModel() {

    data class UiState(
        val checked: Boolean = false,
        val canProceed: Boolean = false
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var accountId: Long = -1L
    private var targetPackage: String? = null
    private var accountNumber: String = ""

    fun bootstrap(accountId: Long, targetPackage: String?, accountNumber: String) {
        this.accountId = accountId
        this.targetPackage = targetPackage
        this.accountNumber = accountNumber
    }

    fun toggleCheck() {
        val s = _state.value
        _state.value = s.copy(checked = !s.checked, canProceed = !s.checked)
    }

    fun confirmAndLaunch(activityContext: Context) {
        if (accountId <= 0) return
        if (accountNumber.isNotBlank()) {
            val cm = activityContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("transfer_account", accountNumber))
        }
        viewModelScope.launch {
            transferRepo.consumeDormantVerification(accountId)
            targetPackage?.let { appLauncher.launchAuthorized(it) }
        }
    }
}

@Composable
private fun Screen(
    state: DormantVerifyViewModel.UiState,
    onToggleCheck: () -> Unit,
    onProceed: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBlack)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Spacer(Modifier.height(32.dp))
                Text(
                    text = "💤 " + stringResource(R.string.dormant_title),
                    color = AlertYellow,
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = stringResource(R.string.dormant_body),
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyLarge
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceDim, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = state.checked,
                        onCheckedChange = { onToggleCheck() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = AlertYellow,
                            uncheckedColor = TextSecondary
                        )
                    )
                    Text(
                        text = stringResource(R.string.dormant_check),
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onProceed,
                    enabled = state.canProceed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AlertYellow,
                        contentColor = SurfaceBlack
                    )
                ) {
                    Text(
                        text = stringResource(R.string.dormant_cta),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.transfer_action_cancel),
                        color = TextSecondary
                    )
                }
            }
        }
    }
}
