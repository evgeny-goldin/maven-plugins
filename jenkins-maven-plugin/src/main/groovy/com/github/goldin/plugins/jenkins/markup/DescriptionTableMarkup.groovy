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

                addRow( 'Job type',     job.jobType.description )
                addRow( 'Node',         { addNodeLink( job.node )})
                addRow( 'Quiet period', job.quietPeriod,           true )
                addRow( 'Retry count',  job.scmCheckoutRetryCount, true )
                addRow( 'JDK name',     job.jdkName,               true, true )

                if ( job.jobType == Job.JobType.maven )
                {
                    addMavenRows()
                }
            }
        }
    }


    @Requires({ link && title })
    @Ensures({ result })
    void addHref    ( String link, String title ) { builder.a( href: link ){ strong( title ) }}
    void addJobLink ( String jobId  )             { addHref( "${ job.jenkinsUrl }/job/${ jobId }",    jobId  ) }
    void addNodeLink( String nodeId )             { addHref( "${ job.jenkinsUrl }/label/${ nodeId }", nodeId ) }


    /**
     * Adds a table row to {@link #builder}.
     *
     * @param title   row title
     * @param value   row value to display
     * @param isCode  whether value should be quoted
     * @param isQuote whether value should be displayed as {@code <code>}
     */
    @Requires({ title })
    void addRow ( String title, String value, boolean isCode = false, boolean isQuote = false )
    {
        if ( value )
        {   // noinspection GroovyAssignmentToMethodParameter
            value = ( isQuote ? '"' + value + '"' : value )
            addRow( title, { isCode ? builder.strong { code ( value ) } : add( value ) })
        }
    }


    @Requires({ title })
    void addRow ( String title, Closure value )
    {
        builder.with {
            tr {
                td( valign: 'top', title )
                td(){ value() }
            }
        }
    }

    void addMavenRows ( )
    {
        builder.with {

            final repoPath = ( job.privateRepository ?            ".jenkins/jobs/${ job.id }/workspace/.repository" :
                               job.privateRepositoryPerExecutor ? '.jenkins/maven-repositories/X'                   :
                                                                  job.localRepoPath ?: '.m2/repository' )
            if ( job.prebuildersTasks )
            {
                addRow( 'Pre-build steps', { addTasks( job.prebuildersTasks ) })
            }
        }
    }


    @Requires({ tasks })
    void addTasks ( Task[] tasks )
    {
        assert tasks

        builder.with {
            if ( tasks.size() == 1 )
            {
                add( "- ${    tag( 'strong', tasks[ 0 ].descriptionTableTitle )} : " +
                     "$QUOT${ tag( 'code',   tasks[ 0 ].commandShortened )}$QUOT" )
            }
            else
            {
                table {
                    for ( task in tasks ) {
                        tr {
                            td{ add ( "- ${    tag( 'strong', task.descriptionTableTitle )}" )}
                            td( ' : ' )
                            td{ add ( "$QUOT${ tag( 'code',   task.commandShortened )}$QUOT" )}
                        }
                    }
                }
            }
        }
    }
}
