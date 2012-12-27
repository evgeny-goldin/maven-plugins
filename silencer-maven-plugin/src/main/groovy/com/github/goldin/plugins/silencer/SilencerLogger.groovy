package com.github.goldin.plugins.silencer

import org.apache.maven.plugin.logging.Log
import org.codehaus.plexus.logging.Logger


/**
 */
class SilencerLogger implements Log, Logger
{
    @Override
    boolean isDebugEnabled (){ false }

    @Override
    void debug ( CharSequence content ){}

    @Override
    void debug ( CharSequence content, Throwable error ){}

    @Override
    void debug ( Throwable error ){}

    @Override
    boolean isInfoEnabled ( ){ false }

    @Override
    void info ( CharSequence content ){}

    @Override
    void info ( CharSequence content, Throwable error ){}

    @Override
    void info ( Throwable error ){}

    @Override
    boolean isWarnEnabled (){ false }

    @Override
    void warn ( CharSequence content ){}

    @Override
    void warn ( CharSequence content, Throwable error ){}

    @Override
    void warn ( Throwable error ){}

    @Override
    boolean isErrorEnabled ( ){ false }

    @Override
    void error ( CharSequence content ){}

    @Override
    void error ( CharSequence content, Throwable error ){}

    @Override
    void error ( Throwable error ){}

    @Override
    void debug ( String message ){}

    @Override
    void debug ( String message, Throwable throwable ){}

    @Override
    void info ( String message ){}

    @Override
    void info ( String message, Throwable throwable ){}

    @Override
    void warn ( String message ){}

    @Override
    void warn ( String message, Throwable throwable ){}

    @Override
    void error ( String message ){}

    @Override
    void error ( String message, Throwable throwable ){}

    @Override
    void fatalError ( String message ){}

    @Override
    void fatalError ( String message, Throwable throwable ){}

    @Override
    boolean isFatalErrorEnabled ( ){ return false }

    @Override
    int getThreshold ( ){ return 0 }

    @Override
    void setThreshold ( int threshold ){}

    @Override
    Logger getChildLogger ( String name ){ return null }

    @Override
    String getName (){ return null }
}
