/*
 * Copyright 2014-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.apple.xcode;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertThat;

import com.facebook.buck.apple.SchemeActionType;
import com.facebook.buck.apple.xcode.xcodeproj.PBXFileReference;
import com.facebook.buck.apple.xcode.xcodeproj.PBXNativeTarget;
import com.facebook.buck.apple.xcode.xcodeproj.PBXReference;
import com.facebook.buck.apple.xcode.xcodeproj.PBXTarget;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.facebook.buck.timing.SettableFakeClock;
import com.facebook.buck.util.HumanReadableException;
import com.facebook.buck.util.ProjectFilesystem;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

public class SchemeGeneratorTest {

  private SettableFakeClock clock;
  private ProjectFilesystem projectFilesystem;

  @Before
  public void setUp() throws IOException {
    clock = new SettableFakeClock(0, 0);
    projectFilesystem = new FakeProjectFilesystem(clock);
  }

  @Test
  public void schemeWithMultipleTargetsBuildsInCorrectOrder() throws Exception {
    ImmutableMap.Builder<BuildTarget, PBXTarget> buildTargetToPbxTargetMapBuilder =
      ImmutableMap.builder();
    ImmutableMap.Builder<PBXTarget, Path> targetToProjectPathMapBuilder =
      ImmutableMap.builder();

    BuildTarget rootBuildTarget = BuildTarget.builder("//foo", "root").build();
    PBXTarget rootTarget = new PBXNativeTarget("rootRule", PBXTarget.ProductType.STATIC_LIBRARY);
    rootTarget.setGlobalID("rootGID");
    rootTarget.setProductReference(
        new PBXFileReference("root.a", "root.a", PBXReference.SourceTree.BUILT_PRODUCTS_DIR));
    buildTargetToPbxTargetMapBuilder.put(rootBuildTarget, rootTarget);

    BuildTarget leftBuildTarget = BuildTarget.builder("//foo", "left").build();
    PBXTarget leftTarget = new PBXNativeTarget("leftRule", PBXTarget.ProductType.STATIC_LIBRARY);
    leftTarget.setGlobalID("leftGID");
    leftTarget.setProductReference(
        new PBXFileReference("left.a", "left.a", PBXReference.SourceTree.BUILT_PRODUCTS_DIR));
    buildTargetToPbxTargetMapBuilder.put(leftBuildTarget, leftTarget);

    BuildTarget rightBuildTarget = BuildTarget.builder("//foo", "right").build();
    PBXTarget rightTarget = new PBXNativeTarget("rightRule", PBXTarget.ProductType.STATIC_LIBRARY);
    rightTarget.setGlobalID("rightGID");
    rightTarget.setProductReference(
        new PBXFileReference("right.a", "right.a", PBXReference.SourceTree.BUILT_PRODUCTS_DIR));
    buildTargetToPbxTargetMapBuilder.put(rightBuildTarget, rightTarget);

    BuildTarget childBuildTarget = BuildTarget.builder("//foo", "child").build();
    PBXTarget childTarget = new PBXNativeTarget("childRule", PBXTarget.ProductType.STATIC_LIBRARY);
    childTarget.setGlobalID("childGID");
    childTarget.setProductReference(
        new PBXFileReference("child.a", "child.a", PBXReference.SourceTree.BUILT_PRODUCTS_DIR));
    buildTargetToPbxTargetMapBuilder.put(childBuildTarget, childTarget);

    Path pbxprojectPath = Paths.get("foo/Foo.xcodeproj/project.pbxproj");
    targetToProjectPathMapBuilder.put(rootTarget, pbxprojectPath);
    targetToProjectPathMapBuilder.put(leftTarget, pbxprojectPath);
    targetToProjectPathMapBuilder.put(rightTarget, pbxprojectPath);
    targetToProjectPathMapBuilder.put(childTarget, pbxprojectPath);

    SchemeGenerator schemeGenerator = new SchemeGenerator(
        projectFilesystem,
        Optional.of(childBuildTarget),
        ImmutableSet.of(rootBuildTarget, leftBuildTarget, rightBuildTarget, childBuildTarget),
        ImmutableSet.<BuildTarget>of(),
        ImmutableSet.<BuildTarget>of(),
        "TestScheme",
        Paths.get("_gen/Foo.xcworkspace/scshareddata/xcshemes"),
        SchemeActionType.DEFAULT_CONFIG_NAMES,
        buildTargetToPbxTargetMapBuilder.build(),
        targetToProjectPathMapBuilder.build());

    Path schemePath = schemeGenerator.writeScheme();
    String schemeXml = projectFilesystem.readFileIfItExists(schemePath).get();
    System.out.println(schemeXml);

    DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
    Document scheme = dBuilder.parse(projectFilesystem.newFileInputStream(schemePath));

    XPathFactory xpathFactory = XPathFactory.newInstance();
    XPath xpath = xpathFactory.newXPath();
    XPathExpression expr =
        xpath.compile("//BuildAction//BuildableReference/@BlueprintIdentifier");
    NodeList nodes = (NodeList) expr.evaluate(scheme, XPathConstants.NODESET);

    List<String> expectedOrdering = ImmutableList.of(
        "rootGID",
        "leftGID",
        "rightGID",
        "childGID");

    List<String> actualOrdering = Lists.newArrayList();
    for (int i = 0; i < nodes.getLength(); i++) {
      actualOrdering.add(nodes.item(i).getNodeValue());
    }
    assertThat(actualOrdering, equalTo(expectedOrdering));
  }

  @Test(expected = HumanReadableException.class)
  public void schemeWithTargetWithoutCorrespondingProjectsFails() throws Exception {
    BuildTarget rootBuildTarget = BuildTarget.builder("//foo", "root").build();

    SchemeGenerator schemeGenerator = new SchemeGenerator(
        projectFilesystem,
        Optional.of(rootBuildTarget),
        ImmutableSet.of(rootBuildTarget),
        ImmutableSet.<BuildTarget>of(),
        ImmutableSet.<BuildTarget>of(),
        "TestScheme",
        Paths.get("_gen/Foo.xcworkspace/scshareddata/xcshemes"),
        SchemeActionType.DEFAULT_CONFIG_NAMES,
        ImmutableMap.<BuildTarget, PBXTarget>of(),
        ImmutableMap.<PBXTarget, Path>of());

    schemeGenerator.writeScheme();
  }

  @Test
  public void schemeIncludesXcodeNativeTargets() throws Exception {
    ImmutableMap.Builder<BuildTarget, PBXTarget> buildTargetToPbxTargetMapBuilder =
      ImmutableMap.builder();
    ImmutableMap.Builder<PBXTarget, Path> targetToProjectPathMapBuilder =
      ImmutableMap.builder();

    BuildTarget rootBuildTarget = BuildTarget.builder("//foo", "root").build();
    PBXTarget rootTarget = new PBXNativeTarget("root", PBXTarget.ProductType.STATIC_LIBRARY);
    rootTarget.setGlobalID("rootGID");
    rootTarget.setProductReference(
        new PBXFileReference(
            "root.a", "root.a", PBXReference.SourceTree.BUILT_PRODUCTS_DIR));
    buildTargetToPbxTargetMapBuilder.put(rootBuildTarget, rootTarget);

    BuildTarget xcodeNativeBuildTarget = BuildTarget.builder("//foo", "xcode-native").build();
    PBXTarget xcodeNativeTarget =
        new PBXNativeTarget("xcode-native", PBXTarget.ProductType.STATIC_LIBRARY);
    xcodeNativeTarget.setGlobalID("xcode-nativeGID");
    xcodeNativeTarget.setProductReference(
        new PBXFileReference(
            "xcode-native.a", "xcode-native.a", PBXReference.SourceTree.BUILT_PRODUCTS_DIR));
    buildTargetToPbxTargetMapBuilder.put(xcodeNativeBuildTarget, xcodeNativeTarget);

    Path projectPath = Paths.get("foo/Foo.xcodeproj/project.pbxproj");
    targetToProjectPathMapBuilder.put(rootTarget, projectPath);

    Path nativeProjectPath = Paths.get("foo/XcodeNative.xcodeproj/project.pbxproj");
    targetToProjectPathMapBuilder.put(xcodeNativeTarget, nativeProjectPath);

    SchemeGenerator schemeGenerator = new SchemeGenerator(
        projectFilesystem,
        Optional.of(rootBuildTarget),
        ImmutableSet.of(xcodeNativeBuildTarget, rootBuildTarget),
        ImmutableSet.<BuildTarget>of(),
        ImmutableSet.<BuildTarget>of(),
        "TestScheme",
        Paths.get("_gen/Foo.xcworkspace/scshareddata/xcshemes"),
        SchemeActionType.DEFAULT_CONFIG_NAMES,
        buildTargetToPbxTargetMapBuilder.build(),
        targetToProjectPathMapBuilder.build());

    Path schemePath = schemeGenerator.writeScheme();
    String schemeXml = projectFilesystem.readFileIfItExists(schemePath).get();
    System.out.println(schemeXml);

    DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
    Document scheme = dBuilder.parse(projectFilesystem.newFileInputStream(schemePath));

    XPathFactory xpathFactory = XPathFactory.newInstance();
    XPath xpath = xpathFactory.newXPath();
    XPathExpression expr =
        xpath.compile("//BuildAction//BuildableReference/@BlueprintIdentifier");
    NodeList nodes = (NodeList) expr.evaluate(scheme, XPathConstants.NODESET);

    List<String> expectedOrdering = ImmutableList.of(
        "xcode-nativeGID",
        "rootGID");

    List<String> actualOrdering = Lists.newArrayList();
    for (int i = 0; i < nodes.getLength(); i++) {
      actualOrdering.add(nodes.item(i).getNodeValue());
    }
    assertThat(actualOrdering, equalTo(expectedOrdering));
  }

  @Test
  public void schemeBuildsAndTestsAppleTestTargets() throws Exception {
    ImmutableMap.Builder<BuildTarget, PBXTarget> buildTargetToPbxTargetMapBuilder =
        ImmutableMap.builder();
    ImmutableMap.Builder<PBXTarget, Path> targetToProjectPathMapBuilder =
        ImmutableMap.builder();

    BuildTarget testDepBuildTarget = BuildTarget.builder("//foo", "testDep").build();
    PBXTarget testDepTarget = new PBXNativeTarget("testDep", PBXTarget.ProductType.STATIC_LIBRARY);
    testDepTarget.setGlobalID("testDepGID");
    testDepTarget.setProductReference(
        new PBXFileReference(
            "libDep.a", "libDep.a", PBXReference.SourceTree.BUILT_PRODUCTS_DIR));
    buildTargetToPbxTargetMapBuilder.put(testDepBuildTarget, testDepTarget);

    BuildTarget libraryBuildTarget = BuildTarget.builder("//foo", "lib").build();
    PBXTarget testLibraryTarget =
        new PBXNativeTarget("testLibrary", PBXTarget.ProductType.STATIC_LIBRARY);
    testLibraryTarget.setGlobalID("testLibraryGID");
    testLibraryTarget.setProductReference(
        new PBXFileReference(
            "lib.a", "lib.a", PBXReference.SourceTree.BUILT_PRODUCTS_DIR));
    buildTargetToPbxTargetMapBuilder.put(libraryBuildTarget, testLibraryTarget);

    BuildTarget testBuildTarget = BuildTarget.builder("//foo", "test").build();
    PBXTarget testTarget = new PBXNativeTarget("test", PBXTarget.ProductType.UNIT_TEST);
    testTarget.setGlobalID("testGID");
    testTarget.setProductReference(
        new PBXFileReference(
            "test.xctest", "test.xctest", PBXReference.SourceTree.BUILT_PRODUCTS_DIR));
    buildTargetToPbxTargetMapBuilder.put(testBuildTarget, testTarget);

    BuildTarget rootBuildTarget = BuildTarget.builder("//foo", "root").build();
    PBXTarget rootTarget = new PBXNativeTarget("root", PBXTarget.ProductType.STATIC_LIBRARY);
    rootTarget.setGlobalID("rootGID");
    rootTarget.setProductReference(
        new PBXFileReference(
            "root.a", "root.a", PBXReference.SourceTree.BUILT_PRODUCTS_DIR));
    buildTargetToPbxTargetMapBuilder.put(rootBuildTarget, rootTarget);

    Path projectPath = Paths.get("foo/test.xcodeproj/project.pbxproj");
    targetToProjectPathMapBuilder.put(testTarget, projectPath);
    targetToProjectPathMapBuilder.put(testDepTarget, projectPath);
    targetToProjectPathMapBuilder.put(testLibraryTarget, projectPath);
    targetToProjectPathMapBuilder.put(rootTarget, projectPath);

    SchemeGenerator schemeGenerator = new SchemeGenerator(
        projectFilesystem,
        Optional.of(rootBuildTarget),
        ImmutableSet.of(rootBuildTarget),
        ImmutableSet.of(testDepBuildTarget, testBuildTarget),
        ImmutableSet.of(testBuildTarget),
        "TestScheme",
        Paths.get("_gen/Foo.xcworkspace/scshareddata/xcshemes"),
        SchemeActionType.DEFAULT_CONFIG_NAMES,
        buildTargetToPbxTargetMapBuilder.build(),
        targetToProjectPathMapBuilder.build());

    Path schemePath = schemeGenerator.writeScheme();
    String schemeXml = projectFilesystem.readFileIfItExists(schemePath).get();
    System.out.println(schemeXml);

    DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
    Document scheme = dBuilder.parse(projectFilesystem.newFileInputStream(schemePath));

    XPathFactory xpathFactory = XPathFactory.newInstance();

    XPath buildXpath = xpathFactory.newXPath();
    XPathExpression buildExpr =
        buildXpath.compile("//BuildAction//BuildableReference/@BlueprintIdentifier");
    NodeList buildNodes = (NodeList) buildExpr.evaluate(scheme, XPathConstants.NODESET);

    List<String> expectedBuildOrdering = ImmutableList.of("rootGID", "testDepGID", "testGID");

    List<String> actualBuildOrdering = Lists.newArrayList();
    for (int i = 0; i < buildNodes.getLength(); i++) {
      actualBuildOrdering.add(buildNodes.item(i).getNodeValue());
    }
    assertThat(actualBuildOrdering, equalTo(expectedBuildOrdering));

    XPath textXpath = xpathFactory.newXPath();
    XPathExpression testExpr = textXpath.compile(
      "//TestAction//TestableReference/BuildableReference/@BlueprintIdentifier");
    NodeList testNodes = (NodeList) testExpr.evaluate(scheme, XPathConstants.NODESET);

    List<String> expectedTestOrdering = ImmutableList.of("testGID");

    List<String> actualTestOrdering = Lists.newArrayList();
    for (int i = 0; i < testNodes.getLength(); i++) {
      actualTestOrdering.add(testNodes.item(i).getNodeValue());
    }
    assertThat(actualTestOrdering, equalTo(expectedTestOrdering));
  }

  @Test
  public void schemeIncludesAllExpectedActions() throws Exception {
    ImmutableMap.Builder<BuildTarget, PBXTarget> buildTargetToPbxTargetMapBuilder =
      ImmutableMap.builder();
    ImmutableMap.Builder<PBXTarget, Path> targetToProjectPathMapBuilder =
      ImmutableMap.builder();

    BuildTarget rootBuildTarget = BuildTarget.builder("//foo", "root").build();
    PBXTarget rootTarget = new PBXNativeTarget("rootRule", PBXTarget.ProductType.STATIC_LIBRARY);
    rootTarget.setGlobalID("rootGID");
    rootTarget.setProductReference(
        new PBXFileReference(
            "root.a", "root.a", PBXReference.SourceTree.BUILT_PRODUCTS_DIR));
    buildTargetToPbxTargetMapBuilder.put(rootBuildTarget, rootTarget);

    BuildTarget testBuildTarget = BuildTarget.builder("//foo", "test").build();
    PBXTarget testTarget = new PBXNativeTarget("testRule", PBXTarget.ProductType.STATIC_LIBRARY);
    testTarget.setGlobalID("testGID");
    testTarget.setProductReference(
        new PBXFileReference(
            "test.a", "test.a", PBXReference.SourceTree.BUILT_PRODUCTS_DIR));
    buildTargetToPbxTargetMapBuilder.put(testBuildTarget, testTarget);

    BuildTarget xctestBuildTarget = BuildTarget.builder("//foo", "xctest").build();
    PBXTarget testBundleTarget =
        new PBXNativeTarget("testBundleRule", PBXTarget.ProductType.UNIT_TEST);
    testBundleTarget.setGlobalID("testBundleGID");
    testBundleTarget.setProductReference(
        new PBXFileReference(
            "test.xctest",
            "test.xctest",
            PBXReference.SourceTree.BUILT_PRODUCTS_DIR));
    buildTargetToPbxTargetMapBuilder.put(xctestBuildTarget, testBundleTarget);

    Path pbxprojectPath = Paths.get("foo/Foo.xcodeproj/project.pbxproj");
    targetToProjectPathMapBuilder.put(rootTarget, pbxprojectPath);
    targetToProjectPathMapBuilder.put(testTarget, pbxprojectPath);
    targetToProjectPathMapBuilder.put(testBundleTarget, pbxprojectPath);

    SchemeGenerator schemeGenerator = new SchemeGenerator(
        projectFilesystem,
        Optional.of(rootBuildTarget),
        ImmutableSet.of(rootBuildTarget),
        ImmutableSet.of(xctestBuildTarget),
        ImmutableSet.of(xctestBuildTarget),
        "TestScheme",
        Paths.get("_gen/Foo.xcworkspace/scshareddata/xcshemes"),
        SchemeActionType.DEFAULT_CONFIG_NAMES,
        buildTargetToPbxTargetMapBuilder.build(),
        targetToProjectPathMapBuilder.build());

    Path schemePath = schemeGenerator.writeScheme();
    String schemeXml = projectFilesystem.readFileIfItExists(schemePath).get();
    System.out.println(schemeXml);

    DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
    Document scheme = dBuilder.parse(projectFilesystem.newFileInputStream(schemePath));

    XPathFactory xpathFactory = XPathFactory.newInstance();

    XPath buildActionXpath = xpathFactory.newXPath();
    XPathExpression buildActionExpr =
        buildActionXpath.compile("//BuildAction//BuildableReference/@BlueprintIdentifier");
    NodeList buildActionNodes = (NodeList) buildActionExpr.evaluate(scheme, XPathConstants.NODESET);

    List<String> expectedOrdering = ImmutableList.of(
        "rootGID",
        "testBundleGID");

    List<String> actualOrdering = Lists.newArrayList();
    for (int i = 0; i < buildActionNodes.getLength(); i++) {
      actualOrdering.add(buildActionNodes.item(i).getNodeValue());
    }
    assertThat(actualOrdering, equalTo(expectedOrdering));

    XPath testActionXpath = xpathFactory.newXPath();
    XPathExpression testActionExpr =
        testActionXpath.compile("//TestAction//BuildableReference/@BlueprintIdentifier");
    String testActionBlueprintIdentifier =
        (String) testActionExpr.evaluate(scheme, XPathConstants.STRING);
    assertThat(testActionBlueprintIdentifier, equalTo("testBundleGID"));

    XPath launchActionXpath = xpathFactory.newXPath();
    XPathExpression launchActionExpr =
        launchActionXpath.compile("//LaunchAction//BuildableReference/@BlueprintIdentifier");
    String launchActionBlueprintIdentifier =
        (String) launchActionExpr.evaluate(scheme, XPathConstants.STRING);
    assertThat(launchActionBlueprintIdentifier, equalTo("rootGID"));

    XPath profileActionXpath = xpathFactory.newXPath();
    XPathExpression profileActionExpr =
        profileActionXpath.compile("//ProfileAction//BuildableReference/@BlueprintIdentifier");
    String profileActionBlueprintIdentifier =
        (String) profileActionExpr.evaluate(scheme, XPathConstants.STRING);
    assertThat(profileActionBlueprintIdentifier, equalTo("rootGID"));
  }

  @Test
  public void buildableReferenceShouldHaveExpectedProperties() throws Exception {
    ImmutableMap.Builder<BuildTarget, PBXTarget> buildTargetToPbxTargetMapBuilder =
      ImmutableMap.builder();
    ImmutableMap.Builder<PBXTarget, Path> targetToProjectPathMapBuilder =
      ImmutableMap.builder();

    BuildTarget rootBuildTarget = BuildTarget.builder("//foo", "root").build();
    PBXTarget rootTarget = new PBXNativeTarget("rootRule", PBXTarget.ProductType.STATIC_LIBRARY);
    rootTarget.setGlobalID("rootGID");
    rootTarget.setProductReference(
        new PBXFileReference(
            "root.a", "root.a", PBXReference.SourceTree.BUILT_PRODUCTS_DIR));
    buildTargetToPbxTargetMapBuilder.put(rootBuildTarget, rootTarget);

    Path pbxprojectPath = Paths.get("foo/Foo.xcodeproj/project.pbxproj");
    targetToProjectPathMapBuilder.put(rootTarget, pbxprojectPath);

    SchemeGenerator schemeGenerator = new SchemeGenerator(
        projectFilesystem,
        Optional.of(rootBuildTarget),
        ImmutableSet.of(rootBuildTarget),
        ImmutableSet.<BuildTarget>of(),
        ImmutableSet.<BuildTarget>of(),
        "TestScheme",
        Paths.get("_gen/Foo.xcworkspace/scshareddata/xcshemes"),
        SchemeActionType.DEFAULT_CONFIG_NAMES,
        buildTargetToPbxTargetMapBuilder.build(),
        targetToProjectPathMapBuilder.build());

    Path schemePath = schemeGenerator.writeScheme();

    DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
    Document scheme = dBuilder.parse(projectFilesystem.newFileInputStream(schemePath));

    XPathFactory xpathFactory = XPathFactory.newInstance();

    XPath buildableReferenceXPath = xpathFactory.newXPath();
    XPathExpression buildableReferenceExpr =
        buildableReferenceXPath.compile("//BuildableReference");
    NodeList buildableReferences = (NodeList) buildableReferenceExpr.evaluate(
        scheme, XPathConstants.NODESET);

    assertThat(buildableReferences.getLength(), greaterThan(0));

    for (int i = 0; i < buildableReferences.getLength(); i++) {
      NamedNodeMap attributes = buildableReferences.item(i).getAttributes();
      assertThat(attributes, notNullValue());
      assertThat(attributes.getNamedItem("BlueprintIdentifier"), notNullValue());
      assertThat(attributes.getNamedItem("BuildableIdentifier"), notNullValue());
      assertThat(attributes.getNamedItem("ReferencedContainer"), notNullValue());
      assertThat(attributes.getNamedItem("BlueprintName"), notNullValue());
      assertThat(attributes.getNamedItem("BuildableName"), notNullValue());
    }
  }

  @Test
  public void allActionsShouldBePresentInSchemeWithDefaultBuildConfigurations() throws Exception {
    ImmutableMap.Builder<BuildTarget, PBXTarget> buildTargetToPbxTargetMapBuilder =
      ImmutableMap.builder();
    ImmutableMap.Builder<PBXTarget, Path> targetToProjectPathMapBuilder =
      ImmutableMap.builder();

    BuildTarget rootBuildTarget = BuildTarget.builder("//foo", "root").build();
    PBXTarget rootTarget = new PBXNativeTarget("rootRule", PBXTarget.ProductType.STATIC_LIBRARY);
    rootTarget.setGlobalID("rootGID");
    rootTarget.setProductReference(
        new PBXFileReference(
            "root.a", "root.a", PBXReference.SourceTree.BUILT_PRODUCTS_DIR));
    buildTargetToPbxTargetMapBuilder.put(rootBuildTarget, rootTarget);

    Path pbxprojectPath = Paths.get("foo/Foo.xcodeproj/project.pbxproj");
    targetToProjectPathMapBuilder.put(rootTarget, pbxprojectPath);

    SchemeGenerator schemeGenerator = new SchemeGenerator(
        projectFilesystem,
        Optional.of(rootBuildTarget),
        ImmutableSet.of(rootBuildTarget),
        ImmutableSet.<BuildTarget>of(),
        ImmutableSet.<BuildTarget>of(),
        "TestScheme",
        Paths.get("_gen/Foo.xcworkspace/scshareddata/xcshemes"),
        SchemeActionType.DEFAULT_CONFIG_NAMES,
        buildTargetToPbxTargetMapBuilder.build(),
        targetToProjectPathMapBuilder.build());

    Path schemePath = schemeGenerator.writeScheme();

    DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
    Document scheme = dBuilder.parse(projectFilesystem.newFileInputStream(schemePath));

    XPathFactory xpathFactory = XPathFactory.newInstance();

    XPath schemeChildrenXPath = xpathFactory.newXPath();
    XPathExpression schemeChildrenExpr =
        schemeChildrenXPath.compile("/Scheme/node()");
    NodeList actions = (NodeList) schemeChildrenExpr.evaluate(scheme, XPathConstants.NODESET);

    assertThat(actions.getLength(), equalTo(6));

    Node buildAction = actions.item(0);
    assertThat(buildAction.getNodeName(), equalTo("BuildAction"));
    assertThat(
        buildAction.getAttributes().getNamedItem("buildConfiguration"),
        nullValue());

    Node testAction = actions.item(1);
    assertThat(testAction.getNodeName(), equalTo("TestAction"));
    assertThat(
        testAction.getAttributes().getNamedItem("buildConfiguration").getNodeValue(),
        equalTo("Debug"));

    Node launchAction = actions.item(2);
    assertThat(launchAction.getNodeName(), equalTo("LaunchAction"));
    assertThat(
        launchAction.getAttributes().getNamedItem("buildConfiguration").getNodeValue(),
        equalTo("Debug"));

    Node profileAction = actions.item(3);
    assertThat(profileAction.getNodeName(), equalTo("ProfileAction"));
    assertThat(
        profileAction.getAttributes().getNamedItem("buildConfiguration").getNodeValue(),
        equalTo("Release"));

    Node analyzeAction = actions.item(4);
    assertThat(analyzeAction.getNodeName(), equalTo("AnalyzeAction"));
    assertThat(
        analyzeAction.getAttributes().getNamedItem("buildConfiguration").getNodeValue(),
        equalTo("Debug"));

    Node archiveAction = actions.item(5);
    assertThat(archiveAction.getNodeName(), equalTo("ArchiveAction"));
    assertThat(
        archiveAction.getAttributes().getNamedItem("buildConfiguration").getNodeValue(),
        equalTo("Release"));
  }

  @Test
  public void schemeIsRewrittenIfContentsHaveChanged() throws IOException {
    {
      ImmutableMap.Builder<BuildTarget, PBXTarget> buildTargetToPbxTargetMapBuilder =
        ImmutableMap.builder();
      ImmutableMap.Builder<PBXTarget, Path> targetToProjectPathMapBuilder =
        ImmutableMap.builder();

      BuildTarget rootBuildTarget = BuildTarget.builder("//foo", "root").build();
      PBXTarget rootTarget = new PBXNativeTarget("rootRule", PBXTarget.ProductType.STATIC_LIBRARY);
      rootTarget.setGlobalID("rootGID");
      rootTarget.setProductReference(
          new PBXFileReference(
              "root.a", "root.a", PBXReference.SourceTree.BUILT_PRODUCTS_DIR));
      buildTargetToPbxTargetMapBuilder.put(rootBuildTarget, rootTarget);

      Path pbxprojectPath = Paths.get("foo/Foo.xcodeproj/project.pbxproj");
      targetToProjectPathMapBuilder.put(rootTarget, pbxprojectPath);

      clock.setCurrentTimeMillis(49152);
      SchemeGenerator schemeGenerator = new SchemeGenerator(
          projectFilesystem,
          Optional.of(rootBuildTarget),
          ImmutableSet.of(rootBuildTarget),
          ImmutableSet.<BuildTarget>of(),
          ImmutableSet.<BuildTarget>of(),
          "TestScheme",
          Paths.get("_gen/Foo.xcworkspace/scshareddata/xcshemes"),
          SchemeActionType.DEFAULT_CONFIG_NAMES,
          buildTargetToPbxTargetMapBuilder.build(),
          targetToProjectPathMapBuilder.build());

      Path schemePath = schemeGenerator.writeScheme();
      assertThat(projectFilesystem.getLastModifiedTime(schemePath), equalTo(49152L));
    }

    {
      BuildTarget rootBuildTarget = BuildTarget.builder("//foo", "root").build();
      PBXTarget rootTarget = new PBXNativeTarget("rootRule2", PBXTarget.ProductType.STATIC_LIBRARY);
      rootTarget.setGlobalID("root2GID");
      rootTarget.setProductReference(
          new PBXFileReference(
              "root2.a", "root2.a", PBXReference.SourceTree.BUILT_PRODUCTS_DIR));

      Path pbxprojectPath = Paths.get("foo/Foo.xcodeproj/project.pbxproj");

      clock.setCurrentTimeMillis(64738);
      SchemeGenerator schemeGenerator = new SchemeGenerator(
          projectFilesystem,
          Optional.of(rootBuildTarget),
          ImmutableSet.of(rootBuildTarget),
          ImmutableSet.<BuildTarget>of(),
          ImmutableSet.<BuildTarget>of(),
          "TestScheme",
          Paths.get("_gen/Foo.xcworkspace/scshareddata/xcshemes"),
          SchemeActionType.DEFAULT_CONFIG_NAMES,
          ImmutableMap.of(rootBuildTarget, rootTarget),
          ImmutableMap.of(rootTarget, pbxprojectPath));

      Path schemePath = schemeGenerator.writeScheme();
      assertThat(projectFilesystem.getLastModifiedTime(schemePath), equalTo(64738L));
    }
  }

  @Test
  public void schemeIsNotRewrittenIfContentsHaveNotChanged() throws IOException {
    {
      BuildTarget rootBuildTarget = BuildTarget.builder("//foo", "root").build();
      PBXTarget rootTarget = new PBXNativeTarget("rootRule", PBXTarget.ProductType.STATIC_LIBRARY);
      rootTarget.setGlobalID("rootGID");
      rootTarget.setProductReference(
          new PBXFileReference(
              "root.a", "root.a", PBXReference.SourceTree.BUILT_PRODUCTS_DIR));

      Path pbxprojectPath = Paths.get("foo/Foo.xcodeproj/project.pbxproj");

      clock.setCurrentTimeMillis(49152);
      SchemeGenerator schemeGenerator = new SchemeGenerator(
          projectFilesystem,
          Optional.of(rootBuildTarget),
          ImmutableSet.of(rootBuildTarget),
          ImmutableSet.<BuildTarget>of(),
          ImmutableSet.<BuildTarget>of(),
          "TestScheme",
          Paths.get("_gen/Foo.xcworkspace/scshareddata/xcshemes"),
          SchemeActionType.DEFAULT_CONFIG_NAMES,
          ImmutableMap.of(rootBuildTarget, rootTarget),
          ImmutableMap.of(rootTarget, pbxprojectPath));

      Path schemePath = schemeGenerator.writeScheme();
      assertThat(projectFilesystem.getLastModifiedTime(schemePath), equalTo(49152L));
    }

    {
      BuildTarget rootBuildTarget = BuildTarget.builder("//foo", "root").build();
      PBXTarget rootTarget = new PBXNativeTarget("rootRule", PBXTarget.ProductType.STATIC_LIBRARY);
      rootTarget.setGlobalID("rootGID");
      rootTarget.setProductReference(
          new PBXFileReference(
              "root.a", "root.a", PBXReference.SourceTree.BUILT_PRODUCTS_DIR));

      Path pbxprojectPath = Paths.get("foo/Foo.xcodeproj/project.pbxproj");

      clock.setCurrentTimeMillis(64738);
      SchemeGenerator schemeGenerator = new SchemeGenerator(
          projectFilesystem,
          Optional.of(rootBuildTarget),
          ImmutableSet.of(rootBuildTarget),
          ImmutableSet.<BuildTarget>of(),
          ImmutableSet.<BuildTarget>of(),
          "TestScheme",
          Paths.get("_gen/Foo.xcworkspace/scshareddata/xcshemes"),
          SchemeActionType.DEFAULT_CONFIG_NAMES,
          ImmutableMap.of(rootBuildTarget, rootTarget),
          ImmutableMap.of(rootTarget, pbxprojectPath));
      Path schemePath = schemeGenerator.writeScheme();
      assertThat(projectFilesystem.getLastModifiedTime(schemePath), equalTo(49152L));
    }
  }

  @Test
  public void schemeWithNoPrimaryRuleCanIncludeTests() throws Exception{
    ImmutableMap.Builder<BuildTarget, PBXTarget> buildTargetToPbxTargetMapBuilder =
      ImmutableMap.builder();
    ImmutableMap.Builder<PBXTarget, Path> targetToProjectPathMapBuilder =
      ImmutableMap.builder();

    BuildTarget libraryBuildTarget = BuildTarget.builder("//foo", "lib").build();
    PBXTarget testLibraryTarget =
        new PBXNativeTarget("testLibrary", PBXTarget.ProductType.STATIC_LIBRARY);
    testLibraryTarget.setGlobalID("testLibraryGID");
    testLibraryTarget.setProductReference(
        new PBXFileReference(
            "lib.a", "lib.a", PBXReference.SourceTree.BUILT_PRODUCTS_DIR));
    buildTargetToPbxTargetMapBuilder.put(libraryBuildTarget, testLibraryTarget);

    BuildTarget testBuildTarget = BuildTarget.builder("//foo", "test").build();
    PBXTarget testTarget = new PBXNativeTarget("testRule", PBXTarget.ProductType.STATIC_LIBRARY);
    testTarget.setGlobalID("testGID");
    testTarget.setProductReference(
        new PBXFileReference(
            "test.a", "test.a", PBXReference.SourceTree.BUILT_PRODUCTS_DIR));
    buildTargetToPbxTargetMapBuilder.put(testBuildTarget, testTarget);

    BuildTarget xctestBuildTarget = BuildTarget.builder("//foo", "xctest").build();
    PBXTarget testBundleTarget =
        new PBXNativeTarget("testBundleRule", PBXTarget.ProductType.UNIT_TEST);
    testBundleTarget.setGlobalID("testBundleGID");
    testBundleTarget.setProductReference(
        new PBXFileReference(
            "test.xctest",
            "test.xctest",
            PBXReference.SourceTree.BUILT_PRODUCTS_DIR));
    buildTargetToPbxTargetMapBuilder.put(xctestBuildTarget, testBundleTarget);

    Path pbxprojectPath = Paths.get("foo/Foo.xcodeproj/project.pbxproj");
    targetToProjectPathMapBuilder.put(testLibraryTarget, pbxprojectPath);
    targetToProjectPathMapBuilder.put(testTarget, pbxprojectPath);
    targetToProjectPathMapBuilder.put(testBundleTarget, pbxprojectPath);

    SchemeGenerator schemeGenerator = new SchemeGenerator(
        projectFilesystem,
        Optional.<BuildTarget>absent(),
        ImmutableSet.<BuildTarget>of(),
        ImmutableSet.of(xctestBuildTarget),
        ImmutableSet.of(xctestBuildTarget),
        "TestScheme",
        Paths.get("_gen/Foo.xcworkspace/scshareddata/xcshemes"),
        SchemeActionType.DEFAULT_CONFIG_NAMES,
        buildTargetToPbxTargetMapBuilder.build(),
        targetToProjectPathMapBuilder.build());

    Path schemePath = schemeGenerator.writeScheme();
    String schemeXml = projectFilesystem.readFileIfItExists(schemePath).get();
    System.out.println(schemeXml);

    DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
    Document scheme = dBuilder.parse(projectFilesystem.newFileInputStream(schemePath));

    XPathFactory xpathFactory = XPathFactory.newInstance();

    XPath buildActionXpath = xpathFactory.newXPath();
    XPathExpression buildActionExpr =
        buildActionXpath.compile("//BuildAction//BuildableReference/@BlueprintIdentifier");
    NodeList buildActionNodes = (NodeList) buildActionExpr.evaluate(scheme, XPathConstants.NODESET);

    List<String> expectedOrdering = ImmutableList.of(
        "testBundleGID");

    List<String> actualOrdering = Lists.newArrayList();
    for (int i = 0; i < buildActionNodes.getLength(); i++) {
      actualOrdering.add(buildActionNodes.item(i).getNodeValue());
    }
    assertThat(actualOrdering, equalTo(expectedOrdering));

    XPath testActionXpath = xpathFactory.newXPath();
    XPathExpression testActionExpr =
        testActionXpath.compile("//TestAction//BuildableReference/@BlueprintIdentifier");
    String testActionBlueprintIdentifier =
        (String) testActionExpr.evaluate(scheme, XPathConstants.STRING);
    assertThat(testActionBlueprintIdentifier, equalTo("testBundleGID"));

    XPath launchActionXpath = xpathFactory.newXPath();
    XPathExpression launchActionExpr =
        launchActionXpath.compile("//LaunchAction//BuildableReference/@BlueprintIdentifier");
    String launchActionBlueprintIdentifier =
        (String) launchActionExpr.evaluate(scheme, XPathConstants.STRING);
    assertThat(launchActionBlueprintIdentifier, equalTo(""));

    XPath launchActionBuildConfigurationXpath = xpathFactory.newXPath();
    XPathExpression launchActionBuildConfigurationExpr =
        launchActionBuildConfigurationXpath.compile("//LaunchAction//@buildConfiguration");
    String launchActionBuildConfigurationBlueprintIdentifier =
        (String) launchActionBuildConfigurationExpr.evaluate(scheme, XPathConstants.STRING);
    assertThat(launchActionBuildConfigurationBlueprintIdentifier, equalTo("Debug"));

    XPath profileActionXpath = xpathFactory.newXPath();
    XPathExpression profileActionExpr =
        profileActionXpath.compile("//ProfileAction//BuildableReference/@BlueprintIdentifier");
    String profileActionBlueprintIdentifier =
        (String) profileActionExpr.evaluate(scheme, XPathConstants.STRING);
    assertThat(profileActionBlueprintIdentifier, equalTo(""));

    XPath profileActionBuildConfigurationXpath = xpathFactory.newXPath();
    XPathExpression profileActionBuildConfigurationExpr =
        profileActionBuildConfigurationXpath.compile("//ProfileAction//@buildConfiguration");
    String profileActionBuildConfigurationBlueprintIdentifier =
        (String) profileActionBuildConfigurationExpr.evaluate(scheme, XPathConstants.STRING);
    assertThat(profileActionBuildConfigurationBlueprintIdentifier, equalTo("Release"));

    XPath analyzeActionBuildConfigurationXpath = xpathFactory.newXPath();
    XPathExpression analyzeActionBuildConfigurationExpr =
        analyzeActionBuildConfigurationXpath.compile("//AnalyzeAction//@buildConfiguration");
    String analyzeActionBuildConfigurationBlueprintIdentifier =
        (String) analyzeActionBuildConfigurationExpr.evaluate(scheme, XPathConstants.STRING);
    assertThat(analyzeActionBuildConfigurationBlueprintIdentifier, equalTo("Debug"));

    XPath archiveActionBuildConfigurationXpath = xpathFactory.newXPath();
    XPathExpression archiveActionBuildConfigurationExpr =
        archiveActionBuildConfigurationXpath.compile("//ArchiveAction//@buildConfiguration");
    String archiveActionBuildConfigurationBlueprintIdentifier =
        (String) archiveActionBuildConfigurationExpr.evaluate(scheme, XPathConstants.STRING);
    assertThat(archiveActionBuildConfigurationBlueprintIdentifier, equalTo("Release"));
  }
}