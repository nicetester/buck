import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertTrue;
import com.google.common.base.Optional;
  @Test
  public void testRevisionIdOrAbsent() throws InterruptedException {
    Optional<String> masterRevision = repoThreeCmdLine.revisionIdOrAbsent(MASTER_THREE_BOOKMARK);
    Optional<String> absentRevision = repoThreeCmdLine.revisionIdOrAbsent("absent_bookmark");
    assertTrue(masterRevision.get().startsWith(MASTER_THREE_ID));
    assertEquals(absentRevision, Optional.<String>absent());
  }

    assertThat(repoTwoCmdLine.changedFiles("."), hasSize(0));
    assertThat(repoThreeCmdLine.changedFiles("."), hasSize(Matchers.greaterThan(0)));
  @Test
  public void testExistingCommonAncestorOrAbsentWithBookmarks() throws InterruptedException {
    assertTrue(repoThreeCmdLine.commonAncestorOrAbsent(
        BRANCH_FROM_MASTER_THREE_BOOKMARK,
        MASTER_THREE_BOOKMARK)
        .get()
        .startsWith(MASTER_THREE_ID));
  }

  @Test
  public void testAbsentCommonAncestorOrAbsentWithBookmarks() throws InterruptedException {
    assertEquals(repoThreeCmdLine.commonAncestorOrAbsent(
        BRANCH_FROM_MASTER_THREE_BOOKMARK,
        "absent_bookmark"),
        Optional.<String>absent());
  }

  @Test
  public void testDiffBetweenRevisions()
      throws VersionControlCommandFailedException, InterruptedException {
    assertEquals(
        "diff --git a/change2 b/change2new file mode 100644diff --git a/file3 b/file3deleted " +
            "file mode 100644",
        repoThreeCmdLine.diffBetweenRevisions("adf7a0", "2911b3"));
  }

  @Test
  public void testDiffBetweenTheSameRevision()
      throws VersionControlCommandFailedException, InterruptedException {
    assertEquals("", repoThreeCmdLine.diffBetweenRevisions("adf7a0", "adf7a0"));
  }

  @Test
  public void testDiffBetweenDiffsOfDifferentBranches()
      throws VersionControlCommandFailedException, InterruptedException {
    assertEquals(
        "diff --git a/change2 b/change2deleted file mode 100644diff --git a/change3 b/change3new " +
            "file mode 100644diff --git a/change3-2 b/change3-2new file mode 100644diff " +
            "--git a/file3 b/file3new file mode 100644",
        repoThreeCmdLine.diffBetweenRevisions("2911b3", "dee670"));
  }

  @Test
  public void testAllBookmarks()
      throws VersionControlCommandFailedException, InterruptedException {
    try {
      assertEquals(
          "{branch_from_master2=3:2911b3cab6b2, branch_from_master3=5:dee6702e3d5e, " +
              "master1=0:b870f77a2738, master2=1:b1fd7e5896af, master3=2:adf7a03ed6f1}",
          repoThreeCmdLine.allBookmarks().toString());
    } catch (VersionControlCommandFailedException e) {
      if (!e.getMessage().contains("option --all not recognized")) {
        throw new VersionControlCommandFailedException(e.getCause());
      }
    }
  }

  @Test
  public void testUntrackedFiles()
      throws VersionControlCommandFailedException, InterruptedException {
    assertEquals(ImmutableSet.of("? local_change"), repoThreeCmdLine.untrackedFiles());
  }

  @Test
  public void testTrackedBookmarksOffRevisionId() throws InterruptedException {
    ImmutableSet<String> bookmarks = ImmutableSet.of("master2");
    assertEquals(
        bookmarks,
        repoThreeCmdLine.trackedBookmarksOffRevisionId("b1fd7e", "2911b3", bookmarks));
    bookmarks = ImmutableSet.of("master3");
    assertEquals(
        bookmarks,
        repoThreeCmdLine.trackedBookmarksOffRevisionId("dee670", "adf7a0", bookmarks));
  }

            new VersionControlBuckConfig(FakeBuckConfig.builder().build().getRawConfig()),
            new VersionControlBuckConfig(FakeBuckConfig.builder().build().getRawConfig()),