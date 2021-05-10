/*
 * Copyright 2020 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.javascript.jscomp.serialization;

import static com.google.common.base.Preconditions.checkState;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.javascript.jscomp.colors.ColorId;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.ObjectType;
import javax.annotation.Nullable;

final class JSTypeColorIdHasher {

  JSTypeColorIdHasher() {}

  ColorId hashObjectType(ObjectType type) {
    checkState(
        type != null
            && !type.isEnumElementType()
            && !type.isNoResolvedType()
            && !type.isTemplatizedType()
            && !type.isUnknownType()
            && !type.isTemplateType(),
        type);

    Hasher hasher = FARM_64.newHasher();

    if (type.isFunctionType()) {
      FunctionType fnType = type.toMaybeFunctionType();
      if (fnType.hasInstanceType()) {
        hasher.putInt(Marker.HAS_INSTANCE_TYPE);
      }
    }

    if (type.hasReferenceName()) {
      this.putReferenceNameType(hasher, type);
    } else {
      for (String prop : type.getOwnPropertyNames()) {
        putUtf8(hasher, prop);
      }
    }

    return ColorId.fromUnsigned(hasher.hash().asLong());
  }

  private void putReferenceNameType(Hasher hasher, ObjectType type) {
    putUtf8(hasher, type.getReferenceName());

    JSType.WithSourceRef sourceRef = sourceRefFor(type);
    if (sourceRef == null) {
      // TODO(b/185519307): This is a hack to work around bugs in the typesystem.
      hasher.putInt(Marker.UNKNOWN_SOURCEREF);
      return;
    }

    String moduleId = sourceRef.getGoogModuleId();
    if (moduleId == null) {
      hasher.putInt(Marker.NO_GOOG_MODULE_ID);
    } else {
      putUtf8(hasher, moduleId);
    }
  }

  @Nullable
  private static JSType.WithSourceRef sourceRefFor(ObjectType type) {
    if (type.isEnumType()) {
      return type.toMaybeEnumType();
    } else if (type.isEnumElementType()) {
      // EnumElementTypes are proxied to their underlying type when transformed to colors.
      throw new AssertionError(type);
    }

    if (type.isFunctionType()) {
      return type.toMaybeFunctionType();
    } else if (type.isFunctionPrototypeType()) {
      return type.getOwnerFunction();
    } else if (type.getConstructor() != null) {
      return type.getConstructor();
    }

    return null;
  }

  private static final HashFunction FARM_64 = Hashing.farmHashFingerprint64();

  private static void putUtf8(Hasher hasher, String s) {
    hasher.putString(s, UTF_8);
  }

  /**
   * Arbitrary constants that may get hashed into ColorId.
   *
   * <p>Since a hash may end up including combinations of markers, using int values reduces that
   * chance that two combinations will concatenate to the same bit sequence.
   */
  private static final class Marker {
    static final int HAS_INSTANCE_TYPE = 0x8c8b70db;
    static final int IS_PROTOTYPE_TYPE = 0x4c906855;
    static final int NO_GOOG_MODULE_ID = 0x2593c5ff;
    static final int UNKNOWN_SOURCEREF = 0x660be782;
  }
}