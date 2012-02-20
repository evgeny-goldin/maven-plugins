package com.goldin.plugins.kotlin

import org.jfrog.maven.annomojo.annotations.MojoGoal
import org.jfrog.maven.annomojo.annotations.MojoParameter
import org.jfrog.maven.annomojo.annotations.MojoPhase
import org.jfrog.maven.annomojo.annotations.MojoRequiresDependencyResolution


/**
 * Plugin compiling Kotlin sources.
 */
@MojoGoal ( 'compile' )
@MojoPhase ( 'compile' )
@MojoRequiresDependencyResolution( 'compile' )
class KotlinCompileMojo extends KotlinBaseMojo
{
    /**
     * Compilation source directories.
     */
    @MojoParameter( defaultValue = '${project.compileSourceRoots}' )
    public List<String> sources
    public List<String> sources () { sources }


    /**
     * Project classpath.
     */
    @MojoParameter( defaultValue = '${project.compileClasspathElements}' )
    public List<String> classpath
    public List<String> classpath () { classpath }


    /**
     * Directory for compiled classes.
     */
    @MojoParameter( defaultValue = '${project.build.outputDirectory}' )
    public String output
    public String output () { output }
}
