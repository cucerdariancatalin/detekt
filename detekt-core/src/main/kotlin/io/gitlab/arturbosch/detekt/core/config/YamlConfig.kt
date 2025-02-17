@file:Suppress("UNCHECKED_CAST")

package io.gitlab.arturbosch.detekt.core.config

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Config.Companion.CONFIG_SEPARATOR
import io.gitlab.arturbosch.detekt.api.Notification
import io.gitlab.arturbosch.detekt.core.config.validation.ValidatableConfiguration
import io.gitlab.arturbosch.detekt.core.config.validation.validateConfig
import org.yaml.snakeyaml.Yaml
import java.io.Reader
import java.nio.file.Path

/**
 * Config implementation using the yaml format. SubConfigurations can return sub maps according to the
 * yaml specification.
 */
class YamlConfig internal constructor(
    val properties: Map<String, Any>,
    override val parentPath: String? = null
) : Config, ValidatableConfiguration {

    override fun subConfig(key: String): Config {
        val subProperties = properties.getOrElse(key) { emptyMap<String, Any>() }
        return YamlConfig(
            subProperties as Map<String, Any>,
            if (parentPath == null) key else "$parentPath $CONFIG_SEPARATOR $key"
        )
    }

    override fun <T : Any> valueOrDefault(key: String, default: T): T {
        val result = properties[key]
        return valueOrDefaultInternal(key, result, default) as T
    }

    override fun <T : Any> valueOrNull(key: String): T? {
        return properties[key] as? T?
    }

    override fun toString(): String {
        return "YamlConfig(properties=$properties)"
    }

    override fun validate(baseline: Config, excludePatterns: Set<Regex>): List<Notification> =
        validateConfig(this, baseline, excludePatterns)

    companion object {

        /**
         * Factory method to load a yaml configuration. Given path must exist
         * and point to a readable file.
         */
        fun load(path: Path): Config =
            load(
                path.toFile().apply {
                    require(exists()) { "Configuration does not exist: $path" }
                    require(isFile) { "Configuration must be a file: $path" }
                    require(canRead()) { "Configuration must be readable: $path" }
                }.reader()
            )

        /**
         * Constructs a [YamlConfig] from any [Reader].
         *
         * Note the reader will be consumed and closed.
         */
        fun load(reader: Reader): Config = reader.buffered().use { bufferedReader ->
            val map: Map<*, *>? = runCatching {
                @Suppress("USELESS_CAST") // runtime inference bug
                Yaml().loadAs(bufferedReader, Map::class.java) as Map<*, *>?
            }.getOrElse { throw Config.InvalidConfigurationError(it) }
            if (map == null) {
                YamlConfig(emptyMap())
            } else {
                YamlConfig(map as Map<String, Any>)
            }
        }
    }
}
