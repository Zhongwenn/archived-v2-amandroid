/*******************************************************************************
 * Copyright (c) 2013 - 2016 Fengguo Wei and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Detailed contributors are listed in the CONTRIBUTOR.md
 ******************************************************************************/
package org.sireum.amandroid.serialization

import org.json4s._
import org.json4s.JsonDSL._
import org.sireum.util._
import org.sireum.jawa.JawaType
import org.sireum.jawa.JavaKnowledge

object JawaTypeSerializer extends CustomSerializer[JawaType](format => (
    {
      case jv: JValue =>
        implicit val formats = format
        val str = (jv \ "typ").extract[String]
        JavaKnowledge.getTypeFromName(str)
    },
    {
      case typ: JawaType =>
        ("typ" -> typ.jawaName)
    }
))

object JawaTypeKeySerializer extends CustomKeySerializer[JawaType](format => (
    {
      case str: String =>
        JavaKnowledge.getTypeFromName(str)
    }, {
      case typ: JawaType =>
        typ.jawaName
    }
))
