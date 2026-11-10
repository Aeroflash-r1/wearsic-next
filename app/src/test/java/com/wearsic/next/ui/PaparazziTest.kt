package com.wearsic.next.ui

/**
 * JUnit category marking every Paparazzi snapshot test. Paparazzi (layoutlib)
 * and tests that load other native libraries in the same JVM target break
 * each other — see cashapp/paparazzi#1979. The build filter in
 * app/build.gradle.kts includes/excludes this category so both worlds coexist.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class PaparazziTest
