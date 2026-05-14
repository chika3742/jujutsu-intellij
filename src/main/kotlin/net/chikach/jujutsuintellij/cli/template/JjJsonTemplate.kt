package net.chikach.jujutsuintellij.cli.template

sealed interface JjJsonValue {
    fun render(ctx: RenderContext = RenderContext): String
}

data class JsonField(val name: String, val value: JjJsonValue)

private fun concatenate(parts: List<String>): String =
    when (parts.size) {
        0 -> RenderContext.quoteString("")
        1 -> parts.single()
        else -> parts.joinToString(" ++ ")
    }

private data class JsonStringValue(private val value: TemplateExpr) : JjJsonValue {
    override fun render(ctx: RenderContext): String = "${stringify(value).render(ctx)}.escape_json()"
}

private data class JsonIntValue(private val value: IntegerExpr) : JjJsonValue {
    override fun render(ctx: RenderContext): String = value.render(ctx)
}

private data class JsonBooleanValue(private val value: BooleanExpr) : JjJsonValue {
    override fun render(ctx: RenderContext): String = value.render(ctx)
}

private data class JsonSerializedValue(private val value: SerializableExpr) : JjJsonValue {
    override fun render(ctx: RenderContext): String = json(value).render(ctx)
}

private data class JsonSerializedListValue(private val list: ListExpr<SerializableExpr>) : JjJsonValue {
    override fun render(ctx: RenderContext): String = "json(${list.render(ctx)})"
}

private data class JsonArrayValue(private val items: List<JjJsonValue>) : JjJsonValue {
    override fun render(ctx: RenderContext): String {
        val parts = mutableListOf<String>()
        parts += ctx.quoteString("[")
        items.forEachIndexed { index, item ->
            if (index > 0) {
                parts += ctx.quoteString(",")
            }
            parts += item.render(ctx)
        }
        parts += ctx.quoteString("]")
        return concatenate(parts)
    }
}

private data class JsonObjectValue(private val fields: List<JsonField>) : JjJsonValue {
    override fun render(ctx: RenderContext): String {
        val parts = mutableListOf<String>()
        parts += ctx.quoteString("{")
        fields.forEachIndexed { index, field ->
            if (index > 0) {
                parts += ctx.quoteString(",")
            }
            parts += ctx.quoteJsonKey(field.name)
            parts += field.value.render(ctx)
        }
        parts += ctx.quoteString("}")
        return concatenate(parts)
    }
}

class JsonObjectBuilder {
    private val fields = mutableListOf<JsonField>()

    infix fun String.to(value: JjJsonValue) {
        fields += JsonField(this, value)
    }

    fun build(): JjJsonValue = JsonObjectValue(fields.toList())
}

fun obj(builder: JsonObjectBuilder.() -> Unit): JjJsonValue =
    JsonObjectBuilder().apply(builder).build()

fun arr(vararg values: JjJsonValue): JjJsonValue = JsonArrayValue(values.toList())

fun string(value: TemplateExpr): JjJsonValue = JsonStringValue(value)

fun num(value: IntegerExpr): JjJsonValue = JsonIntValue(value)

fun bool(value: BooleanExpr): JjJsonValue = JsonBooleanValue(value)

fun serialized(value: SerializableExpr): JjJsonValue = JsonSerializedValue(value)

/** Emits a JSON array by serializing the list with jj's `json()` function. */
fun serialized(list: ListExpr<SerializableExpr>): JjJsonValue = JsonSerializedListValue(list)

object JjTemplates {
    fun commitJsonLine(builder: CommitScope.() -> JjJsonValue): String =
        renderJsonLine(JjTemplateScopes.commit().builder())

    fun annotationJsonLine(builder: AnnotationScope.() -> JjJsonValue): String =
        renderJsonLine(JjTemplateScopes.annotation().builder())

    fun bookmarkRefJsonLine(builder: BookmarkRefScope.() -> JjJsonValue): String =
        renderJsonLine(JjTemplateScopes.bookmarkRef().builder())

    private fun renderJsonLine(value: JjJsonValue): String =
        concatenate(listOf(value.render(), RenderContext.quoteString("\n")))
}
