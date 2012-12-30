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
     * Error messages to display when jobs is not properly configured
     */
    private String notConfigured( String errorMessage ){ "$this is not configured correctly: $errorMessage" }
    private String misConfigured( String errorMessage ){ "$this is mis-configured: $errorMessage" }

    /**
     * Default Maven goals used in Maven projects and Maven tasks in free-style projects.
     */
    static final String DEFAULT_MAVEN_GOALS = '-B -e clean install'


    /**
     * Job types supported
     */
    static enum JobType
    {
        free  ( 'Free-Style' ),
        maven ( 'Maven'      )

        final String typeDescription

        @Requires({ typeDescription })
        JobType ( String typeDescription )
        {
            this.typeDescription = typeDescription
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
        assert id?.trim()

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
    * 3) Update {@link #validate} where job configuration is validated for correctness.
    * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    */

    JobType              jobType
    Boolean              buildOnSNAPSHOT
    Boolean              useUpdate
    Boolean              doRevert
    Boolean              privateRepository
    Boolean              privateRepositoryPerExecutor
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
    List<Groovy>         groovys(){ generalBean().list( groovys, groovy )}

    Trigger[]            triggers
    Trigger              trigger
    List<Trigger>        triggers() { generalBean().list( triggers, trigger ) }

    Parameter[]          parameters
    Parameter            parameter
    List<Parameter>      parameters() { generalBean().list( parameters, parameter ) }

    Repository[]         repositories
    Repository           repository
    List<Repository>     repositories() { generalBean().list( repositories, repository ) }


    String                                  scmType
    final Map<String, Class<? extends Scm>> scmClasses = [ 'none' : None,
                                                           'cvs'  : Cvs,
                                                           'svn'  : Svn,
                                                           'git'  : Git,
                                                           'hg'   : Hg ].asImmutable()
    Class<? extends Scm> getScmClass()
    {
        if ( repositories() || ( 'none' == scmType ))
        {
            assert scmType
            Class<? extends Scm> scmClass = scmClasses[ scmType ]
            assert scmClass, "Unknown <scmType>$scmType</scmType>, known types are ${ scmClasses.keySet()}"
            scmClass
        }
        else
        {
            null
        }
    }


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
     * Groovy extension points
     */
    String               process
    String[]             processes
    List<String>         processes() { generalBean().list( processes, process ) }

    /**
     * Set by {@link JenkinsMojo#configureJobs}
     */
    String               jenkinsUrl
    String               generationPom
    Job[]                childJobs
    Job[]                invokedBy


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

        assert ( defaultValue != null ), "Default value should be specified for property [$propertyName], unknown property type [$propertyType]"

        if (( this[ propertyName ] == null ) || override )
        {
            this[ propertyName ] = parentJob[ propertyName ] ?: defaultValue
        }

        assert ( this[ propertyName ] != null ), "$this has null [$propertyName]"

        if ( verifyClosure ) { verifyClosure( this[ propertyName ] ) }
    }


    /**
     * Sets job's tasks using property name and parent job specified.
     *
     * @param propertyName  name of the property to set
     * @param parentJob     job to copy tasks from if current job has tasks undefined
     * @param override      whether current job tasks should be overridden in any case
     */
    private void setJobTasks ( String  propertyName,
                               Job     parentJob,
                               boolean override )
    {
        assert ( propertyName && parentJob )

        Task[] parentTasks   = ( Task[] ) ( parentJob[ propertyName ] ?: [] as Task[] )
        Task[] ourTasks      = ( Task[] ) ( this[ propertyName ]      ?: [] as Task[] )
        this[ propertyName ] = ( override    ) ? parentTasks :
                               ( appendTasks ) ? ( parentTasks.toList() + ourTasks.toList()) as Task[] :
                                                 ourTasks ?: parentTasks

        assert ( this[ propertyName ] != null ), "$this has null [$propertyName]"
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
        set( 'jobType',          parentJob, override, JobType.maven, { JobType t -> assert t } )
        set( 'jdkName',          parentJob, override, '(Default)',   { String  s -> assert s } )
        set( 'mail',             parentJob, override, new Mail())
        set( 'invoke',           parentJob, override, new Invoke())
        set( 'descriptionTable', parentJob, override, new DescriptionRow[ 0 ])

        setMany( split( '|authToken|scm|buildWrappers|properties|publishers|quietPeriod|scmCheckoutRetryCount|gitHubUrl' +
                        '|useUpdate|doRevert|blockBuildWhenDownstreamBuilding|blockBuildWhenUpstreamBuilding|appendTasks|daysToKeep' +
                        '|numToKeep|artifactDaysToKeep|artifactNumToKeep', '|' ),
                 parentJob, override )

        //noinspection GroovyConditionalCanBeElvis
        scmType = ((( ! scmType ) || override ) &&  parentJob.scmType ) ? parentJob.scmType :
                  scmType                                               ? scmType :
                  ( repositories().empty && isAbstract )                ? null : // Parent job without repos can't make a proper discovery
                                                                          discoverScmType( 'svn' )
        assert ( scmType || isAbstract )

        if ((( ! processes())  || ( override )) && parentJob.processes())
        {
            processes = parentJob.processes() as String[]
        }

        if ((( ! triggers())   || ( override )) && parentJob.triggers())
        {
            triggers = parentJob.triggers() as Trigger[]
        }

        if ((( ! parameters()) || ( override )) && parentJob.parameters())
        {
            parameters = parentJob.parameters() as Parameter[]
        }
        else if ( parentJob.parameters())
        {
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
            setJobTasks( 'tasks', parentJob, override )
        }

        if ( jobType == JobType.maven )
        {
            set( 'pom',                    parentJob, override, 'pom.xml',           { String s         -> assert s } )
            set( 'mavenGoals',             parentJob, override, DEFAULT_MAVEN_GOALS, { String s         -> assert s } )
            set( 'runPostStepsIfResult',   parentJob, override, PostStepResult.all,  { PostStepResult r -> assert r } )
            set( 'artifactory',            parentJob, override, new Artifactory())
            setJobTasks( 'prebuildersTasks',  parentJob, override )
            setJobTasks( 'postbuildersTasks', parentJob, override )

            setMany( split( '|mavenName|mavenOpts|reporters|localRepoBase|localRepo|buildOnSNAPSHOT' +
                            '|privateRepository|privateRepositoryPerExecutor|archivingDisabled' +
                            '|prebuilders|postbuilders|incrementalBuild', '|' ),
                     parentJob, override )

        }
    }


    /**
     * Attempts to discover {@link #scmType} value given remote repository URL.
     *
     * @param defaultScmType default type to return if unable to make the discovery
     * @return {@link #scmType} value
     */
    private String discoverScmType( String defaultScmType )
    {
        assert defaultScmType

        if ( repositories().empty ) { return isAbstract ? null : defaultScmType }

        assert repositories().first().remote
        scmClasses.keySet().find{ repositories().first().remote.contains( it ) } ?: defaultScmType
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
        mavenGoals = verifyBean().notNullOrEmpty( mavenGoals ).addDollar()

        if ( privateRepository || privateRepositoryPerExecutor )
        {
            assert ( ! ( localRepoBase || localRepo )), "[${this}] has <privateRepository> or <privateRepositoryPerExecutor> set, " +
                                                        "<localRepoBase> and <localRepo> shouldn't be specified"
        }
        else if ( localRepoBase || localRepo )
        {
            /**
             * Adding "-Dmaven.repo.local=/x/y/z" using {@link #localRepoBase} and {@link #localRepo}
             * or replacing it with updated value, if already exists
             */

            localRepoPath = (( localRepoBase ?: "${ constantsBean().USER_HOME }/.m2/repository" ) +
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
     * Validates job is fully configured.
     *
     * @return validated {@link Job} instance
     */
     @SuppressWarnings( 'AbcComplexity' )
     Job validate ()
     {
         if ( isAbstract ) { return this }

         assert id,                                notConfigured( 'missing <id>' )
         assert jenkinsUrl,                        notConfigured( 'missing <jenkinsUrl>' )
         assert generationPom,                     notConfigured( 'missing <generationPom>' )
         assert description,                       notConfigured( 'missing <description>' )
         assert jobType,                           notConfigured( 'missing <jobType>' )
         assert scmType,                           notConfigured( 'missing <scmType>' )
         assert jdkName,                           notConfigured( 'missing <jdkName>' )

         assert ( scm                   != null ), notConfigured( '"scm" is null' )
         assert ( authToken             != null ), notConfigured( '"authToken" is null' )
         assert ( properties            != null ), notConfigured( '"properties" is null' )
         assert ( publishers            != null ), notConfigured( '"publishers" is null' )
         assert ( buildWrappers         != null ), notConfigured( '"buildWrappers" is null' )
         assert ( useUpdate             != null ), notConfigured( '"useUpdate" is null' )
         assert ( doRevert              != null ), notConfigured( '"doRevert" is null' )
         assert ( daysToKeep            != null ), notConfigured( '"daysToKeep" is null' )
         assert ( numToKeep             != null ), notConfigured( '"numToKeep" is null' )
         assert ( artifactDaysToKeep    != null ), notConfigured( '"artifactDaysToKeep" is null' )
         assert ( artifactNumToKeep     != null ), notConfigured( '"artifactNumToKeep" is null' )
         assert ( descriptionTable      != null ), notConfigured( '"descriptionTable" is null' )
         assert ( mail                  != null ), notConfigured( '"mail" is null' )
         assert ( invoke                != null ), notConfigured( '"invoke" is null' )
         assert ( quietPeriod           != null ), notConfigured( '"quietPeriod" is null' )
         assert ( scmCheckoutRetryCount != null ), notConfigured( '"scmCheckoutRetryCount" is null' )
         assert ( gitHubUrl             != null ), notConfigured( '"gitHubUrl" is null' )

         assert ( blockBuildWhenDownstreamBuilding != null ), notConfigured( '"blockBuildWhenDownstreamBuilding" is null' )
         assert ( blockBuildWhenUpstreamBuilding   != null ), notConfigured( '"blockBuildWhenUpstreamBuilding" is null' )
         assert ( appendTasks                      != null ), notConfigured( '"appendTasks" is null' )

         validateRepositories()

         if ( jobType == JobType.free )
         {
             assert tasks,                            notConfigured( 'missing "<tasks>"' )

             assert ! pom,                            misConfigured( '<pom> is not active in free-style jobs' )
             assert ! mavenGoals,                     misConfigured( '<mavenGoals> is not active in free-style jobs' )
             assert ! mavenName,                      misConfigured( '<mavenName> is not active in free-style jobs' )

             assert ( mavenOpts                    == null ), misConfigured( '<mavenOpts> is not active in free-style jobs' )
             assert ( buildOnSNAPSHOT              == null ), misConfigured( '<buildOnSNAPSHOT> is not active in free-style jobs' )
             assert ( incrementalBuild             == null ), misConfigured( '<incrementalBuild> is not active in free-style jobs' )
             assert ( privateRepository            == null ), misConfigured( '<privateRepository> is not active in free-style jobs' )
             assert ( privateRepositoryPerExecutor == null ), misConfigured( '<privateRepositoryPerExecutor> is not active in free-style jobs' )
             assert ( archivingDisabled            == null ), misConfigured( '<archivingDisabled> is not active in free-style jobs' )
             assert ( reporters                    == null ), misConfigured( '<reporters> is not active in free-style jobs' )
             assert ( localRepoBase                == null ), misConfigured( '<localRepoBase> is not active in free-style jobs' )
             assert ( localRepo                    == null ), misConfigured( '<localRepo> is not active in free-style jobs' )
             assert ( deploy                       == null ), misConfigured( '<deploy> is not active in free-style jobs' )
             assert ( artifactory                  == null ), misConfigured( '<artifactory> is not active in free-style jobs' )
             assert ( prebuilders                  == null ), misConfigured( '<prebuilders> is not active in free-style jobs' )
             assert ( postbuilders                 == null ), misConfigured( '<postbuilders> is not active in free-style jobs' )
             assert ( prebuildersTasks             == null ), misConfigured( '<prebuildersTasks> is not active in free-style jobs' )
             assert ( postbuildersTasks            == null ), misConfigured( '<postbuildersTasks> is not active in free-style jobs' )
             assert ( runPostStepsIfResult         == null ), misConfigured( '<runPostStepsIfResult> is not active in free-style jobs' )
         }
         else if ( jobType == JobType.maven )
         {
             assert ! tasks,                          misConfigured( '<tasks> is not active in maven jobs' )

             assert pom,                              notConfigured( 'missing <pom>' )
             assert mavenGoals,                       notConfigured( 'missing <mavenGoals>' )
             assert mavenName,                        notConfigured( 'missing <mavenName>' )

             assert ( mavenOpts                    != null ), notConfigured( '"mavenOpts" is null' )
             assert ( buildOnSNAPSHOT              != null ), notConfigured( '"buildOnSNAPSHOT" is null' )
             assert ( incrementalBuild             != null ), notConfigured( '"incrementalBuild" is null' )
             assert ( privateRepository            != null ), notConfigured( '"privateRepository" is null' )
             assert ( privateRepositoryPerExecutor != null ), notConfigured( '"privateRepositoryPerExecutor" is null' )
             assert ( archivingDisabled            != null ), notConfigured( '"archivingDisabled" is null' )
             assert ( reporters                    != null ), notConfigured( '"reporters" is null' )
             assert ( localRepoBase                != null ), notConfigured( '"localRepoBase" is null' )
             assert ( localRepo                    != null ), notConfigured( '"localRepo" is null' )
             assert ( artifactory                  != null ), notConfigured( '"artifactory" is null' )
             assert ( prebuilders                  != null ), notConfigured( '"prebuilders" is null' )
             assert ( postbuilders                 != null ), notConfigured( '"postbuilders" is null' )
             assert ( prebuildersTasks             != null ), notConfigured( '"prebuildersTasks" is null' )
             assert ( postbuildersTasks            != null ), notConfigured( '"postbuildersTasks" is null' )
             assert ( runPostStepsIfResult         != null ), notConfigured( '"runPostStepsIfResult" is null' )

             if ( deploy?.url || artifactory?.name )
             {
                 assert ( ! archivingDisabled ), \
                        "$this has archiving disabled - artifacts deploy to Maven or Artifactory repository can not be used"
             }

             assert ( ! ( privateRepository && privateRepositoryPerExecutor )), \
                    "$this - both <privateRepository> and <privateRepositoryPerExecutor> can't be set to \"true\""

             if ( privateRepository || privateRepositoryPerExecutor )
             {
                assert ( ! ( localRepoBase || localRepo || localRepoPath )), \
                        "$this has <privateRepository> or <privateRepositoryPerExecutor> specified, " +
                        "no <localRepoBase>, <localRepo>, or <localRepoPath> should be defined"
             }
         }
         else
         {
             throw new IllegalArgumentException ( "Unknown job type [${ jobType }]. " +
                                                  "Known types are \"${ JobType.free.name() }\" and \"${ JobType.maven.name() }\"" )
         }

         this
     }


    /**
     * Validates remote repositories for correctness.
     */
     void validateRepositories ()
     {
         if ( gitHubUrl )
         {
            assert repositories(), "$this: Missing <repository> or <repositories>"
         }

         if ( repositories())
         {
            assert scmType, "$this: Missing <scmType>"
         }

         for ( repo in repositories().remote )
         {
             int counter = 0

             repositories().remote.each
             {
                 String otherRepo ->

                 if (( repo == otherRepo ) && (( ++counter ) != 1 ))
                 {
                     /**
                      * Repository should only equal to itself once
                      */

                     throw new MojoExecutionException( "$this: Repo [$repo] is duplicated" )
                 }

                 if (( ! ( repo == otherRepo )) && ( otherRepo.toLowerCase().contains( repo.toLowerCase() + '/' )))
                 {
                     throw new MojoExecutionException(
                         "$this: Repo [$repo] is duplicated in [$otherRepo] - you should remove [$otherRepo]" )
                 }
             }
         }
     }
}
