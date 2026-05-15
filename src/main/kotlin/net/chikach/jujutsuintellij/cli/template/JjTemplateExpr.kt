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

sealed interface CommitRefExpr : SerializableExpr

sealed interface SignatureExpr : SerializableExpr

sealed interface AnnotationLineExpr : JjTemplateExpr

sealed interface ListExpr<out T : JjTemplateExpr> : JjTemplateExpr

sealed interface LambdaExpr<out ElementT : JjTemplateExpr, out OutT> : JjTemplateExpr

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
private class RenderedCommitRefExpr(source: String) : RenderedExpr(source), CommitRefExpr
private class RenderedSignatureExpr(source: String) : RenderedExpr(source), SignatureExpr
private class RenderedAnnotationLineExpr(source: String) : RenderedExpr(source), AnnotationLineExpr
private class RenderedListExpr<T : JjTemplateExpr>(source: String) : RenderedExpr(source), ListExpr<T>
private class RenderedLambdaExpr<ElementT : JjTemplateExpr, OutT: JjTemplateExpr>(source: String) : RenderedExpr(source), LambdaExpr<ElementT, OutT>

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

private fun lambda(varName: String = "e", expr: JjTemplateExpr): String =
    buildString {
        append("|")
        append(varName)
        append("| ")
        append(expr.render())
    }

fun literal(value: String): StringExpr = RenderedStringExpr(RenderContext.quoteString(value))

fun stringify(value: TemplateExpr): StringExpr = RenderedStringExpr(functionCall("stringify", value))

fun json(value: SerializableExpr): StringExpr = RenderedStringExpr(functionCall("json", value))

fun templateExpr(source: String): TemplateExpr = RenderedTemplateExpr(source)

fun serializableTemplateExpr(source: String): SerializableTemplateExpr = RenderedSerializableTemplateExpr(source)

fun integerExpr(source: String): IntegerExpr = RenderedIntegerExpr(source)

fun booleanExpr(source: String): BooleanExpr = RenderedBooleanExpr(source)

fun commitExpr(source: String): CommitExpr = RenderedCommitExpr(source)

fun commitRefExpr(source: String): CommitRefExpr = RenderedCommitRefExpr(source)

fun annotationLineExpr(source: String): AnnotationLineExpr = RenderedAnnotationLineExpr(source)

fun <T : JjTemplateExpr> listExpr(source: String): ListExpr<T> = RenderedListExpr(source)

fun <ElementT : JjTemplateExpr, OutT : JjTemplateExpr> lambdaExpr(source: String): LambdaExpr<ElementT, OutT> =
    RenderedLambdaExpr(source)

/**
 * Builds a typed lambda expression from a Kotlin lambda. [elementFactory] constructs a
 * placeholder for the lambda parameter using the supplied variable name; the name is referenced
 * inside the rendered jj template body.
 */
fun <ElementT : JjTemplateExpr, OutT : JjTemplateExpr> lambda(
    elementFactory: (varName: String) -> ElementT,
    body: (ElementT) -> OutT,
): LambdaExpr<ElementT, OutT> {
    val varName = "p"
    val element = elementFactory(varName)
    val out = body(element)
    return lambdaExpr("|$varName| ${out.render()}")
}

fun <InT: JjTemplateExpr, OutT: JjTemplateExpr> ListExpr<InT>.map(lambda: LambdaExpr<InT, OutT>): ListExpr<OutT> =
    RenderedListExpr(methodCall(this, "map", lambda))

fun CommitExpr.commitId(): SerializableTemplateExpr = serializableTemplateExpr(methodCall(this, "commit_id"))

fun CommitExpr.changeId(): SerializableTemplateExpr = serializableTemplateExpr(methodCall(this, "change_id"))

fun CommitExpr.description(): StringExpr = RenderedStringExpr(methodCall(this, "description"))

fun CommitExpr.author(): SignatureExpr = RenderedSignatureExpr(methodCall(this, "author"))

fun CommitExpr.parents(): ListExpr<CommitExpr> = RenderedListExpr(methodCall(this, "parents"))

fun CommitExpr.localBookmarks(): ListExpr<CommitRefExpr> = RenderedListExpr(methodCall(this, "local_bookmarks"))

/** Maps `parents` to their commit ids. */
fun ListExpr<CommitExpr>.commitIds(): ListExpr<SerializableTemplateExpr> =
    map(lambda(::commitExpr) { it.commitId() })

fun SignatureExpr.name(): StringExpr = RenderedStringExpr(methodCall(this, "name"))

fun SignatureExpr.email(): StringExpr = RenderedStringExpr(methodCall(this, "email"))

fun SignatureExpr.timestamp(): TimestampExpr = RenderedTimestampExpr(methodCall(this, "timestamp"))

/** Renders the timestamp as ISO 8601 / RFC 3339 (`chrono` `%+` format). */
fun TimestampExpr.iso8601(): StringExpr =
    RenderedStringExpr(methodCall(this, "format", literal("%+")))

fun AnnotationLineExpr.commit(): CommitExpr = RenderedCommitExpr(methodCall(this, "commit"))

fun AnnotationLineExpr.lineNumber(): IntegerExpr = RenderedIntegerExpr(methodCall(this, "line_number"))

fun CommitRefExpr.name(): StringExpr = RenderedStringExpr(methodCall(this, "name"))

fun CommitRefExpr.remote(): StringExpr = RenderedStringExpr(methodCall(this, "remote"))

/**
 * Renders the commit id of `normal_target()` when present, or an empty string otherwise.
 * `normal_target()` is `Option<Commit>`, which is empty during a bookmark conflict.
 */
fun CommitRefExpr.normalTargetCommitId(): StringExpr {
    val src = render()
    return RenderedStringExpr("if($src.normal_target(), $src.normal_target().commit_id(), \"\")")
}

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

class BookmarkRefScope internal constructor(
    val self: CommitRefExpr,
) {
    val name: StringExpr get() = self.name()
    val remote: StringExpr get() = self.remote()
    val normalTargetCommitId: StringExpr get() = self.normalTargetCommitId()
}

object JjTemplateScopes {
    fun commit(): CommitScope = CommitScope(commitExpr("self"))

    fun annotation(): AnnotationScope = AnnotationScope(annotationLineExpr("self"))

    fun bookmarkRef(): BookmarkRefScope = BookmarkRefScope(commitRefExpr("self"))
}
