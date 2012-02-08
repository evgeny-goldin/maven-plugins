package com.goldin.plugins.kotlin

import org.jetbrains.jet.compiler.CompileEnvironment
import org.jetbrains.jet.compiler.CompileEnvironmentException

import com.goldin.plugins.common.BaseGroovyMojo3
import org.jfrog.maven.annomojo.annotations.MojoGoal
import org.jfrog.maven.annomojo.annotations.MojoPhase


/**
 * Plugin compiling Kotlin sources
 */
@MojoGoal ( 'kotlinc' )
@MojoPhase ( 'compile' )
class KotlinMojo extends BaseGroovyMojo3
{

    @Override
    void doExecute ()
    {
    }
}
