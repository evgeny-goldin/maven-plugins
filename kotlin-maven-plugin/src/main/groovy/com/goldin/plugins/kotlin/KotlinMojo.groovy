package com.goldin.plugins.kotlin

import com.goldin.plugins.common.BaseGroovyMojo3
import org.jetbrains.jet.buildtools.core.BytecodeCompiler
import org.jfrog.maven.annomojo.annotations.MojoGoal
import org.jfrog.maven.annomojo.annotations.MojoParameter
import org.jfrog.maven.annomojo.annotations.MojoPhase


/**
 * Plugin compiling Kotlin sources
 */
@MojoGoal ( 'kotlinc' )
@MojoPhase ( 'compile' )
class KotlinMojo extends BaseGroovyMojo3
{
    /**
     * Source directories containing the sources to be compiled.
     */
    @MojoParameter( defaultValue = '${project.compileSourceRoots}' )
    public List<String> sourceRoots

    /**
     * Project classpath.
     */
    @MojoParameter( defaultValue = '${project.compileClasspathElements}' )
    public List<String> classpathElements

    /**
     * Directory for compiled classes.
     */
    @MojoParameter( defaultValue = '${project.build.outputDirectory}' )
    public File output

    @MojoParameter ( required = false )
    public File jar

    @MojoParameter ( required = false )
    public File stdlib

    @MojoParameter ( required = false )
    public File src

    @MojoParameter ( required = false )
    public File module

    @MojoParameter ( required = false )
    public boolean includeRuntime = true


    @Override
    void doExecute ()
    {
        final compiler = new BytecodeCompiler()

        sourceRoots.each {
            String sourceDir ->
            log.info( "Compiling [$sourceDir] => [$output]" )
            compiler.sourcesToDir( sourceDir, output, stdlib, classpathElements as String[] )
        }
    }
}
