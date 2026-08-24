plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.notresemaine.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.notresemaine.app"
        minSdk = 29
        targetSdk = 35
        // À incrémenter à chaque livraison : c'est ce que Réglages affiche,
        // et le seul moyen de vérifier quelle version est réellement installée.
        versionCode = 22
        versionName = "15.0"
    }

    /**
     * Clé de signature fixe, versionnée avec le projet.
     *
     * Sans elle, chaque construction sur GitHub fabriquait une clé neuve : Android
     * refusait alors d'installer par-dessus la version précédente (« Application
     * non installée »), et il fallait désinstaller — donc tout reconfigurer — à
     * chaque livraison. Avec une clé stable, toutes les versions suivantes
     * s'installent par simple mise à jour, en gardant les réglages.
     *
     * Ce n'est pas une clé de publication : l'application ne va pas sur le Play
     * Store, elle se partage entre deux téléphones. Le mot de passe est dans le
     * dépôt, assumé — sa seule fonction est que les versions se reconnaissent
     * entre elles.
     */
    signingConfigs {
        getByName("debug") {
            storeFile = file("../signing/notre-semaine.keystore")
            storePassword = "notresemaine"
            keyAlias = "notresemaine"
            keyPassword = "notresemaine"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    // Photo d'un repas : l'orientation notée par l'appareil photo. Sans elle, une
    // photo prise à la verticale part couchée et l'assistant décrit une assiette
    // de travers.
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.health.connect:connect-client:1.1.0-alpha07")

    // Réseau : sert à la synchronisation Supabase comme à l'assistant.
    // L'assistant tient en quelques requêtes HTTP : embarquer un SDK complet
    // alourdirait l'application de plusieurs mégaoctets pour rien.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}
