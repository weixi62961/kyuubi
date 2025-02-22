/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.kyuubi.plugin.spark.authz

import java.net.URI
import javax.annotation.Nonnull

import org.apache.kyuubi.plugin.spark.authz.PrivilegeObjectActionType.PrivilegeObjectActionType
import org.apache.kyuubi.plugin.spark.authz.PrivilegeObjectType._
import org.apache.kyuubi.plugin.spark.authz.serde.{Database, Function, Table, Uri}

/**
 * Build a Spark logical plan to different `PrivilegeObject`s
 * - For queries, they may generates a list of **input** `PrivilegeObject`s, which describe
 *   a SELECT-only privilege type for different tables or columns
 *
 * - For commands, they may generates a list of **out** `PrivilegeObject`s, which describe
 * a CREATE/ALTER/DELETE-related privilege type for different objects, sometimes they also
 * generates a list of **input** `PrivilegeObject`s if contain a child query, like `CTAS`.
 *
 * Then we converts all of these lists to `AccessRequest` to the apache ranger admin server.
 * @param privilegeObjectType db, table, function
 * @param actionType describe the action on a object
 * @param dbname database name
 * @param objectName object name - database, table, or function
 * @param columns column list if any
 */
case class PrivilegeObject(
    privilegeObjectType: PrivilegeObjectType,
    actionType: PrivilegeObjectActionType,
    dbname: String,
    objectName: String,
    @Nonnull columns: Seq[String],
    owner: Option[String],
    catalog: Option[String])

object PrivilegeObject {

  def apply(database: Database): PrivilegeObject = {
    new PrivilegeObject(
      DATABASE,
      PrivilegeObjectActionType.OTHER,
      database.database,
      database.database,
      Nil,
      None,
      database.catalog)
  }

  def apply(
      table: Table,
      columns: Seq[String] = Nil,
      actionType: PrivilegeObjectActionType = PrivilegeObjectActionType.OTHER): PrivilegeObject = {
    new PrivilegeObject(
      TABLE_OR_VIEW,
      actionType,
      table.database.orNull,
      table.table,
      columns,
      table.owner,
      table.catalog)
  }

  def apply(function: Function): PrivilegeObject = {
    new PrivilegeObject(
      FUNCTION,
      PrivilegeObjectActionType.OTHER,
      function.database.orNull,
      function.functionName,
      Nil,
      None,
      None
    ) // TODO: Support catalog for function
  }

  def apply(uri: Uri): PrivilegeObject = {
    val privilegeObjectType = Option(new URI(uri.path).getScheme) match {
      case Some("file") => LOCAL_URI
      case _ => DFS_URL
    }
    new PrivilegeObject(
      privilegeObjectType,
      PrivilegeObjectActionType.OTHER,
      uri.path,
      null,
      Nil,
      None,
      None)
  }
}
