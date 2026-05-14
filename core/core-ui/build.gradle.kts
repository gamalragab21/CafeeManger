plugins {
    alias(libs.plugins.waselak.kmp.compose.library)
}

android {
    namespace = "net.marllex.waselak.core.ui"
}

compose.resources {
    publicResClass = true
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:core-model"))
            implementation(project(":core:core-common"))
            implementation(libs.kotlinx.datetime)
            api(libs.coil.compose)
            api(libs.coil.network.ktor)
        }
        androidMain.dependencies {
            implementation(libs.androidx.appcompat)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.print)
            implementation(libs.zxing.core)
            implementation(libs.mlkit.barcode.scanning)
            implementation(libs.camerax.camera2)
            implementation(libs.camerax.lifecycle)
            implementation(libs.camerax.view)
            // ESC/POS thermal printer driver (Bluetooth + USB). Used by
            // ThermalPrinterManager / EscPosReceiptFormatter for direct
            // receipt printing on the cashier app without depending on
            // RawBT or any other 3rd-party Android print-service app.
            implementation("com.github.DantSu:ESCPOS-ThermalPrinter-Android:3.3.0")
        }
        val desktopMain by getting
        desktopMain.dependencies {
            implementation(libs.zxing.core)
        }
    }
}
