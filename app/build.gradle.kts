import jdk.javadoc.internal.doclint.DocLint.main
import org.apache.commons.logging.LogFactory.release
import org.gradle.kotlin.dsl.implementation

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.ai_belt_mobile"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.ai_belt_mobile"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // apache包
        useLibrary("org.apache.http.legacy")
        

    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
        dataBinding = true
    }




}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.legacy.support.v4)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.viewpager2)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("com.github.dmytrodanylyk:circular-progress-button:1.4")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.squareup.okhttp3:okhttp:5.3.2")

    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:${cameraxVersion}")
    implementation("androidx.camera:camera-camera2:${cameraxVersion}")
    implementation("androidx.camera:camera-lifecycle:${cameraxVersion}")
    implementation("androidx.camera:camera-view:${cameraxVersion}")

    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // SparkChain SDK
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))

    // 权限请求框架：https://github.com/getActivity/XXPermissions
    implementation("com.github.getActivity:XXPermissions:28.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    //内部自动解析为JSON
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlin-coroutines-adapter:0.9.2")

    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.recyclerview:recyclerview:1.2.1")
    implementation("androidx.lifecycle:lifecycle-extensions:2.2.0")
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("com.airbnb.android:lottie:3.6.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.4.30")
    implementation("com.github.bumptech.glide:glide:4.12.0")

   // implementation("com.google.android.gms:play-services-location:21.0.1")高精度定位比原生的LocationManager精准但是谷歌服务框架大部分国产手机用不了

}