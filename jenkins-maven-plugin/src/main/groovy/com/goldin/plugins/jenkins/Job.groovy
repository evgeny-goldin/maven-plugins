package com.goldin.plugins.jenkins

import static com.goldin.plugins.common.GMojoUtils.*
import org.apache.maven.plugin.MojoExecutionException
import com.goldin.plugins.jenkins.beans.*


/**
 * Class describing a Jenkins job
 */
@SuppressWarnings( 'StatelessClass' )
class Job
{
   /**
    * Folder names that are illegal on Windows:
    *
    * "Illegal Characters on Various Operating Systems"
    * http://support.grouplogic.com/?p=1607
    * http://msdn.microsoft.com/en-us/library/aa365247%28VS.85%29.aspx
    */
    private static final Set<String> ILLEGAL_NAMES = new HashSet<String>
        ([ 'com1', 'com2', 'com3', 'com4', 'com5', 'com6', 'com7', 'com8', 'com9',
           'lpt1', 'lpt2', 'lpt3', 'lpt4', 'lpt5', 'lpt6', 'lpt7', 'lpt8', 'lpt9',
           'con',  'nul',  'prn' ])

    /**
     * Error messages to display when jobs are not properly configured
     */
    private static final String NOT_CONFIGURED = 'is not configured correctly'
    private static final String MIS_CONFIGURED = 'is mis-configured'

    /**
     * Job types supported
     */
    static enum JOB_TYPE
    {
        free  ( 'Free-Style' ),
        maven ( 'Maven'      )

        final String description
        JOB_TYPE ( String description ) { this.description = description }
    }


    /**
     * Individual property, not inherited from parent job
     * @see #extend
     */
    String  id             // Job's ID when a folder is created, has illegal characters "fixed"
    String  originalId     // Job's ID used for logging

    void setId( String id )
    {
        assert id?.trim()?.length()

        /**
         * Job Id should never have any illegal characters in it since it becomes a folder name later
         * (in Jenkins workspace - '.jenkins/jobs/JobId' )
         */
        this.originalId = id
        this.id         = fixIllegalChars( id, 'Job id' )
    }

    /**
     * Individual boolean properties, not inherited from parent job
     * @see #extend
     */
    boolean              isAbstract = false
    void                 setAbstract( boolean isAbstract ) { this.isAbstract = isAbstract }
    boolean              disabled   = false

    /**
    * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    * If you add more fields - do not forget to update {@link #extend(Job)}
    * where current job is "extended" with the "parent Job"
     * DO NOT specify default values, let {@link #extend(Job)} inheritance take car of it.
    * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    */

    JOB_TYPE             jobType
    // Maven 2 can't set String to Enum, Maven 3 can
    void                 setJobType( String jobType ){ this.jobType = ( JOB_TYPE ) JOB_TYPE.valueOf( jobType ) }
    Boolean              buildOnSNAPSHOT
    Boolean              useUpdate
    Boolean              doRevert
    Boolean              privateRepository
    Boolean              archivingDisabled
    Boolean              blockBuildWhenDownstreamBuilding
    Boolean              blockBuildWhenUpstreamBuilding
    String               jenkinsUrl
    String               generationPom
    String               parent
    String               description
    DescriptionRow[]     descriptionTable
    Integer              daysToKeep // Number of days to keep old builds
    Integer              numToKeep  // Number of old builds to keep
    String               scmType
    String               getScmClass()
    {
        assert this.scmType

        def    scmClass = [ none : 'hudson.scm.NullSCM',
                            cvs  : 'hudson.scm.CVSSCM',
                            svn  : 'hudson.scm.SubversionSCM',
                            git  : 'hudson.plugins.git.GitSCM' ][ this.scmType ]
        assert scmClass, "Unknown <scmType>${ this.scmType }</scmType>"
               scmClass
    }

    Task[]               tasks
    String               node
    String               pom
    String               localRepoBase
    String               localRepo
    String               localRepoPath // See updateMavenGoals()
    Artifactory          artifactory

    void setLocalRepo ( String localRepo )
    {
        /**
         * Local repo should never have any illegal characters in it since it becomes a folder name later
         */
        this.localRepo = fixIllegalChars( localRepo, 'Local repo' )
    }

    String               mavenGoals
    String               mavenName
    String               jdkName
    String               mavenOpts
    Deploy               deploy
    Mail                 mail
    Invoke               invoke
    Job[]                invokedBy
    String               authToken

    Trigger[]            triggers
    Trigger              trigger
    List<Trigger>        triggers() { general().list( this.triggers, this.trigger ) }

    Parameter[]          parameters
    Parameter            parameter
    List<Parameter>      parameters() { general().list( this.parameters, this.parameter ) }

    Repository[]         repositories
    Repository           repository
    List<Repository>     repositories() { general().list( this.repositories, this.repository ) }


    /**
     * Extension points - tags accepting raw XML content wrapped in <![CDATA[ ... ]]>
     */
    String               scm
    String               reporters
    String               publishers
    String               buildWrappers
    String               properties

    /**
     * Groovy extension point
     */
    String               process



   /**
    * Retrieves job description table
    */
    String getHtmlDescriptionTable() { makeTemplate( '/descriptionTable.html', [ job : this ] ) }


    /**
     * Checks that String specifies contains no illegal characters and can be used for creating a folder
     */
    String fixIllegalChars( String s, String title )
    {
        if ( ! s )
        {
            return s
        }

        if ( ILLEGAL_NAMES.contains( s.toLowerCase()))
        {
            throw new MojoExecutionException( "$title [${ id }] is illegal! " +
                                              "It becomes a folder name and the following names are illegal on Windows: ${ ILLEGAL_NAMES.sort() }" )
        }

        /**
         *
         * Leaving only letters/digits, '-', '.' and '_' characters:
         * \w = word character: [a-zA-Z_0-9]
         */
        s.replaceAll( /[^\w\.-]+/, '-' )
    }


    @Override
    String toString () { "Job \"${ this.originalId }\"" }


    /**
     * Sets the property specified using the value of another job or default value
     */
    private void set( String  property,
                      Job     otherJob,
                      boolean override,
                      Closure verifyClosure,
                      Object  defaultValue )
    {
        if (( this[ property ] == null ) || override )
        {
            this[ property ] = otherJob[ property ] ?: defaultValue
        }

        assert ( this[ property ] != null ), "[$this] has null [$property]"
        verifyClosure( this[ property ] )
    }


   /**
    * Extends job definition using the parent job specified
    * (completes missing data with that inherited from the parent job)
    *
    * @param parentJob parent job to take the missing data from
    * @param override  whether or not parentJob data is of higher priority than this job data,
    *                  usually it's not - only used when we want to "override" this job data
    */
    @SuppressWarnings( 'AbcComplexity' )
    void extend ( Job parentJob, boolean override = false )
    {
        set( 'description',       parentJob, override, { verify().notNullOrEmpty( it )}, '&nbsp;' )
        set( 'scmType',           parentJob, override, { verify().notNullOrEmpty( it )}, 'svn' )
        set( 'jobType',           parentJob, override, { verify().notNullOrEmpty( it.toString() )}, JOB_TYPE.maven )
        set( 'node',              parentJob, override, { verify().notNullOrEmpty( it )}, 'master' )
        set( 'jdkName',           parentJob, override, { verify().notNullOrEmpty( it )}, '(Default)' )
        set( 'authToken',         parentJob, override, { it }, '' )
        set( 'scm',               parentJob, override, { it }, '' )
        set( 'properties',        parentJob, override, { it }, '' )
        set( 'publishers',        parentJob, override, { it }, '' )
        set( 'buildWrappers',     parentJob, override, { it }, '' )
        set( 'process',           parentJob, override, { it }, '' )
        set( 'useUpdate',         parentJob, override, { it }, false )
        set( 'doRevert',          parentJob, override, { it }, false )
        set( 'blockBuildWhenDownstreamBuilding', parentJob, override, { it }, false )
        set( 'blockBuildWhenUpstreamBuilding',   parentJob, override, { it }, false )
        set( 'daysToKeep',        parentJob, override, { it }, -1 )
        set( 'numToKeep',         parentJob, override, { it }, -1 )
        set( 'descriptionTable',  parentJob, override, { it }, new DescriptionRow[ 0 ])
        set( 'mail',              parentJob, override, { it }, new Mail())
        set( 'invoke',            parentJob, override, { it }, new Invoke())

        if ((( ! this.triggers())   || ( override )) && parentJob.triggers())
            { setTriggers ( parentJob.triggers() as Trigger[] ) }

        if ((( ! this.parameters()) || ( override )) && parentJob.parameters())
        {
            setParameters ( parentJob.parameters() as Parameter[] )
        }
        else if ( parentJob.parameters())
        {
            /**
             * Set gives a lower priority to parentJob parameters - parameters having the
             * same name and type *are not taken*, see {@link Parameter#equals(Object)}
             */
            setParameters( joinParameters( parentJob.parameters(), this.parameters()) as Parameter[] )
        }

        if ((( ! this.repositories()) || ( override )) && parentJob.repositories())
            { setRepositories ( parentJob.repositories() as Repository[] ) }

        if ( this.jobType == JOB_TYPE.maven )
        {
            set( 'pom',               parentJob, override, { verify().notNullOrEmpty( it )}, 'pom.xml' )
            set( 'mavenGoals',        parentJob, override, { verify().notNullOrEmpty( it )}, '-B -e clean install' )
            set( 'mavenName',         parentJob, override, { verify().notNullOrEmpty( it )}, '' )
            set( 'mavenOpts',         parentJob, override, { it }, ''    )
            set( 'buildOnSNAPSHOT',   parentJob, override, { it }, false )
            set( 'privateRepository', parentJob, override, { it }, false )
            set( 'archivingDisabled', parentJob, override, { it }, false )
            set( 'reporters',         parentJob, override, { it }, ''    )
            set( 'localRepoBase',     parentJob, override, { it }, '' )
            set( 'localRepo',         parentJob, override, { it }, '' )
            set( 'deploy',            parentJob, override, { it }, new Deploy())
            set( 'artifactory',       parentJob, override, { it }, new Artifactory())
        }
        else if ( this.jobType == JOB_TYPE.free )
        {
            if ((( ! this.tasks ) || ( override )) && parentJob.tasks )
                { setTasks ( parentJob.tasks ) }
        }
    }


    /**
     * Joins two set of parameters, those inhered from the parent job and those of the current job.
     *
     * @param parentParameters  parameters inherited from the parent job.
     * @param currentParameters parameters of the current job.
     * @return new set of parameters
     */
    private static List<Parameter> joinParameters( List<Parameter> parentParameters, List<Parameter> currentParameters )
    {
        List<String> parentNames  = parentParameters*.name
        List<String> currentNames = currentParameters*.name

        if ( parentNames.intersect( currentNames ))
        {
            parentParameters.findAll { ! currentNames.contains( it.name ) } + currentParameters
        }
        else
        {
            parentParameters + currentParameters
        }
    }


   /**
    * Updates job's {@link #mavenGoals}:
    * - replaces "{...}" with "${...}"
    * - updates "-Dmaven.repo.local" value
    */
    void updateMavenGoals()
    {
        assert this.jobType == JOB_TYPE.maven

        /**
         * {..} => ${..}
         */
        this.mavenGoals = verify().notNullOrEmpty( this.mavenGoals ).addDollar()

        if ( this.privateRepository )
        {
            assert ( ! ( this.localRepoBase || this.localRepo )), "[${this}] has <privateRepository> set, " +
                                                                  "<localRepoBase> and <localRepo> shouldn't be specified"
        }
        else if ( this.localRepoBase || this.localRepo )
        {
            /**
             * Adding "-Dmaven.repo.local=/x/y/z" using {@link #localRepoBase} and {@link #localRepo}
             * or replacing it with updated value, if already exists
             */

            this.localRepoPath = (( this.localRepoBase ?: "${ constants().USER_HOME }/.m2/repository" ) +
                                  '/' +
                                  ( this.localRepo     ?: '.' )).
                                 replace( '\\', '/' )

            String localRepoArg = "-Dmaven.repo.local=&quot;${ this.localRepoPath }&quot;"
            this.mavenGoals     = ( this.mavenGoals.contains( '-Dmaven.repo.local' )) ?
                                      ( this.mavenGoals.replaceAll( /-Dmaven.repo.local\S+/, localRepoArg )) :
                                      ( this.mavenGoals +  ' ' + localRepoArg )
        }
    }


    /**
     * Verifies job is fully configured before config file is created
     *
     * All job properties used "as-is" (without "if" and safe navigation operator)
     * in "config.xml" and "descriptionTable.html" are verified to be defined
     */
     @SuppressWarnings( 'AbcComplexity' )
     void verifyAll ()
     {
         if ( this.isAbstract ) { return }

         assert this.id,            "[${ this }] $NOT_CONFIGURED: missing <id>"
         assert this.jenkinsUrl,    "[${ this }] $NOT_CONFIGURED: missing <jenkinsUrl>"
         assert this.generationPom, "[${ this }] $NOT_CONFIGURED: missing <generationPom>"
         assert this.scmClass,      "[${ this }] $NOT_CONFIGURED: unknown <scmType>?"
         assert this.description,   "[${ this }] $NOT_CONFIGURED: missing <description>"
         assert this.scmType,       "[${ this }] $NOT_CONFIGURED: missing <scmType>"
         assert this.jobType,       "[${ this }] $NOT_CONFIGURED: missing <jobType>"
         assert this.node,          "[${ this }] $NOT_CONFIGURED: missing <node>"
         assert this.jdkName,       "[${ this }] $NOT_CONFIGURED: missing <jdkName>"

         assert ( this.authToken         != null ), "[${ this }] $NOT_CONFIGURED: 'authToken' is null?"
         assert ( this.scm               != null ), "[${ this }] $NOT_CONFIGURED: 'scm' is null?"
         assert ( this.properties        != null ), "[${ this }] $NOT_CONFIGURED: 'properties' is null?"
         assert ( this.publishers        != null ), "[${ this }] $NOT_CONFIGURED: 'publishers' is null?"
         assert ( this.buildWrappers     != null ), "[${ this }] $NOT_CONFIGURED: 'buildWrappers' is null?"
         assert ( this.process           != null ), "[${ this }] $NOT_CONFIGURED: 'process' is null?"
         assert ( this.useUpdate         != null ), "[${ this }] $NOT_CONFIGURED: 'useUpdate' is null?"
         assert ( this.doRevert          != null ), "[${ this }] $NOT_CONFIGURED: 'doRevert' is null?"
         assert ( this.blockBuildWhenDownstreamBuilding != null ), "[${ this }] $NOT_CONFIGURED: 'blockBuildWhenDownstreamBuilding' is null?"
         assert ( this.blockBuildWhenUpstreamBuilding   != null ), "[${ this }] $NOT_CONFIGURED: 'blockBuildWhenUpstreamBuilding' is null?"
         assert ( this.daysToKeep        != null ), "[${ this }] $NOT_CONFIGURED: 'daysToKeep' is null?"
         assert ( this.numToKeep         != null ), "[${ this }] $NOT_CONFIGURED: 'numToKeep' is null?"
         assert ( this.descriptionTable  != null ), "[${ this }] $NOT_CONFIGURED: 'descriptionTable' is null?"
         assert ( this.mail              != null ), "[${ this }] $NOT_CONFIGURED: 'mail' is null?"
         assert ( this.invoke            != null ), "[${ this }] $NOT_CONFIGURED: 'invoke' is null?"

         verifyRepositories()

         if ( this.jobType == JOB_TYPE.free )
         {
             assert this.tasks, "[${ this }] $NOT_CONFIGURED: missing '<tasks>'"
             for ( task in this.tasks )
             {
                 assert ( task.hudsonClass && task.markup ), "Task [$task] - Hudson class or markup is missing"
             }

             assert ! this.pom,        "[${ this }] $MIS_CONFIGURED: <pom> is not active in free-style jobs"
             assert ! this.mavenGoals, "[${ this }] $MIS_CONFIGURED: <mavenGoals> is not active in free-style jobs"
             assert ! this.mavenName,  "[${ this }] $MIS_CONFIGURED: <mavenName> is not active in free-style jobs"
             assert ( this.mavenOpts         == null ), "[${ this }] $MIS_CONFIGURED: <mavenOpts> is not active in free-style jobs"
             assert ( this.buildOnSNAPSHOT   == null ), "[${ this }] $MIS_CONFIGURED: <buildOnSNAPSHOT> is not active in free-style jobs"
             assert ( this.privateRepository == null ), "[${ this }] $MIS_CONFIGURED: <privateRepository> is not active in free-style jobs"
             assert ( this.archivingDisabled == null ), "[${ this }] $MIS_CONFIGURED: <archivingDisabled> is not active in free-style jobs"
             assert ( this.reporters         == null ), "[${ this }] $MIS_CONFIGURED: <reporters> is not active in free-style jobs"
             assert ( this.localRepoBase     == null ), "[${ this }] $MIS_CONFIGURED: <localRepoBase> is not active in free-style jobs"
             assert ( this.localRepo         == null ), "[${ this }] $MIS_CONFIGURED: <localRepo> is not active in free-style jobs"
             assert ( this.deploy            == null ), "[${ this }] $MIS_CONFIGURED: <deploy> is not active in free-style jobs"
             assert ( this.artifactory       == null ), "[${ this }] $MIS_CONFIGURED: <artifactory> is not active in free-style jobs"
         }
         else if ( this.jobType == JOB_TYPE.maven )
         {
             assert ! this.tasks, "[${ this }] $MIS_CONFIGURED: <tasks> is not active in maven jobs"

             assert this.pom,        "[${ this }] $NOT_CONFIGURED: missing <pom>"
             assert this.mavenGoals, "[${ this }] $NOT_CONFIGURED: missing <mavenGoals>"
             assert this.mavenName,  "[${ this }] $NOT_CONFIGURED: missing <mavenName>"

             assert ( this.mavenOpts         != null ), "[${ this }] $NOT_CONFIGURED: 'mavenOpts' is null?"
             assert ( this.buildOnSNAPSHOT   != null ), "[${ this }] $NOT_CONFIGURED: 'buildOnSNAPSHOT' is null?"
             assert ( this.privateRepository != null ), "[${ this }] $NOT_CONFIGURED: 'privateRepository' is null?"
             assert ( this.archivingDisabled != null ), "[${ this }] $NOT_CONFIGURED: 'archivingDisabled' is null?"
             assert ( this.reporters         != null ), "[${ this }] $NOT_CONFIGURED: 'reporters' is null?"
             assert ( this.localRepoBase     != null ), "[${ this }] $NOT_CONFIGURED: 'localRepoBase' is null?"
             assert ( this.localRepo         != null ), "[${ this }] $NOT_CONFIGURED: 'localRepo' is null?"
             assert ( this.deploy            != null ), "[${ this }] $NOT_CONFIGURED: 'deploy' is null?"
             assert ( this.artifactory       != null ), "[${ this }] $NOT_CONFIGURED: 'artifactory' is null?"

             if ( this.deploy?.url || this.artifactory?.name )
             {
                 assert ( ! this.archivingDisabled ), \
                        "[${ this }] has archiving disabled - artifacts deploy to Maven or Artifactory repository can not be used"
             }

             if ( this.privateRepository )
             {
                assert ( ! ( this.localRepoBase || this.localRepo || this.localRepoPath )), \
                        "[${ this }] has <privateRepository> specified, no <localRepoBase>, <localRepo>, or <localRepoPath> should be defined"
             }
         }
         else
         {
             throw new IllegalArgumentException ( "Unknown job type [${ this.jobType }]. " +
                                                  "Known types are \"${JOB_TYPE.free.name()}\" and \"${JOB_TYPE.maven.name()}\"" )
         }
     }


    /**
     * Verifies remote repositories for correctness:
     * - No remote repository appears more than once
     * - No remote repository appears as part of another remote repository
     *
     * Otherwise, Jenkins fails when project is checked out!
     */
     void verifyRepositories()
     {
         this.repositories().remote.each
         {
             String repoToCheck ->
             int counter = 0

             repositories().remote.each
             {
                 String otherRepo ->

                 if (( repoToCheck == otherRepo ) && (( ++counter ) != 1 ))
                 {
                     /**
                      * Repository should only equal to itself once
                      */

                     throw new MojoExecutionException( "[${ this }]: Repo [$repoToCheck] is duplicated" )
                 }

                 if (( ! ( repoToCheck == otherRepo )) && ( otherRepo.toLowerCase().contains( repoToCheck.toLowerCase() + '/' )))
                 {
                     throw new MojoExecutionException(
                         "[${ this }]: Repo [$repoToCheck] is duplicated in [$otherRepo] - you should remove [$otherRepo]" )
                 }
             }
         }
     }

}
