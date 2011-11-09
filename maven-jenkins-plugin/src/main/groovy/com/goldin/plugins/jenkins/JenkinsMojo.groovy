package com.goldin.plugins.jenkins

import static com.goldin.plugins.common.GMojoUtils.*
import com.goldin.plugins.common.BaseGroovyMojo
import com.goldin.plugins.jenkins.Job.JOB_TYPE
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

    private Job[] jobs() { general().array( this.jobs, this.job, Job )  }


    @Override
    void doExecute ()
    {
        int             jobNamePad   = 0; // Number of characters to pad the job name, when logged
        int             jobParentPad = 0; // Number of characters to pad the job parent, when logged
        def             jobParent    = { Job job -> job.parent ? "<parent>${ job.parent }</parent>" : 'No <parent>' }
        Collection<Job> jobs         = configureJobs( jenkinsUrl(), generationPom(), svnRepositoryLocalBase )

        /**
         * Verifying job's state and calculating logging pads
         */
        jobs.each {
            Job job ->

            job.verifyAll()

            jobNamePad   = Math.max( jobNamePad,   job.toString().size())
            jobParentPad = Math.max( jobParentPad, jobParent( job ).size())
        }

        /**
         * Generating config files
         */
        jobs.each {
            Job job ->

            String configPath = ''

            if ( job.isAbstract )
            {
                configPath = "${ job } is abstract"
            }
            else
            {
                File configFile = new File( outputDirectory, "${ job.id }/config.xml" )
                configPath      = configFile.canonicalPath.replace( '\\', '/' )
                def  timestamp  = timestamp ? 'on ' + new Date().format( timestampFormat ) : null
                def  config     = makeTemplate( '/config.xml', [ job : job, timestamp : timestamp ], endOfLine, true )
                Node n          = verify().notNull( new XmlParser().parseText( config ))

                file().mkdirs( configFile.parentFile )

                if ( job.process )
                {
                    def printer                = new XmlNodePrinter( configFile.newPrintWriter( 'UTF-8' ))
                    printer.preserveWhitespace = true
                    String expression          = job.process.trim().with {
                        endsWith( '.groovy' ) ? verify().file( new File(( String ) delegate )).getText( 'UTF-8' ) : delegate
                    }

                    printer.print( eval( expression, Node, null, 'config', config,
                                                                  'node',  n,
                                                                  'file',  configFile ))
                }
                else
                {
                    configFile.write( config )
                    assert verify().file( configFile ).size() == config.size()
                }
            }

            assert configPath
            log.info( "${ job.toString().padRight( jobNamePad ) }  ${ jobParent( job ).padRight( jobParentPad ) }  ==>  [${ configPath }]" )
        }
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
        jobs().each
        {
            Job job ->
            Job prevJob = allJobs.put( job.id, job )
            assert ( ! prevJob ), "[$job] is defined more than once"

            job.jenkinsUrl    = jenkinsUrl
            job.generationPom = generationPom

            job.repositories().each
            {
                Repository repo ->

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


        jobs().each
        {
            Job job ->

            /**
             * "Extending" each job with a <parent> jobs or with default values
             */

            job.extend( job.parent ? composeJob( allJobs, job.parent ) :
                                     new Job())

            if ( job.mavenGoals && ( job.jobType == JOB_TYPE.maven ))
            {
                /**
                 * Top-level "base" job may have no maven goals set and it has nowhere to "inherit" it from
                 */
                job.updateMavenGoals()
            }

            /**
             * Verifying all jobs invoked are defined
             */

            job.invoke?.jobsSplit?.each
            {
                String invokedJobId ->
                assert allJobs[ invokedJobId ], "[$job] invokes job [$invokedJobId] but it's not defined. " +
                                                "Defined jobs: ${ allJobs.keySet() }"
            }

           /**
            * Updating "Invoked By" list
            */

            List<Job> invokedBy = []

            jobs().findAll{ it.id != job.id }.each
            {
                Job otherJob ->
                if ( otherJob.invoke?.jobsSplit?.any{ it == job.id } ) { invokedBy << otherJob }
            }

            job.invokedBy = invokedBy as Job[]
        }

        allJobs.values()
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
            split( parentJobs ).each {
                String parentJobId ->
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
}
