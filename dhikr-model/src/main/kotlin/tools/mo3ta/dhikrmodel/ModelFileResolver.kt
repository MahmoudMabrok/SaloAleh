package tools.mo3ta.dhikrmodel

internal fun requireRecommendedModelFile(
    model: ModelSpec,
    availableFiles: Set<String>,
): String {
    if (model.file in availableFiles) return model.file

    val recommendedVariant = model.variants.entries
        .firstOrNull { it.value == model.file }
        ?.key
        ?: model.file.substringAfterLast('_').substringBefore(".tflite")
    val installedAlternatives = model.variants
        .filterValues(availableFiles::contains)
        .map { (variant, file) ->
            val reasons = model.rejectionReasons[variant].orEmpty()
            if (reasons.isEmpty()) {
                "$variant ('$file') is installed, but it is not the recommended export"
            } else {
                "$variant ('$file') is installed but was rejected: ${reasons.joinToString()}"
            }
        }
    val installedNote = if (installedAlternatives.isEmpty()) {
        "No model variant from this export is installed."
    } else {
        installedAlternatives.joinToString(separator = "; ")
    }

    throw DhikrModelException(
        "The recommended $recommendedVariant model '${model.file}' is missing. " +
            "$installedNote. Copy '${model.file}' from the same export bundle; " +
            "do not silently substitute a rejected variant.",
    )
}
