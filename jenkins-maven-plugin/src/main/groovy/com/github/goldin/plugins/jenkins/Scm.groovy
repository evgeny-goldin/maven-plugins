package com.github.goldin.plugins.jenkins

import com.github.goldin.plugins.jenkins.markup.Markup
import groovy.xml.MarkupBuilder
import org.gcontracts.annotations.Requires


abstract class Scm extends Markup
{
    final Job              job
    final List<Repository> repositories


    @Requires({ builder && job })
    Scm( MarkupBuilder builder, Job job )
    {
        super( builder )

        this.job          = job
        this.repositories = job.repositories()

        assert this.builder && this.job && ( this.repositories || ( this.class == None ))
    }
}


class None extends Scm
{
    None( MarkupBuilder builder, Job job ){ super( builder, job )}

    @Override
    void buildMarkup(){ builder.scm( class: 'hudson.scm.NullSCM' ){}}
}


class Cvs extends Scm
{
    Cvs( MarkupBuilder builder, Job job ){ super( builder, job )}

    @Override
    void buildMarkup ()
    {
        assert repositories.size() == 1, "[${ job }] - multiple CVS repositories are not supported"
        final repository = repositories.first()

        builder.with {
            scm( class: 'hudson.scm.CVSSCM' ) {
                cvsroot( repository.remote )
                add( 'module', repository.cvsModule )
                add( 'branch', repository.cvsBranch )
                add( 'cvsRsh', repository.cvsRsh )
                canUseUpdate( repository.cvsUpdate )
                flatten( ! repository.cvsLegacy )

                if ( repository.cvsRepoBrowserClass && repository.repoBrowserUrl )
                {
                    repositoryBrowser( class: repository.cvsRepoBrowserClass ) { url( repository.repoBrowserUrl ) }
                }

                isTag( repository.cvsTag )
                excludedRegions( repository.cvsExcludedRegions )
            }
        }
    }
}


class Svn extends Scm
{
    Svn( MarkupBuilder builder, Job job ){ super( builder, job )}

    @Override
    void buildMarkup ()
    {
        builder.with {
            scm( class: 'hudson.scm.SubversionSCM' ) {
                locations {
                    for ( repository in repositories ) {
                        'hudson.scm.SubversionSCM_-ModuleLocation' {
                            remote( repository.remote )
                            add( 'local', repository.local )
                        }
                    }
                }
                useUpdate( job.useUpdate )
                doRevert( job.doRevert )
            }
        }
    }
}


class Git extends Scm
{
    Git( MarkupBuilder builder, Job job ){ super( builder, job )}

    @Override
    void buildMarkup ()
    {
        final gitRepository = repositories.first()

        builder.with {

            scm( class:  'hudson.plugins.git.GitSCM' ) {

                configVersion( 2 )
                userRemoteConfigs {
                    for ( repository in repositories ) {
                        'hudson.plugins.git.UserRemoteConfig' {
                            name( repository.gitName )
                            refspec( repository.gitRefspec )
                            url( repository.remote )
                        }
                    }
                }

                branches {
                    for ( repository in repositories ) {
                        'hudson.plugins.git.BranchSpec' {
                            name( repository.gitBranch )
                        }
                    }
                }
                add( 'localBranch', gitRepository.gitLocalBranch )

                if ( gitRepository.gitMergeRepo || gitRepository.gitMergeBranch )
                {
                    userMergeOptions {
                        mergeRemote( gitRepository.gitMergeRepo )
                        mergeTarget( gitRepository.gitMergeBranch )
                    }
                }

                recursiveSubmodules( gitRepository.gitUpdateSubmodules )
                doGenerateSubmoduleConfigurations( false )
                authorOrCommitter( gitRepository.gitCommitAuthor )
                clean( gitRepository.gitCleanAfterCheckout )
                wipeOutWorkspace( gitRepository.gitWipeOutWorkspace )
                pruneBranches( gitRepository.gitPruneBranches )
                remotePoll( gitRepository.gitRemotePolling )
                buildChooser( class: 'hudson.plugins.git.util.DefaultBuildChooser' )
                gitTool( 'Default' )

                if ( gitRepository.gitRepoBrowserClass && gitRepository.repoBrowserUrl )
                {
                    browser( class: gitRepository.gitRepoBrowserClass ) { url( gitRepository.repoBrowserUrl ) }
                }

                submoduleCfg( class: 'list' )
                relativeTargetDir( gitRepository.gitLocalSubdirectory )
                excludedRegions( gitRepository.gitExcludedRegions )
                excludedUsers( gitRepository.gitExcludedUsers )
                gitConfigName( gitRepository.gitConfigName )
                gitConfigEmail( gitRepository.gitConfigEmail )
                skipTag( gitRepository.gitSkipTag )
                scmName( gitRepository.gitScmName )
            }
        }
    }
}


@SuppressWarnings([ 'GroovyClassNamingConvention' ])
class Hg extends Scm
{
    Hg( MarkupBuilder builder, Job job ){ super( builder, job )}

    @Override
    void buildMarkup ()
    {
        assert repositories.size() == 1, "[${ job }] - multiple Mercurial repositories are not supported"
        final repository = repositories.first()

        builder.with {
            scm( class: 'hudson.plugins.mercurial.MercurialSCM' ) {
                source ( repository.remote )
                modules( repository.hgModules )
                add( 'branch', repository.hgBranch )
                add( 'subdir', repository.hgSubdir )
                clean( repository.hgClean )

                if ( repository.hgRepoBrowserClass && repository.repoBrowserUrl )
                {
                    browser( class: repository.hgRepoBrowserClass ) { url( repository.repoBrowserUrl ) }
                }
            }
        }
    }
}
