package app.morphe.patches.manager.community

import app.morphe.patcher.data.BytecodeContext
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.fingerprint.MethodFingerprint
import app.morphe.patcher.patch.BytecodePatch
import app.morphe.patcher.patch.annotation.CompatiblePackage
import app.morphe.patcher.patch.annotation.Patch
import app.morphe.patcher.util.smali.ExternalLabel

object MainActivityOnCreateFingerprint : MethodFingerprint(
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
    customFingerprint = { methodDef, classDef ->
        classDef.type.endsWith("MainActivity;") && methodDef.name == "onCreate"
    }
)

object ActivityStartActivityFingerprint : MethodFingerprint(
    returnType = "V",
    parameters = listOf("Landroid/content/Intent;"),
    customFingerprint = { methodDef, classDef ->
        classDef.type.endsWith("MainActivity;") && methodDef.name == "startActivity"
    }
)

@Patch(
    name = "Embedded Community Hub & Auto-Updater",
    description = "Embeds morphe-patches.software directly inside Morphe Manager with live 'Already Added' source detection and automatic GitHub release downloading & self-patching.",
    compatiblePackages = [
        CompatiblePackage("app.morphe.manager"),
        CompatiblePackage("com.morphe.manager")
    ],
    use = true
)
@Suppress("unused")
object CommunityHubAndAutoUpdaterPatch : BytecodePatch(
    setOf(MainActivityOnCreateFingerprint, ActivityStartActivityFingerprint)
) {
    override fun execute(context: BytecodeContext) {
        MainActivityOnCreateFingerprint.result?.mutableMethod?.let { method ->
            val insertIndex = method.implementation!!.instructions.size - 1
            method.addInstructions(
                insertIndex,
                """
                    invoke-static {p0}, Lapp/morphe/extension/MorpheEmbeddedExtension;->checkAndAutoPatchLatestRelease(Landroid/app/Activity;)V
                """
            )
        }

        ActivityStartActivityFingerprint.result?.mutableMethod?.let { method ->
            val firstInstruction = method.implementation!!.instructions.first()
            method.addInstructionsWithLabels(
                0,
                """
                    invoke-static {p0, p1}, Lapp/morphe/extension/MorpheEmbeddedExtension;->interceptExternalBrowser(Landroid/app/Activity;Landroid/content/Intent;)Z
                    move-result v0
                    if-eqz v0, :proceed_normal
                    return-void
                """,
                ExternalLabel("proceed_normal", firstInstruction)
            )
        }
    }
}
