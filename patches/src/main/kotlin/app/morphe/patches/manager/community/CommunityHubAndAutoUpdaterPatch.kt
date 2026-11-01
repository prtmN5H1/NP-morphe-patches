package app.morphe.patches.manager.community

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

private const val EXTENSION_CLASS = "Lapp/morphe/extension/MorpheEmbeddedExtension;"

object MainActivityOnCreateFingerprint : Fingerprint(
    definingClass = "/MainActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;")
)

@Suppress("unused")
val communityHubAndAutoUpdaterPatch = bytecodePatch(
    name = "Embedded Community Hub & Auto-Updater",
    description = "Embeds morphe-patches.software directly inside Morphe Manager with live 'Already Added' source detection and automatic GitHub release downloading & self-patching.",
    default = false
) {
    extendWith("extensions/extension.mpe")

    execute {
        MainActivityOnCreateFingerprint.method.addInstructions(
            0,
            """
                invoke-static {p0}, $EXTENSION_CLASS;->checkAndAutoPatchLatestRelease(Landroid/app/Activity;)V
            """
        )
    }
}
