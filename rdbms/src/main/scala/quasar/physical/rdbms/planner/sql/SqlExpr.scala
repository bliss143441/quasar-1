/*
 * Copyright 2014–2017 SlamData Inc.
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

package quasar.physical.rdbms.planner.sql

import slamdata.Predef._
import quasar.Data

sealed abstract class SqlExpr[T]

object SqlExpr extends SqlExprInstances {

  import Select._
  final case class Id[T](v: String) extends SqlExpr[T]
  final case class Ref[T](ref: T) extends SqlExpr[T]
  final case class Null[T]() extends SqlExpr[T]

  final case class SelectRow[T](selection: Selection[T], from: From[T])
      extends SqlExpr[T]

  final case class Select[T](selection: Selection[T],
                             from: From[T],
                             filter: Option[Filter[T]])
      extends SqlExpr[T]
  final case class From[T](v: T, alias: Option[Id[T]])
  final case class Selection[T](v: T, alias: Option[Id[T]])
  final case class Table[T](name: String) extends SqlExpr[T]

  final case class Op[T](op: String, left: T, right: T) extends SqlExpr[T]
  final case class Constant[T](data: Data) extends SqlExpr[T]


  object Select {
    final case class Filter[T](v: T)
    final case class RowIds[T]() extends SqlExpr[T]
    final case class AllCols[T](alias: String) extends SqlExpr[T]
    final case class WithIds[T](v: T) extends SqlExpr[T]
  }
}
