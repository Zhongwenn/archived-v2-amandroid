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
package org.sireum.amandroid

import org.sireum.util._
import org.sireum.jawa.alir.dataDependenceAnalysis.InterproceduralDataDependenceInfo
import org.sireum.amandroid.parser.IntentFilterDataBase
import org.sireum.jawa.alir.pta.PTAResult
import java.util.zip.ZipInputStream
import java.io.FileInputStream
import java.util.zip.ZipEntry
import org.sireum.amandroid.parser.ComponentType
import org.sireum.jawa.JawaType
import org.sireum.jawa.Signature
import org.sireum.amandroid.parser.ComponentInfo
import org.sireum.amandroid.parser.LayoutControl

object Apk {
  def isValidApk(nameUri: FileResourceUri): Boolean = {
    class FindManifest extends Exception
    val file = FileUtil.toFile(nameUri)
    file match {
      case dir if dir.isDirectory() => false
      case _ => 
        var valid: Boolean = false
        var foundManifest: Boolean = false
        var foundDex: Boolean = false
        var archive : ZipInputStream = null
        try {
          archive = new ZipInputStream(new FileInputStream(file))
          var entry: ZipEntry = null
          entry = archive.getNextEntry()
          while (entry != null) {
            val entryName = entry.getName()
            if(entryName == "AndroidManifest.xml"){
              foundManifest = true
            } else if(entryName == "classes.dex"){
              foundDex = true
            }
            if(foundManifest && foundDex) {
              valid = true
              throw new FindManifest
            }
            entry = archive.getNextEntry()
          }
        } catch {
          case ie: InterruptedException => throw ie
          case e: Exception =>
        } finally {
          if (archive != null)
            archive.close()
        }
        valid
    }
  }
}

case class InvalidApk(fileUri: FileResourceUri) extends Exception

/**
 * this is an object, which hold information of apps. e.g. components, intent-filter database, etc.
 *
 * @author <a href="mailto:fgwei@k-state.edu">Fengguo Wei</a>
 * @author <a href="mailto:sroy@k-state.edu">Sankardas Roy</a> 
 */
case class Apk(nameUri: FileResourceUri) {
  import Apk._
  require(isValidApk(nameUri), throw InvalidApk(nameUri))
  private final val TITLE = "Apk"
  def getAppName: String = FileUtil.toFile(nameUri).getName
  private val activities: MSet[JawaType] = msetEmpty
  private val services: MSet[JawaType] = msetEmpty
  private val receivers: MSet[JawaType] = msetEmpty
  private val providers: MSet[JawaType] = msetEmpty
	private val dynamicRegisteredReceivers: MSet[JawaType] = msetEmpty
	
  private val rpcMethods: MMap[JawaType, MSet[Signature]] = mmapEmpty
  
	def addActivity(activity: JawaType) = this.synchronized{this.activities += activity}
  def addService(service: JawaType) = this.synchronized{this.services += service}
  def addReceiver(receiver: JawaType) = this.synchronized{this.receivers += receiver}
  def addProvider(provider: JawaType) = this.synchronized{this.providers += provider}
  def addActivities(activities: ISet[JawaType]) = this.synchronized{this.activities ++= activities}
  def addServices(services: ISet[JawaType]) = this.synchronized{this.services ++= services}
  def addReceivers(receivers: ISet[JawaType]) = this.synchronized{this.receivers ++= receivers}
  def addProviders(providers: ISet[JawaType]) = this.synchronized{this.providers ++= providers}
  
  def addRpcMethod(comp: JawaType, rpc: Signature) = this.rpcMethods.getOrElseUpdate(comp, msetEmpty) += rpc
  def addRpcMethods(comp: JawaType, rpcs: ISet[Signature]) = this.rpcMethods.getOrElseUpdate(comp, msetEmpty) ++= rpcs
  def addRpcMethods(map: IMap[JawaType, ISet[Signature]]) = map.foreach{case (k, vs) => this.rpcMethods.getOrElseUpdate(k, msetEmpty) ++= vs}
  def getRpcMethods(comp: JawaType): ISet[Signature] = this.rpcMethods.getOrElse(comp, msetEmpty).toSet
  def getRpcMethods: ISet[Signature] = this.rpcMethods.flatMap(_._2).toSet
  def getRpcMethodMapping: IMap[JawaType, ISet[Signature]] = this.rpcMethods.map {
    case (k, vs) => k -> vs.toSet
  }.toMap
	
  def getComponentType(comp: JawaType): Option[AndroidConstants.CompType.Value] = {
    if(activities.contains(comp)) Some(AndroidConstants.CompType.ACTIVITY)
    else if(services.contains(comp)) Some(AndroidConstants.CompType.SERVICE)
    else if(receivers.contains(comp)) Some(AndroidConstants.CompType.RECEIVER)
    else if(providers.contains(comp)) Some(AndroidConstants.CompType.PROVIDER)
    else None
  }
  
	def setComponents(comps: ISet[(JawaType, ComponentType.Value)]) = this.synchronized{
    comps.foreach{
      case (ac, typ) => 
        typ match {
          case ComponentType.ACTIVITY =>
            this.addActivity(ac)
          case ComponentType.SERVICE =>
            this.addService(ac)
          case ComponentType.RECEIVER =>
            this.addReceiver(ac)
          case ComponentType.PROVIDER =>
            this.addProvider(ac)
        }
    }
  }
	
	def getComponents: ISet[JawaType] = (this.activities ++ this.services ++ this.receivers ++ this.providers).toSet
	def getActivities: ISet[JawaType] = this.activities.toSet
  def getServices: ISet[JawaType] = this.services.toSet
  def getReceivers: ISet[JawaType] = this.receivers.toSet
  def getProviders: ISet[JawaType] = this.providers.toSet
  
	def addDynamicRegisteredReceiver(receiver: JawaType) = 
    this.synchronized{
      this.dynamicRegisteredReceivers += receiver
      this.receivers += receiver
    }
	def addDynamicRegisteredReceivers(receivers: ISet[JawaType]) = 
    this.synchronized{
      this.dynamicRegisteredReceivers ++= receivers
      this.receivers ++= receivers
    }

	def getDynamicRegisteredReceivers: ISet[JawaType] = this.dynamicRegisteredReceivers.toSet
	
	private val uses_permissions: MSet[String] = msetEmpty
  private val callbackMethods: MMap[JawaType, MSet[Signature]] = mmapEmpty
  private val componentInfos: MSet[ComponentInfo] = msetEmpty
  private val layoutControls: MMap[Int, LayoutControl] = mmapEmpty
  private var appPackageName: String = null
  private val intentFdb: IntentFilterDataBase = new IntentFilterDataBase
  private var codeLineCounter: Int = 0
	/**
   * Map from record name to it's env method code.
   */
  protected val envProcMap: MMap[JawaType, (Signature, String)] = mmapEmpty
  
  def setCodeLineCounter(c: Int) = this.codeLineCounter = c
  def getCodeLineCounter: Int = this.codeLineCounter
	def setIntentFilterDB(i: IntentFilterDataBase) = this.synchronized{this.intentFdb.reset.merge(i)}
	def updateIntentFilterDB(i: IntentFilterDataBase) = this.synchronized{this.intentFdb.merge(i)}
	def getIntentFilterDB = this.intentFdb
  def setPackageName(pn: String) = this.appPackageName = pn
  def getPackageName: String = this.appPackageName
  def addUsesPermissions(ps: ISet[String]) = this.uses_permissions ++= ps
  def getUsesPermissions: ISet[String] = this.uses_permissions.toSet
  def addLayoutControls(i: Int, lc: LayoutControl) = this.layoutControls(i) = lc
  def addLayoutControls(lcs: IMap[Int, LayoutControl]) = this.layoutControls ++= lcs
  def getLayoutControls: IMap[Int, LayoutControl] = this.layoutControls.toMap
  def addCallbackMethods(typ: JawaType, sigs: ISet[Signature]) = this.callbackMethods.getOrElseUpdate(typ, msetEmpty) ++= sigs
  def addCallbackMethods(map: IMap[JawaType, ISet[Signature]]) = map.foreach {case (k, vs) => this.callbackMethods.getOrElseUpdate(k, msetEmpty) ++= vs}
  def getCallbackMethodMapping: IMap[JawaType, ISet[Signature]] = this.callbackMethods.map {
	  case (k, vs) => k -> vs.toSet
	}.toMap
  def getCallbackMethods: ISet[Signature] = if(!this.callbackMethods.isEmpty)this.callbackMethods.map(_._2.toSet).reduce(iunion[Signature]) else isetEmpty
  def addComponentInfo(ci: ComponentInfo) = this.componentInfos += ci
  def addComponentInfos(cis: ISet[ComponentInfo]) = this.componentInfos ++= cis
  def getComponentInfos: ISet[ComponentInfo] = this.componentInfos.toSet
  def printEnvs() =
    envProcMap.foreach{case(k, v) => println("Environment for " + k + "\n" + v._2)}

  def printEntrypoints() = {
    if (this.componentInfos == null)
      println("Entry points not initialized")
    else {
      println("Classes containing entry points:")
      for (record <- componentInfos)
        println("\t" + record)
      println("End of Entrypoints")
    }
  }

  def getEntryPoints: ISet[JawaType] = this.componentInfos.filter(_.enabled).map(_.compType).toSet
  
  def addEnvMap(typ: JawaType, sig: Signature, code: String) = this.envProcMap(typ) = ((sig, code))
  def getEnvMap = this.envProcMap.toMap
  def getEnvString: String = {
    val sb = new StringBuilder
    this.envProcMap.foreach{
      case (k, v) =>
        sb.append("*********************** Environment for " + k + " ************************\n")
        sb.append(v._2 + "\n\n")
    }
    sb.toString.intern()
  }

  def hasEnv(typ: JawaType): Boolean = this.envProcMap.contains(typ)
	
  def reset = {
    this.activities.clear()
    this.services.clear()
    this.receivers.clear()
    this.providers.clear()
    this.dynamicRegisteredReceivers.clear()
    this.intentFdb.reset
  }
}