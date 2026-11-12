package com.nextdrive.pleos.ai

import java.util.concurrent.atomic.AtomicBoolean

/** The same app-wide lease for saved-issue UI, voice, Gleo and follow-up dialogue. */
internal object IssueAiLease { val busy=AtomicBoolean(false) }
