package com.github.goldin.plugins.jenkins

import groovy.xml.MarkupBuilder
import org.gcontracts.annotations.Ensures
import org.gcontracts.annotations.Requires


abstract class Scm
{
    Job              job
    List<Repository> repositories
    MarkupBuilder    builder

    @Ensures({ result })
    abstract String getScmClass()

    /**
     * Adds SCM-specific markup to the {@link #builder}.
     */
    abstract void addMarkup ()


    /**
     * Adds tag specified to the builder only if value specified evaluates to true according to the Groovy Truth.
     */
    final void add ( String tagName, Object value )
    {
        if ( value ) { builder."$tagName"( value ) }
    }


    /**
     * Builds SCM section markup.
     *
     * @return SCM section markup
     */
    @Requires({ builder })
    final String addMarkup( MarkupBuilder builder )
    {
        this.builder = builder
        assert this.builder && job && ( repositories || ( this.class == None ))
        builder.scm( class: scmClass ) { addMarkup() }
    }
}


class None extends Scm
{
    @Override
    String getScmClass(){ 'hudson.scm.NullSCM' }

    @Override
    void addMarkup (){}
}


class Cvs extends Scm
{
    @Override
    String getScmClass(){ 'hudson.scm.CVSSCM' }

    @Override
    void addMarkup ()
    {
        assert repositories.size() == 1, "[${ job }] - multiple CVS repositories are not supported"
        final repository = repositories.first()

        builder.with {

            cvsroot( repository.remote )
            add( 'module', repository.cvsModule )
            add( 'branch', repository.cvsBranch )
            add( 'cvsRsh', repository.cvsRsh )
            canUseUpdate( repository.cvsUpdate )
            flatten( ! repository.cvsLegacy )

            if ( repository.cvsRepoBrowserClass && repository.repoBrowserUrl )
            {
                repositoryBrowser( class: repository.cvsRepoBrowserClass ) {
                    url( repository.repoBrowserUrl )
                }
            }

            isTag( repository.cvsTag )
            excludedRegions( repository.cvsExcludedRegions )
        }
    }
}


class Svn extends Scm
{
    @Override
    String getScmClass(){ 'hudson.scm.SubversionSCM' }

    @Override
    void addMarkup ()
    {
        builder.with {

            locations {
                for ( repository in repositories ) {
                    "${ scmClass }_-ModuleLocation" {
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


class Git extends Scm
{
    @Override
    String getScmClass(){ 'hudson.plugins.git.GitSCM' }

    @Override
    void addMarkup ()
    {
        final gitRepository = repositories.first()

        builder.with {

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
                browser( class: gitRepository.gitRepoBrowserClass ) {
                    url( gitRepository.repoBrowserUrl )
                }
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


@SuppressWarnings([ 'GroovyClassNamingConvention' ])
class Hg extends Scm
{
    @Override
    String getScmClass(){ 'hudson.plugins.mercurial.MercurialSCM' }

    @Override
    void addMarkup ()
    {
        assert repositories.size() == 1, "[${ job }] - multiple Mercurial repositories are not supported"
        final repository = repositories.first()

        builder.with {

            source ( repository.remote )
            modules( repository.hgModules )
            add( 'branch', repository.hgBranch )
            add( 'subdir', repository.hgSubdir )
            clean( repository.hgClean )

            if ( repository.hgRepoBrowserClass && repository.repoBrowserUrl )
            {
                browser( class: repository.hgRepoBrowserClass ) {
                    url( repository.repoBrowserUrl )
                }
            }
        }
    }
}
