package com.github.goldin.plugins.silencer


/**
 * Logger swallowing all messages sent to it.
 */
class SilentLogger extends    org.apache.log4j.Logger
                   implements org.apache.maven.plugin.logging.Log,
                              org.codehaus.plexus.logging.Logger,
                              org.apache.commons.logging.Log,
                              org.sonatype.aether.spi.log.Logger,
                              org.slf4j.Logger
{
    SilentLogger (){ super( 'SilentLogger' ) }

    @Override
    boolean isDebugEnabled (){ false }

    @Override
    void debug ( CharSequence content ){}

    @Override
    void debug ( CharSequence content, Throwable error ){}

    @Override
    void debug ( Throwable error ){}

    @Override
    boolean isInfoEnabled (){ false }

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
    boolean isErrorEnabled (){ false }

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
    boolean isFatalErrorEnabled (){ false }

    @Override
    int getThreshold (){ 0 }

    @Override
    void setThreshold ( int threshold ){}

    @Override
    org.codehaus.plexus.logging.Logger getChildLogger ( String name ){ null }

    @Override
    boolean isFatalEnabled (){ false }

    @Override
    void trace ( String s ){}

    @Override
    void trace ( String s, Object o ){}

    @Override
    void trace ( String s, Object o, Object o1 ){}

    @Override
    void trace ( String s, Object... objects ){}

    @Override
    void trace ( String s, Throwable throwable ){}

    @Override
    boolean isTraceEnabled ( org.slf4j.Marker marker ){ false  }

    @Override
    void trace ( org.slf4j.Marker marker, String s ){}

    @Override
    void trace ( org.slf4j.Marker marker, String s, Object o ){}

    @Override
    void trace ( org.slf4j.Marker marker, String s, Object o, Object o1 ){}

    @Override
    void trace ( org.slf4j.Marker marker, String s, Object... objects ){}

    @Override
    void trace ( org.slf4j.Marker marker, String s, Throwable throwable ){}

    @Override
    void debug ( String s, Object o ){}

    @Override
    void debug ( String s, Object o, Object o1 ){}

    @Override
    void debug ( String s, Object... objects ){}

    @Override
    boolean isDebugEnabled ( org.slf4j.Marker marker ){ false }

    @Override
    void debug ( org.slf4j.Marker marker, String s ){}

    @Override
    void debug ( org.slf4j.Marker marker, String s, Object o ){}

    @Override
    void debug ( org.slf4j.Marker marker, String s, Object o, Object o1 ){}

    @Override
    void debug ( org.slf4j.Marker marker, String s, Object... objects ){}

    @Override
    void debug ( org.slf4j.Marker marker, String s, Throwable throwable ){}

    @Override
    void info ( String s, Object o ){}

    @Override
    void info ( String s, Object o, Object o1 ){}

    @Override
    void info ( String s, Object... objects ){}

    @Override
    boolean isInfoEnabled ( org.slf4j.Marker marker ){ false }

    @Override
    void info ( org.slf4j.Marker marker, String s ){}

    @Override
    void info ( org.slf4j.Marker marker, String s, Object o ){}

    @Override
    void info ( org.slf4j.Marker marker, String s, Object o, Object o1 ){}

    @Override
    void info ( org.slf4j.Marker marker, String s, Object... objects ){}

    @Override
    void info ( org.slf4j.Marker marker, String s, Throwable throwable ){}

    @Override
    void warn ( String s, Object o ){}

    @Override
    void warn ( String s, Object... objects ){}

    @Override
    void warn ( String s, Object o, Object o1 ){}

    @Override
    boolean isWarnEnabled ( org.slf4j.Marker marker ){ false }

    @Override
    void warn ( org.slf4j.Marker marker, String s ){}

    @Override
    void warn ( org.slf4j.Marker marker, String s, Object o ){}

    @Override
    void warn ( org.slf4j.Marker marker, String s, Object o, Object o1 ){}

    @Override
    void warn ( org.slf4j.Marker marker, String s, Object... objects ){}

    @Override
    void warn ( org.slf4j.Marker marker, String s, Throwable throwable ){}

    @Override
    void error ( String s, Object o ){}

    @Override
    void error ( String s, Object o, Object o1 ){}

    @Override
    void error ( String s, Object... objects ){}

    @Override
    boolean isErrorEnabled ( org.slf4j.Marker marker ){ false }

    @Override
    void error ( org.slf4j.Marker marker, String s ){}

    @Override
    void error ( org.slf4j.Marker marker, String s, Object o ){}

    @Override
    void error ( org.slf4j.Marker marker, String s, Object o, Object o1 ){}

    @Override
    void error ( org.slf4j.Marker marker, String s, Object... objects ){}

    @Override
    void error ( org.slf4j.Marker marker, String s, Throwable throwable ){}
}
