package com.github.goldin.plugins.common;

import org.apache.maven.artifact.Artifact;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.annotations.*;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.logging.AbstractLogEnabled;

import java.io.File;

/**
 * ICompatibilityProvider which delegates to a maven version specific implementation.
 *
 * @author j.boesl, 12.02.15
 */
@Component(role = ICompatibilityProvider.class)
public class RepositoryResolver extends AbstractLogEnabled implements ICompatibilityProvider
{

  @Requirement
  private PlexusContainer container;


  @Override
  public File resolveFile(Artifact pArtifact)
  {
    return _getProvider().resolveFile(pArtifact);
  }

  private ICompatibilityProvider _getProvider()
  {
    try
    {
      return container.lookup(ICompatibilityProvider.class, _getHint());
    }
    catch (ComponentLookupException e)
    {
      throw new RuntimeException("Failed to lookup ICompatibilityProvider", e);
    }
  }

  private String _getHint()
  {
    String hint = _isMaven31() ? "maven31" : "maven30";
    getLogger().info("Running deploy check using " + hint);
    return hint;
  }

  private boolean _isMaven31()
  {
    // Maven 3.1 specific class, cannot be found for 3.0.
    return _canFindCoreClass("org.eclipse.aether.artifact.Artifact");
  }

  private boolean _canFindCoreClass(final String className)
  {
    try
    {
      Thread.currentThread().getContextClassLoader().loadClass(className);
      return true;
    }
    catch (ClassNotFoundException e)
    {
      return false;
    }
  }


}
