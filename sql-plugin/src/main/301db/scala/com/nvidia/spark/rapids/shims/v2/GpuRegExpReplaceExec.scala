/*
 * Copyright (c) 2021, NVIDIA CORPORATION.
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
package com.nvidia.spark.rapids.shims.v2

import com.nvidia.spark.rapids.{CudfRegexTranspiler, DataFromReplacementRule, GpuExpression, GpuOverrides, RapidsConf, RapidsMeta, RegexUnsupportedException, TernaryExprMeta}

import org.apache.spark.sql.catalyst.expressions.{Expression, Literal, RegExpReplace}
import org.apache.spark.sql.rapids.{GpuRegExpReplace, GpuStringReplace}
import org.apache.spark.sql.types.DataTypes
import org.apache.spark.unsafe.types.UTF8String

class GpuRegExpReplaceMeta(
    expr: RegExpReplace,
    conf: RapidsConf,
    parent: Option[RapidsMeta[_, _, _]],
    rule: DataFromReplacementRule)
  extends TernaryExprMeta[RegExpReplace](expr, conf, parent, rule) {

  private var pattern: Option[String] = None

  override def tagExprForGpu(): Unit = {
    expr.regexp match {
      case Literal(s: UTF8String, DataTypes.StringType) if s != null =>
        if (GpuOverrides.isSupportedStringReplacePattern(expr.regexp)) {
          // use GpuStringReplace
        } else {
          try {
            pattern = Some(new CudfRegexTranspiler(replace = true).transpile(s.toString))
          } catch {
            case e: RegexUnsupportedException =>
              willNotWorkOnGpu(e.getMessage)
          }
        }

      case _ =>
        willNotWorkOnGpu(s"only non-null literal strings are supported on GPU")
    }
  }

  override def convertToGpu(
      lhs: Expression,
      regexp: Expression,
      rep: Expression): GpuExpression = {
    if (GpuOverrides.isSupportedStringReplacePattern(expr.regexp)) {
      GpuStringReplace(lhs, regexp, rep)
    } else {
      GpuRegExpReplace(lhs, regexp, rep, pattern.getOrElse(
        throw new IllegalStateException("Expression has not been tagged with cuDF regex pattern")))
    }
  }
}