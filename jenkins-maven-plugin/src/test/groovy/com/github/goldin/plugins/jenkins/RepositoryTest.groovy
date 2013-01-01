package com.github.goldin.plugins.jenkins

import static com.github.goldin.plugins.common.GMojoUtils.*
import com.github.goldin.plugins.common.ThreadLocals
import org.apache.maven.plugin.logging.Log
import org.junit.Test


class RepositoryTest
{
    private static Log getLog () { ThreadLocals.get( Log ) }

    RepositoryTest ()
    {
        initTestThreadLocals()
    }


    /**
     * Reads a "/file" specified from the current class classpath and returns a list
     * of its rows, trim()-ed, cleaned up from empty lines and #-comments
     *
     * @param fileName name of the file to read
     */
    @SuppressWarnings( 'UnnecessaryDotClass' )
    private static List<String> read( String fileName )
    {
        def    stream = RepositoryTest.class.getResourceAsStream( "/$fileName" )
        assert stream, "Failed to load [/${ fileName }] from [${ RepositoryTest }] classpath"

        readLines( stream.text ).findAll{ String s -> ! s.startsWith( '#' ) }
    }


    private static checkEquals( Object expected, Object result, String logMessage )
    {
        assert expected == result, logMessage
    }


    @Test
    @SuppressWarnings( 'JUnitTestMethodWithoutAssert' )
    void getRemoteLink()
    {
        log.info( 'getRemoteLink()' )

        read( 'getRemoteLink.txt' ).each {
            def ( String remoteUrl, String httpUrl ) = it.split()
            checkEquals( httpUrl, new Repository( remote : remoteUrl ).remoteLink, "[$remoteUrl] => [$httpUrl]" )
        }
    }


    @Test
    void getGitRemoteBranchLink()
    {
        log.info( 'getGitRemoteBranchLink()' )

        read( 'getGitRemoteBranchLink.txt' ).each {
            def ( String remoteUrl, String branchName, String httpUrl ) = it.split()
            def repository                         = new Repository( remote: remoteUrl, gitBranch: branchName )

            assert repository.isGit()
            checkEquals( httpUrl, repository.gitRemoteBranchLink, "[$remoteUrl] + [$branchName] => [$httpUrl]" )
        }
    }


    @Test
    @SuppressWarnings( 'JUnitTestMethodWithoutAssert' )
    void getRemotePathLink()
    {
        log.info( 'getRemotePathLink()' )

        read( 'getRemotePathLink.txt' ).each {
            def split      = it.split()
            def path       = split[ -2 ]
            def httpUrl    = split[ -1 ]
            def repository = ( split.size() == 3 ) ? new Repository( remote: split[ 0 ] ) :                      // SVN, no branch name
                                                     new Repository( remote: split[ 0 ], gitBranch: split[ 1 ] ) // Git

            checkEquals( httpUrl, repository.getRemotePathLink( path ),
                         "[${ repository.remote }]${ repository.isGit() ? ' + [' + repository.gitBranch + ']' : '' } + [$path] => [$httpUrl]" )
        }
    }
}