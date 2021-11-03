package me.racci.raccicore.utils.math

object MathUtils {

    fun multiplyByPercent(value: Double, percent: Double): Double {
        return (value * (percent / 100))
    }

    fun getMultiplierFromPercent(percent: Double): Double {
        return (percent / 100)
    }

}