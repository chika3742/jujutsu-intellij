package net.chikach.jujutsuintellij.repo.model

fun formatAuthor(authorName: String, authorEmail: String): String = when {
    authorName.isNotBlank() && authorEmail.isNotBlank() -> "$authorName <$authorEmail>"
    authorName.isNotBlank() -> authorName
    else -> authorEmail
}