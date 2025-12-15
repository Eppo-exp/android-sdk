package cloud.eppo.kotlin.model

data class ResolutionDetails<T : Any>(
    val value: T,
    val variant: String? = null,
    val reason: ResolutionReason? = null,
    val errorCode: ErrorCode? = null,
    val errorMessage: String? = null,
    val flagMetadata: Map<String, String> = emptyMap()
)
