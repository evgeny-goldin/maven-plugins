package com.github.goldin.plugins.silencer

import org.apache.maven.plugin.logging.Log


/**
 */
class SilencerLogger implements Log
{
    @Override
    boolean isDebugEnabled (){ false }

    @Override
    void debug ( CharSequence content ){}

    @Override
    void debug ( CharSequence content , Throwable error ){}

    @Override
    void debug ( Throwable error ){}

    @Override
    boolean isInfoEnabled ( ){ false }

    @Override
    void info ( CharSequence content ){}

    @Override
    void info ( CharSequence content , Throwable error ){}

    @Override
    void info ( Throwable error ){}

    @Override
    boolean isWarnEnabled (){ false }

    @Override
    void warn ( CharSequence content ){}

    @Override
    void warn ( CharSequence content , Throwable error ){}

    @Override
    void warn ( Throwable error ){}

    @Override
    boolean isErrorEnabled ( ){ false }

    @Override
    void error ( CharSequence content ){}

    @Override
    void error ( CharSequence content , Throwable error ){}

    @Override
    void error ( Throwable error ){}
}
