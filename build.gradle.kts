plugins {
    id("com.android.application") version "8.6.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.gms.google-services") version "4.5.0" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}

// Guarda os erros de compilação num ficheiro (build-errors.log) para facilitar o diagnóstico.
gradle.rootProject {
    val ficheiroLog = File(rootDir, "build-errors.log")
    try { ficheiroLog.writeText("") } catch (_: Exception) {}
    logging.addStandardErrorListener { msg -> try { ficheiroLog.appendText(msg.toString()) } catch (_: Exception) {} }
    logging.addStandardOutputListener { msg ->
        val t = msg.toString()
        if (t.contains("e: ") || t.contains("error:") || t.contains("FAILED") || t.contains("What went wrong")) {
            try { ficheiroLog.appendText(t) } catch (_: Exception) {}
        }
    }
}
