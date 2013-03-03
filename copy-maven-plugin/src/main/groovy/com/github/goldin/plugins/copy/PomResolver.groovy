package com.github.goldin.plugins.copy

import com.github.goldin.plugins.common.BaseGroovyMojo
import com.github.goldin.plugins.common.ConversionUtils

import org.apache.maven.model.resolution.ModelResolver
import org.apache.maven.model.Repository
import org.apache.maven.model.building.FileModelSource
import org.apache.maven.model.building.ModelSource
import org.apache.maven.model.resolution.UnresolvableModelException
import org.apache.maven.artifact.InvalidRepositoryException

/**
 * Resolves Maven POM files.
 */
class PomResolver implements ModelResolver {

    BaseGroovyMojo baseMojo;
    boolean verbose;
    boolean failIfNotFound;
    
    PomResolver(BaseGroovyMojo mojo, boolean verbose, boolean failIfNotFound) {
        this.baseMojo = mojo
        this.verbose = verbose
        this.failIfNotFound = failIfNotFound
    }
    
    /**
     * Tries to resolve the POM for the specified coordinates.
     *
     * @param groupId The group identifier of the POM, must not be {@code null}.
     * @param artifactId The artifact identifier of the POM, must not be {@code null}.
     * @param version The version of the POM, must not be {@code null}.
     * @return The source of the requested POM, never {@code null}.
     * @throws UnresolvableModelException If the POM could not be resolved from any configured repository.
     */
    FileModelSource resolveModel( String groupId, String artifactId, String version ) throws UnresolvableModelException
    {
        def artifact = baseMojo.downloadArtifact( ConversionUtils.toMavenArtifact(groupId, artifactId, version, '', 'pom', '', false), verbose, failIfNotFound)
        new FileModelSource(artifact.file)
    }

    /**
     * Adds a repository to use for subsequent resolution requests. The order in which repositories are added matters,
     * repositories that were added first should also be searched first. When multiple repositories with the same
     * identifier are added, only the first repository being added will be used.
     *
     * @param repository The repository to add to the internal search chain, must not be {@code null}.
     * @throws InvalidRepositoryException If the repository could not be added (e.g. due to invalid URL or layout).
     */
    void addRepository( Repository repository ) throws InvalidRepositoryException
    {
        // This is ignored, we use the repos as grabbed from our Maven session in BaseGroovyMojo
    }

    /**
     * Clones this resolver for usage in a forked resolution process. In general, implementors need not provide a deep
     * clone. The only requirement is that invocations of {@link #addRepository(Repository)} on the clone do not affect
     * the state of the original resolver and vice versa.
     *
     * @return The cloned resolver, never {@code null}.
     */
    ModelResolver newCopy()
    {
        // We don't hold no unique state
        return this;
    }
}

