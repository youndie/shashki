// **No build logic here, and one job that cannot be done anywhere else.** A plugin a module names
// without a version has to already be on the build's plugin classpath, and this block is what puts
// it there. The Kotlin plugins are declared for the same reason: the multiplatform and JVM plugins
// land on the classpath once, so a module asking for a *versioned* one fails with "already on the
// classpath with an unknown version" — a message about neither the plugin nor the module.
//
// What is deliberately absent is shared configuration. Every module states its own target list,
// because research §1.6 found four libraries in this stack whose published targets are not what the
// brief assumed; a target inherited from here would be a target nobody argued for.
plugins {
    alias(wip.plugins.kotlinJvm) apply false
    alias(wip.plugins.kotlinMultiplatform) apply false
    alias(wip.plugins.kotlinSerialization) apply false
    alias(wip.plugins.composeMultiplatform) apply false
    alias(wip.plugins.composeCompiler) apply false
    alias(wip.plugins.ksp) apply false
    alias(libs.plugins.sborkaJvm) apply false
    alias(libs.plugins.sborkaKmp) apply false
    alias(libs.plugins.sborkaLint) apply false
    alias(libs.plugins.viddik) apply false
}
