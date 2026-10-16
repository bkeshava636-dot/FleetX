package com.college.bustracker.diagnostics

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

enum class RomType {
    SAMSUNG_ONE_UI,
    VIVO_ORIGIN_FUNTOUCH,
    XIAOMI_HYPEROS_MIUI,
    OPPO_COLOR_OS,
    ONEPLUS_OXYGEN_OS,
    REALME_UI,
    MOTOROLA_HELLO_MY_UX,
    TRANSSION_HIOS_XOS,
    HUAWEI_HONOR,
    GOOGLE_PIXEL,
    NOTHING_OS,
    ASUS_ZENUI,
    GENERIC_AOSP
}

data class RomInfo(
    val romType: RomType,
    val displayName: String,
    val romVersion: String,
    val killRisk: String, // "Extreme", "High", "Medium", "Standard"
    val advice: String,
    val autostartPackageName: String? = null
)

/**
 * Universal Android OEM ROM & Background Execution Restriction Detector.
 * Dynamically detects Samsung One UI, Vivo OriginOS / Funtouch OS, Xiaomi HyperOS / MIUI,
 * Oppo ColorOS, OnePlus OxygenOS, Realme UI, and others via manufacturer signatures and system properties.
 */
object DeviceRomDetector {

    private const val TAG = "DeviceRomDetector"

    private var cachedRomInfo: RomInfo? = null

    @SuppressLint("PrivateApi")
    fun getSystemProperty(propName: String): String {
        return try {
            val systemPropertiesClass = Class.forName("android.os.SystemProperties")
            val getMethod = systemPropertiesClass.getMethod("get", String::class.java)
            (getMethod.invoke(null, propName) as? String)?.trim() ?: ""
        } catch (e: Throwable) {
            ""
        }
    }

    fun getRomInfo(): RomInfo {
        cachedRomInfo?.let { return it }

        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        val fingerprint = Build.FINGERPRINT.lowercase()
        val display = Build.DISPLAY.lowercase()

        val romInfo = when {
            // 1. Samsung One UI
            manufacturer.contains("samsung") -> {
                val oneUiProp = getSystemProperty("ro.build.version.oneui")
                val semVersion = getSystemProperty("ro.build.version.sem")
                val versionStr = when {
                    oneUiProp.isNotBlank() -> {
                        val v = oneUiProp.toIntOrNull()
                        if (v != null && v > 10000) "One UI ${(v / 10000)}.${(v % 10000) / 100}" else "One UI $oneUiProp"
                    }
                    semVersion.isNotBlank() -> "One UI (SEM $semVersion)"
                    else -> "One UI (Android ${Build.VERSION.RELEASE})"
                }
                RomInfo(
                    romType = RomType.SAMSUNG_ONE_UI,
                    displayName = "Samsung One UI",
                    romVersion = versionStr,
                    killRisk = "High",
                    advice = "In Device Care > Battery: disable 'Put unused apps to sleep' and add FleetX to 'Never Sleeping Apps'. Set Battery to 'Unrestricted'.",
                    autostartPackageName = "com.samsung.android.lool"
                )
            }

            // 2. Vivo / iQOO (OriginOS / Funtouch OS)
            manufacturer.contains("vivo") || brand.contains("vivo") || brand.contains("iqoo") || manufacturer.contains("iqoo") -> {
                val vivoOsName = getSystemProperty("ro.vivo.os.name")
                val vivoOsVer = getSystemProperty("ro.vivo.os.version")
                val vivoBuildVer = getSystemProperty("ro.vivo.os.build.version")
                val isOrigin = vivoOsName.contains("origin", ignoreCase = true) || vivoBuildVer.contains("origin", ignoreCase = true)
                val osLabel = if (isOrigin) "Vivo OriginOS" else "Vivo Funtouch OS"
                val ver = listOf(vivoOsVer, vivoBuildVer).firstOrNull { it.isNotBlank() } ?: "Android ${Build.VERSION.RELEASE}"

                RomInfo(
                    romType = RomType.VIVO_ORIGIN_FUNTOUCH,
                    displayName = osLabel,
                    romVersion = "$osLabel $ver",
                    killRisk = "Extreme",
                    advice = "In Settings > Battery > Background Power Consumption: set FleetX to 'High Background Power Consumption'. In iManager, enable 'Autostart'.",
                    autostartPackageName = "com.iqoo.secure"
                )
            }

            // 3. Xiaomi / Redmi / POCO (HyperOS / MIUI)
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || brand.contains("poco") -> {
                val hyperOsVer = getSystemProperty("ro.mi.os.version.name")
                val miuiVer = getSystemProperty("ro.miui.ui.version.name")
                val isHyperOs = hyperOsVer.isNotBlank()
                val osName = if (isHyperOs) "Xiaomi HyperOS" else "Xiaomi MIUI"
                val ver = if (isHyperOs) hyperOsVer else if (miuiVer.isNotBlank()) miuiVer else "Android ${Build.VERSION.RELEASE}"

                RomInfo(
                    romType = RomType.XIAOMI_HYPEROS_MIUI,
                    displayName = osName,
                    romVersion = "$osName $ver",
                    killRisk = "Extreme",
                    advice = "In App Info: enable 'Autostart' and set Battery Saver to 'No Restrictions'. Lock app in Recent Apps overview.",
                    autostartPackageName = "com.miui.securitycenter"
                )
            }

            // 4. OnePlus (OxygenOS / ColorOS base)
            manufacturer.contains("oneplus") || brand.contains("oneplus") -> {
                val oxygenVer = getSystemProperty("ro.oxygen.version")
                val oplusRom = getSystemProperty("ro.build.version.oplusrom")
                val romVer = listOf(oxygenVer, oplusRom).firstOrNull { it.isNotBlank() } ?: "Android ${Build.VERSION.RELEASE}"

                RomInfo(
                    romType = RomType.ONEPLUS_OXYGEN_OS,
                    displayName = "OnePlus OxygenOS",
                    romVersion = "OxygenOS $romVer",
                    killRisk = "High",
                    advice = "In Battery > Advanced Settings: disable 'Sleep standby optimization' and set FleetX to 'Don't optimize' with 'Allow background activity'.",
                    autostartPackageName = "com.oneplus.security"
                )
            }

            // 5. Oppo (ColorOS)
            manufacturer.contains("oppo") || brand.contains("oppo") -> {
                val colorOsVer = getSystemProperty("ro.build.version.opporom")
                val oplusRom = getSystemProperty("ro.build.version.oplusrom")
                val romVer = listOf(colorOsVer, oplusRom).firstOrNull { it.isNotBlank() } ?: "Android ${Build.VERSION.RELEASE}"

                RomInfo(
                    romType = RomType.OPPO_COLOR_OS,
                    displayName = "Oppo ColorOS",
                    romVersion = "ColorOS $romVer",
                    killRisk = "High",
                    advice = "In Battery > App Battery Management: enable 'Allow background activity' and 'Allow auto-launch'.",
                    autostartPackageName = "com.coloros.safecenter"
                )
            }

            // 6. Realme (Realme UI)
            manufacturer.contains("realme") || brand.contains("realme") -> {
                val realmeVer = getSystemProperty("ro.build.version.realmeui")
                val romVer = if (realmeVer.isNotBlank()) realmeVer else "Android ${Build.VERSION.RELEASE}"

                RomInfo(
                    romType = RomType.REALME_UI,
                    displayName = "Realme UI",
                    romVersion = "Realme UI $romVer",
                    killRisk = "High",
                    advice = "In App Management > Auto-launch: enable FleetX. In Battery > App battery management: enable 'Allow background activity'.",
                    autostartPackageName = "com.coloros.safecenter"
                )
            }

            // 7. Motorola / Lenovo (Hello UI / My UX)
            manufacturer.contains("motorola") || manufacturer.contains("lenovo") -> {
                RomInfo(
                    romType = RomType.MOTOROLA_HELLO_MY_UX,
                    displayName = "Motorola Hello UI / My UX",
                    romVersion = "Motorola (Android ${Build.VERSION.RELEASE})",
                    killRisk = "Medium",
                    advice = "In App Info > Battery: select 'Unrestricted'. Ensure Background Data is enabled.",
                    autostartPackageName = null
                )
            }

            // 8. Transsion: Infinix / Tecno / itel (XOS / HiOS)
            manufacturer.contains("infinix") || manufacturer.contains("tecno") || manufacturer.contains("transsion") || manufacturer.contains("itel") -> {
                val xosVer = getSystemProperty("ro.tr_os.version")
                val hiosVer = getSystemProperty("ro.hi_os.version")
                val name = if (manufacturer.contains("tecno")) "Tecno HiOS" else "Infinix XOS"
                val ver = listOf(xosVer, hiosVer).firstOrNull { it.isNotBlank() } ?: "Android ${Build.VERSION.RELEASE}"

                RomInfo(
                    romType = RomType.TRANSSION_HIOS_XOS,
                    displayName = name,
                    romVersion = "$name $ver",
                    killRisk = "High",
                    advice = "In Phone Master > Auto-start Management: enable FleetX. Set Battery optimization to 'Not optimized'.",
                    autostartPackageName = "com.transsion.phonemaster"
                )
            }

            // 9. Huawei / Honor (EMUI / MagicOS)
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                val emuiVer = getSystemProperty("ro.build.version.emui")
                val magicVer = getSystemProperty("ro.build.version.magic")
                val name = if (manufacturer.contains("honor")) "Honor MagicOS" else "Huawei EMUI"
                val ver = listOf(emuiVer, magicVer).firstOrNull { it.isNotBlank() } ?: "Android ${Build.VERSION.RELEASE}"

                RomInfo(
                    romType = RomType.HUAWEI_HONOR,
                    displayName = name,
                    romVersion = "$name $ver",
                    killRisk = "Extreme",
                    advice = "In Settings > Apps > Launch: disable 'Manage automatically' and enable 'Auto-launch', 'Secondary launch', and 'Run in background'.",
                    autostartPackageName = "com.huawei.systemmanager"
                )
            }

            // 10. Google Pixel (Stock Android)
            manufacturer.contains("google") || brand.contains("google") -> {
                RomInfo(
                    romType = RomType.GOOGLE_PIXEL,
                    displayName = "Google Pixel (Stock Android)",
                    romVersion = "Android ${Build.VERSION.RELEASE} (Stock)",
                    killRisk = "Standard",
                    advice = "In App Info > App battery usage: select 'Unrestricted'. Standard AOSP Doze applies.",
                    autostartPackageName = null
                )
            }

            // 11. Nothing OS
            manufacturer.contains("nothing") -> {
                RomInfo(
                    romType = RomType.NOTHING_OS,
                    displayName = "Nothing OS",
                    romVersion = "Nothing OS (Android ${Build.VERSION.RELEASE})",
                    killRisk = "Medium",
                    advice = "In App Info > Battery: select 'Unrestricted'.",
                    autostartPackageName = null
                )
            }

            else -> {
                RomInfo(
                    romType = RomType.GENERIC_AOSP,
                    displayName = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} Android",
                    romVersion = "Android ${Build.VERSION.RELEASE} (${Build.MODEL})",
                    killRisk = "Medium",
                    advice = "In Settings > Apps > FleetX > Battery: select 'Unrestricted' and disable battery optimizations.",
                    autostartPackageName = null
                )
            }
        }

        cachedRomInfo = romInfo
        return romInfo
    }

    /**
     * Attempts to launch the OEM-specific Autostart / Background Manager intent.
     * Falls back to Android Application Details or Battery Optimization exemption dialog.
     */
    fun openOemBatteryOptimizationSettings(context: Context): Boolean {
        val rom = getRomInfo()
        val pkg = context.packageName

        val intentsToTry = mutableListOf<Intent>()

        when (rom.romType) {
            RomType.VIVO_ORIGIN_FUNTOUCH -> {
                intentsToTry.add(Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")))
                intentsToTry.add(Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")))
                intentsToTry.add(Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.MainGuideActivity")))
                intentsToTry.add(Intent().setComponent(ComponentName("com.vivo.abe", "com.vivo.abe.feature.moneymode.fragment.HighPowerDetailActivity")))
            }
            RomType.XIAOMI_HYPEROS_MIUI -> {
                intentsToTry.add(Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")))
                intentsToTry.add(Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")).apply {
                    putExtra("package_name", pkg)
                    putExtra("package_label", "FleetX")
                })
            }
            RomType.SAMSUNG_ONE_UI -> {
                intentsToTry.add(Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")))
                intentsToTry.add(Intent().setComponent(ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity")))
            }
            RomType.OPPO_COLOR_OS, RomType.REALME_UI -> {
                intentsToTry.add(Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")))
                intentsToTry.add(Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")))
                intentsToTry.add(Intent().setComponent(ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")))
            }
            RomType.ONEPLUS_OXYGEN_OS -> {
                intentsToTry.add(Intent().setComponent(ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")))
            }
            RomType.HUAWEI_HONOR -> {
                intentsToTry.add(Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")))
                intentsToTry.add(Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")))
            }
            RomType.TRANSSION_HIOS_XOS -> {
                intentsToTry.add(Intent().setComponent(ComponentName("com.transsion.phonemaster", "com.transsion.phonemaster.MainActivity")))
            }
            else -> {}
        }

        // Standard Android Battery Optimization request
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (pm != null && !pm.isIgnoringBatteryOptimizations(pkg)) {
            intentsToTry.add(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$pkg")
                }
            )
        }

        // Standard App details fallback
        intentsToTry.add(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$pkg")
            }
        )

        for (intent in intentsToTry) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.i(TAG, "Successfully launched settings intent: ${intent.component ?: intent.action}")
                return true
            } catch (e: Exception) {
                Log.d(TAG, "Intent not available on this device: ${intent.component ?: intent.action}")
            }
        }

        return false
    }
}
