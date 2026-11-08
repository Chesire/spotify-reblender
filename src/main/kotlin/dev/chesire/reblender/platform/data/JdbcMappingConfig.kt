package dev.chesire.reblender.platform.data

import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.relational.core.mapping.RelationalMappingContext

@Configuration
class JdbcMappingConfig {
    companion object {
        /**
         * Spring Data JDBC quotes identifiers by default, and quoted names given in `@Table`/`@MappedCollection`
         * keep their case. H2 stores the unquoted names from schema.sql in upper case, so quoted lower-case names
         * wouldn't match. Unquoted identifiers resolve the same way as in schema.sql.
         */
        @Bean
        @JvmStatic
        fun unquotedIdentifiers(): BeanPostProcessor =
            object : BeanPostProcessor {
                override fun postProcessBeforeInitialization(
                    bean: Any,
                    beanName: String,
                ): Any {
                    if (bean is RelationalMappingContext) bean.isForceQuote = false
                    return bean
                }
            }
    }
}
