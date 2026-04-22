package net.chikach.jujutsuintellij.cli.template

sealed interface JjTemplateExpr {
    fun render(ctx: RenderContext = RenderContext): String
}

sealed interface TemplateExpr : JjTemplateExpr

sealed interface SerializableExpr : JjTemplateExpr

sealed interface SerializableTemplateExpr : TemplateExpr, SerializableExpr

sealed interface StringExpr : SerializableTemplateExpr

sealed interface IntegerExpr : SerializableTemplateExpr

sealed interface BooleanExpr : SerializableTemplateExpr

sealed interface TimestampExpr : SerializableTemplateExpr

sealed interface CommitExpr : SerializableExpr

sealed interface SignatureExpr : SerializableExpr

sealed interface AnnotationLineExpr : JjTemplateExpr

sealed interface ListExpr<out T : JjTemplateExpr> : JjTemplateExpr

object RenderContext {
    fun quoteString(value: String): String {
        val escaped = buildString {
            value.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    '\b' -> append("\\x08")
                    '\u000C' -> append("\\x0c")
                    '\u0000' -> append("\\0")
                    else -> {
                        if (ch.code < 0x20) {
                            append("\\x")
                            append(ch.code.toString(16).padStart(2, '0'))
                        } else {
                            append(ch)
                        }
                    }
                }
            }
        }
        return "\"$escaped\""
    }

    fun quoteJsonKey(key: String): String = quoteString("${escapeJsonString(key)}:")

    private fun escapeJsonString(value: String): String {
        val escaped = buildString {
            value.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> {
                        if (ch.code < 0x20) {
                            append("\\u")
                            append(ch.code.toString(16).padStart(4, '0'))
                        } else {
                            append(ch)
                        }
                    }
                }
            }
        }
        return "\"$escaped\""
    }
}

private open class RenderedExpr(private val source: String) : JjTemplateExpr {
    override fun render(ctx: RenderContext): String = source
}

private class RenderedTemplateExpr(source: String) : RenderedExpr(source), TemplateExpr
private class RenderedSerializableTemplateExpr(source: String) : RenderedExpr(source), SerializableTemplateExpr
private class RenderedStringExpr(source: String) : RenderedExpr(source), StringExpr
private class RenderedIntegerExpr(source: String) : RenderedExpr(source), IntegerExpr
private class RenderedBooleanExpr(source: String) : RenderedExpr(source), BooleanExpr
private class RenderedTimestampExpr(source: String) : RenderedExpr(source), TimestampExpr
private class RenderedCommitExpr(source: String) : RenderedExpr(source), CommitExpr
private class RenderedSignatureExpr(source: String) : RenderedExpr(source), SignatureExpr
private class RenderedAnnotationLineExpr(source: String) : RenderedExpr(source), AnnotationLineExpr
private class RenderedListExpr<T : JjTemplateExpr>(source: String) : RenderedExpr(source), ListExpr<T>

private fun methodCall(target: JjTemplateExpr, method: String, vararg args: JjTemplateExpr): String =
    buildString {
        append(target.render())
        append('.')
        append(method)
        append('(')
        append(args.joinToString(", ") { it.render() })
        append(')')
    }

private fun functionCall(name: String, vararg args: JjTemplateExpr): String =
    buildString {
        append(name)
        append('(')
        append(args.joinToString(", ") { it.render() })
        append(')')
    }

fun literal(value: String): StringExpr = RenderedStringExpr(RenderContext.quoteString(value))

fun stringify(value: TemplateExpr): StringExpr = RenderedStringExpr(functionCall("stringify", value))

fun json(value: SerializableExpr): StringExpr = RenderedStringExpr(functionCall("json", value))

fun templateExpr(source: String): TemplateExpr = RenderedTemplateExpr(source)

fun serializableTemplateExpr(source: String): SerializableTemplateExpr = RenderedSerializableTemplateExpr(source)

fun integerExpr(source: String): IntegerExpr = RenderedIntegerExpr(source)

fun booleanExpr(source: String): BooleanExpr = RenderedBooleanExpr(source)

fun commitExpr(source: String): CommitExpr = RenderedCommitExpr(source)

fun annotationLineExpr(source: String): AnnotationLineExpr = RenderedAnnotationLineExpr(source)

fun CommitExpr.commitId(): SerializableTemplateExpr = serializableTemplateExpr(methodCall(this, "commit_id"))

fun CommitExpr.changeId(): SerializableTemplateExpr = serializableTemplateExpr(methodCall(this, "change_id"))

fun CommitExpr.description(): StringExpr = RenderedStringExpr(methodCall(this, "description"))

fun CommitExpr.author(): SignatureExpr = RenderedSignatureExpr(methodCall(this, "author"))

fun CommitExpr.parents(): ListExpr<CommitExpr> = RenderedListExpr(methodCall(this, "parents"))

fun SignatureExpr.name(): StringExpr = RenderedStringExpr(methodCall(this, "name"))

fun SignatureExpr.email(): StringExpr = RenderedStringExpr(methodCall(this, "email"))

fun SignatureExpr.timestamp(): TimestampExpr = RenderedTimestampExpr(methodCall(this, "timestamp"))

fun AnnotationLineExpr.commit(): CommitExpr = RenderedCommitExpr(methodCall(this, "commit"))

fun AnnotationLineExpr.lineNumber(): IntegerExpr = RenderedIntegerExpr(methodCall(this, "line_number"))

class CommitScope internal constructor(
    val self: CommitExpr,
) {
    val commitId: SerializableTemplateExpr get() = self.commitId()
    val changeId: SerializableTemplateExpr get() = self.changeId()
    val description: StringExpr get() = self.description()
    val author: SignatureExpr get() = self.author()
    val parents: ListExpr<CommitExpr> get() = self.parents()
}

class AnnotationScope internal constructor(
    val self: AnnotationLineExpr,
) {
    val commit: CommitExpr get() = self.commit()
    val commitId: SerializableTemplateExpr get() = commit.commitId()
    val changeId: SerializableTemplateExpr get() = commit.changeId()
    val author: SignatureExpr get() = commit.author()
    val lineNumber: IntegerExpr get() = self.lineNumber()
}

object JjTemplateScopes {
    fun commit(): CommitScope = CommitScope(commitExpr("self"))

    fun annotation(): AnnotationScope = AnnotationScope(annotationLineExpr("self"))
}
