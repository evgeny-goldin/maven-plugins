
ruleset {

    description 'CodeNarc RuleSet'

    ruleset( "http://codenarc.sourceforge.net/StarterRuleSet-AllRulesByCategory.groovy.txt" ) {

        DuplicateNumberLiteral      ( enabled : false )
        DuplicateStringLiteral      ( enabled : false )
        BracesForClass              ( enabled : false )
        BracesForMethod             ( enabled : false )
        BracesForIfElse             ( enabled : false )
        BracesForForLoop            ( enabled : false )
        BracesForTryCatchFinally    ( enabled : false )
        JavaIoPackageAccess         ( enabled : false )
        ConfusingMethodName         ( enabled : false )
        UnnecessarySubstring        ( enabled : false )
        FactoryMethodName           ( enabled : false )
        GetterMethodCouldBeProperty ( enabled : false )
        SpaceBeforeOpeningBrace     ( enabled : false )
        SpaceAfterOpeningBrace      ( enabled : false )
        SpaceBeforeClosingBrace     ( enabled : false )
        SpaceAfterClosingBrace      ( enabled : false )
        PrivateFieldCouldBeFinal	( enabled : false )

        VariableName                ( finalRegex : /[a-zA-Z0-9_]+/ )
        LineLength                  ( length     : 160   )
        MethodName                  ( regex      : /[a-z][\w\s'\(\)]*/ ) // Spock method names
    }
}
