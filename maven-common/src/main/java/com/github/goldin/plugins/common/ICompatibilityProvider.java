package com.github.goldin.plugins.common;

import org.apache.maven.artifact.Artifact;

import java.io.File;

/**
 * Provides methods that are dependent on maven version.
 *
 * @author j.boesl, 12.02.15
 */
public interface ICompatibilityProvider
{
  File resolveFile(Artifact pArtifact);
}
