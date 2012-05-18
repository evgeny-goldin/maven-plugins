package com.github.goldin.plugins.jenkins.markup

import static com.github.goldin.plugins.common.GMojoUtils.*
import com.github.goldin.plugins.jenkins.Job
import com.github.goldin.plugins.jenkins.Task
import com.github.goldin.plugins.jenkins.beans.DescriptionRow
import org.gcontracts.annotations.Ensures
import org.gcontracts.annotations.Requires


/**
 * Builds description table markup.
 */
class DescriptionTableMarkup extends Markup
{
    private final Job job


    @Requires({ job })
    DescriptionTableMarkup ( Job job )
    {
        this.job = job
    }


    @Override
    void buildMarkup ()
    {
        builder.with {
            table( border: '1', width: '100%', cellpadding:'3', cellspacing:'3' ) {

                tr {
                    td( width: '15%', valign: 'top', 'Job' )
                    td( width: '85%' ){ addJobLink( job.id ) }
                }

                for ( row in ( Collection<DescriptionRow> ) job.descriptionTable.findAll{ ! it.bottom } )
                {
                    addRow( row.key, row.value )
                }

                addRow( 'Display name', job.displayName )

                if ( job.parent ){ addRow( 'Parent job', {
                    job.parentIsReal ? addJobLink( job.parent ) : add( job.parent )
                })}

                if ( job.childJobs ){ addRow( 'Child job' + general().s( job.childJobs.size()), {
                    job.childJobs.eachWithIndex {
                        Job childJob, int index ->
                        addJobLink( childJob.id )
                        if ( index < ( job.childJobs.size() - 1 )) { add( ', ' ) }
                    }
                })}

                addRow( 'Job type',        job.jobType.description )
                addRow( 'Node',            { addNodeLink( job.node )})
                addRow( 'JDK name',        job.jdkName, true )
                addRow( 'Mail recipients', job.mail?.recipients )
                addRow( 'Quiet period',    job.quietPeriod )
                addRow( 'Retry count',     job.scmCheckoutRetryCount )

                if ( job.scmType == 'svn' )
                {
                    addRow( 'Svn update policy', "Revert - [${ job.doRevert }], update - [${ job.useUpdate }], checkout - [${ ! job.useUpdate }]" )
                }

                if ( job.repositories())               { addRepositories() }
                if ( job.triggers())                   { addTriggers    () }
                if ( job.jobType == Job.JobType.maven ){ addMavenRows   () }
                if ( job.jobType == Job.JobType.free  ){ addRow( 'Build steps', { addTasks( job.tasks ) })}

                for ( row in ( Collection<DescriptionRow> ) job.descriptionTable.findAll{ it.bottom } )
                {
                    addRow( row.key, row.value )
                }
            }
        }
    }


    /**
     * Adds repositories row to the table.
     */
    void addRepositories()
    {
        addRow( "${ job.scmType.capitalize() } repositor${ ( job.repositories().size() == 1 ) ? 'y' : 'ies' }", {
            for ( repository in job.repositories())
            {
                addBullet( job.repositories())
                addLink( repository.remoteLink, repository.remote )
            }
        })
    }


    /**
     * Adds triggers row to the table.
     */
    void addTriggers()
    {
        addRow( 'Triggers', {
            for ( trigger in job.triggers())
            {
                addBullet( job.triggers())
                add( strong ( code( trigger.type )))
                if ( trigger.expression  ){ add( " : ${ code( trigger.expression )}" ) }
                if ( trigger.description ){ builder.em( "(${  trigger.description })" )}
            }
        })
    }



    /**
     * Adds Maven rows to the table.
     */
    void addMavenRows ( )
    {
        builder.with {

            final repoPath = ( job.privateRepository ?            ".jenkins/jobs/${ job.id }/workspace/.repository" :
                               job.privateRepositoryPerExecutor ? '.jenkins/maven-repositories/X'                   :
                                                                  job.localRepoPath ?: '.m2/repository' )

            if ( job.prebuildersTasks ){ addRow( 'Pre-build steps', { addTasks( job.prebuildersTasks ) })}

            addRow( 'Maven name',       job.mavenName,  true )
            addRow( 'Maven goals',      job.mavenGoals, true )
            addRow( 'Maven repository', repoPath,       true )
            addRow( 'Maven options',    job.mavenOpts,  true )

            if ( job.postbuildersTasks ){ addRow( 'Post-build steps', { addTasks( job.postbuildersTasks ) })}
            if ( job.artifactory?.name ){ addArtifactory() }
        }
    }


    /**
     * Adds Artifactory row to the table.
     */
    void addArtifactory()
    {
        addRow( 'Deployed to Artifactory', {
            addLink ( job.artifactory.name, job.artifactory.name )
            add     ( ' =&gt; ' )
            addLink ( "${ job.artifactory.name }/${ job.artifactory.repository }/", job.artifactory.repository )
        })
    }


    /**
     * Adds a link to the {@link #builder}.
     * @param link  HTTP link address
     * @param title link title
     */
    @Requires({ link && title })
    @Ensures({ result })
    void   addLink    ( String link, String title ){ builder.a( href: link ){ add( strong( title )) }}


    /**
     * Various small helper methods.
     */

    void   addJobLink ( String   jobId   ){ addLink( "${ job.jenkinsUrl }/job/${ jobId }",    jobId  )}
    void   addNodeLink( String   nodeId  ){ addLink( "${ job.jenkinsUrl }/label/${ nodeId }", nodeId )}
    void   addBullet  ( Object[] objects ){ if ( objects.size() > 1 ){ add( '- ' ) }}
    String tag        ( String tagName, String value ){ "<$tagName>$value</$tagName>" }
    String code       ( String expression )           { tag( 'code',   QUOT + expression + QUOT )}
    String strong     ( String expression )           { tag( 'strong', expression )}


    /**
     * Adds a table row to the {@link #builder}.
     *
     * @param title   row title
     * @param value   row value to display
     * @param isCode  whether value should be quoted
     */
    @Requires({ title })
    void addRow ( String title, String value, boolean isCode = false )
    {
        if ( value ) { addRow( title, { add( isCode ? strong ( code ( value )) : value ) })}
    }


    /**
     * Adds a table row to the {@link #builder}.
     *
     * @param title         row title
     * @param valueClosure  {@link Closure} to invoke to add the value to the {@link #builder}
     */
    @Requires({ title })
    void addRow ( String title, Closure valueClosure )
    {
        builder.with {
            tr {
                td( valign: 'top', title )
                td(){ valueClosure() }
            }
        }
    }


    /**
     * Adds tasks specified to the {@link #builder}.
     * @param tasks task to add
     */
    @Requires({ tasks })
    void addTasks ( Task[] tasks )
    {
        assert tasks

        builder.with {
            if ( tasks.size() == 1 )
            {
                add( "${ strong( tasks[ 0 ].descriptionTableTitle )} : " +
                     "${ code  ( tasks[ 0 ].commandShortened )}" )
            }
            else
            {
                table {
                    for ( task in tasks ) {
                        tr {
                            td{ add ( "- ${ strong( task.descriptionTableTitle )}" )}
                            td{ add ( ' : ' )}
                            td{ add ( "${ code(     task.commandShortened )}" )}
                        }
                    }
                }
            }
        }
    }
}
