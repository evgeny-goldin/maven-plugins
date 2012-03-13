package com.goldin.plugins.jenkins.beans


enum  ParameterType { bool, choice, string, password, run, file, jira }
class Parameter
{
    String        name        = 'UNDEFINED'
    ParameterType type        = null
    String        value       = ''
    String        description = ''

    void setType( String type ) { this.type = ParameterType.valueOf( type ) }

    @Override
    int     hashCode ()           { "[$name][$type]".hashCode()  }

    @Override
    boolean equals   ( Object o ) { ( o instanceof Parameter ) &&
                                    ( this.name            == o.name            ) &&
                                    ( this.type.toString() == o.type.toString() ) }
}

