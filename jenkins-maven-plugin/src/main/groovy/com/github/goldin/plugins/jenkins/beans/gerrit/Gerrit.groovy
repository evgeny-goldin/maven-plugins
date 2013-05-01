package com.github.goldin.plugins.jenkins.beans.gerrit

import static com.github.goldin.plugins.common.GMojoUtils.*


@SuppressWarnings([ 'FieldName' ])
enum GerritType
{
    plain  ( 'PLAIN'   ),
    path   ( 'ANT'     ),
    regexp ( 'REG_EXP' )

    private final String typeTitle

    GerritType( String typeTitle )
    {
        this.typeTitle = verifyBean().notNullOrEmpty( typeTitle )
    }

    @Override
    String toString(){ this.typeTitle }
}


@SuppressWarnings([ 'AbstractClassWithoutAbstractMethod' ])
abstract class TypePattern
{
    GerritType type    = GerritType.plain
    String     pattern = ''
}


class Project extends TypePattern
{
    Branch         branch
    Branch[]       branches
    List<Branch>   branches(){ generalBean().list( branches, branch )}

    FilePath       filePath
    FilePath[]     filePaths
    List<FilePath> filePaths(){ generalBean().list( filePaths, filePath )}
}


class Branch    extends TypePattern {}
class FilePath  extends TypePattern {}
