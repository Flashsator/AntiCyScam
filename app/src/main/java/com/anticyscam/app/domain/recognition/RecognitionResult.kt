package com.anticyscam.app.domain.recognition

import com.anticyscam.app.domain.model.ScamTactic
import com.anticyscam.app.domain.model.SuspiciousName
import com.anticyscam.app.domain.model.WarnedAccount

enum class RiskLevel { HIGH, MEDIUM, LOW, SAFE }

enum class RecognitionMode { TEXT, SCREENSHOT, VOICE }

data class MatchedTactic(
    val tactic: ScamTactic,
    val score: Int,
    val matchedKeywords: List<String>
)

data class SuspiciousNameHit(
    val match: SuspiciousName,
    val matchedText: String
)

data class WarnedAccountHit(
    val match: WarnedAccount,
    val matchedText: String
)

data class RecognitionResult(
    val inputText: String,
    val mode: RecognitionMode,
    val riskLevel: RiskLevel,
    val maxScore: Int,
    val matches: List<MatchedTactic>,
    val hardRuleHits: List<HardRuleHit> = emptyList(),
    val nameHits: List<SuspiciousNameHit> = emptyList(),
    val accountHits: List<WarnedAccountHit> = emptyList()
)
