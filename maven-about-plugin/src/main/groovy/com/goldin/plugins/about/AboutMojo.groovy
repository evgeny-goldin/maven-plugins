package com.goldin.plugins.about

import org.jfrog.maven.annomojo.annotations.MojoGoal
import org.jfrog.maven.annomojo.annotations.MojoPhase
import com.goldin.plugins.common.BaseGroovyMojo
import org.jfrog.maven.annomojo.annotations.MojoParameter
import com.goldin.gcommons.GCommons

/**
 * Updates files specified with "about" build metadata
 */
@MojoGoal( 'create-about' )
@MojoPhase( 'package' )
class AboutMojo extends BaseGroovyMojo
{

    @MojoParameter
    public String scm

    @MojoParameter
    public File directory

    @MojoParameter
    public File file

    @MojoParameter
    public String include
    
    @MojoParameter
    public String exclude


    public void setFile ( File file )
    {
        GCommons.verify().file( file )
        assert file.parentFile, "File [$file.canonicalPath] has no parent directory"

        this.directory = file.parentFile
        this.include   = file.name
    }


    /**
     * Splits the String specified.
     * @param s String to split
     * @return String specified split to tokens
     */
    def split( String s ) { s ? s.split( /,/ ).toList()*.trim().findAll{ it } : null }


    def content()
    {
        'aaaaaaaaaaa'
    }


    @Override
    void doExecute ()
    {
        def files    = GCommons.file().files( directory, split( include ?: '*.jar' ), split( exclude ))
        def tempFile = new File( outputDirectory, "about-${project.groupId}-${project.artifactId}-${project.version}.txt" )
        tempFile.write( content())

        for ( file in files )
        {
            new AntBuilder().zip( destfile: file.canonicalPath,
                                  update  : true ){
                zipfileset( file  : tempFile.canonicalPath,
                            prefix: 'META-INF' )
            }

            log.info( "[$file.canonicalPath] updated" )
        }
    }
}
