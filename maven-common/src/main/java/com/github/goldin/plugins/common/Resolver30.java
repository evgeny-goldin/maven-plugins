package com.github.goldin.plugins.common;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.component.annotations.Requirement;
import org.sonatype.aether.*;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.resolution.*;
import org.sonatype.aether.util.artifact.DefaultArtifact;

import java.io.File;
import java.util.List;


/**
 * ICompatibilityProvider for maven 3.0.
 *
 * @author j.boesl, 12.02.15
 */
@org.codehaus.plexus.component.annotations.Component(role = ICompatibilityProvider.class, hint = "maven30")
public class Resolver30 implements ICompatibilityProvider
{

  @Requirement
  private RepositorySystem repoSystem;

  @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
  private RepositorySystemSession repoSession;

  @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true)
  private List<RemoteRepository> remoteRepos;


  @Override
  public File resolveFile(Artifact pArtifact)
  {
    try
    {
      DefaultArtifact defaultArtifact = new DefaultArtifact(
          pArtifact.getGroupId(), pArtifact.getArtifactId(), pArtifact.getClassifier(), pArtifact.getType(),
          pArtifact.getVersion(), null, pArtifact.getFile());
      ArtifactRequest request = new ArtifactRequest(defaultArtifact, remoteRepos, null);
      org.sonatype.aether.artifact.Artifact artifact = repoSystem.resolveArtifact(repoSession, request).getArtifact();
      return artifact == null ? null : artifact.getFile();
    }
    catch (ArtifactResolutionException e)
    {
      throw new RuntimeException(e);
    }
  }
}
