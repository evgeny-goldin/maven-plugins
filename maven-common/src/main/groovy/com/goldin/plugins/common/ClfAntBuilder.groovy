package com.goldin.plugins.common;


import com.goldin.org.apache.tools.ant.taskdefs.optional.net.FTP

/**
 * {@link groovy.util.AntBuilder} extension to override a definition of "ftp" task
 * to
 * {@link com.goldin.org.apache.tools.ant.taskdefs.optional.net.FTP}
 */
public class ClfAntBuilder extends AntBuilder
{
    public ClfAntBuilder ()
    {
        super();
        getProject().addTaskDefinition( "ftp", FTP.class );
    }
}
