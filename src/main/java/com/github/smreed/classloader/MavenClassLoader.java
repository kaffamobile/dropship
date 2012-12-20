package com.github.smreed.classloader;

import org.apache.maven.repository.internal.MavenRepositorySystemSession;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.sonatype.aether.AbstractRepositoryListener;
import org.sonatype.aether.RepositoryEvent;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.collection.CollectRequest;
import org.sonatype.aether.collection.DependencyCollectionException;
import org.sonatype.aether.graph.Dependency;
import org.sonatype.aether.graph.DependencyFilter;
import org.sonatype.aether.graph.DependencyNode;
import org.sonatype.aether.repository.LocalRepository;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.repository.RepositoryPolicy;
import org.sonatype.aether.resolution.ArtifactResolutionException;
import org.sonatype.aether.resolution.DependencyRequest;
import org.sonatype.aether.resolution.DependencyResolutionException;
import org.sonatype.aether.transfer.AbstractTransferListener;
import org.sonatype.aether.transfer.TransferCancelledException;
import org.sonatype.aether.transfer.TransferEvent;
import org.sonatype.aether.util.artifact.DefaultArtifact;
import org.sonatype.aether.util.filter.ScopeDependencyFilter;
import org.sonatype.aether.util.graph.PreorderNodeListGenerator;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.github.smreed.classloader.NotGuava.checkArgument;
import static com.github.smreed.classloader.NotGuava.checkNotNull;
import static com.github.smreed.classloader.NotGuava.propagate;
import static com.github.smreed.classloader.NotLogger.info;

public final class MavenClassLoader {

  public static class ClassLoaderBuilder {

    private static final String COMPILE_SCOPE = "compile";
    private static final ClassLoader SHARE_NOTHING = null;

    private final List<RemoteRepository> repositories;
    private final File localRepositoryDirectory;

    private ClassLoaderBuilder(RemoteRepository... repositories) {
      checkNotNull(repositories);
      checkArgument(repositories.length > 0, "Must specify at least one remote repository.");

      List<RemoteRepository> repositoriesCopy = new ArrayList<RemoteRepository>();
      Collections.addAll(repositoriesCopy, repositories);
      this.repositories = Collections.unmodifiableList(repositoriesCopy);
      this.localRepositoryDirectory = new File(Settings.localRepoPath());
    }

    public URLClassLoader forGAV(String gav) {
      try {
        info("Collecting maven metadata.");
        CollectRequest collectRequest = createCollectRequestForGAV(gav);
        info("Resolving dependencies.");
        List<Artifact> artifacts = collectDependenciesIntoArtifacts(collectRequest);
        info("Building classpath for %s from %d URLs.", gav, artifacts.size());
        List<URL> urls = new ArrayList<URL>();
        for (Artifact artifact : artifacts) {
          urls.add(artifact.getFile().toURI().toURL());
        }

        return new URLClassLoader(urls.toArray(new URL[urls.size()]), SHARE_NOTHING);
      } catch (Exception e) {
        throw propagate(e);
      }
    }

    private CollectRequest createCollectRequestForGAV(String gav) {
      DefaultArtifact artifact = new DefaultArtifact(gav);
      Dependency dependency = new Dependency(artifact, COMPILE_SCOPE);

      CollectRequest collectRequest = new CollectRequest();
      collectRequest.setRoot(dependency);
      for (RemoteRepository repository : repositories) {
        collectRequest.addRepository(repository);
      }

      return collectRequest;
    }

    private List<Artifact> collectDependenciesIntoArtifacts(CollectRequest collectRequest)
      throws PlexusContainerException, ComponentLookupException, DependencyCollectionException, ArtifactResolutionException, DependencyResolutionException {

      RepositorySystem repositorySystem = newRepositorySystem();
      RepositorySystemSession session = newSession(repositorySystem);
      DependencyNode node = repositorySystem.collectDependencies(session, collectRequest).getRoot();

      DependencyFilter filter = new ScopeDependencyFilter();

      DependencyRequest request = new DependencyRequest(node, filter);

      repositorySystem.resolveDependencies(session, request);

      PreorderNodeListGenerator nlg = new PreorderNodeListGenerator();
      node.accept(nlg);

      return nlg.getArtifacts(false);
    }

    private RepositorySystem newRepositorySystem() throws PlexusContainerException, ComponentLookupException {
      return new DefaultPlexusContainer().lookup(RepositorySystem.class);
    }

    private RepositorySystemSession newSession(RepositorySystem system) {
      MavenRepositorySystemSession session = new MavenRepositorySystemSession();
      session.setRepositoryListener(new AbstractRepositoryListener() {

        private Map<String , Long> startTimes = new HashMap<String, Long>();

        private String artifactAsString(Artifact artifact) {
          return artifact.getGroupId() + ':' + artifact.getArtifactId() + ':' + artifact.getVersion();
        }

        @Override
        public void artifactDownloading(RepositoryEvent event) {
          super.artifactDownloading(event);
          Artifact artifact = event.getArtifact();
          String key = artifactAsString(artifact);
          startTimes.put(key, System.nanoTime());
        }

        @Override
        public void artifactDownloaded(RepositoryEvent event) {
          super.artifactDownloaded(event);
          Artifact artifact = event.getArtifact();
          String key = artifactAsString(artifact);
          double downloadTimeNanos = (double) System.nanoTime() - startTimes.remove(key);
          double downloadTimeMs = downloadTimeNanos / 1E6;
          double downloadTimeSec = downloadTimeMs / 1E3;
          long size = artifact.getFile().length();
          double sizeK = (1 / 1024D) * size;
          double downloadRateKBytesPerSecond = sizeK / downloadTimeSec;
          info("Downloaded %s (%d bytes) in %gms (%g kbytes/sec).", key, size, downloadTimeMs, downloadRateKBytesPerSecond);
        }
      });

      session.setChecksumPolicy(RepositoryPolicy.CHECKSUM_POLICY_FAIL);

      session.setTransferListener(new AbstractTransferListener() {
        @Override
        public void transferCorrupted(TransferEvent event) throws TransferCancelledException {
          super.transferCorrupted(event);
        }
      });

      session.setIgnoreInvalidArtifactDescriptor(false);
      session.setIgnoreMissingArtifactDescriptor(false);
      session.setNotFoundCachingEnabled(false);
      session.setTransferErrorCachingEnabled(false);
      session.setUpdatePolicy(RepositoryPolicy.UPDATE_POLICY_ALWAYS);

      LocalRepository localRepo = new LocalRepository(localRepositoryDirectory);
      session.setLocalRepositoryManager(system.newLocalRepositoryManager(localRepo));

      return session;
    }
  }

  /**
   * Creates a classloader that will resolve artifacts against the default "central" repository. Throws
   * {@link IllegalArgumentException} if the GAV is invalid, {@link NullPointerException} if the GAV is null.
   *
   * @param gav artifact group:artifact:version, i.e. joda-time:joda-time:1.6.2
   * @return a classloader that can be used to load classes from the given artifact
   */
  public static URLClassLoader forGAV(String gav) {
    return usingCentralRepo().forGAV(checkNotNull(gav));
  }

  public static ClassLoaderBuilder using(String url) {
    RemoteRepository custom = new RemoteRepository("custom", "default", url);
    return new ClassLoaderBuilder(custom);
  }

  public static ClassLoaderBuilder usingCentralRepo() {
    RemoteRepository central = new RemoteRepository("central", "default", "http://repo1.maven.org/maven2/");
    return new ClassLoaderBuilder(central);
  }
}
