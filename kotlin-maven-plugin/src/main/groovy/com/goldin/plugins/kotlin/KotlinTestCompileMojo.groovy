package com.goldin.plugins.kotlin

import org.jfrog.maven.annomojo.annotations.MojoGoal
import org.jfrog.maven.annomojo.annotations.MojoParameter
import org.jfrog.maven.annomojo.annotations.MojoPhase
import org.jfrog.maven.annomojo.annotations.MojoRequiresDependencyResolution


/**
 * Plugin compiling Kotlin test sources.
 */
@MojoGoal ( 'testCompile' )
@MojoPhase ( 'test-compile' )
@MojoRequiresDependencyResolution( 'test' )
class KotlinTestCompileMojo extends KotlinBaseMojo
{
    /**
     * Test compilation source directories.
     */
    @MojoParameter( defaultValue = '${project.testCompileSourceRoots}' )
    public List<String> sources
    public List<String> sources () { sources }


    /**
     * Project test classpath.
     */
    @MojoParameter( defaultValue = '${project.testClasspathElements}' )
    public List<String> classpath
    public List<String> classpath () { classpath }


    /**
     * Directory for compiled test classes.
     */
    @MojoParameter( defaultValue = '${project.build.testOutputDirectory}' )
    public String output
    public String output () { output }


    boolean isTest () { true }
}
