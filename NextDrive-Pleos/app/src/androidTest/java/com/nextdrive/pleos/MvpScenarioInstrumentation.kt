package com.nextdrive.pleos

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.*
import com.nextdrive.pleos.data.SqliteIssueRepository
import com.nextdrive.pleos.domain.*
import com.nextdrive.pleos.model.*
import com.nextdrive.pleos.vehicle.*
import java.util.UUID

/** Framework-only instrumentation: real SQLite, restart, local handover and real production UI. */
class MvpScenarioInstrumentation : Instrumentation() {
    private var argumentsBundle:Bundle?=null
    private var checks = 0
    private val output = StringBuilder()
    private var migrationOnly=false
    private var verifyProduction=false
    private var onboardingOnly=false
    private var reliabilityOnly=false
    private var coldOnly=false
    private var preservationOnly=false
    private var navigationOnly=false
    private var navigationColdOnly=false
    private var automaticOnly=false
    private var automaticColdOnly=false
    override fun onCreate(arguments: Bundle?) { argumentsBundle=arguments; navigationOnly=arguments?.getString("navigationOnly")=="true";navigationColdOnly=arguments?.getString("navigationColdOnly")=="true";coldOnly=arguments?.getString("coldOnly")=="true";preservationOnly=arguments?.getString("preservationOnly")=="true";reliabilityOnly=arguments?.getString("reliabilityOnly")=="true";onboardingOnly=arguments?.getString("onboardingOnly")=="true";migrationOnly=arguments?.getString("migrationOnly")=="true";verifyProduction=arguments?.getString("verifyProduction")=="true";super.onCreate(arguments); start() }
    override fun onStart() {
        val result = Bundle()
        try {
            ValidationEnvironment.requireIsolated(targetContext)
            if(argumentsBundle?.getString("voiceUsageReadOnly")=="true"){val lines=VoiceRelayChecks(this).usage();result.putString("stream","CARITDA_VOICE_USAGE_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("homeVoiceExtraOneOnly")=="true"){val lines=HomeVoiceRecoveryChecks(this).run(argumentsBundle?.getString("voiceExtraModelApproval").orEmpty(),true);result.putString("stream","CARITDA_HOME_EXTRA_ONE_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("homeVoiceResumeOriginalOnly")=="true"){val lines=HomeVoiceRecoveryChecks(this).run(argumentsBundle?.getString("voiceRealModelApproval").orEmpty());result.putString("stream","CARITDA_HOME_ORIGINAL_RECOVERY_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("homeVoiceResultReadOnly")=="true"){val lines=HomeVoiceModelResultChecks(this).run();result.putString("stream","CARITDA_HOME_MODEL_READBACK_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("homeVoiceMicrophoneOnly")=="true"||argumentsBundle?.getString("homeVoiceRealOnly")=="true"||argumentsBundle?.getString("homeVoiceRealInputOnly")=="true"){val lines=HomeVoiceMicrophoneChecks(this).run(argumentsBundle?.getString("homeVoiceRealOnly")=="true"||argumentsBundle?.getString("homeVoiceRealInputOnly")=="true",argumentsBundle?.getString("voiceRealModelApproval").orEmpty(),argumentsBundle?.getString("homeVoiceRealInputOnly")=="true");result.putString("stream","CARITDA_HOME_MICROPHONE_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("voiceRealPrepareOnly")=="true"){val lines=VoiceRealModelChecks(this).run(argumentsBundle?.getString("voiceRealModelApproval").orEmpty(),true);result.putString("stream","CARITDA_VOICE_REAL_PREPARE_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("voiceReauthOnly")=="true"){val lines=VoiceReauthChecks(this).run();result.putString("stream","CARITDA_VOICE_REAUTH_PASS checks=${lines.size}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("voiceRelayAuthOnly")=="true"){val lines=VoiceRelayChecks(this).authProbe();result.putString("stream","CARITDA_VOICE_AUTH_PROBE_PASS checks=0\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("homeWakeConfirmationOnly")=="true"){val lines=HomeWakeConfirmationChecks(this).run();result.putString("stream","CARITDA_WAKE_CONFIRMATION_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("homeVoiceOnly")=="true"){val lines=HomeVoiceChecks(this).run();result.putString("stream","CARITDA_HOME_VOICE_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("homeVoicePlatformOnly")=="true"){val lines=HomeVoicePlatformChecks(this).run();result.putString("stream","CARITDA_HOME_PLATFORM_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("voiceAutomationOnly")=="true"){val lines=VoiceAutomationChecks(this).run();result.putString("stream","CARITDA_VOICE_AUTOMATION_PASS checks=${lines.size}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("voiceAutomationUiOnly")=="true"){val lines=VoiceAutomationUiChecks(this).run();result.putString("stream","CARITDA_VOICE_AUTOMATION_UI_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("voiceAutomationLiveOnly")=="true"){val lines=VoiceAutomationLiveChecks(this).run();result.putString("stream","CARITDA_VOICE_AUTOMATION_LIVE_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("voiceRelayReadOnly")=="true"){val lines=VoiceRelayChecks(this).run();result.putString("stream","CARITDA_VOICE_RELAY_READ_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("voiceRealModelOnly")=="true"){val lines=VoiceRealModelChecks(this).run(argumentsBundle?.getString("voiceRealModelApproval").orEmpty());result.putString("stream","CARITDA_VOICE_REAL_MODEL_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("voiceAttentionSocLiveOnly")=="true"){val lines=VoiceAttentionSocChecks(this,argumentsBundle?.getString("driveCheckRun").orEmpty()).run();result.putString("stream","CARITDA_VOICE_ATTENTION_SOC_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("bLightReferenceOnly")=="true"){val lines=BLightReferenceChecks(this).run();result.putString("stream","CARITDA_B_LIGHT_REFERENCE_PASS checks=${lines.size}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("bLightLayoutOnly")=="true"||argumentsBundle?.getString("bLightLayoutSmokeOnly")=="true"){val lines=BLightLayoutChecks(this).run(argumentsBundle?.getString("bLightLayoutSmokeOnly")=="true");result.putString("stream","CARITDA_B_LIGHT_LAYOUT_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("bLightStageTwoOnly")=="true"){val lines=BLightStageTwoChecks(this).run();result.putString("stream","CARITDA_B_LIGHT_STAGE_TWO_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("bLightColdOnly")=="true"){val lines=BLightStageTwoChecks(this).cold();result.putString("stream","CARITDA_B_LIGHT_COLD_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("bLightStageOneOnly")=="true"){val lines=BLightStageOneChecks(this).run();result.putString("stream","CARITDA_B_LIGHT_STAGE_ONE_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("driveCheckNavigationUiOnly")=="true"){val lines=DriveCheckP0Checks(this).navigationUi();result.putString("stream","CARITDA_DRIVECHECK_NAV_UI_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("driveCheckMeasurementOnly")=="true"){val lines=DriveCheckMeasurementChecks(this).run();result.putString("stream","CARITDA_DRIVECHECK_MEASUREMENT_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("driveCheckNavigationCleanupOnly")=="true"){val lines=DriveCheckNavigationLiveChecks(this).cleanupOwned();result.putString("stream","CARITDA_DRIVECHECK_NAV_CLEANUP_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("driveCheckSocLiveOnly")=="true"){val lines=DriveCheckSocLiveChecks(this,argumentsBundle?.getString("driveCheckRun").orEmpty()).run();result.putString("stream","CARITDA_DRIVECHECK_SOC_LIVE_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("driveCheckSocLiveColdOnly")=="true"){val lines=DriveCheckSocLiveChecks(this).cold();result.putString("stream","CARITDA_DRIVECHECK_SOC_LIVE_COLD_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("driveCheckRestoredOnly")=="true"){val lines=DriveCheckSocLiveChecks(this).restored();result.putString("stream","CARITDA_DRIVECHECK_RESTORED_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("driveCheckP1Only")=="true"){val lines=DriveCheckP1Checks(this).core();result.putString("stream","CARITDA_DRIVECHECK_P1_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("driveCheckSocUiOnly")=="true"){val lines=DriveCheckP1Checks(this).ui();result.putString("stream","CARITDA_DRIVECHECK_SOC_UI_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("driveCheckSocColdOnly")=="true"){val lines=DriveCheckP1Checks(this).cold();result.putString("stream","CARITDA_DRIVECHECK_SOC_COLD_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("driveCheckNavigationLiveOnly")=="true"){val lines=DriveCheckNavigationLiveChecks(this).run();result.putString("stream","CARITDA_DRIVECHECK_NAV_LIVE_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("driveCheckP0Only")=="true"){val lines=DriveCheckP0Checks(this).core();result.putString("stream","CARITDA_DRIVECHECK_P0_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("driveCheckLocationUiOnly")=="true"){val lines=DriveCheckP0Checks(this).locationUi();result.putString("stream","CARITDA_DRIVECHECK_LOCATION_UI_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("driveCheckLocationColdOnly")=="true"){val lines=DriveCheckP0Checks(this).coldLocation();result.putString("stream","CARITDA_DRIVECHECK_LOCATION_COLD_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("integratedVehicleOnly")=="true"){val lines=IntegratedFlowChecks(this).vehicle();result.putString("stream","CARITDA_INTEGRATED_VEHICLE_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("integratedFlowColdOnly")=="true"){val lines=IntegratedFlowChecks(this).cold();result.putString("stream","CARITDA_INTEGRATED_COLD_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("integratedFlowOnly")=="true"){val lines=IntegratedFlowChecks(this).run();result.putString("stream","CARITDA_INTEGRATED_FLOW_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("integratedTtsOnly")=="true"){val lines=IntegratedFlowChecks(this).tts();result.putString("stream","CARITDA_INTEGRATED_TTS_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("locationLinkOnly")=="true"){val lines=LocationLinkChecks(this).run();result.putString("stream","CARITDA_LOCATION_LINK_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("gleoOnly")=="true"){val lines=GleoIntegrationChecks(this).run();result.putString("stream","CARITDA_GLEO_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("speechDraftUiOnly")=="true"){val lines=SpeechDraftUiChecks(this).run();result.putString("stream","CARITDA_SPEECH_DRAFT_UI_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("sttAutomaticUiOnly")=="true"){val lines=SpeechAutomaticUiChecks(this).run();result.putString("stream","CARITDA_STT_AUTOMATIC_UI_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("sttStopOnly")=="true"){val lines=SpeechFixtureChecks(this).run(true);result.putString("stream","CARITDA_STT_STOP_DIAGNOSTIC checks=0\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("sttRequestOnly")=="true"){val lines=SpeechRequestChecks(this).run();result.putString("stream","CARITDA_STT_REQUEST_DIAGNOSTIC checks=0\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("sttFixtureOnly")=="true"){val lines=SpeechFixtureChecks(this).run();result.putString("stream","CARITDA_STT_FIXTURE_DIAGNOSTIC checks=0\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("sttInputPathOnly")=="true"){val lines=SpeechInputPathChecks(this).run();result.putString("stream","CARITDA_STT_INPUT_PATH_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("sttSessionOnly")=="true"){val lines=SpeechSessionChecks(this).run();result.putString("stream","CARITDA_STT_SESSION_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("coreDatabaseOnly")=="true"){databaseScenario();observationScenario(false);result.putString("stream","CARITDA_CORE_DATABASE_PASS checks=$checks\n$output");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("driveExperimentOnly")=="true"){val lines=DriveExperimentChecks(this).run();result.putString("stream","CARITDA_DRIVE_EXPERIMENT_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("integrationOnly")=="true"){val lines=IntegrationChecks(this).run();result.putString("stream","CARITDA_INTEGRATION_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("integrationLiveOnly")=="true"){val lines=IntegrationChecks(this).live();result.putString("stream","CARITDA_INTEGRATION_LIVE_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("issueAiColdOnly")=="true"){val lines=IssueAiChecks(this).cold();result.putString("stream","CARITDA_ISSUE_AI_COLD_PASS checks=${lines.size}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("issueAiOnly")=="true"){val lines=IssueAiChecks(this).run();result.putString("stream","CARITDA_ISSUE_AI_LOCAL_PASS checks=${lines.size}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("portableAiOnly")=="true"){val lines=PortableAiChecks(this).run();result.putString("stream","CARITDA_PORTABLE_AI_LOCAL_PASS checks=${lines.size}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("portableAiUiOnly")=="true"){val lines=PortableAiChecks(this).ui();result.putString("stream","CARITDA_PORTABLE_AI_UI_PASS checks=${lines.size}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("portableAuthInteractiveOnly")=="true"){val lines=PortableAuthChecks(this).interactive();result.putString("stream","CARITDA_PORTABLE_AUTH_INTERACTIVE_PASS checks=${lines.size}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("portableAuthSignedInOnly")=="true"){val lines=PortableAuthChecks(this).signedIn();result.putString("stream","CARITDA_PORTABLE_AUTH_SIGNED_IN_PASS checks=${lines.size}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("portableAuthOnly")=="true"){val lines=PortableAuthChecks(this).run();result.putString("stream","CARITDA_PORTABLE_AUTH_LOCAL_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("portableAuthColdOnly")=="true"){val lines=PortableAuthChecks(this).cold();result.putString("stream","CARITDA_PORTABLE_AUTH_COLD_PASS checks=${lines.size}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("geminiPocOnly")=="true"){val lines=GeminiPocChecks(this).run();result.putString("stream","CARITDA_GEMINI_LOCAL_PASS checks=${lines.size}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("analyticsContractOnly")=="true"){val lines=AnalyticsChecks(this).run(true);result.putString("stream","CARITDA_ANALYTICS_CONTRACT_PASS checks=${lines.size}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("analyticsInventory")=="true"){val lines=AnalyticsChecks(this).inventory();result.putString("stream",lines.joinToString("\n"));finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("analyticsPreservation")=="true"){val lines=AnalyticsChecks(this).preservation();result.putString("stream","CARITDA_ANALYTICS_PRESERVATION_PASS checks=${lines.size}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("analyticsCold")=="true"){val lines=AnalyticsChecks(this).cold();result.putString("stream","CARITDA_ANALYTICS_COLD_PASS checks=${lines.size}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("socColdOnly")=="true"){val lines=SocPocChecks(this).cold();result.putString("stream","CARITDA_SOC_COLD_PASS checks=${lines.size}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("compatColdOnly")=="true"){val lines=CompatibilityChecks(this).cold();result.putString("stream","CARITDA_COMPAT_COLD_PASS checks=${lines.size}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("automaticPreservationOnly")=="true"){val lines=AutomaticChecks(this).preservation();result.putString("stream","CARITDA_AM_PRESERVATION_PASS checks=${lines.size}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("designPreservationOnly")=="true"){val lines=DesignChecks(this).preservation();result.putString("stream","CARITDA_DESIGN_PRESERVATION_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            automaticOnly=argumentsBundle?.getString("automaticOnly")=="true"
            automaticColdOnly=argumentsBundle?.getString("automaticColdOnly")=="true"
            if(automaticColdOnly){val lines=AutomaticChecks(this).cold();result.putString("stream","CARITDA_AM_COLD_PASS checks=${lines.size}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(navigationColdOnly){val lines=NavigationChecks(this).coldRun();result.putString("stream","CARITDA_NAV_COLD_PASS checks=${lines.size}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(preservationOnly){result.putString("stream",RecordMigrationChecks.verifyAfter(targetContext));finish(Activity.RESULT_OK,result);return}
            if(coldOnly){val lines=ReliabilityChecks(this).coldCheck();result.putString("stream","CARITDA_COLD_PASS checks=${lines.size}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            ValidationEnvironment.prepare(targetContext)
            if(argumentsBundle?.getString("analyticsOnly")=="true"){val lines=AnalyticsChecks(this).run();result.putString("stream","CARITDA_ANALYTICS_PASS checks=${lines.size}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("analyticsLive")=="true"){val lines=AnalyticsChecks(this).live();result.putString("stream","CARITDA_ANALYTICS_LIVE_PASS checks=${lines.size}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("socPocOnly")=="true"){val lines=SocPocChecks(this).rules();result.putString("stream","CARITDA_SOC_RULE_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("socLiveOnly")=="true"){val lines=SocPocChecks(this).live();result.putString("stream","CARITDA_SOC_LIVE_PASS checks=${lines.size}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("compatOnly")=="true"){val lines=CompatibilityChecks(this).run();result.putString("stream","CARITDA_COMPAT_PASS checks=${lines.size}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(argumentsBundle?.getString("designOnly")=="true"){val lines=DesignChecks(this).run();result.putString("stream","CARITDA_DESIGN_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(automaticOnly){val lines=AutomaticChecks(this).run();result.putString("stream","CARITDA_AM_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(navigationOnly){val lines=NavigationChecks(this).run();result.putString("stream","CARITDA_NAV_PASS checks=${lines.size}\n${lines.joinToString("\n")}");finish(Activity.RESULT_OK,result);return}
            if(reliabilityOnly){val lines=RecordMigrationChecks.run(targetContext)+ReliabilityChecks(this).run();result.putString("stream","CARITDA_RECORD_PASS checks=${lines.size}\n${lines.joinToString("\n")}\n");finish(Activity.RESULT_OK,result);return}
            if(onboardingOnly){val lines=OnboardingChecks(this).run();result.putString("stream","CARITDA_ON_PASS checks=${lines.count{it.startsWith("PASS ")}}\n${lines.joinToString("\n")}\n");finish(Activity.RESULT_OK,result);return}
            if(migrationOnly){val lines=MigrationChecks.run(targetContext);result.putString("stream","MIGRATION_PASS checks=${lines.size}\n${lines.joinToString("\n")}\n");finish(Activity.RESULT_OK,result);return}
            check(!verifyProduction) { "Production testing has been retired; use the isolated validation application" }
            databaseScenario()
            uiScenario(result)
            result.putString("stream", "CARITDA_MVP_PASS checks=$checks\n$output")
            val baseline = checks
            observationScenario()
            val observed=checks-baseline
            managementScenario()
            result.putString("stream", "CARITDA_MVP_PASS checks=$baseline\n$output\nCARITDA_OBSERVATION_PASS checks=$observed\nCARITDA_UI_PASS checks=${checks-baseline-observed}\n")
            finish(Activity.RESULT_OK, result)
        } catch (failure: Throwable) {
            result.putString("stream", "CARITDA_MVP_FAIL checks=$checks\n$output\n${failure.stackTraceToString()}")
            result.putString("shortMsg", (failure as? com.nextdrive.pleos.ai.PortableFailure)?.code?:failure.message?:failure.javaClass.name)
            finish(Activity.RESULT_CANCELED, result)
        }
    }
    private fun verify(condition: Boolean, label: String) {
        check(condition) { label }; checks++; output.appendLine("PASS $label")
    }
    private fun databaseScenario() {
        val name = "caritda-test-${UUID.randomUUID()}.db"
        targetContext.openOrCreateDatabase(name,0,null).also { com.nextdrive.pleos.data.DriverMigration.createV1(it);it.version=1;it.close() }
        var repo = SqliteIssueRepository(targetContext, name)
        var allowed = true
        var now = System.currentTimeMillis()
        var service = CaritdaService(repo, { allowed }, { now++ })
        service.selectDriver(Driver.A)
        val issue = service.record(SymptomType.VIBRATION, "가속할 때 핸들이 진동함", now - 60_000, "기술 검증용 예시입니다.")
        verify(issue.reporter == Driver.A && issue.status == IssueStatus.UNRESOLVED, "A records unresolved vibration")
        repo.close()
        repo = SqliteIssueRepository(targetContext, name)
        service = CaritdaService(repo, { allowed }, { now++ })
        service.selectDriver(service.driver())
        verify(service.issue(issue.id).situation == "가속할 때 핸들이 진동함", "SQLite persists after close/reopen")
        verify(service.events(issue.id).single().actor == Driver.A, "reporter and registration history persist")
        service.selectDriver(Driver.B)
        verify(service.needsAcknowledgement(service.issue(issue.id)), "B sees unacknowledged A issue")
        verify(service.acknowledge(issue.id), "B acknowledgement saved")
        verify(!service.acknowledge(issue.id), "repeated acknowledgement is idempotent")
        verify(service.events(issue.id).count { it.kind == EventKind.ACKNOWLEDGED } == 1, "one B confirmation for current revision")
        service.addNote(issue.id, "점검 결과 메모를 남겼습니다.")
        IssueStatus.entries.forEach { service.changeStatus(issue.id, it) }
        verify(service.issue(issue.id).status == IssueStatus.RESOLVED, "all four statuses supported")
        var summary = ConsultationSummary.build(service.issue(issue.id), service.events(issue.id))
        verify(summary.contains("운전자 B") && summary.contains("점검 결과 메모") && summary.contains("진동"), "consultation summary includes handover and action")
        service.recur(issue.id, "같은 진동이 다시 발생했습니다.")
        verify(service.issue(issue.id).revision == 2 && service.issue(issue.id).status == IssueStatus.UNRESOLVED, "recurrence reopens the same card")
        verify(service.needsAcknowledgement(service.issue(issue.id)), "new recurrence needs a new B confirmation")
        service.acknowledge(issue.id)
        verify(service.events(issue.id).count { it.kind == EventKind.ACKNOWLEDGED } == 2, "old acknowledgement history preserved")
        repo.close()
        repo = SqliteIssueRepository(targetContext, name)
        service = CaritdaService(repo, { allowed }, { now++ })
        service.selectDriver(service.driver())
        verify(service.driver() == Driver.B, "selected local profile persists")
        summary = ConsultationSummary.build(service.issue(issue.id), service.events(issue.id))
        verify(summary.contains("다시 발생") && summary.contains("점검 결과 메모"), "recurrence and prior note survive restart")
        val before = service.events(issue.id).size
        allowed = false
        val blocked = listOf<() -> Unit>(
            { service.selectDriver(Driver.A) },
            { service.record(SymptomType.NOISE, "test", now, "") },
            { service.addNote(issue.id, "must not save") },
            { service.changeStatus(issue.id, IssueStatus.RESOLVED) },
            { service.acknowledge(issue.id) },
            { service.recur(issue.id, "must not save") },
        ).count { operation -> try { operation(); false } catch (_: EditingBlockedException) { true } }
        verify(blocked == 6 && service.events(issue.id).size == before && service.issues().size == 1,
            "unknown/restricted editing blocks all six mutations without changing SQLite")
        allowed = true
        val invalid = listOf<() -> Unit>(
            { service.record(SymptomType.OTHER, "  ", now, "") },
            { service.record(SymptomType.OTHER, "test", now + 120_000, "") },
            { service.addNote(issue.id, "  ") },
            { service.recur(issue.id, " ") },
        ).count { operation -> try { operation(); false } catch (_: IllegalArgumentException) { true } }
        verify(invalid == 4 && service.issues().size == 1, "invalid input is rejected without partial records")
        repo.close()
    }

    private fun uiScenario(result: Bundle) {
        val probe = SqliteIssueRepository(targetContext)
        val originalIds = probe.issues().map { it.id }.toSet()
        var activity = launch()
        await("platform UX restriction permits parked editing") { main { tagged(activity, "driver_A")?.isEnabled == true } }
        click(activity, "driver_A")
        click(activity, "start_driving")
        click(activity, "new_issue")
        click(activity,"type_VIBRATION")
        ValidationUiActions.reveal(this,activity,"memo")
        main {
            (tagged(activity, "situation") as EditText).setText("가속할 때 핸들이 진동함")
            (tagged(activity, "memo") as EditText).setText("에뮬레이터 시나리오 검증용 수기 예시입니다.")
        }
        waitForIdleSync()
        click(activity, "save_issue")
        val issue = probe.issues().single { it.id !in originalIds }
        verify(issue.type == SymptomType.VIBRATION && issue.reporter == Driver.A, "UI saves A vibration record")
        main { activity.finish() }; waitForIdleSync()
        activity = launch()
        await("parked UX reconnects after activity restart") { main { tagged(activity, "driver_B")?.isEnabled == true } }
        verify(probe.issue(issue.id)?.situation == "가속할 때 핸들이 진동함", "UI-created record survives app activity restart")
        click(activity, "driver_B")
        click(activity, "start_driving")
        navigate(activity,"ISSUES")
        click(activity, "issue_${issue.id}")
        click(activity, "acknowledge")
        verify(probe.events(issue.id).any { it.kind == EventKind.ACKNOWLEDGED && it.actor == Driver.B }, "UI stores B handover confirmation")
        click(activity, "add_note")
        await("action dialog becomes accessible") { findNode(uiAutomation.rootInActiveWindow) { it.className == "android.widget.EditText" } != null }
        val field = findNode(uiAutomation.rootInActiveWindow) { it.className == "android.widget.EditText" }!!
        verify(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "진동 관련 점검 결과를 확인했습니다.")
        }), "UI action memo entered")
        click(activity,"save_action")
        await("UI memo saved") { probe.events(issue.id).any { it.kind == EventKind.NOTE } }
        click(activity, "issue_summary")
        val text = main { (tagged(activity, "consultation_summary") as TextView).text.toString() }
        verify(text.contains("가속할 때 핸들이 진동함") && text.contains("운전자 B") && text.contains("진동 관련 점검 결과"),
            "all nine requested steps reach a consultation summary in the real UI")
        verify(probe.issues().map { it.id }.containsAll(originalIds), "existing production records are preserved")
        navigate(activity,"HOME")
        val content = main { (activity.findViewById<ViewGroup>(android.R.id.content)).getChildAt(0) }
        val originalWidth = main { content.layoutParams.width }
        val narrowWidth = (600 * activity.resources.displayMetrics.density).toInt()
        try {
            main { content.layoutParams = content.layoutParams.apply { width = narrowWidth }; content.requestLayout() }
            await("600dp viewport lays out") { main { content.width == narrowWidth } }
            verify(main { tagged(activity, "new_issue")!!.width <= narrowWidth }, "home fits a 600dp viewport")
            click(activity, "new_issue")
            verify(main { tagged(activity, "situation")!!.width <= narrowWidth }, "record form fits a 600dp viewport")
            click(activity, "back")
        } finally {
            main { content.layoutParams = content.layoutParams.apply { width = originalWidth }; content.requestLayout() }
        }
        result.putString("createdIssueId", issue.id)
        output.appendLine("UI_ISSUE_ID=${issue.id}")
        probe.close()
        main { activity.finish() }; waitForIdleSync()
    }
    private fun observationScenario(includeRuntime:Boolean=true) {
        val name = "observation-test-${UUID.randomUUID()}.db"
        var store = ObservationStore(targetContext, name)
        val session = ObservationStore.sessionId()
        ObservationStatus.entries.filter { it != ObservationStatus.RECEIVED }.forEach { status ->
            store.append(VehicleObservation("TEST_CONNECTION", null, null, null, ObservationSource.NONE,
                null, false, session, status, "계측 시험 전용 실패 상태"))
        }
        verify(store.latest().all { it.raw == null && it.receivedAt == null && !it.valid }, "failure states never invent a sensor value")
        // Test-only data in isolated DB, never presented as an actual Vehicle SDK response.
        store.append(VehicleObservation("TEST_SOC", "42", "%", 12345L, ObservationSource.TEST_ONLY,
            true, true, session, ObservationStatus.RECEIVED, "저장 형식 시험용이며 SDK 수신이 아닙니다.", driverId = "future-test-driver"))
        store.close(); store = ObservationStore(targetContext, name)
        val value = store.latest().first()
        verify(value.raw == "42" && value.unit == "%" && value.receivedAt == 12345L &&
            value.source == ObservationSource.TEST_ONLY && value.supported == true && value.valid &&
            value.session == session && value.driverId == "future-test-driver", "isolated observation metadata survives SQLite reopen")
        val count = store.latest().size
        val rejected = runCatching { store.append(value.copy(source = ObservationSource.NONE)) }.isFailure
        verify(rejected && store.latest().size == count, "invalid received observation rejected without insert")
        store.close()
        if(!includeRuntime)return
        val activity = launch()
        await("parked UX before connection screen") { main { tagged(activity, "driver_B")?.isEnabled == true } }
        click(activity,"driver_B");click(activity,"start_driving")
        click(activity, "nav_VEHICLE")
        val production = ObservationStore(targetContext)
        val original = production.latest(100)
        val before = System.currentTimeMillis()
        await("entry automatic read finishes before explicit refresh") { main { tagged(activity,"read_vehicle")?.isEnabled==true } }
        click(activity, "read_vehicle")
        val coreKinds=setOf("EV_SOC","ODOMETER","FUEL_PERCENT")
        await("three actual SDK outcomes") { production.latest(100).filter{it.kind in coreKinds}.count { it.recordedAt >= before } >= 3 }
        val responses = production.latest(100).filter { it.recordedAt >= before&&it.kind in coreKinds }.distinctBy{it.kind}.take(3)
        responses.forEach { output.appendLine("SDK_RESULT=$it") }
        verify(responses.map { it.kind }.toSet() == setOf("EV_SOC", "ODOMETER", "FUEL_PERCENT") &&
            responses.all { it.source == ObservationSource.PLEOS_EMULATOR_SDK && it.status != ObservationStatus.SDK_UNAVAILABLE },
            "actual emulator SDK returns three distinct outcomes")
        verify(responses.any { it.status == ObservationStatus.RECEIVED && it.raw != null && it.receivedAt != null &&
            it.unit in listOf("%", "km") && it.supported == true && it.valid }, "at least one capability-approved SDK value is received and saved")
        main { activity.finish() }; waitForIdleSync()
        val restarted = launch()
        await("parked UX after restart") { main { tagged(restarted, "driver_B")?.isEnabled == true } }
        click(restarted,"driver_B");click(restarted,"start_driving")
        click(restarted, "nav_VEHICLE")
        verify(production.latest(100).size >= original.size && responses.all { it in production.latest(100) },
            "actual SDK observations persist after app activity restart")
        production.close()
        main{restarted.finish()};waitForIdleSync()
    }

    private fun navigate(activity:Activity,target:String){
        if(main{tagged(activity,"nav_$target")==null})click(activity,"menu")
        click(activity,"nav_$target")
    }
    private fun managementScenario(){
        val repo=SqliteIssueRepository(targetContext)
        val oldDriver=repo.currentDriver()!!.id
        val oldIds=repo.issues().map{it.id}.toSet()
        var activity=launch()
        await("driver selection ready"){main{tagged(activity,"start_driving")?.isEnabled==true}}
        verify(main{tagged(activity,"new_issue")==null},"cold start requires explicit driver confirmation")
        click(activity,"driver_A");click(activity,"cancel_driver")
        verify(repo.currentDriver()!!.id==oldDriver,"cancelled candidate leaves current driver unchanged")
        click(activity,"change_driver");click(activity,"add_driver")
        val name="검증${UUID.randomUUID().toString().take(5)}"
        val beforeProfiles=repo.drivers().size
        click(activity,"save_driver")
        verify(repo.drivers().size==beforeProfiles && main{(tagged(activity,"feedback") as TextView).text.isNotBlank()},"invalid name shows persistent error without creating profile")
        main{(tagged(activity,"driver_name") as EditText).setText(name)};click(activity,"save_driver")
        val profile=repo.drivers().single{it.label==name}
        verify(repo.currentDriver()!!.id==oldDriver,"UI registration creates a profile without switching")
        click(activity,"start_driving")
        verify(repo.currentDriver()!!.id==profile.id,"explicit start confirms selected permanent driver ID")
        click(activity,"new_issue")
        main{(tagged(activity,"situation") as EditText).setText("검증용 초안을 보존합니다.")}
        val draftId=repo.draft()!!.id
        click(activity,"change_driver")
        verify(repo.draft()?.id==draftId && main{tagged(activity,"save_issue")!=null},"dirty record blocks driver UI transition and preserves text")
        val monitor=addMonitor(MainActivity::class.java.name,null,false)
        main{activity.recreate()}
        activity=monitor.waitForActivityWithTimeout(10_000) as? MainActivity ?: error("configuration recreation not observed")
        removeMonitor(monitor)
        await("confirmed draft after recreation"){main{tagged(activity,"save_issue")?.isEnabled==true}}
        verify(repo.draft()?.id==draftId && main{(tagged(activity,"situation") as EditText).text.toString()}=="검증용 초안을 보존합니다.","configuration recreation retains confirmation and authored draft")
        main{activity.finish()};waitForIdleSync();activity=launch()
        await("driver selection after draft restart"){main{tagged(activity,"driver_B")?.isEnabled==true}}
        click(activity,"driver_B");click(activity,"start_driving")
        verify(repo.currentDriver()!!.id==profile.id && repo.draft()?.ownerId==profile.id,"draft prevents another driver from claiming author on restart")
        click(activity,"driver_${profile.id}");click(activity,"start_driving");click(activity,"resume_draft")
        verify(main{(tagged(activity,"situation") as EditText).text.toString()}=="검증용 초안을 보존합니다.","UI draft restored with original text")
        val save=main{tagged(activity,"save_issue")!!}
        main{check(save.performClick());save.performClick()};waitForIdleSync()
        verify(repo.issue(draftId)?.reporter?.id==profile.id && repo.events(draftId).size==1 && repo.draft()==null,"double save creates one authored issue and clears only its draft")
        click(activity,"add_note")
        main{(tagged(activity,"action_note") as EditText).setText("검증용 조치 초안입니다.")}
        main{activity.finish()};waitForIdleSync();activity=launch()
        await("driver after action draft restart"){main{tagged(activity,"start_driving")?.isEnabled==true}}
        click(activity,"start_driving");click(activity,"resume_draft")
        verify(main{(tagged(activity,"action_note") as EditText).text.toString()}=="검증용 조치 초안입니다.","action draft and author survive UI restart")
        click(activity,"save_action")
        click(activity,"recurrence")
        main{(tagged(activity,"action_note") as EditText).setText("검증용 재발 기록입니다.")};click(activity,"save_action")
        verify(repo.issue(draftId)!!.revision==2 && repo.events(draftId).count{it.kind==EventKind.NOTE}==1,"UI action and recurrence preserve previous events")
        click(activity,"add_note")
        main{(tagged(activity,"action_note") as EditText).setText("취소할 검증 초안")}
        click(activity,"discard_draft");clickAccessible("확인")
        await("draft discarded"){repo.draft()==null}
        verify(repo.events(draftId).size==3,"explicit draft cancellation preserves committed issue events")
        navigate(activity,"ISSUES");click(activity,"issue_$draftId")
        click(activity,"change_status");clickAccessible("점검 예정")
        verify(repo.issue(draftId)!!.status==IssueStatus.PLANNED,"large status selector saves planned state")
        click(activity,"change_driver");click(activity,"manage_drivers");click(activity,"rename_${profile.id}")
        main{(tagged(activity,"driver_name") as EditText).setText("변경${name}")};click(activity,"save_driver")
        verify(repo.issue(draftId)!!.reporter.label==name && repo.events(draftId).all{it.actor.label==name},"UI rename leaves historical problem and event author snapshots intact")
        click(activity,"driver_B");click(activity,"start_driving")
        click(activity,"change_driver");click(activity,"manage_drivers");click(activity,"archive_${profile.id}")
        clickAccessible("확인")
        await("archive committed"){repo.drivers().none{it.id==profile.id}}
        verify(repo.drivers(true).single{it.id==profile.id}.archived && repo.issue(draftId)!!.reporter.label==name && repo.issues().map{it.id}.containsAll(oldIds),"UI archive preserves all previous user records and new authored history")
        click(activity,"back");click(activity,"cancel_driver")
        val issueCount=repo.issues().size
        click(activity,"new_issue");main{(tagged(activity,"situation") as EditText).setText("취소할 새 문제 초안")}
        click(activity,"discard_draft");clickAccessible("확인")
        await("new issue draft cancelled"){repo.draft()==null}
        verify(repo.issues().size==issueCount && main{tagged(activity,"new_issue")!=null},"cancelling within the form returns home without an inert save screen or new issue")
        val content=main{(activity.findViewById<ViewGroup>(android.R.id.content)).getChildAt(0)}
        val width=main{content.layoutParams.width}
        val config=android.content.res.Configuration(activity.resources.configuration)
        val priorTheme=activity.getPreferences(android.content.Context.MODE_PRIVATE).getString("theme","system")
        try{
            for(dpWidth in listOf(320,480,600,840,1024)){
                val px=(dpWidth*activity.resources.displayMetrics.density).toInt()
                main{content.layoutParams=content.layoutParams.apply{this.width=px};content.requestLayout()}
                await("width $dpWidth"){main{content.width==px}}
                waitForIdleSync()
                verify(main{tagged(activity,"new_issue")!!.width<=px && tagged(activity,"new_issue")!!.height>=76*activity.resources.displayMetrics.density},"home CTA fits ${dpWidth}dp and retains large touch target")
            }
            for(scale in listOf(1.0f,1.3f,1.5f,2.0f)){
                main{
                    val changed=android.content.res.Configuration(config).apply{fontScale=scale}
                    @Suppress("DEPRECATION")
                    activity.resources.updateConfiguration(changed,activity.resources.displayMetrics)
                }
                cycleTheme(activity);waitForIdleSync()
                verify(main{val b=tagged(activity,"new_issue") as TextView;b.layout.getEllipsisCount(0)==0 && b.layoutParams.height==ViewGroup.LayoutParams.WRAP_CONTENT},"${(scale*100).toInt()} percent app font scale preserves expandable CTA text")
            }
            val colors=mutableSetOf<Int>()
            var contrastPass=true
            repeat(3){
                cycleTheme(activity)
                main{
                    val button=tagged(activity,"new_issue") as TextView;colors.add(button.currentTextColor)
                    val background=(button.background.current as android.graphics.drawable.GradientDrawable).color!!.defaultColor
                    contrastPass=contrastPass && contrast(button.currentTextColor,background)>=4.5
                }
            }
            verify(colors.size>=2 && contrastPass,"light and dark themes keep measured CTA text contrast at least 4.5 to 1")
        }finally{
            main{
                @Suppress("DEPRECATION")
                activity.resources.updateConfiguration(config,activity.resources.displayMetrics)
                check(activity.getPreferences(android.content.Context.MODE_PRIVATE).edit().putString("theme",priorTheme).commit())
                content.layoutParams=content.layoutParams.apply{this.width=width};content.requestLayout()
            }
        }
        repo.close();main{activity.finish()};waitForIdleSync()
    }
    private fun clickAccessible(text:String){
        await("accessible action $text"){findNode(uiAutomation.rootInActiveWindow){it.text?.toString()==text && it.isClickable}!=null}
        val node=findNode(uiAutomation.rootInActiveWindow){it.text?.toString()==text && it.isClickable}?:error(text)
        check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK));waitForIdleSync()
    }
    private fun cycleTheme(activity:Activity){click(activity,"help");click(activity,"theme");click(activity,"close_help")}
    private fun contrast(first:Int,second:Int):Double{
        fun luminance(color:Int):Double{
            fun channel(value:Int):Double{val normalized=value/255.0;return if(normalized<=0.04045)normalized/12.92 else Math.pow((normalized+0.055)/1.055,2.4)}
            return .2126*channel(android.graphics.Color.red(color))+.7152*channel(android.graphics.Color.green(color))+.0722*channel(android.graphics.Color.blue(color))
        }
        val a=luminance(first);val b=luminance(second);return (maxOf(a,b)+.05)/(minOf(a,b)+.05)
    }

    private fun launch(): MainActivity = startActivitySync(Intent(Intent.ACTION_MAIN).apply {
        setClassName(targetContext, MainActivity::class.java.name)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }) as MainActivity
    private fun click(activity: Activity, tag: String) {
        ValidationUiActions.reveal(this,activity,tag)
        main { val view = tagged(activity, tag) ?: error("Missing UI: $tag"); check(view.isEnabled) { "Disabled UI: $tag" }; check(view.performClick()) }
        waitForIdleSync()
    }
    private fun tagged(activity: Activity, tag: String): View? = activity.window.decorView.findViewWithTag(tag)
    private fun findNode(node: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (node == null) return null
        if (predicate(node)) return node
        for (i in 0 until node.childCount) findNode(node.getChild(i), predicate)?.let { return it }
        return null
    }
    private fun await(label: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 25_000
        while (System.currentTimeMillis() < deadline) { if (condition()) return; Thread.sleep(100) }
        error("Timed out: $label")
    }
    private fun <T> main(action: () -> T): T {
        var result: T? = null; var failure: Throwable? = null
        runOnMainSync { try { result = action() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
}
