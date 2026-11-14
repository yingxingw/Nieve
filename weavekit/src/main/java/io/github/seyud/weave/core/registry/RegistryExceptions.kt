package io.github.seyud.weave.core.registry

/**
 * Minimal shims of WeaveMask core registry exceptions (the full RegistryParser is
 * not part of the extracted UI kit; only the exception types are needed by
 * [RegistryUrls]).
 */
open class RegistryException(message: String, cause: Throwable? = null) : Exception(message, cause)

class RegistryFormatException(message: String, cause: Throwable? = null) : RegistryException(message, cause)
