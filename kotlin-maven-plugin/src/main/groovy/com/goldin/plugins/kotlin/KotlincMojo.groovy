package com.goldin.plugins.kotlin

import static com.goldin.plugins.common.GMojoUtils.*
import com.goldin.plugins.common.BaseGroovyMojo3
import org.jetbrains.jet.buildtools.core.BytecodeCompiler
import org.jfrog.maven.annomojo.annotations.MojoGoal
import org.jfrog.maven.annomojo.annotations.MojoParameter
import org.jfrog.maven.annomojo.annotations.MojoPhase
import com.goldin.plugins.ivy.IvyMojo


/**
 * Plugin compiling Kotlin sources
 */
@MojoGoal ( 'kotlinc' )
@MojoPhase ( 'compile' )
class KotlincMojo extends BaseGroovyMojo3
{
    /**
     * Single compilation source directory.
     */
    @MojoParameter ( required = false )
    public String src

    /**
     * Source directories containing the sources to be compiled.
     */
    @MojoParameter( defaultValue = '${project.compileSourceRoots}' )
    public List<String> sources

    /**
     * Project classpath.
     */
    @MojoParameter( defaultValue = '${project.compileClasspathElements}' )
    public String[] classpath

    /**
     * Directory for compiled classes.
     */
    @MojoParameter( defaultValue = '${project.build.outputDirectory}' )
    public String output

    /**
     * Destination jar.
     */
    @MojoParameter ( required = false )
    public String jar

    /**
     * "stdlib" kotlinc argument (path to runtime.jar)
     */
    @MojoParameter ( required = false )
    public String stdlib

    /**
     * Kotlin compilation module, as alternative to source files or folders.
     */
    @MojoParameter ( required = false )
    public String module

    /**
     * "-includeRuntime" kotlinc argument, whether Kotlin runtime library is included in jar created.
     */
    @MojoParameter ( required = false )
    public boolean includeRuntime = false


    @Override
    void doExecute ()
    {
        addKotlinToRuntime()

        final compiler = new BytecodeCompiler()
        if ( stdlib         ) { verify().file( new File( stdlib )) }
        if ( includeRuntime ) { assert ( module || jar ), "<includeRuntime> parameter can only be used with <module> source or <jar> destination" }

        if ( module )
        {   /**
             * Compiling module.
             */

            verify().file( new File( module ))
            log.info( jar ? "Compiling [$module] => [$jar]" : "Compiling [$module]" )
            compiler.moduleToJar( module, jar, includeRuntime, stdlib, classpath )
        }
        else
        {   /**
             * Compiling sources.
             */

            final  sourceLocations  = ( src ? [ src ] : this.sources )
            final  destination      = ( jar ?: output )
            assert destination // 'output' is always defined by Maven

            sourceLocations.each {
                String sourceLocation -> // May be a Kotlin file or sources directory

                verify().exists( new File( sourceLocation ))
                if ( ! jar ) { verify().directory( file().mkdirs( new File( output ))) }

                log.info( "Compiling [$sourceLocation] => [$destination]" )

                if ( jar )
                {
                    compiler.sourcesToJar( sourceLocation, jar, includeRuntime, stdlib, classpath )
                }
                else
                {
                    compiler.sourcesToDir( sourceLocation, output, stdlib, classpath )
                }
            }
        }
    }


    /**
     * Adds Kotlin dependencies to plugin's classloader.
     */
    private addKotlinToRuntime ()
    {
        IvyMojo ivyMojo    = new IvyMojo()
        ivyMojo.project    = project
        ivyMojo.log        = log
        ivyMojo.session    = session
        ivyMojo.repoSystem = repoSystem
        ivyMojo.ivy        = this.class.classLoader.getResource( 'ivy.xml'     ).toString()
        ivyMojo.ivyconf    = this.class.classLoader.getResource( 'ivyconf.xml' ).toString()
        ivyMojo.scope      = 'plugin-runtime' // This injects all Ivy dependencies into plugin's runtime classloader
        ivyMojo.verbose    = false            // Do not log anything
        ivyMojo.execute()
    }
}
