できます。**おすすめ設計は「JJのテンプレート文字列を直接組み立てるのではなく、Kotlin側で型付きASTを持ち、それを `jj` のテンプレート式へコンパイルし、必要に応じて `json(...)` を使ってJSON出力へ寄せる」方式**です。Jujutsu にはテンプレート言語自体に `json(value: Serialize) -> String` があり、`Serialize` 可能な値をJSON化できます。また、文字列には `.escape_json()` もあるので、機械可読なJSON/JSONLを安全に生成できます。 ([jj-vcs.github.io](https://jj-vcs.github.io/jj/latest/templates/?utm_source=openai))

以下に、**Kotlinでのラッパー設計案**を示します。

---

# 目標

作りたいものを次の3層に分けると設計しやすいです。

1. **Kotlin DSL / AST層**
    - Kotlinコードでテンプレート式を安全に組み立てる
    - 文字列連結ベースではなく型付き表現にする

2. **JJ Template Compiler層**
    - ASTを `jj -T '...'` に渡せるテンプレート文字列へ変換
    - 文字列リテラルやJSON文字列のエスケープを責務分離

3. **Execution / Decoding層**
    - `jj` コマンド実行
    - `stdout` を JSON / JSONL として `kotlinx.serialization` でデコード

---

# Jujutsu仕様を踏まえた設計方針

Jujutsuテンプレート言語には以下の重要点があります。

- `json(value)` で `Serialize` 型をJSON化できる ([jj-vcs.github.io](https://jj-vcs.github.io/jj/latest/templates/?utm_source=openai))
- `String.escape_json()` を使うと手書きJSON構築がしやすい ([jj-vcs.github.io](https://jj-vcs.github.io/jj/latest/templates/?utm_source=openai))
- すべての値がJSON化できるわけではなく、**`Serialize` 型だけ**が対象 ([jj-vcs.github.io](https://jj-vcs.github.io/jj/latest/templates/?utm_source=openai))
- `Template` と `Serialize` は別概念なので、**表示向けテンプレート**と**機械可読JSON向けテンプレート**を型で分けた方が安全**です** ([jj-vcs.github.io](https://jj-vcs.github.io/jj/latest/templates/?utm_source=openai))

このため、Kotlin側でも少なくとも次を分離するとよいです。

- **TemplateExpr**: 表示テンプレート
- **SerializableExpr**: `json(...)` に渡せる値
- **StringExpr / BoolExpr / IntExpr / ListExpr / CommitExpr** などの型付き式

---

# 推奨アーキテクチャ

## 1. 公開APIイメージ

利用者には次のように見せます。

```kotlin
val query = JjTemplates.commitJson {
    obj(
        "id" to commitId.short(12),
        "changeId" to changeId.short(12),
        "description" to description,
        "author" to obj(
            "name" to author.name(),
            "email" to author.email()
        ),
        "parents" to parents.map { it.commitId().short(12) }
    )
}

val result: List<CommitSummary> = jjClient.logJson(query)
```

またはJSONL向けに:

```kotlin
val template = commitJsonLine {
    obj(
        "id" to commitId.short(),
        "empty" to empty,
        "bookmarks" to bookmarks
    )
}
```

ここで内部的には、Jujutsuの `json(value)` が使えるケースはそれを優先し、**オブジェクトを手で構築する場合は `"{" ++ ... ++ "}"` ではなく、JSON専用ビルダが `.escape_json()` を自動適用**する設計にします。`json(value)` は便利ですが、任意キー名のカスタムオブジェクト生成をそのまま自然に表現できるとは限らないため、**「Serialize直列化モード」と「JSONオブジェクト構築モード」の二本立て**が実用的です。これは仕様上、`json()` と `escape_json()` の両方が提供されていることとも整合的です。 ([jj-vcs.github.io](https://jj-vcs.github.io/jj/latest/templates/?utm_source=openai))

---

# 2. Kotlinの型モデル

## コア式

```kotlin
sealed interface JjExpr {
    fun render(ctx: RenderContext = RenderContext()): String
}

sealed interface TemplateExpr : JjExpr
sealed interface SerializableExpr : JjExpr
sealed interface StringExpr : TemplateExpr, SerializableExpr
sealed interface BoolExpr : TemplateExpr, SerializableExpr
sealed interface IntExpr : TemplateExpr, SerializableExpr
```

Jujutsuでは `String`, `Boolean`, `Integer` は `Serialize` 可能です。 ([jj-vcs.github.io](https://jj-vcs.github.io/jj/latest/templates/?utm_source=openai))

## ドメイン型

```kotlin
sealed interface CommitExpr : JjExpr, SerializableExpr
sealed interface SignatureExpr : JjExpr, SerializableExpr
sealed interface TimestampExpr : JjExpr, SerializableExpr
sealed interface ListExpr<T : JjExpr> : JjExpr
sealed interface ListTemplateExpr : TemplateExpr
```

`Commit`, `Signature`, `Timestamp` などはJujutsuドキュメント上 `Serialize` 可能です。 ([jj-vcs.github.io](https://jj-vcs.github.io/jj/latest/templates/?utm_source=openai))

---

# 3. ASTノード例

```kotlin
data class Identifier(val name: String) : JjExpr {
    override fun render(ctx: RenderContext) = name
}

data class StringLiteral(val value: String) : StringExpr {
    override fun render(ctx: RenderContext): String = ctx.quoteString(value)
}

data class IntLiteral(val value: Int) : IntExpr {
    override fun render(ctx: RenderContext) = value.toString()
}

data class BoolLiteral(val value: Boolean) : BoolExpr {
    override fun render(ctx: RenderContext) = if (value) "true" else "false"
}

data class MethodCall<T : JjExpr>(
    val target: JjExpr,
    val method: String,
    val args: List<JjExpr> = emptyList()
) : JjExpr {
    override fun render(ctx: RenderContext): String =
        "${target.render(ctx)}.$method(${args.joinToString(", ") { it.render(ctx) }})"
}

data class FunctionCall(
    val name: String,
    val args: List<JjExpr>
) : JjExpr {
    override fun render(ctx: RenderContext): String =
        "$name(${args.joinToString(", ") { it.render(ctx) }})"
}
```

---

# 4. 型付き拡張API

JujutsuのテンプレートメソッドをKotlin拡張で隠蔽します。

```kotlin
fun CommitExpr.commitId(): StringExpr = MethodCall(this, "commit_id") as StringExpr
fun CommitExpr.changeId(): StringExpr = MethodCall(this, "change_id") as StringExpr
fun CommitExpr.description(): StringExpr = MethodCall(this, "description") as StringExpr
fun CommitExpr.author(): SignatureExpr = MethodCall(this, "author") as SignatureExpr
fun CommitExpr.parents(): ListExpr<CommitExpr> = MethodCall(this, "parents") as ListExpr<CommitExpr>

fun SignatureExpr.name(): StringExpr = MethodCall(this, "name") as StringExpr
fun SignatureExpr.email(): StringExpr = MethodCall(this, "email") as StringExpr

fun StringExpr.short(length: Int? = null): StringExpr =
    if (length == null) MethodCall(this, "short") as StringExpr
    else MethodCall(this, "short", listOf(IntLiteral(length))) as StringExpr
```

実装上 `as` は避けたいので、本当はジェネリックな `TypedExpr<T>` に寄せた方がきれいです。

---

# 5. より安全な型システム案

より良い形は phantom type を使うことです。

```kotlin
sealed interface JjType
object TTemplate : JjType
object TString : JjType
object TInt : JjType
object TBool : JjType
object TCommit : JjType
object TSignature : JjType
object TTimestamp : JjType
object TSerializable : JjType
data class TList<T : JjType>(val inner: T) : JjType

class Expr<T : JjType>(private val node: Node) {
    fun render(ctx: RenderContext = RenderContext()): String = node.render(ctx)
}
```

ただしKotlinの型だけで「`Serialize`可能性」を完全表現するのは少し面倒なので、実務上は次で十分です。

- `Expr<T>`
- `SerializableMarker`
- `TemplateMarker`

---

# 6. JSONラッパーの肝

## A. `json(value)` をそのまま使うモード

Jujutsuの `Serialize` 値に対しては最も安全です。 ([jj-vcs.github.io](https://jj-vcs.github.io/jj/latest/templates/?utm_source=openai))

```kotlin
fun json(value: SerializableExpr): StringExpr =
    FunctionCall("json", listOf(value)) as StringExpr
```

��:

```kotlin
val tmpl = json(Identifier("self") as CommitExpr)
```

これは commit 全体をJSONにできます。  
ただし**出力スキーマを厳密制御したい場合**には過剰です。

---

## B. カスタムJSONオブジェクト構築モード

Jujutsuのテンプレート式でJSONオブジェクトを手組みします。ただし利用者に文字列連結させません。

```kotlin
sealed interface JsonValueBuilder {
    fun toTemplate(): TemplateExpr
}

data class JsonString(val expr: StringExpr) : JsonValueBuilder
data class JsonNumber(val expr: IntExpr) : JsonValueBuilder
data class JsonBoolean(val expr: BoolExpr) : JsonValueBuilder
data class JsonObject(val fields: List<Pair<String, JsonValueBuilder>>) : JsonValueBuilder
data class JsonArray(val items: List<JsonValueBuilder>) : JsonValueBuilder
```

レンダリング時に:

- キーはKotlin側でJSONエスケープ
- 文字列値は `expr.escape_json()`
- 数値/真偽値はそのまま
- nullable は `if(..., ..., "null")` パターンを提供

Jujutsu docs では `String.escape_json()` がJSON/JSONL生成に有用と明示されています。 ([jj-vcs.github.io](https://jj-vcs.github.io/jj/latest/templates/?utm_source=openai))

---

# 7. 実装例: JSON DSL

```kotlin
class JsonObjectBuilder {
    private val fields = mutableListOf<Pair<String, JsonValueBuilder>>()

    infix fun String.to(value: JsonValueBuilder) {
        fields += this to value
    }

    fun build(): JsonObject = JsonObject(fields)
}

fun obj(init: JsonObjectBuilder.() -> Unit): JsonObject =
    JsonObjectBuilder().apply(init).build()

fun str(expr: StringExpr) = JsonString(expr)
fun num(expr: IntExpr) = JsonNumber(expr)
fun bool(expr: BoolExpr) = JsonBoolean(expr)
fun arr(vararg values: JsonValueBuilder) = JsonArray(values.toList())
```

利用例:

```kotlin
val jsonTemplate = obj {
    "id" to str(commitId)
    "description" to str(description)
    "empty" to bool(empty)
}
```

---

# 8. レンダラ設計

```kotlin
class RenderContext {
    fun quoteString(value: String): String {
        val escaped = buildString {
            value.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
        }
        return "\"$escaped\""
    }
}
```

JSONビルダのレンダラ:

```kotlin
fun JsonValueBuilder.render(ctx: RenderContext = RenderContext()): String = when (this) {
    is JsonString -> "${expr.render(ctx)}.escape_json()"
    is JsonNumber -> expr.render(ctx)
    is JsonBoolean -> expr.render(ctx)
    is JsonObject -> fields.joinToString(
        prefix = "'{' ++ ",
        separator = " ++ ',' ++ ",
        postfix = " ++ '}'"
    ) { (k, v) ->
        val key = ctx.quoteString(k)
        "'$key:' ++ ${v.render(ctx)}"
    }
    is JsonArray -> items.joinToString(
        prefix = "'[' ++ ",
        separator = " ++ ',' ++ ",
        postfix = " ++ ']'"
    ) { it.render(ctx) }
}
```

ただしこのままだと引用符が二重化しやすいので、実際には**「JSON断片は常にJJ Template式として返す」専用クラス**を作る方が安全です。

---

# 9. より実用的な最終API

## テンプレート生成

```kotlin
object JjTemplates {
    fun commitJsonLine(init: CommitScope.() -> JsonObject): String {
        val scope = CommitScope()
        return scope.init().toTemplateExpr().render()
    }
}
```

## スコープ

```kotlin
class CommitScope {
    val self: CommitExpr = CommitVar("self")
    val commitId: StringExpr get() = self.commitId()
    val changeId: StringExpr get() = self.changeId()
    val description: StringExpr get() = self.description()
    val author: SignatureExpr get() = self.author()
    val parents: ListExpr<CommitExpr> get() = self.parents()
}
```

---

# 10. 実行層

Kotlinで `jj` を叩くクライアント層です。

```kotlin
class JjClient(
    private val workingDirectory: Path,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    fun <T> logJsonLines(
        template: String,
        deserializer: KSerializer<T>
    ): List<T> {
        val process = ProcessBuilder(
            "jj", "log", "--no-graph", "-T", template
        )
            .directory(workingDirectory.toFile())
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readLines()
        val exit = process.waitFor()
        require(exit == 0) { "jj failed: ${output.joinToString("\n")}" }

        return output.filter { it.isNotBlank() }
            .map { json.decodeFromString(deserializer, it) }
    }
}
```

---

# 11. DTOとの接続

```kotlin
@kotlinx.serialization.Serializable
data class CommitSummary(
    val id: String,
    val changeId: String,
    val description: String,
    val author: Author,
    val parents: List<String>
)

@kotlinx.serialization.Serializable
data class Author(
    val name: String,
    val email: String
)
```

---

# 12. 実用サンプル

```kotlin
val template = JjTemplates.commitJsonLine {
    obj {
        "id" to str(commitId.short(12))
        "changeId" to str(changeId.short(12))
        "description" to str(description)
        "author" to obj {
            "name" to str(author.name())
            "email" to str(author.email())
        }
        "parents" to jsonArrayFrom(parents) { parent ->
            str(parent.commitId().short(12))
        }
    }
}

val commits = jjClient.logJsonLines(template, CommitSummary.serializer())
```

---

# 13. 重要な設計判断

## 判断1: 文字列ベースAPIは避ける
悪い例:

```kotlin
jj.logTemplate(""" "{" ++ "\"id\":" ++ commit_id.escape_json() ++ "}" """)
```

これは壊れやすいです。  
**JSON DSLで隠すべき**です。

## 判断2: `Serialize` を優先利用
Jujutsuには `json(value)` があるので、**そのままシリアライズ可能な値はなるべくJJ側に任せる**のが安全です。 ([jj-vcs.github.io](https://jj-vcs.github.io/jj/latest/templates/?utm_source=openai))

例:
- commit 全体
- signature
- timestamp
- boolean / integer / string

## 判断3: カスタムオブジェクトはKotlin側DSLで作る
JJテンプレート言語は「表示テンプレート」であって、任意構造JSONを定義するための専用言語ではありません。  
そのため、**Kotlin側でオブジェクトDSLを持つ**のが妥当です。

## 判断4: JSONLを標準にする
`jj log` の1コミット1行出力にすると扱いやすいです。  
大きな配列JSONを1回で返すより、**JSON Lines** の方がストリーミング処理や失敗切り分けに向きます。

---

# 14. 最小実装で十分な範囲

最初は以下だけ実装するとよいです。

- プリミティブ型
    - String / Int / Bool
- CommitScope
    - `commit_id`, `change_id`, `description`, `author`, `parents`
- SignatureScope
    - `name`, `email`
- JSON DSL
    - object
    - array
    - string/number/bool
- `jj log --no-graph -T ...`
- `kotlinx.serialization` での decode

これだけでかなり使えます。

---

# 15. 将来拡張

あとで追加しやすいです。

- `if(...)` のラッパー
- `coalesce(...)`
- `map/filter/any/all`
- `Timestamp.format()`
- `bookmarks`, `tags`, `working_copies`
- `jj op log` 用 `OperationScope`
- `files()` や diff系のJSON化
- schema versioning

---

# 16. 推奨パッケージ構成

```text
com.example.jj
├─ api/
│  ├─ JjClient.kt
│  └─ JjTemplates.kt
├─ ast/
│  ├─ Expr.kt
│  ├─ Literals.kt
│  ├─ Calls.kt
│  └─ Types.kt
├─ dsl/
│  ├─ CommitScope.kt
│  ├─ JsonDsl.kt
│  └─ Functions.kt
├─ render/
│  ├─ RenderContext.kt
│  └─ TemplateRenderer.kt
└─ model/
   └─ CommitSummary.kt
```

---

# 17. 設計の結論

**ベストプラクティスは次です。**

- Kotlinで**型付きAST/DSL**を提供
- JJテンプレート文字列は**最終段でレンダリング**
- JSON出力は
    - まず `json(Serialize)` を優先
    - 任意形状は `escape_json()` を内部利用した JSON DSL で生成
- 実行結果は `kotlinx.serialization` でDTOへ変換

この形なら、**安全性・保守性・拡張性のバランスがよい**です。

必要なら次に、
1. **この設計をそのまま Kotlin の実コードとして雛形実装**
2. **Gradleプロジェクト構成付きでファイル単位に出力**
3. **`jj log` 向けの最小MVPをコンパイル可能なコードで提示**
   できます。