/*
 * Copyright 2015-present Facebook, Inc.
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

package com.facebook.buck.android;

import com.facebook.buck.java.JavaCompilationConstants;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.rules.AbstractNodeBuilder;
import com.facebook.buck.rules.TestSourcePath;

import java.nio.file.Path;

public class AndroidPrebuiltAarBuilder
    extends AbstractNodeBuilder<AndroidPrebuiltAarDescription.Arg> {

 private AndroidPrebuiltAarBuilder(BuildTarget target) {
  super(new AndroidPrebuiltAarDescription(JavaCompilationConstants.ANDROID_JAVAC_OPTIONS), target);
 }

 public static AndroidPrebuiltAarBuilder createBuilder(BuildTarget target) {
  return new AndroidPrebuiltAarBuilder(target);
 }

 public AndroidPrebuiltAarBuilder setBinaryAar(Path binaryAar) {
  arg.aar = new TestSourcePath(binaryAar.toString());
  return this;
 }
}