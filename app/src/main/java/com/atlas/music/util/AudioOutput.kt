package com.atlas.music.util

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

enum class OutputKind { SPEAKER, WIRED, BLUETOOTH, USB_DAC, HDMI, EARPIECE, OTHER }

data class OutputDevice(
    val id: Int,
    val type: Int,
    val kind: OutputKind,
    val name: String,
)

/** All audio output routes currently visible to this app (reactive — re-query on device callbacks). */
object AudioOutput {

    fun listOutputs(context: Context): List<OutputDevice> {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.type !in IGNORED_TYPES }
            .map { device ->
                val kind = kindOf(device.type)
                OutputDevice(
                    id = device.id,
                    type = device.type,
                    kind = kind,
                    name = nameOf(device, kind),
                )
            }
            .sortedBy { it.kind.ordinal }
    }

    fun currentLabel(context: Context): String {
        val outputs = listOutputs(context)
        val preferred = outputs.firstOrNull { it.kind != OutputKind.SPEAKER && it.kind != OutputKind.EARPIECE }
        return (preferred ?: outputs.firstOrNull())?.name ?: "Speaker"
    }

    private val IGNORED_TYPES = setOf(
        AudioDeviceInfo.TYPE_UNKNOWN,
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
        AudioDeviceInfo.TYPE_TELEPHONY,
    )

    private fun kindOf(type: Int): OutputKind = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> OutputKind.SPEAKER
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_LINE_ANALOG,
        AudioDeviceInfo.TYPE_LINE_DIGITAL -> OutputKind.WIRED
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> OutputKind.BLUETOOTH
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> OutputKind.USB_DAC
        AudioDeviceInfo.TYPE_HDMI,
        AudioDeviceInfo.TYPE_HDMI_ARC -> OutputKind.HDMI
        else -> OutputKind.OTHER
    }

    private fun isTechnical(name: String): Boolean {
        val lower = name.lowercase()
        return lower.contains("sdk_") || lower.contains("gphone") || lower.contains("goldfish") ||
            lower.contains("ranchu") || lower.contains("vemu") || lower.contains("emu") ||
            lower.contains("generic") || lower.contains("_")
    }

    private fun nameOf(device: AudioDeviceInfo, kind: OutputKind): String {
        val product = device.productName?.toString()?.trim().orEmpty()
        if (product.isNotBlank() && !product.equals("unknown", ignoreCase = true) && !isTechnical(product)) return product
        return when (kind) {
            OutputKind.SPEAKER -> "Phone speaker"
            OutputKind.WIRED -> "Wired headset"
            OutputKind.BLUETOOTH -> "Bluetooth audio"
            OutputKind.USB_DAC -> "USB DAC / audio"
            OutputKind.HDMI -> "HDMI audio"
            OutputKind.EARPIECE -> "Earpiece"
            OutputKind.OTHER -> "Audio output"
        }
    }
}
