package com.goldin.plugins.ivy

import com.goldin.plugins.common.BaseGroovyMojo
import org.apache.maven.plugin.dependency.fromConfiguration.ArtifactItem
import org.gcontracts.annotations.Ensures
import org.gcontracts.annotations.Requires
import org.jfrog.maven.annomojo.annotations.MojoComponent
import org.jfrog.maven.annomojo.annotations.MojoGoal
import org.jfrog.maven.annomojo.annotations.MojoParameter
import org.jfrog.maven.annomojo.annotations.MojoPhase
import org.sonatype.aether.RepositorySystem


/**
 * Plugin that delegates artifacts resolving to Ivy, adds dependencies resolved to the Maven scope or
 * copies them to local directory.
 */
@MojoGoal ( 'ivy' )
@MojoPhase ( 'initialize' )
class IvyMojo extends BaseGroovyMojo
{
   /**
    * Ivy settings file: http://ant.apache.org/ivy/history/latest-milestone/settings.html.
    */
    @MojoParameter ( required = true )
    public File ivyconf

    /**
     * Ivy file: http://ant.apache.org/ivy/history/latest-milestone/ivyfile.html.
     */
    @MojoParameter ( required = false )
    public File ivy

    /**
     * Maven-style {@code <dependencies>}.
     */
    @MojoParameter ( required = false )
    public ArtifactItem[] dependencies

    /**
     * Maven scope to add the dependencies resolved to: "compile", "runtime", "test", etc.
     * Similar to Ivy's <cachepath>: http://ant.apache.org/ivy/history/latest-milestone/use/cachepath.html.
     */
    @MojoParameter ( required = false )
    public String scope

    /**
     * Directory to copy resolved dependencies to.
     */
    @MojoParameter ( required = false )
    public File dir

    /**
     * Aether components:
     * http://aether.sonatype.org/using-aether-in-maven-plugins.html
     * https://docs.sonatype.org/display/AETHER/Home
     */

    @MojoComponent
    public RepositorySystem repoSystem


    @Override
    @Requires({ ivyconf.file })
    void doExecute ()
    {
        // Ain't that cool to assign to private?!
        repoSystem.artifactResolver = new IvyArtifactResolver( repoSystem.artifactResolver, ivyconf )

        if ( scope || dir )
        {
            final dependencies = resolveDependencies( ivy, dependencies )
            if ( scope ){ addDependenciesToScope( scope, dependencies ) }
            if ( dir  ) { copyDependenciesToDir ( dir,   dependencies ) }
        }
    }


    /**
     * Resolves dependencies specified and retrieves their local paths.
     *
     * @param ivyFile      "ivy.xml" file
     * @param dependencies Maven-style dependencies
     * @return             local paths of dependencies resolved
     */
    @Requires({ ivyFile?.file || dependencies })
    @Ensures({ result })
    List<File> resolveDependencies( File ivyFile, ArtifactItem[] dependencies )
    {
        []
    }


    /**
     * Adds dependencies resolved to the scope specified.
     *
     * @param scope        Maven scope to add the dependecies to: "compile", "runtime", "test", etc.
     * @param dependencies dependencies to add to the scope
     */
    @Requires({ scope && dependencies && dependencies.every{ it.file } })
    void addDependenciesToScope ( String scope, List<File> dependencies )
    {
    }


    /**
     * Copies dependencies resolved to the directory specified.
     *
     * @param directory    directory to copy the dependencies to
     * @param dependencies dependencies to copy
     */
    @Requires({ directory && dependencies && dependencies.every{ it.file } })
    void copyDependenciesToDir ( File directory, List<File> dependencies )
    {
    }
}
