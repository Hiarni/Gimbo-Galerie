package de.luma.gallery.localization

import android.content.res.Resources
import androidx.annotation.StringRes
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

interface ResourceFailure {
    val messageRes: Int
    val arguments: Array<out Any>
}

class MessageArgumentException(@StringRes override val messageRes: Int, vararg args: Any) : IllegalArgumentException(), ResourceFailure {
    override val arguments = args
}

class MessageStateException(@StringRes override val messageRes: Int, vararg args: Any) : IllegalStateException(), ResourceFailure {
    override val arguments = args
}

@OptIn(ExperimentalContracts::class)
fun requireMessage(condition: Boolean, @StringRes messageRes: Int, vararg args: Any) {
    contract { returns() implies condition }
    if (!condition) throw MessageArgumentException(messageRes, *args)
}

@OptIn(ExperimentalContracts::class)
fun checkMessage(condition: Boolean, @StringRes messageRes: Int, vararg args: Any) {
    contract { returns() implies condition }
    if (!condition) throw MessageStateException(messageRes, *args)
}

fun messageError(@StringRes messageRes: Int, vararg args: Any): Nothing = throw MessageStateException(messageRes, *args)


fun Throwable.userMessage(resources: Resources, @StringRes fallback: Int): String =
    if (this is ResourceFailure) resources.getString(messageRes, *arguments) else resources.getString(fallback)
