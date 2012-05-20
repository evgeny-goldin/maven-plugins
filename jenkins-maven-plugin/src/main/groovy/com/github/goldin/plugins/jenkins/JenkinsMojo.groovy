package com.github.goldin.plugins.jenkins

import static com.github.goldin.plugins.common.GMojoUtils.*
import com.github.goldin.plugins.common.BaseGroovyMojo
import com.github.goldin.plugins.jenkins.markup.ConfigMarkup
import org.gcontracts.annotations.Ensures
import org.gcontracts.annotations.Requires
import org.jfrog.maven.annomojo.annotations.MojoGoal
import org.jfrog.maven.annomojo.annotations.MojoParameter
import org.jfrog.maven.annomojo.annotations.MojoPhase


/**
 * Plugin that creates Jenkins config files to define new build projects
 */
@MojoGoal ( 'generate' )
@MojoPhase ( 'compile' )
@SuppressWarnings( [ 'StatelessClass', 'UnnecessaryPublicModifier', 'PublicInstanceField', 'NonFinalPublicField' ] )
class JenkinsMojo extends BaseGroovyMojo
{
    @MojoParameter ( required = true )
    public String jenkinsUrl
    public String jenkinsUrl() { verify().notNullOrEmpty( this.jenkinsUrl ) }


    @MojoParameter ( required = true )
    public String generationPom
    public String generationPom() { verify().notNullOrEmpty( this.generationPom ) }


    @MojoParameter ( required = true, defaultValue = '${project.build.directory}' )
    public File outputDirectory

    /**
     * When no repository local path is specified - the remote one starting from this value is used
     * For example: for remote repository      "http://google-guice.googlecode.com/svn/trunk/"
     *              and svnRepositoryLocalBase "svn"
     *              local repo path will be    "svn/trunk/"
     */
    @MojoParameter ( required = false )
    public String svnRepositoryLocalBase = 'svn'

    @MojoParameter ( required = false )
    public String endOfLine = 'windows'

    @MojoParameter ( required = false )
    public boolean timestamp = true

    @MojoParameter ( required = false )
    public String timestampFormat = 'MMMM dd, yyyy (HH:mm:ss, \'GMT\'Z)'

    @MojoParameter
    public Job[] jobs

    @MojoParameter
    public Job   job

    private List<Job> jobs() { general().list( this.jobs, this.job ) }


    @Override
    void doExecute ()
    {
        final jobs       = configureJobs( jenkinsUrl(), generationPom(), svnRepositoryLocalBase )
        final jobNamePad = jobs*.toString()*.size().max()

        for ( job in jobs )
        {
            final configPath = ( job.isAbstract ? "abstract job" : generateConfigFile( job ).canonicalPath.replace( '\\', '/' ))
            log.info( "${ job.toString().padRight( jobNamePad ) }  ==>  ${ configPath }" )
        }
    }


    /**
     * Generates config file for the job specified.
     *
     * @param job job definition to generate config file for
     * @return config file generated
     */
    @Requires({ outputDirectory && job && ( ! job.isAbstract ) })
    @Ensures({ result.file })
    File generateConfigFile( Job job )
    {
        final configFile   = new File( outputDirectory, "${ job.id }/config.xml" )
        final indent       = ' ' * 2
        final newLine      = (( 'windows' == endOfLine ) ? '\r\n' : '\n' )
        final timestamp    = ( timestamp ? ' on ' + new Date().format( timestampFormat ) : '' )
        final configMarkup = new ConfigMarkup( job, timestamp, indent, newLine ).markup

        if ( job.process )
        {
            configMarkup = process( configMarkup, configFile, job, indent, newLine )
        }

        file().mkdirs( file().delete( configFile ).parentFile )
        configFile.write( configMarkup.trim(), 'UTF-8' )
        verify().file( configFile )
    }


    /**
    * Reads all jobs specified and configures them.
    *
    * Verifies that no job is defined more than once and all jobs referenced
    * (via <base> and <invoke>) are defined. Also, when job extends another job
    * (with <base>) - it's construction is completed using a <base> job.
    *
    * Returns a mapping of "job ID" => job itself.
    */
    @Requires({ jenkinsUrl && generationPom && svnRepositoryLocalBase })
    private Collection<Job> configureJobs ( String jenkinsUrl, String generationPom, String svnRepositoryLocalBase )
    {
        Map<String, Job> allJobs = [:]

        /**
         * - Reading all jobs,
         * - Building allJobs[] map
         * - Updating job's "Jenkins URL" and "generation POM" properties
         * - For each repository - setting it's "local" part
         *   (most of the time it is omitted by user since it can be calculated from "remote" part)
         */
        for ( job in jobs())
        {
            Job prevJob = allJobs.put( job.id, job )
            assert ( ! prevJob ), "[$job] is defined more than once"

            job.jenkinsUrl    = jenkinsUrl
            job.generationPom = generationPom

            for ( repo in job.repositories())
            {
                repo.remote = verify().notNullOrEmpty( repo.remote ).replaceAll( '/$', '' ) // Trimming trailing '/'
                assert  ( ! ( verify().notNullOrEmpty( repo.remote )).endsWith( '/' ))

                if (( ! repo.local ) && ( repo.svn ))
                {
                    int index                    = repo.remote.lastIndexOf( svnRepositoryLocalBase )
                    if     ( index < 0 ) { index = repo.remote.lastIndexOf( '/' ) + 1 } // last path chunk
                    assert ( index > 0 )
                    repo.local = verify().notNullOrEmpty( repo.remote.substring( index ))
                }
            }
        }

        for( job in jobs())
        {
            /**
             * "Extending" each job with a <parent> jobs or with default values
             */

            job.extend( job.parent ? composeJob( allJobs, job.parent ) : new Job())

            /**
             * Whether job's parent is a real or an abstract one
             */
            job.parentIsReal = ( job.parent && ( ! allJobs[ job.parent ].isAbstract ))

            if ( job.mavenGoals && ( job.jobType == Job.JobType.maven ))
            {   /**
                 * Top-level "base" job may have no maven goals set and it has nowhere to "inherit" it from
                 */
                job.updateMavenGoals()
            }

            /**
             * Verifying all jobs invoked are defined
             */

            for ( invokedJobId in job.invoke?.jobsSplit )
            {
                assert allJobs[ invokedJobId ], "[$job] invokes job [$invokedJobId] but it's not defined. " +
                                                "Defined jobs: ${ allJobs.keySet() }"
            }

           /**
            * Updating "Child Jobs" and "Invoked By" list
            */

            List<Job> childJobs = []
            List<Job> invokedBy = []

            for ( otherJob in jobs().findAll{ it.id != job.id } )
            {
                if ( otherJob.parent == job.id )                       { childJobs << otherJob }
                if ( otherJob.invoke?.jobsSplit?.any{ it == job.id } ) { invokedBy << otherJob }
            }

            job.childJobs = childJobs as Job[]
            job.invokedBy = invokedBy as Job[]
        }

        allJobs.values()*.validate()
    }


    /**
     * Composes one job from all "parent jobs" specified, separated with comma
     *
     * @param allJobs    mapping of all existing jobs: job id => job instance
     * @param parentJobs parent jobs to compose the result job from, separated with comma.
     *
     *                 Note: later jobs in the list are of higher priority, they override
     *                       values set by earlier jobs!
     *                       In "nightly, git" - "git" job is of higher priority and will override values
     *                                           set by "nightly" one
     * @return composed job
     */
    @Requires({ allJobs && parentJobs })
    @Ensures({ result })
    private Job composeJob( Map<String, Job> allJobs, String parentJobs )
    {
        Job resultJob = null

        if ( parentJobs.contains( ',' ))
        {   /**
             * Multiple inheritance - we need to compose an "aggregator" job from all "base" jobs
             */

            resultJob = new Job( id: "Composition of jobs [$parentJobs]" )
            for ( parentJobId in split( parentJobs ))
            {
                Job    parentJob = allJobs[ parentJobId ]
                assert parentJob, "Parent job [$parentJobId] is undefined"

                resultJob.extend( parentJob, true )
            }
        }
        else
        {   /**
             * No multiple inheritance
             */

            resultJob = allJobs[ parentJobs ]
            assert resultJob, "Parent job [$parentJobs] is undefined"
        }

        resultJob
    }


    /**
     * Invokes Groovy specified with job's {@code <process>}.
     *
     * @param configMarkup original config markup
     * @param configFile   config file data will be written to
     * @param job          original job
     * @param indent       markup indent
     * @param newLine      markup new line
     * @return            new config markup
     */
    @Requires({ configMarkup && configFile && job && indent && newLine })
    @Ensures({ result })
    private String process( String configMarkup, File configFile, Job job, String indent, String newLine )
    {
        assert job.process

        final rootNode    = new XmlParser().parseText( configMarkup )
        String expression = job.process.trim().with {
            endsWith( '.groovy' ) ? new File(( String ) delegate ).getText( 'UTF-8' ) : delegate
        }

        /**
         * Updating rootNode structure with custom Groovy expression.
         */
        assert configMarkup && rootNode && configFile
        eval( expression, null, null, 'config', configMarkup, 'node', rootNode, 'file', configFile )

        final writer               = new StringWriter  ( configMarkup.size())
        final printer              = new XmlNodePrinter( new NewLineIndentPrinter( writer, indent, newLine ))
        printer.preserveWhitespace = true // So we have <tag>value</tag> and not <tag>\nvalue\n</tag>
        printer.print( rootNode )
        writer.toString()
    }
}
