package com.github.goldin.plugins.jenkins

import static com.github.goldin.plugins.common.GMojoUtils.*
import com.github.goldin.plugins.common.BaseGroovyMojo
import com.github.goldin.plugins.jenkins.markup.ConfigMarkup
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.gcontracts.annotations.Ensures
import org.gcontracts.annotations.Requires


/**
 * Plugin that creates Jenkins config files to define new build projects
 */
@Mojo ( name = 'generate', defaultPhase = LifecyclePhase.COMPILE, threadSafe = true )
@SuppressWarnings([ 'StatelessClass', 'UnnecessaryPublicModifier', 'PublicInstanceField', 'NonFinalPublicField' ])

class JenkinsMojo extends BaseGroovyMojo
{
    @Parameter ( required = true )
    private String jenkinsUrl
    private String jenkinsUrl() { verifyBean().notNullOrEmpty( this.jenkinsUrl ) }


    @Parameter ( required = true )
    private String generationPom
    private String generationPom() { verifyBean().notNullOrEmpty( this.generationPom ) }


    @Parameter ( required = true, defaultValue = '${project.build.directory}' )
    private File outputDirectory

    /**
     * When no repository local path is specified - the remote one starting from this value is used
     * For example: for remote repository      "http://google-guice.googlecode.com/svn/trunk/"
     *              and svnRepositoryLocalBase "svn"
     *              local repo path will be    "svn/trunk/"
     */
    @Parameter ( required = false )
    private String svnRepositoryLocalBase = 'svn'

    @Parameter ( required = false )
    private String endOfLine = 'windows'

    @Parameter ( required = false )
    private boolean timestamp = true

    @Parameter ( required = false )
    private String timestampFormat = 'MMMM dd, yyyy (HH:mm:ss, \'GMT\'Z)'

    @Parameter
    private Job[] jobs

    @Parameter
    private Job   job

    private List<Job> jobs() { generalBean().list( this.jobs, this.job ) }


    @Override
    void doExecute ()
    {
        final t          = System.currentTimeMillis()
        final jobsMap    = configureJobs( jenkinsUrl(), generationPom(), svnRepositoryLocalBase )
        final jobNamePad = jobsMap.values()*.toString()*.size().max()

        for ( job in jobsMap.values())
        {
            final configPath = ( job.isAbstract ? "abstract job" :
                                                  generateConfigFile( job, jobsMap ).canonicalPath.replace( '\\', '/' ))
            log.info( "${ job.toString().padRight( jobNamePad ) }  ==>  ${ configPath }" )
        }

        log.info( "[${ jobsMap.size()}] job${ generalBean().s( jobsMap.size()) } generated in [${ System.currentTimeMillis() - t }] ms" )
    }


    /**
     * Generates config file for the job specified.
     *
     * @param job  job definition to generate config file for
     * @param jobs all jobs
     * @return     config file generated
     */
    @Requires({ outputDirectory && job && ( ! job.isAbstract ) && jobs })
    @Ensures({ result.file })
    File generateConfigFile( Job job, Map<String, Job> jobs )
    {
        final configFile   = new File( outputDirectory, "${ job.id }/config.xml" )
        final indent       = '  '
        final newLine      = (( 'windows' == endOfLine ) ? '\r\n' : '\n' )
        final timestamp    = ( timestamp ? ' on ' + new Date().format( timestampFormat ) : '' )
        final configMarkup = new ConfigMarkup( job, jobs, timestamp, indent, newLine ).markup

        if ( job.processes())
        {
            configMarkup = process( configMarkup, configFile, job, jobs, indent, newLine )
        }

        write( configFile, configMarkup.trim())
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
    @Ensures({ result })
    private Map<String, Job> configureJobs ( String jenkinsUrl, String generationPom, String svnRepositoryLocalBase )
    {
        assert jenkinsUrl && generationPom && svnRepositoryLocalBase

        final List<Job>        jobsList = jobs()
        final Map<String, Job> jobsMap  = [:]
        assert jobsList, "No jobs configured. Use either <job> or <jobs> to define Jenkins jobs."

        /**
         * - Reading all jobs,
         * - Building jobsMap
         * - Updating job's "Jenkins URL" and "generation POM" properties
         * - For each repository - setting it's "local" part
         *   (most of the time it is omitted by user since it can be calculated from "remote" part)
         */

        for ( job in jobsList )
        {
            // noinspection GroovyMapPutCanBeKeyedAccess
            Job prevJob = jobsMap.put( job.id, job )
            assert ( ! prevJob ), "$job is defined more than once"

            job.jenkinsUrl    = jenkinsUrl
            job.generationPom = generationPom

            for ( repo in job.repositories())
            {
                assert repo.remote, "$job - every <repository> needs to have <remote> specified"
                repo.remote = repo.remote.replaceAll( '/$', '' ) // Trimming trailing '/'
                assert ( ! repo.remote.endsWith( '/' ))

                if (( ! repo.local ) && ( repo.svn ))
                {
                    int index                    = repo.remote.lastIndexOf( svnRepositoryLocalBase )
                    if     ( index < 0 ) { index = repo.remote.lastIndexOf( '/' ) + 1 } // last path chunk
                    assert ( index > 0 )
                    repo.local = verifyBean().notNullOrEmpty( repo.remote.substring( index ))
                }
            }
        }

        for( job in jobsList )
        {
            /**
             * "Extending" each job with a <parent> jobs or with default values
             */

            job.extend( job.parent ? composeJob( jobsMap, job.parent ) : new Job())

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
                assert jobsMap[ invokedJobId ], "[$job] invokes job [$invokedJobId] but it's not defined. " +
                                                "Defined jobs: ${ jobsMap.keySet() }"
            }

           /**
            * Updating "Child Jobs" and "Invoked By" list
            */

            List<Job> childJobs = []
            List<Job> invokedBy = []

            for ( otherJob in jobsList.findAll{ it.id != job.id } )
            {
                if ( otherJob.parent == job.id )                       { childJobs << otherJob }
                if ( otherJob.invoke?.jobsSplit?.any{ it == job.id } ) { invokedBy << otherJob }
            }

            job.childJobs = childJobs as Job[]
            job.invokedBy = invokedBy as Job[]
        }

        assert jobsMap
        jobsMap.values().each { it.validate() }
        jobsMap
    }


    /**
     * Composes one job from all "parent jobs" specified, separated with comma
     *
     * @param jobsMap    mapping of all existing jobs: job id => job instance
     * @param parentJobs parent jobs to compose the result job from, separated with comma.
     *
     *                 Note: later jobs in the list are of higher priority, they override
     *                       values set by earlier jobs!
     *                       In "nightly, git" - "git" job is of higher priority and will override values
     *                                           set by "nightly" one
     * @return composed job
     */
    @Requires({ jobsMap && parentJobs })
    @Ensures({ result })
    private Job composeJob( Map<String, Job> jobsMap, String parentJobs )
    {
        Job resultJob = null

        if ( parentJobs.contains( ',' ))
        {   /**
             * Multiple inheritance - we need to compose an "aggregator" job from all "base" jobs
             */

            resultJob = new Job( id: "Composition of jobs [$parentJobs]" )
            for ( parentJobId in split( parentJobs ))
            {
                Job    parentJob = jobsMap[ parentJobId ]
                assert parentJob, "Parent job [$parentJobId] is undefined"

                resultJob.extend( parentJob, true )
            }
        }
        else
        {   /**
             * No multiple inheritance
             */

            resultJob = jobsMap[ parentJobs ]
            assert resultJob, "Parent job [$parentJobs] is undefined"
        }

        resultJob
    }


    /**
     * Invokes Groovy specified with job's {@code <process>} or {@code <processes>}.
     *
     * @param configMarkup original config markup
     * @param configFile   config file data will be written to
     * @param job          original job
     * @param jobs         all jobs
     * @param indent       markup indent
     * @param newLine      markup new line
     * @return             new config markup
     */
    @Requires({ configMarkup && configFile && job && job.processes() && jobs && indent && newLine })
    @Ensures({ result })
    private String process( String configMarkup, File configFile, Job job, Map<String, Job> jobs, String indent, String newLine )
    {
        List<String> expressions = (( List<String> ) job.processes().collect {
            String expression ->
            expression.trim().with { endsWith( '.groovy' ) ? new File(( String ) delegate ).getText( 'UTF-8' ) : delegate }
        })*.trim()

        /**
         * Updating rootNode structure with custom Groovy expression.
         */
        final rootNode = verifyBean().notNull( new XmlParser().parseText( configMarkup ))
        for ( expression in expressions )
        {
            eval( expression, null, null, 'config', configMarkup,
                                          'node',   rootNode,
                                          'file',   configFile,
                                          'job',    job,
                                          'jobs',   jobs )
        }

        final writer               = new StringWriter  ( configMarkup.size())
        final printer              = new XmlNodePrinter( new NewLineIndentPrinter( writer, indent, newLine ))
        printer.preserveWhitespace = true // So we have <tag>value</tag> and not <tag>\nvalue\n</tag>
        printer.print( rootNode )
        writer.toString()
    }
}
