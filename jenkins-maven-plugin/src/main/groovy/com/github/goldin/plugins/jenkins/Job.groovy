package com.github.goldin.plugins.jenkins

import static com.github.goldin.plugins.common.GMojoUtils.*
import org.apache.maven.plugin.MojoExecutionException
import org.gcontracts.annotations.Requires
import com.github.goldin.plugins.jenkins.beans.*


/**
 * Class describing a Jenkins job
 */
@SuppressWarnings([ 'StatelessClass' ])
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
    static enum JobType
    {
        free  ( 'Free-Style' ),
        maven ( 'Maven'      )

        final String description

        @Requires({ description })
        JobType ( String description )
        {
            this.description = description
        }
    }

    /**
     * "runPostStepsIfResult" types supported
     */
    static enum PostStepResult
    {
        success  ( 'SUCCESS',  0, 'BLUE'   ),
        unstable ( 'UNSTABLE', 1, 'YELLOW' ),
        all      ( 'FAILURE',  2, 'RED'    )

        final String name
        final int    ordinal
        final String color

        @Requires({ name && color })
        PostStepResult( String name, int ordinal, String color )
        {
            this.name    = name
            this.ordinal = ordinal
            this.color   = color
        }
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
     * Individual properties, *not inherited* from parent job
     * @see #extend
     */
    boolean              isAbstract  = false
    void                 setAbstract( boolean isAbstract ) { this.isAbstract = isAbstract }
    boolean              disabled    = false
    String               displayName = ''

    /**
    * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    * When adding fields:
    * 1) Update {@link #extend(Job)} where current job is "extended" with the "parent Job"
    * 2) DO NOT specify default values, let {@link #extend(Job)} inheritance take care of it.
    * 3) Update {@link #verifyAll} where job configuration is checked for correctness.
    * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    */

    JobType              jobType
    Boolean              buildOnSNAPSHOT
    Boolean              useUpdate
    Boolean              doRevert
    Boolean              privateRepository
    Boolean              archivingDisabled
    Boolean              blockBuildWhenDownstreamBuilding
    Boolean              blockBuildWhenUpstreamBuilding
    Boolean              appendTasks
    Boolean              incrementalBuild
    String               parent
    String               description
    DescriptionRow[]     descriptionTable
    Integer              daysToKeep
    Integer              numToKeep
    Integer              artifactDaysToKeep
    Integer              artifactNumToKeep
    String               scmType
    Task[]               tasks
    Task[]               prebuildersTasks
    Task[]               postbuildersTasks
    String               node
    String               pom
    String               localRepoBase
    String               localRepo
    void                 setLocalRepo ( String localRepo ){ this.localRepo = fixIllegalChars( localRepo, 'Local repo' ) }
    String               localRepoPath
    Artifactory          artifactory
    String               mavenGoals
    String               mavenName
    String               jdkName
    String               mavenOpts
    String               quietPeriod
    String               scmCheckoutRetryCount
    String               gitHubUrl
    Deploy               deploy
    Mail                 mail
    Invoke               invoke
    String               authToken
    PostStepResult       runPostStepsIfResult

    Groovy[]             groovys
    Groovy               groovy
    List<Groovy>         groovys(){ general().list( groovys, groovy )}

    Trigger[]            triggers
    Trigger              trigger
    List<Trigger>        triggers() { general().list( triggers, trigger ) }

    Parameter[]          parameters
    Parameter            parameter
    List<Parameter>      parameters() { general().list( parameters, parameter ) }

    Repository[]         repositories
    Repository           repository
    List<Repository>     repositories() { general().list( repositories, repository ) }


    /**
     * Extension points - tags accepting raw XML content wrapped in <![CDATA[ ... ]]>
     */
    String               scm
    String               reporters
    String               publishers
    String               buildWrappers
    String               properties
    String               prebuilders
    String               postbuilders

    /**
     * Groovy extension point
     */
    String               process

    /**
     * Set by {@link JenkinsMojo#configureJobs}
     */
    String               jenkinsUrl
    String               generationPom
    Boolean              parentIsReal
    Job[]                childJobs
    Job[]                invokedBy

    /**
     * Converts {@link #scmType} to SCM class name.
     */
    String getScmClass()
    {
        assert scmType

        def    scmClass = [ none : 'hudson.scm.NullSCM',
                            cvs  : 'hudson.scm.CVSSCM',
                            svn  : 'hudson.scm.SubversionSCM',
                            git  : 'hudson.plugins.git.GitSCM' ][ scmType ]
        assert scmClass, "Unknown <scmType>${ scmType }</scmType>"
               scmClass
    }


   /**
    * Retrieves job description table
    */
    String getDescriptionTableMarkup() { makeTemplate( '/descriptionTable.html', [ job : this, jenkinsUrl : jenkinsUrl ] ) }


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
    String toString () { "Job \"${ originalId }\"" }


    /**
     * Sets properties specified by calling {@link #set} for each one.
     *
     * @param propertyNames name of the properties to set
     * @param parentJob     job to copy property value from if current job has this property undefined
     * @param override      whether current property should be overridden in any case
     */
    private void setMany( List<String> propertyNames,
                          Job          parentJob,
                          boolean      override )
    {
        assert propertyNames && parentJob
        propertyNames.each { set( it, parentJob, override ) }
    }


    /**
     * Sets the property specified using the value of another job or default value.
     *
     * @param propertyName  name of the property to set
     * @param parentJob     job to copy property value from if current job has this property undefined
     * @param override      whether current property should be overridden in any case
     * @param defaultValue  default value to set to the property if other job has it undefined as well,
     *                      if unspecified or null, then {@code ''} for {@code String},
     *                      {@code false} for {@code Boolean} and {@code -1} for {@code Integer} properties are used
     * @param verifyClosure closure to pass the resulting property values, it can verify its correctness
     */
    private void set( String  propertyName,
                      Job     parentJob,
                      boolean override,
                      Object  defaultValue  = null,
                      Closure verifyClosure = null )
    {
        assert propertyName && parentJob

        final propertyType = metaClass.getMetaProperty( propertyName ).type
        // noinspection GroovyAssignmentToMethodParameter
        defaultValue       = ( defaultValue != null    ) ? defaultValue :
                             ( propertyType == String  ) ? ''           :
                             ( propertyType == Boolean ) ? false        :
                             ( propertyType == Integer ) ? -1           :
                                                           null

        assert ( defaultValue != null ), "Default value should be specified for unknown property type [$propertyType]"

        if (( this[ propertyName ] == null ) || override )
        {
            this[ propertyName ] = parentJob[ propertyName ] ?: defaultValue
        }

        assert ( this[ propertyName ] != null ), "[$this] has null [$propertyName]"

        if ( verifyClosure ) { verifyClosure( this[ propertyName ] ) }
    }


    /**
     * Sets job's tasks using property name and parent job specified.
     *
     * @param propertyName  name of the property to set
     * @param parentJob     job to copy tasks from if current job has tasks undefined
     * @param override      whether current job tasks should be overridden in any case
     */
    private void setTasks( String  propertyName,
                           Job     parentJob,
                           boolean override )
    {
        assert ( propertyName && parentJob )

        Task[] parentTasks   = ( Task[] ) ( parentJob[ propertyName ] ?: [] as Task[] )
        Task[] ourTasks      = ( Task[] ) ( this[ propertyName ]      ?: [] as Task[] )
        this[ propertyName ] = ( override    ) ? parentTasks :
                               ( appendTasks ) ? ( parentTasks.toList() + ourTasks.toList()) as Task[] :
                                                 ourTasks ?: parentTasks

        assert ( this[ propertyName ] != null ), "[$this] has null [$propertyName]"
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
        set( 'description',      parentJob, override, '&nbsp;',      { String  s -> assert s } )
        set( 'scmType',          parentJob, override, 'svn',         { String  s -> assert s } )
        set( 'jobType',          parentJob, override, JobType.maven, { JobType t -> assert t } )
        set( 'node',             parentJob, override, 'master',      { String  s -> assert s } )
        set( 'jdkName',          parentJob, override, '(Default)',   { String  s -> assert s } )
        set( 'mail',             parentJob, override, new Mail())
        set( 'invoke',           parentJob, override, new Invoke())
        set( 'descriptionTable', parentJob, override, new DescriptionRow[ 0 ])

        setMany( split( '|authToken|scm|buildWrappers|properties|publishers|process|quietPeriod|scmCheckoutRetryCount|gitHubUrl' +
                        '|useUpdate|doRevert|blockBuildWhenDownstreamBuilding|blockBuildWhenUpstreamBuilding|appendTasks|daysToKeep' +
                        '|numToKeep|artifactDaysToKeep|artifactNumToKeep', '\\|' ),
                 parentJob, override )

        if ((( ! triggers())   || ( override )) && parentJob.triggers())
        {
            triggers = parentJob.triggers() as Trigger[]
        }

        if ((( ! parameters()) || ( override )) && parentJob.parameters())
        {
            parameters = parentJob.parameters() as Parameter[]
        }
        else if ( parentJob.parameters())
        {   /**
             * Set gives a lower priority to parentJob parameters - parameters having the
             * same name and type *are not taken*, see {@link Parameter#equals(Object)}
             */
            parameters = joinParameters( parentJob.parameters(), parameters()) as Parameter[]
        }

        if ((( ! repositories()) || ( override )) && parentJob.repositories())
        {
            repositories = parentJob.repositories() as Repository[]
        }

        if ((( ! groovys()) || ( override )) && parentJob.groovys())
        {
            groovys = parentJob.groovys() as Groovy[]
        }

        if ( jobType == JobType.free )
        {
            setTasks( 'tasks', parentJob, override )
        }

        if ( jobType == JobType.maven )
        {
            set( 'pom',                    parentJob, override, 'pom.xml',             { String s         -> assert s } )
            set( 'mavenGoals',             parentJob, override, '-B -e clean install', { String s         -> assert s } )
            set( 'runPostStepsIfResult',   parentJob, override, PostStepResult.all,    { PostStepResult r -> assert r } )
            set( 'deploy',                 parentJob, override, new Deploy())
            set( 'artifactory',            parentJob, override, new Artifactory())
            setTasks( 'prebuildersTasks',  parentJob, override )
            setTasks( 'postbuildersTasks', parentJob, override )

            setMany( split( '|mavenName|mavenOpts|reporters|localRepoBase|localRepo|buildOnSNAPSHOT' +
                            '|privateRepository|archivingDisabled|prebuilders|postbuilders|incrementalBuild', '\\|' ),
                     parentJob, override )

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
        assert jobType == JobType.maven

        /**
         * {..} => ${..}
         */
        mavenGoals = verify().notNullOrEmpty( mavenGoals ).addDollar()

        if ( privateRepository )
        {
            assert ( ! ( localRepoBase || localRepo )), "[${this}] has <privateRepository> set, " +
                                                        "<localRepoBase> and <localRepo> shouldn't be specified"
        }
        else if ( localRepoBase || localRepo )
        {
            /**
             * Adding "-Dmaven.repo.local=/x/y/z" using {@link #localRepoBase} and {@link #localRepo}
             * or replacing it with updated value, if already exists
             */

            localRepoPath = (( localRepoBase ?: "${ constants().USER_HOME }/.m2/repository" ) +
                              '/' +
                             ( localRepo     ?: '.' )).
                            replace( '\\', '/' )

            String localRepoArg = "-Dmaven.repo.local=&quot;${ localRepoPath }&quot;"
            mavenGoals          = ( mavenGoals.contains( '-Dmaven.repo.local' )) ?
                                      ( mavenGoals.replaceAll( /-Dmaven.repo.local\S+/, localRepoArg )) :
                                      ( mavenGoals +  ' ' + localRepoArg )
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
         if ( isAbstract ) { return }

         assert id,            "[${ this }] $NOT_CONFIGURED: missing <id>"
         assert jenkinsUrl,    "[${ this }] $NOT_CONFIGURED: missing <jenkinsUrl>"
         assert generationPom, "[${ this }] $NOT_CONFIGURED: missing <generationPom>"
         assert scmClass,      "[${ this }] $NOT_CONFIGURED: unknown <scmType>?"
         assert description,   "[${ this }] $NOT_CONFIGURED: missing <description>"
         assert scmType,       "[${ this }] $NOT_CONFIGURED: missing <scmType>"
         assert jobType,       "[${ this }] $NOT_CONFIGURED: missing <jobType>"
         assert node,          "[${ this }] $NOT_CONFIGURED: missing <node>"
         assert jdkName,       "[${ this }] $NOT_CONFIGURED: missing <jdkName>"

         assert ( authToken             != null ), "[${ this }] $NOT_CONFIGURED: 'authToken' is null?"
         assert ( scm                   != null ), "[${ this }] $NOT_CONFIGURED: 'scm' is null?"
         assert ( properties            != null ), "[${ this }] $NOT_CONFIGURED: 'properties' is null?"
         assert ( publishers            != null ), "[${ this }] $NOT_CONFIGURED: 'publishers' is null?"
         assert ( buildWrappers         != null ), "[${ this }] $NOT_CONFIGURED: 'buildWrappers' is null?"
         assert ( process               != null ), "[${ this }] $NOT_CONFIGURED: 'process' is null?"
         assert ( useUpdate             != null ), "[${ this }] $NOT_CONFIGURED: 'useUpdate' is null?"
         assert ( doRevert              != null ), "[${ this }] $NOT_CONFIGURED: 'doRevert' is null?"
         assert ( daysToKeep            != null ), "[${ this }] $NOT_CONFIGURED: 'daysToKeep' is null?"
         assert ( numToKeep             != null ), "[${ this }] $NOT_CONFIGURED: 'numToKeep' is null?"
         assert ( artifactDaysToKeep    != null ), "[${ this }] $NOT_CONFIGURED: 'artifactDaysToKeep' is null?"
         assert ( artifactNumToKeep     != null ), "[${ this }] $NOT_CONFIGURED: 'artifactNumToKeep' is null?"
         assert ( descriptionTable      != null ), "[${ this }] $NOT_CONFIGURED: 'descriptionTable' is null?"
         assert ( mail                  != null ), "[${ this }] $NOT_CONFIGURED: 'mail' is null?"
         assert ( invoke                != null ), "[${ this }] $NOT_CONFIGURED: 'invoke' is null?"
         assert ( quietPeriod           != null ), "[${ this }] $NOT_CONFIGURED: 'quietPeriod' is null?"
         assert ( scmCheckoutRetryCount != null ), "[${ this }] $NOT_CONFIGURED: 'scmCheckoutRetryCount' is null?"
         assert ( gitHubUrl             != null ), "[${ this }] $NOT_CONFIGURED: 'gitHubUrl' is null?"

         assert ( blockBuildWhenDownstreamBuilding != null ), "[${ this }] $NOT_CONFIGURED: 'blockBuildWhenDownstreamBuilding' is null?"
         assert ( blockBuildWhenUpstreamBuilding   != null ), "[${ this }] $NOT_CONFIGURED: 'blockBuildWhenUpstreamBuilding' is null?"
         assert ( appendTasks                      != null ), "[${ this }] $NOT_CONFIGURED: 'appendTasks' is null?"

         verifyRepositories()

         if ( jobType == JobType.free )
         {
             assert tasks, "[${ this }] $NOT_CONFIGURED: missing '<tasks>'"

             assert ! pom,        "[${ this }] $MIS_CONFIGURED: <pom> is not active in free-style jobs"
             assert ! mavenGoals, "[${ this }] $MIS_CONFIGURED: <mavenGoals> is not active in free-style jobs"
             assert ! mavenName,  "[${ this }] $MIS_CONFIGURED: <mavenName> is not active in free-style jobs"

             assert ( mavenOpts            == null ), "[${ this }] $MIS_CONFIGURED: <mavenOpts> is not active in free-style jobs"
             assert ( buildOnSNAPSHOT      == null ), "[${ this }] $MIS_CONFIGURED: <buildOnSNAPSHOT> is not active in free-style jobs"
             assert ( incrementalBuild     == null ), "[${ this }] $MIS_CONFIGURED: <incrementalBuild> is not active in free-style jobs"
             assert ( privateRepository    == null ), "[${ this }] $MIS_CONFIGURED: <privateRepository> is not active in free-style jobs"
             assert ( archivingDisabled    == null ), "[${ this }] $MIS_CONFIGURED: <archivingDisabled> is not active in free-style jobs"
             assert ( reporters            == null ), "[${ this }] $MIS_CONFIGURED: <reporters> is not active in free-style jobs"
             assert ( localRepoBase        == null ), "[${ this }] $MIS_CONFIGURED: <localRepoBase> is not active in free-style jobs"
             assert ( localRepo            == null ), "[${ this }] $MIS_CONFIGURED: <localRepo> is not active in free-style jobs"
             assert ( deploy               == null ), "[${ this }] $MIS_CONFIGURED: <deploy> is not active in free-style jobs"
             assert ( artifactory          == null ), "[${ this }] $MIS_CONFIGURED: <artifactory> is not active in free-style jobs"
             assert ( prebuilders          == null ), "[${ this }] $MIS_CONFIGURED: <prebuilders> is not active in free-style jobs"
             assert ( postbuilders         == null ), "[${ this }] $MIS_CONFIGURED: <postbuilders> is not active in free-style jobs"
             assert ( prebuildersTasks     == null ), "[${ this }] $MIS_CONFIGURED: <prebuildersTasks> is not active in free-style jobs"
             assert ( postbuildersTasks    == null ), "[${ this }] $MIS_CONFIGURED: <postbuildersTasks> is not active in free-style jobs"
             assert ( runPostStepsIfResult == null ), "[${ this }] $MIS_CONFIGURED: <runPostStepsIfResult> is not active in free-style jobs"
         }
         else if ( jobType == JobType.maven )
         {
             assert ! tasks, "[${ this }] $MIS_CONFIGURED: <tasks> is not active in maven jobs"

             assert pom,        "[${ this }] $NOT_CONFIGURED: missing <pom>"
             assert mavenGoals, "[${ this }] $NOT_CONFIGURED: missing <mavenGoals>"
             assert mavenName,  "[${ this }] $NOT_CONFIGURED: missing <mavenName>"

             assert ( mavenOpts            != null ), "[${ this }] $NOT_CONFIGURED: 'mavenOpts' is null?"
             assert ( buildOnSNAPSHOT      != null ), "[${ this }] $NOT_CONFIGURED: 'buildOnSNAPSHOT' is null?"
             assert ( incrementalBuild     != null ), "[${ this }] $NOT_CONFIGURED: 'incrementalBuild' is null?"
             assert ( privateRepository    != null ), "[${ this }] $NOT_CONFIGURED: 'privateRepository' is null?"
             assert ( archivingDisabled    != null ), "[${ this }] $NOT_CONFIGURED: 'archivingDisabled' is null?"
             assert ( reporters            != null ), "[${ this }] $NOT_CONFIGURED: 'reporters' is null?"
             assert ( localRepoBase        != null ), "[${ this }] $NOT_CONFIGURED: 'localRepoBase' is null?"
             assert ( localRepo            != null ), "[${ this }] $NOT_CONFIGURED: 'localRepo' is null?"
             assert ( deploy               != null ), "[${ this }] $NOT_CONFIGURED: 'deploy' is null?"
             assert ( artifactory          != null ), "[${ this }] $NOT_CONFIGURED: 'artifactory' is null?"
             assert ( prebuilders          != null ), "[${ this }] $NOT_CONFIGURED: 'prebuilders' is null?"
             assert ( postbuilders         != null ), "[${ this }] $NOT_CONFIGURED: 'postbuilders' is null?"
             assert ( prebuildersTasks     != null ), "[${ this }] $NOT_CONFIGURED: 'prebuildersTasks' is null?"
             assert ( postbuildersTasks    != null ), "[${ this }] $NOT_CONFIGURED: 'postbuildersTasks' is null?"
             assert ( runPostStepsIfResult != null ), "[${ this }] $NOT_CONFIGURED: 'runPostStepsIfResult' is null?"

             if ( deploy?.url || artifactory?.name )
             {
                 assert ( ! archivingDisabled ), \
                        "[${ this }] has archiving disabled - artifacts deploy to Maven or Artifactory repository can not be used"
             }

             if ( privateRepository )
             {
                assert ( ! ( localRepoBase || localRepo || localRepoPath )), \
                        "[${ this }] has <privateRepository> specified, no <localRepoBase>, <localRepo>, or <localRepoPath> should be defined"
             }
         }
         else
         {
             throw new IllegalArgumentException ( "Unknown job type [${ jobType }]. " +
                                                  "Known types are \"${JobType.free.name()}\" and \"${JobType.maven.name()}\"" )
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
         for ( repoToCheck in repositories().remote )
         {
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
