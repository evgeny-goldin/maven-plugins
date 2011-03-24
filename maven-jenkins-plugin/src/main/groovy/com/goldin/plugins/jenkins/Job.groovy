package com.goldin.plugins.jenkins


import static com.goldin.plugins.common.GMojoUtils.*
import com.goldin.plugins.common.GMojoUtils
import com.goldin.plugins.jenkins.beans.*


 /**
 * Class describing a Jenkins job
 */
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
     * Job types supported
     */
    static enum JOB_TYPE
    {
        free  ( 'Free-Style' ),
        maven ( 'Maven2'     );

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
        this.id         = fixIllegalChars( id, "Job id" )
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
    public void       setJobType( String jobType ){ this.jobType = JOB_TYPE.valueOf( jobType ) } // Maven 2 can't set String to Enum, Maven 3 can
    Boolean              buildOnSNAPSHOT
    Boolean              useUpdate
    Boolean              doRevert
    Boolean              privateRepository
    Boolean              archivingDisabled
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
        this.localRepo = fixIllegalChars( localRepo, "Local repo" )
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
    Trigger[]            triggers() { general().array( this.triggers, this.trigger, Trigger ) }

    Parameter[]          parameters
    Parameter            parameter
    Parameter[]          parameters() { general().array( this.parameters, this.parameter, Parameter ) }

    Repository[]         repositories
    Repository           repository
    Repository[]         repositories() { general().array( this.repositories, this.repository, Repository ) }


    /**
     * Extension points - tags accepting raw XML content wrapped in <![CDATA[ ... ]]>
     */
    String               scm
    String               properties
    String               reporters
    String               publishers
    String               buildWrappers



   /**
    * Retrieves job description table
    */
    String getHtmlDescriptionTable() { GMojoUtils.makeTemplate( '/htmlDescriptionTable.html', [ job : this ] ) }


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
            throw new RuntimeException( "$title [${ id }] is illegal! " +
                                        "It becomes a folder name and the following names are illegal on Windows: ${ ILLEGAL_NAMES.sort() }" )
        }

        /**
         *
         * Leaving only letters/digits, '-', '.' and '_' characters:
         * \w = word character: [a-zA-Z_0-9]
         */
        return s.replaceAll( /[^\w\.-]+/, "-" )
    }


    @Override
    String toString () { "Job \"${ this.originalId }\"" }


    /**
     * Sets the property specified using the value of another job or default value
     */
     def set( String  property,
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
    void extend ( Job parentJob, override = false )
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
        set( 'useUpdate',         parentJob, override, { it }, false )
        set( 'doRevert',          parentJob, override, { it }, false )
        set( 'daysToKeep',        parentJob, override, { it }, -1 )
        set( 'numToKeep',         parentJob, override, { it }, -1 )
        set( 'descriptionTable',  parentJob, override, { it }, new DescriptionRow[ 0 ])
        set( 'mail',              parentJob, override, { it }, new Mail())
        set( 'invoke',            parentJob, override, { it }, new Invoke())

        if ((( ! this.triggers())   || ( override )) && parentJob.triggers())
            { setTriggers ( parentJob.triggers()) }

        if ((( ! this.parameters()) || ( override )) && parentJob.parameters())
        {
            setParameters ( parentJob.parameters())
        }
        else if ( parentJob.parameters())
        {
            /**
             * Set gives a lower priority to parentJob parameters - parameters having the
             * same name and type *are not taken*, see {@link Parameter#equals(Object)}
             */
            def newParams = new HashSet<Parameter>( this.parameters().toList() + parentJob.parameters().toList())
            setParameters( newParams.toArray( new Parameter[ newParams.size() ] ))
        }

        if ((( ! this.repositories()) || ( override )) && parentJob.repositories())
            { setRepositories ( parentJob.repositories()) }

        if ( this.jobType == JOB_TYPE.maven )
        {
            set( 'pom',               parentJob, override, { verify().notNullOrEmpty( it )}, 'pom.xml' )
            set( 'mavenGoals',        parentJob, override, { verify().notNullOrEmpty( it )}, '-e clean install' )
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

            String localRepoArg = "-Dmaven.repo.local=\"${ this.localRepoPath }\""
            this.mavenGoals     = ( this.mavenGoals.contains( '-Dmaven.repo.local' )) ?
                                      ( this.mavenGoals.replaceAll( /-Dmaven.repo.local\S+/, localRepoArg )) :
                                      ( this.mavenGoals +  ' ' + localRepoArg )
        }
    }


    /**
     * Verifies job is fully configured before config file is created
     *
     * All job properties used "as-is" (without "if" and safe navigation operator)
     * in "config.xml" and "htmlDescriptionTable.html" are verified to be defined
     */
     void verifyAll ()
     {
         if ( this.isAbstract ) { return }

         assert this.id,            "[${ this }] is not configured: missing <id>"
         assert this.jenkinsUrl,    "[${ this }] is not configured: missing <jenkinsUrl>"
         assert this.generationPom, "[${ this }] is not configured: missing <generationPom>"
         assert this.getScmClass(), "[${ this }] is not configured: unknown <scmType>?"
         assert this.description,   "[${ this }] is not configured: missing <description>"
         assert this.scmType,       "[${ this }] is not configured: missing <scmType>"
         assert this.jobType,       "[${ this }] is not configured: missing <jobType>"
         assert this.node,          "[${ this }] is not configured: missing <node>"
         assert this.jdkName,       "[${ this }] is not configured: missing <jdkName>"

         assert ( this.authToken         != null ), "[${ this }] is not configured: 'authToken' is null?"
         assert ( this.scm               != null ), "[${ this }] is not configured: 'scm' is null?"
         assert ( this.properties        != null ), "[${ this }] is not configured: 'properties' is null?"
         assert ( this.publishers        != null ), "[${ this }] is not configured: 'publishers' is null?"
         assert ( this.buildWrappers     != null ), "[${ this }] is not configured: 'buildWrappers' is null?"
         assert ( this.useUpdate         != null ), "[${ this }] is not configured: 'useUpdate' is null?"
         assert ( this.doRevert          != null ), "[${ this }] is not configured: 'doRevert' is null?"
         assert ( this.daysToKeep        != null ), "[${ this }] is not configured: 'daysToKeep' is null?"
         assert ( this.numToKeep         != null ), "[${ this }] is not configured: 'numToKeep' is null?"
         assert ( this.descriptionTable  != null ), "[${ this }] is not configured: 'descriptionTable' is null?"
         assert ( this.mail              != null ), "[${ this }] is not configured: 'mail' is null?"
         assert ( this.invoke            != null ), "[${ this }] is not configured: 'invoke' is null?"

         verifyRepositories()

         if ( this.jobType == JOB_TYPE.free )
         {
             assert this.tasks, "[${ this }] is not configured: missing '<tasks>'"
             for ( task in this.tasks )
             {
                 assert ( task.hudsonClass && task.markup ), "Task [$task] - Hudson class or markup is missing"
             }

             assert ! this.pom,        "[${ this }] is mis-configured: <pom> is not active in free-style jobs"
             assert ! this.mavenGoals, "[${ this }] is mis-configured: <mavenGoals> is not active in free-style jobs"
             assert ! this.mavenName,  "[${ this }] is mis-configured: <mavenName> is not active in free-style jobs"
             assert ( this.mavenOpts         == null ), "[${ this }] is mis-configured: <mavenOpts> is not active in free-style jobs"
             assert ( this.buildOnSNAPSHOT   == null ), "[${ this }] is mis-configured: <buildOnSNAPSHOT> is not active in free-style jobs"
             assert ( this.privateRepository == null ), "[${ this }] is mis-configured: <privateRepository> is not active in free-style jobs"
             assert ( this.archivingDisabled == null ), "[${ this }] is mis-configured: <archivingDisabled> is not active in free-style jobs"
             assert ( this.reporters         == null ), "[${ this }] is mis-configured: <reporters> is not active in free-style jobs"
             assert ( this.localRepoBase     == null ), "[${ this }] is mis-configured: <localRepoBase> is not active in free-style jobs"
             assert ( this.localRepo         == null ), "[${ this }] is mis-configured: <localRepo> is not active in free-style jobs"
             assert ( this.deploy            == null ), "[${ this }] is mis-configured: <deploy> is not active in free-style jobs"
             assert ( this.artifactory       == null ), "[${ this }] is mis-configured: <artifactory> is not active in free-style jobs"
         }
         else if ( this.jobType == JOB_TYPE.maven )
         {
             assert ! this.tasks, "[${ this }] is mis-configured: <tasks> is not active in maven jobs"

             assert this.pom,        "[${ this }] is not configured: missing <pom>"
             assert this.mavenGoals, "[${ this }] is not configured: missing <mavenGoals>"
             assert this.mavenName,  "[${ this }] is not configured: missing <mavenName>"

             assert ( this.mavenOpts         != null ), "[${ this }] is not configured: 'mavenOpts' is null?"
             assert ( this.buildOnSNAPSHOT   != null ), "[${ this }] is not configured: 'buildOnSNAPSHOT' is null?"
             assert ( this.privateRepository != null ), "[${ this }] is not configured: 'privateRepository' is null?"
             assert ( this.archivingDisabled != null ), "[${ this }] is not configured: 'archivingDisabled' is null?"
             assert ( this.reporters         != null ), "[${ this }] is not configured: 'reporters' is null?"
             assert ( this.localRepoBase     != null ), "[${ this }] is not configured: 'localRepoBase' is null?"
             assert ( this.localRepo         != null ), "[${ this }] is not configured: 'localRepo' is null?"
             assert ( this.deploy            != null ), "[${ this }] is not configured: 'deploy' is null?"
             assert ( this.artifactory       != null ), "[${ this }] is not configured: 'artifactory' is null?"

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
             assert false, "Unknown job type [${ this.jobType }]"
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

                 if ( repoToCheck.equals( otherRepo ) && (( ++counter ) != 1 ))
                 {
                     /**
                      * Repository should only equal to itself once
                      */

                     throw new RuntimeException(
                         "[${ this }]: Repo [$repoToCheck] is duplicated" )
                 }

                 if (( ! repoToCheck.equals( otherRepo )) && ( otherRepo.toLowerCase().contains( repoToCheck.toLowerCase() + '/' )))
                 {
                     throw new RuntimeException(
                         "[${ this }]: Repo [$repoToCheck] is duplicated in [$otherRepo] - you should remove [$otherRepo]" )
                 }
             }
         }
     }

}
