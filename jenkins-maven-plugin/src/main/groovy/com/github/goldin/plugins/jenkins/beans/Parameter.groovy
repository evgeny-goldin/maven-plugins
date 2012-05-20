package com.github.goldin.plugins.jenkins.beans

import com.github.goldin.plugins.jenkins.markup.Markup
import org.gcontracts.annotations.Requires


enum  ParameterType { bool, choice, string, password, run, file, jira }
class Parameter extends Markup
{
    String        name        = 'UNDEFINED'
    ParameterType type        = null
    String        value       = ''
    String        description = ''

    /**
     * Invoked by Maven.
     */
    @Requires({ type })
    void setType( String type ){ this.type = ParameterType.valueOf( ParameterType, type ) }

    @Override
    int hashCode ()            { "[$name][$type]".hashCode()  }

    @Override
    boolean equals( Object o ) { ( o instanceof Parameter ) && ( this.name == o.name ) && ( this.type == o.type ) }

    @Override
    void addMarkup ()
    {
        final nameDescription = { builder.name( name ); builder.description( description )}

        builder.with {
            switch ( type )
            {
                case ParameterType.bool:
                    'hudson.model.BooleanParameterDefinition' {
                        nameDescription()
                        defaultValue( Boolean.valueOf( value ))
                    }
                    break

                case ParameterType.choice:
                    'hudson.model.ChoiceParameterDefinition' {
                        nameDescription()
                        choices( class: 'java.util.Arrays$ArrayList' ) {
                            a( class: 'string-array' ) {
                                value.split( /\s*,\s*/ ).each { string( it ) }
                            }
                        }
                    }
                    break

                case ParameterType.string:
                    'hudson.model.StringParameterDefinition' {
                        nameDescription()
                        defaultValue( value )
                    }
                    break

                case ParameterType.password:
                    'hudson.model.PasswordParameterDefinition' {
                        nameDescription()
                        defaultValue( value )
                    }
                    break

                case ParameterType.run:
                    'hudson.model.RunParameterDefinition' {
                        nameDescription()
                        projectName( value )
                    }
                    break

                case ParameterType.file:
                    'hudson.model.FileParameterDefinition' {
                        nameDescription()
                    }
                    break

                case ParameterType.jira:
                    'hudson.plugins.jira.JiraProjectProperty' {
                        siteName( value )
                    }
                    break

                default:
                    throw new RuntimeException( "Unsupported parameter type [$type]" )
            }
        }
    }
}

