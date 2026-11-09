package io.github.ctjerm.lowlight.dimmer

@JvmInline
value class DimmerLevel private constructor(val percent: Int) {
    val opacity: Float
        get() = percent / 100f

    companion object {
        const val MIN_PERCENT = 5
        const val MAX_PERCENT = 75
        const val DEFAULT_PERCENT = 45
        const val STEP_PERCENT = 5

        fun from(percent: Int): DimmerLevel {
            val clamped = percent.coerceIn(MIN_PERCENT, MAX_PERCENT)
            val rounded = ((clamped + STEP_PERCENT / 2) / STEP_PERCENT) * STEP_PERCENT
            return DimmerLevel(rounded.coerceIn(MIN_PERCENT, MAX_PERCENT))
        }
    }
}
