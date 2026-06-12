package net.chikach.jujutsuintellij.cli.template

sealed interface JjTemplateExpr {
    fun render(ctx: RenderContext = RenderContext): String
}

sealed interface TemplateExpr : JjTemplateExpr

sealed interface SerializableExpr : JjTemplateExpr

sealed interface SerializableTemplateExpr : TemplateExpr, SerializableExpr

sealed interface StringExpr : SerializableTemplateExpr

sealed interface BooleanExpr : SerializableTemplateExpr

sealed interface TimestampExpr : SerializableTemplateExpr

sealed interface CommitExpr : SerializableExpr

sealed interface CommitRefExpr : SerializableExpr

sealed interface TreeEntryExpr : SerializableExpr

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
private class RenderedBooleanExpr(source: String) : RenderedExpr(source), BooleanExpr
private class RenderedTimestampExpr(source: String) : RenderedExpr(source), TimestampExpr
private class RenderedCommitExpr(source: String) : RenderedExpr(source), CommitExpr
private class RenderedCommitRefExpr(source: String) : RenderedExpr(source), CommitRefExpr
private class RenderedTreeEntryExpr(source: String) : RenderedExpr(source), TreeEntryExpr
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

fun commitExpr(source: String): CommitExpr = RenderedCommitExpr(source)

fun commitRefExpr(source: String): CommitRefExpr = RenderedCommitRefExpr(source)

fun treeEntryExpr(source: String): TreeEntryExpr = RenderedTreeEntryExpr(source)

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

fun <T: JjTemplateExpr> ListExpr<T>.filter(lambda: LambdaExpr<T, BooleanExpr>): ListExpr<T> =
    RenderedListExpr(methodCall(this, "filter", lambda))

/** Logical negation: `!x`. */
fun BooleanExpr.not(): BooleanExpr = RenderedBooleanExpr("!${render()}")

/** Logical conjunction: `x && y`. */
fun BooleanExpr.and(other: BooleanExpr): BooleanExpr = RenderedBooleanExpr("${render()} && ${other.render()}")

/** Inequality comparison: `x != y`. */
fun StringExpr.notEquals(other: StringExpr): BooleanExpr = RenderedBooleanExpr("${render()} != ${other.render()}")

/** jj template concatenation: `x ++ y`. */
fun StringExpr.concat(other: StringExpr): StringExpr = RenderedStringExpr("${render()} ++ ${other.render()}")

fun CommitExpr.commitId(): SerializableTemplateExpr = serializableTemplateExpr(methodCall(this, "commit_id"))

fun CommitExpr.changeId(): SerializableTemplateExpr = serializableTemplateExpr(methodCall(this, "change_id"))

fun CommitExpr.description(): StringExpr = RenderedStringExpr(methodCall(this, "description"))

fun CommitExpr.author(): SignatureExpr = RenderedSignatureExpr(methodCall(this, "author"))

fun CommitExpr.parents(): ListExpr<CommitExpr> = RenderedListExpr(methodCall(this, "parents"))

fun CommitExpr.root(): BooleanExpr = RenderedBooleanExpr(methodCall(this, "root"))

fun CommitExpr.bookmarks(): ListExpr<SerializableTemplateExpr> = RenderedListExpr<SerializableTemplateExpr>(methodCall(this, "bookmarks"))
    .map(lambda(::commitRefExpr) { it.name() })

/** Names of all local tags pointing to the commit (`self.local_tags()`). */
fun CommitExpr.localTags(): ListExpr<SerializableTemplateExpr> = RenderedListExpr<SerializableTemplateExpr>(methodCall(this, "local_tags"))
    .map(lambda(::commitRefExpr) { it.name() })

fun TreeEntryExpr.path(): SerializableTemplateExpr =
    serializableTemplateExpr(methodCall(this, "path"))

/**
 * `self.conflicted_files().map(|p| stringify(p.path()))` — yields a list of path strings.
 * `stringify` flattens jj's `RepoPath` to its string form so `serialized(...)` renders it
 * as a JSON array of plain strings via jj's `json()` function.
 */
fun CommitExpr.conflictedFilePaths(): ListExpr<SerializableTemplateExpr> =
    listExpr<TreeEntryExpr>(methodCall(this, "conflicted_files"))
        .map(lambda(::treeEntryExpr) { stringify(it.path()) })

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

fun CommitRefExpr.name(): StringExpr = RenderedStringExpr(methodCall(this, "name"))

fun CommitRefExpr.remote(): StringExpr = RenderedStringExpr(methodCall(this, "remote"))

/** True when this remote ref is tracked by a local ref of the same name. */
fun CommitRefExpr.tracked(): BooleanExpr = RenderedBooleanExpr(methodCall(this, "tracked"))

fun CommitExpr.remoteBookmarks(): ListExpr<CommitRefExpr> =
    RenderedListExpr(methodCall(this, "remote_bookmarks"))

/** `name@remote` strings for the commit's untracked remote bookmarks (excluding the `git` pseudo-remote). */
fun CommitExpr.untrackedRemoteBookmarkLabels(): ListExpr<SerializableTemplateExpr> =
    remoteBookmarkLabels(tracked = false)

/** `name@remote` strings for the commit's tracked remote bookmarks (excluding the `git` pseudo-remote). */
fun CommitExpr.trackedRemoteBookmarkLabels(): ListExpr<SerializableTemplateExpr> =
    remoteBookmarkLabels(tracked = true)

/**
 * `self.remote_bookmarks().filter(|p| <tracked> && p.remote() != "git").map(|p| stringify(p.name() ++ "@" ++ p.remote()))`
 * — yields a list of `name@remote` strings for the commit's remote bookmarks whose tracking state
 * matches [tracked], excluding the internal `git` pseudo-remote. Each element is wrapped in
 * `stringify` so it is a serializable string (the `++` concatenation alone is a non-serializable
 * template). Pair with `serialized(...)` to render a JSON string array.
 */
private fun CommitExpr.remoteBookmarkLabels(tracked: Boolean): ListExpr<SerializableTemplateExpr> =
    remoteBookmarks()
        .filter(lambda(::commitRefExpr) { ref ->
            val trackedPredicate = if (tracked) ref.tracked() else ref.tracked().not()
            trackedPredicate.and(ref.remote().notEquals(literal("git")))
        })
        .map(lambda(::commitRefExpr) { ref ->
            stringify(ref.name().concat(literal("@")).concat(ref.remote()))
        })

/**
 * Renders the commit id of `normal_target()` as a JSON value: a quoted commit id when present,
 * or the literal `null` when the ref is in a conflicted state (no normal target).
 * Pair with [rawJson] to embed in a JSON object.
 */
fun CommitRefExpr.normalTargetCommitIdJson(): TemplateExpr {
    val src = render()
    return templateExpr(
        "if($src.normal_target(), json($src.normal_target().commit_id()), \"null\")"
    )
}
