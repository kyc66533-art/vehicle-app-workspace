import java.io.File
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Public Firebase Android configuration only. No Google Services plugin or invented defaults.
// Each variant must match its own registered Android package in the console download.
fun firebasePublicConfig(path:java.io.File, packageId:String):Map<String,String> {
    val empty=mapOf("state" to "MISSING","project" to "","app" to "","key" to "","package" to "")
    if(!path.isFile)return empty
    try {
        val json=groovy.json.JsonSlurper().parse(path) as Map<*,*>
        val project=(json["project_info"] as? Map<*,*>)?.get("project_id") as? String?:""
        val client=(json["client"] as? List<*>)?.filterIsInstance<Map<*,*>>()?.firstOrNull {
            (((it["client_info"] as? Map<*,*>)?.get("android_client_info") as? Map<*,*>)?.get("package_name"))==packageId
        }?:return empty+mapOf("state" to "PACKAGE_MISMATCH")
        val app=(client["client_info"] as? Map<*,*>)?.get("mobilesdk_app_id") as? String?:""
        val key=(client["api_key"] as? List<*>)?.filterIsInstance<Map<*,*>>()?.firstOrNull()?.get("current_key") as? String?:""
        require(Regex("[a-z][a-z0-9-]{4,62}").matches(project)&&Regex("1:[0-9]+:android:[a-fA-F0-9]+").matches(app)&&Regex("[A-Za-z0-9_-]{30,100}").matches(key))
        return mapOf("state" to "READY","project" to project,"app" to app,"key" to key,"package" to packageId)
    } catch(_:Exception){throw GradleException("Firebase 공개 Android 설정을 읽지 못했습니다. 파일 형식과 앱 등록을 확인해 주세요. 값은 출력하지 않습니다.")}
}
val generalFirebase=firebasePublicConfig(file("google-services.json"),"com.nextdrive.pleos")
val validationConfigFile=file("src/validation/google-services.json").takeIf{it.isFile}?:file("google-services.json")
val validationFirebase=firebasePublicConfig(validationConfigFile,"com.nextdrive.pleos.validation")

// Independent Korean wake recognition remains experimental: actual user wake tests failed.
// The shipping/Studio default uses bounded Pleos standby. Opt-in only for engine validation.
// Neither path downloads during a build.
val homeWakeFolder=rootProject.file("../.tools/home-wake-engine")
val homeWakeManifest=File(homeWakeFolder,"manifest.json")
val homeWakeEngine=providers.gradleProperty("caritdaHomeWakeEngine").getOrElse("false").toBooleanStrict()
if(homeWakeEngine){
    val manifestFile=homeWakeManifest
    require(manifestFile.isFile){"Run scripts/Prepare-HomeWakeEngine.mjs --download first."}
    val manifest=groovy.json.JsonSlurper().parse(manifestFile) as Map<*,*>
    require(manifest["complete"]==true){"Home wake assets are incomplete."}
    for(entry in manifest["files"] as List<*>){
        val item=entry as Map<*,*>;val name=item["name"] as String
        require(name in setOf("vosk-android-0.3.47.aar","jna-5.13.0.aar","vosk-model-small-ko-0.22.zip"))
        val asset=File(homeWakeFolder,(if(item["model"]==true) "assets/" else "")+name)
        val digest=MessageDigest.getInstance("SHA-256");asset.inputStream().use{stream->val b=ByteArray(65536);while(true){val n=stream.read(b);if(n<0)break;digest.update(b,0,n)}}
        require(digest.digest().joinToString(""){"%02x".format(it)}==item["sha256"]){"Home wake asset digest mismatch."}
    }
}

android {
    useLibrary("android.car")
    namespace = "com.nextdrive.pleos"
    compileSdk = 34
    buildToolsVersion = "35.0.0"
    sourceSets.getByName("main").java.srcDir(if(homeWakeEngine) "src/homeWake/java" else "src/noHomeWake/java")
    if(homeWakeEngine){
        sourceSets.getByName("main").assets.srcDir(rootProject.file("../.tools/home-wake-engine/assets"))
        sourceSets.getByName("main").assets.srcDir("src/homeWake/assets")
    }

    defaultConfig {
        applicationId = "com.nextdrive.pleos"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "com.nextdrive.pleos.MvpScenarioInstrumentation"
        generalFirebase.forEach{(key,value)->resValue("string","caritda_firebase_$key",value)}
    }

    // Instrumentation must never target the app holding a driver's real journal.
    signingConfigs.getByName("debug") {
        providers.gradleProperty("caritdaDebugKeystore").orNull?.let { storeFile = file(it) }
    }
    buildTypes {
        // Same release code/settings, separate install identity: never replace the user's Debug app.
        create("submissionCheck") {
            initWith(getByName("release"))
            applicationIdSuffix = ".submissioncheck"
            matchingFallbacks += listOf("release")
            resValue("string", "app_name", "[TEST] CARITDA Release 확인")
        }
        create("validation") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".validation"
            matchingFallbacks += listOf("debug")
            resValue("string", "app_name", "[TEST] CARITDA 검증 전용")
            validationFirebase.forEach{(key,value)->resValue("string","caritda_firebase_$key",value)}
        }
    }
    // The debug override is only for replacing a stale production-target runner with the
    // package guard. Its entry point rejects production before any DB preparation or UI.
    testBuildType = providers.gradleProperty("caritdaInstrumentationTarget").getOrElse("validation")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // The preserved Pleos dependency graph already supplies JNA 5.13.0.
    // Adding its downloaded AAR a second time creates duplicate native/classes.
    if(homeWakeEngine)implementation(files(File(homeWakeFolder,"vosk-android-0.3.47.aar")))
    // Email/password Auth only. Public FirebaseOptions are supplied separately; no AI key.
    // Auth 25.0.0 is Kotlin metadata 2.4; the preserved Kotlin 2.2.21 compiler rejects it.
    // Official 24.2.0 (also mapped by BoM 34.19.0) is the compatibility candidate to verify.
    implementation("com.google.firebase:firebase-auth:24.2.0")
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    // Official 2026-10 SDK family; each service is checked at runtime before use.
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    implementation("ai.pleos.playground:FusedLocation:1.1.3")
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    implementation("ai.pleos.playground:SpeechToText:3.3.1")
    // Align instrumentation compile APIs with the versions already selected by the app.
    androidTestImplementation("androidx.annotation:annotation-experimental:1.3.1")
    androidTestImplementation("androidx.lifecycle:lifecycle-common:2.6.1")
    // Public ErrorCode is published as runtime-only by Vehicle.
    // Vehicle already packages this at runtime; expose only its public constants to the compiler.
    compileOnly("ai.pleos.playground:vehicle-data:1.1.11") { isTransitive = false }
}
