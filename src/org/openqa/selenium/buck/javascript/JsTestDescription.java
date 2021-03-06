/*
 * Copyright 2013-present Facebook, Inc.
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

package org.openqa.selenium.buck.javascript;


import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.parser.NoSuchBuildTargetException;
import com.facebook.buck.rules.AbstractDescriptionArg;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.BuildRuleType;
import com.facebook.buck.rules.CellPathResolver;
import com.facebook.buck.rules.Description;
import com.facebook.buck.rules.ImplicitDepsInferringDescription;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePaths;
import com.facebook.buck.rules.TargetGraph;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSortedSet;

import java.util.Collections;

public class JsTestDescription implements
    Description<JsTestDescription.Arg>,
    ImplicitDepsInferringDescription<JsTestDescription.Arg> {

  private static final BuildRuleType TYPE = BuildRuleType.of("js_test");

  private final JavascriptConfig config;

  public JsTestDescription(JavascriptConfig config) {
    this.config = config;
  }

  @Override
  public BuildRuleType getBuildRuleType() {
    return TYPE;
  }

  @Override
  public Arg createUnpopulatedConstructorArg() {
    return new Arg();
  }

  @Override
  public <A extends Arg> BuildRule createBuildRule(
      TargetGraph targetGraph,
      BuildRuleParams params,
      BuildRuleResolver resolver,
      A args) throws NoSuchBuildTargetException {
    return null;
  }

  @Override
  public Iterable<BuildTarget> findDepsForTargetFromConstructorArgs(
      BuildTarget buildTarget, CellPathResolver cellRoots, Arg arg) {
    SourcePath compiler = config.getClosureCompilerSourcePath(arg.compiler);
    return SourcePaths.filterBuildTargetSourcePaths(Collections.singleton(compiler));
  }

  static class Arg extends AbstractDescriptionArg {
    public Optional<SourcePath> compiler;
    public ImmutableSortedSet<SourcePath> srcs;

    public Optional<ImmutableSortedSet<BuildTarget>> deps;
  }
}
