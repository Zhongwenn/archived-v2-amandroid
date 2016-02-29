/*******************************************************************************
 * Copyright (c) 2013 - 2016 Fengguo Wei and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Main Contributors:
 *    Fengguo Wei - Argus Lab @ University of South Florida
 *    Sankardas Roy - Bowling Green State University
 *    
 * Contributors:
 *    Robby - Santos Lab @ Kansas State University
 *    Wu Zhou - Fireeye
 *    Fengchi Lin - Chinese People's Public Security University
 ******************************************************************************/
package org.sireum.amandroid.serialization

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import org.sireum.jawa.alir.pta._

object InstanceSerializer extends CustomSerializer[Instance](format => (
    {
      case jv: JValue =>
        implicit val formats = format + ContextSerializer + JawaTypeSerializer
        println(jv)
        jv match {
          case JObject(List(JField("ClassInstance", v))) => Extraction.extract[ClassInstance](v)
          case JObject(List(JField("PTAInstance", v))) => Extraction.extract[PTAInstance](v)
          case JObject(List(JField("PTATupleInstance", v))) => Extraction.extract[PTATupleInstance](v)
          case JObject(List(JField("PTAPointStringInstance", v))) => Extraction.extract[PTAPointStringInstance](v)
          case JObject(List(JField("PTAConcreteStringInstance", v))) => Extraction.extract[PTAConcreteStringInstance](v)
        }
    }, {
      case ins: Instance =>
        implicit val formats = format + ContextSerializer + JawaTypeSerializer
        ins match {
          case c: ClassInstance => ("ClassInstance" -> ("classtyp" -> Extraction.decompose(c.classtyp)) ~ ("defSite" -> Extraction.decompose(c.defSite)))
          case c: PTAInstance => ("PTAInstance" -> ("typ" -> Extraction.decompose(c.typ)) ~ ("defSite" -> Extraction.decompose(c.defSite)) ~ ("isNull_" -> c.isNull_))
          case c: PTATupleInstance => ("PTATupleInstance" -> ("left" -> Extraction.decompose(c.left)) ~ ("right" -> Extraction.decompose(c.right)) ~ ("defSite" -> Extraction.decompose(c.defSite)))
          case c: PTAPointStringInstance => ("PTAPointStringInstance" -> ("defSite" -> Extraction.decompose(c.defSite)))
          case c: PTAConcreteStringInstance => ("PTAConcreteStringInstance" -> ("string" -> c.string) ~ ("defSite" -> Extraction.decompose(c.defSite)))
        }
    }
))
