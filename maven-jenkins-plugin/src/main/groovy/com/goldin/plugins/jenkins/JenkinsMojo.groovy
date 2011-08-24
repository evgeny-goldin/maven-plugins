package com.goldin.plugins.jenkins

import static com.goldin.plugins.common.GMojoUtils.*
import com.goldin.plugins.common.BaseGroovyMojo
import com.goldin.plugins.jenkins.Job.JOB_TYPE
import org.jfrog.maven.annomojo.annotations.MojoGoal
import org.jfrog.maven.annomojo.annotations.MojoParameter
import org.jfrog.maven.annomojo.annotations.MojoPhase


/**
 * Plugin that creates Jenkins config files to define new build projects
 */
@MojoGoal ( "generate" )
@MojoPhase ( "compile" )
public class JenkinsMojo extends BaseGroovyMojo
{
    public JenkinsMojo ()
    {
    }


    @MojoParameter ( required = true )
    public String jenkinsUrl
    public String jenkinsUrl() { verifyBean().notNullOrEmpty( this.jenkinsUrl ) }


    @MojoParameter ( required = true )
    public String generationPom
    public String generationPom() { verifyBean().notNullOrEmpty( this.generationPom ) }


    @MojoParameter ( required = true, defaultValue = '${project.build.directory}' )
    public File outputDirectory

    @MojoParameter ( required = false )
    public String endOfLine = 'windows'

    @MojoParameter ( required = false )
    public boolean timestamp = true

    @MojoParameter ( required = false )
    public String timestampFormat = 'MMMM dd, yyyy (HH:mm:ss, \'GMT\'Z)'


    /**
     * When no repository local path is specified - the remote one starting from this value is used
     * For example: for remote repository      "http://google-guice.googlecode.com/svn/trunk/"
     *              and svnRepositoryLocalBase "svn"
     *              local repo path will be    "svn/trunk/"
     */
    @MojoParameter ( required = false )
    public String svnRepositoryLocalBase = 'svn'


    @MojoParameter
    public Job[] jobs

    @MojoParameter
    public Job   job

    private Job[] jobs() { generalBean().array( this.jobs, this.job, Job )  }


    @Override
    public void doExecute ()
    {
        int             jobNamePad   = 0; // Number of characters to pad the job name, when logged
        int             jobParentPad = 0; // Number of characters to pad the job parent, when logged
        def             jobParent    = { Job job -> job.parent ? "<parent>${ job.parent }</parent>" : "No <parent>" }
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

            String configPath

            if ( job.isAbstract )
            {
                configPath = "${ job } is abstract"
            }
            else
            {
                File   configFile = new File( outputDirectory, "${ job.id }/config.xml" )
                fileBean().mkdirs( configFile.parentFile )

                def timestamp = timestamp ? 'on ' + new Date().format( timestampFormat ) : null
                def config    = makeTemplate( '/config.xml', [ job : job, timestamp : timestamp ], endOfLine, true )

                configFile.write( verifyBean().notNullOrEmpty( config ))
                assert (( configFile.file ) && ( configFile.size() == config.size()) && ( configFile.text == config ))

                configPath = validate( configFile ).canonicalPath
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
    private Collection<Job> configureJobs ( String jenkinsUrl, String generationPom, String svnRepositoryLocalBase )
    {
        verifyBean().notNullOrEmpty( jenkinsUrl, generationPom, svnRepositoryLocalBase )

        Map<String, Job> allJobs = new LinkedHashMap<String, Job>()

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

                repo.remote = verifyBean().notNullOrEmpty( repo.remote ).replaceAll( '/$', '' ) // Trimming trailing '/'
                assert  ( ! ( verifyBean().notNullOrEmpty( repo.remote )).endsWith( '/' ))

                if (( ! repo.local ) && ( repo.svn ))
                {
                    int index                    = repo.remote.lastIndexOf( svnRepositoryLocalBase )
                    if     ( index < 0 ) { index = repo.remote.lastIndexOf( '/' ) + 1 } // last path chunk
                    assert ( index > 0 )
                    repo.local = verifyBean().notNullOrEmpty( repo.remote.substring( index ))
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

            List<Job> invokedBy = new ArrayList<Job>()

            jobs().findAll{ it.id != job.id }.each
            {
                Job otherJob ->
                if ( otherJob.invoke?.jobsSplit?.any{ it == job.id } ) { invokedBy << otherJob }
            }

            job.setInvokedBy( invokedBy.toArray( new Job[ invokedBy.size() ] ))
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
    private Job composeJob( Map<String, Job> allJobs, String parentJobs )
    {
        Job resultJob = null

        if ( ! parentJobs.contains( ',' ))
        {
            /**
             * No multiple inheritance
             */

            resultJob = allJobs[ parentJobs ]
            assert resultJob, "Parent job [$parentJobs] is undefined"
        }
        else
        {
            /**
             * Multiple inheritance - we need to compose an "aggregator" job from all "base" jobs
             */

            resultJob = new Job( id: "Composition of jobs [$parentJobs]" )
            parentJobs.split( /\s*,\s*/ ).each
            {
                String parentJobId ->
                Job    parentJob = allJobs[( parentJobId )]
                assert parentJob, "Parent job [$parentJobId] is undefined"

                resultJob.extend( parentJob, true )
            }
        }

        assert resultJob
        resultJob
    }
}
