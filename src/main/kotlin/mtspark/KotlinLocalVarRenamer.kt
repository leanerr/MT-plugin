package mtspark

import java.io.File

class KotlinLocalVarRenamer : Mutator {
    override fun mutate(file: File): MutationResult {
        if (!file.extension.equals("kt", ignoreCase = true)) {
            return MutationResult(file, null, null, false)
        }
        // TODO: implement with Kotlin PSI / compiler analysis
        return MutationResult(file, null, null, false)
    }
}