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
    public List<String> classpathElements

    /**
     * Directory for compiled classes.
     */
    @MojoParameter( defaultValue = '${project.build.outputDirectory}' )
    public String output

    @MojoParameter ( required = false )
    public String jar

    @MojoParameter ( required = false )
    public String stdlib


    @MojoParameter ( required = false )
    public String module

    @MojoParameter ( required = false )
    public boolean includeRuntime = true


    @Override
    void doExecute ()
    {
        addKotlinRuntime()

        final compiler   = new BytecodeCompiler()
        final sourceDirs = ( src ? [ new File( src ).with { file ? parent : delegate } ] : sources )

        sourceDirs.each {
            String sourceDir ->

            verify().directory( new File( sourceDir ))
            verify().directory( file().mkdirs( new File( output )))
            if ( stdlib ) { verify().file( new File( stdlib )) }

            log.info( "Compiling [$sourceDir] => [$output]" )
            compiler.sourcesToDir( sourceDir, output, stdlib, classpathElements as String[] )
        }
    }


    /**
     * Add Kotlin dependencies to plugin's classloader.
     */
    private addKotlinRuntime()
    {
        IvyMojo ivyMojo    = new IvyMojo()
        ivyMojo.project    = this.project
        ivyMojo.repoSystem = repoSystem
        ivyMojo.ivy        = new File( this.class.classLoader.getResource( 'ivy.xml' ).toString())
        ivyMojo.ivyconf    = new File( this.class.classLoader.getResource( 'ivyconf.xml' ).toString())
        ivyMojo.scope      = 'plugin-runtime'
        ivyMojo.doExecute()
    }
}
