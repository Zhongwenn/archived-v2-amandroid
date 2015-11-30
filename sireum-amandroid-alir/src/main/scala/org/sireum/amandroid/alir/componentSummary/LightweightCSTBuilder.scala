package org.sireum.amandroid.alir.componentSummary

import org.sireum.amandroid.appInfo.AppInfoCollector
import org.sireum.jawa.Reporter
import org.sireum.amandroid.Apk
import org.sireum.jawa.Global
import org.sireum.util._
import org.sireum.jawa.util.MyTimer
import java.io.File
import org.sireum.jawa.JawaClass
import org.sireum.amandroid.appInfo.ReachableInfoCollector
import org.sireum.jawa.alir.pta.suspark.InterproceduralSuperSpark
import org.sireum.jawa.alir.dataFlowAnalysis.InterProceduralDataFlowGraph
import org.sireum.amandroid.alir.componentSummary.ComponentSummaryTable.CHANNELS
import org.sireum.jawa.alir.controlFlowGraph.ICFGEntryNode
import org.sireum.jawa.alir.controlFlowGraph.ICFGCallNode
import org.sireum.amandroid.AndroidConstants
import org.sireum.jawa.alir.pta.VarSlot
import org.sireum.jawa.alir.pta.Instance
import org.sireum.jawa.alir.Context
import org.sireum.amandroid.alir.pta.reachingFactsAnalysis.IntentHelper.IntentContent
import org.sireum.amandroid.parser.UriData
import org.sireum.jawa.alir.pta.PTAConcreteStringInstance
import org.sireum.jawa.alir.pta.ClassInstance
import org.sireum.amandroid.alir.pta.reachingFactsAnalysis.IntentHelper

/**
 * Hopefully this will be really light weight to build the component based summary table
 * @author fgwei
 */
class LightweightCSTBuilder(global: Global, apk: Apk, yard: ApkYard, outputUri: FileResourceUri, timer: Option[MyTimer]) extends AppInfoCollector(global, apk, outputUri, timer) {
  
  private val intentContents: MMap[JawaClass, MMap[Instance, MSet[IntentContent]]] = mmapEmpty
  
  override def collectInfo: Unit = {
    val manifestUri = outputUri + File.separator + "AndroidManifest.xml"
    val mfp = AppInfoCollector.analyzeManifest(global.reporter, manifestUri)
    this.intentFdb.merge(mfp.getIntentDB)
    val components = msetEmpty[JawaClass]
    mfp.getComponentInfos.foreach {
      f =>
        if(f.enabled){
          val comp = global.getClassOrResolve(f.compType)
          if(!comp.isUnknown && comp.isApplicationClass){
            components += comp
          }
        }
    }
    components.foreach {
      comp =>
        val rpcs = getRpcMethods(comp)
        apk.addRpcMethods(comp, rpcs)
    }
    apk.setComponents(components.toSet)
    apk.updateIntentFilterDB(this.intentFdb)
    apk.setAppInfo(this)
    buildCST(components.toSet)
  }
  
  private def buildCST(comps: ISet[JawaClass]) = {
    comps foreach {
      comp =>
        val methods = comp.getDeclaredMethods
        val idfg = InterproceduralSuperSpark(global, methods, timer)
        collectIntentContent(comp, idfg)
        buildCSTFromIDFG(comp, idfg)
    }
  }
  
  private def collectIntentContent(component: JawaClass, idfg: InterProceduralDataFlowGraph) = {
    val cpIntentmap = intentContents.getOrElseUpdate(component, mmapEmpty)
    val allIntent = msetEmpty[Instance]
    val impreciseExplicit = msetEmpty[Instance]
    val impreciseImplicit = msetEmpty[Instance]
    val componentNames = mmapEmpty[Instance, MSet[String]]
    val actions = mmapEmpty[Instance, MSet[String]]
    val categories = mmapEmpty[Instance, MSet[String]]
    val datas = mmapEmpty[Instance, MSet[UriData]]
    val types = mmapEmpty[Instance, MSet[String]]
    val unresolvedData = mmapEmpty[Instance, MSet[Instance]]
    val unresolvedComp = mmapEmpty[Instance, MSet[Instance]]
    
    val icfg = idfg.icfg
    val ptaresult = idfg.ptaresult
    icfg.nodes.foreach {
      node =>
        node match {
          case cn: ICFGCallNode =>
            val callees = cn.getCalleeSet
            val args = cn.argNames
            val currentContext = cn.context
            callees foreach {
              callee =>
                callee.callee.getSignature.signature match {
                  case "Landroid/content/Intent;.<init>:(Landroid/content/Context;Ljava/lang/Class;)V" =>  //public constructor
                    val thisSlot = VarSlot(args(0), false, true)
                    val thisValue = ptaresult.pointsToSet(thisSlot, currentContext)
                    allIntent ++= thisValue
                    val param2Slot = VarSlot(args(2), false, true)
                    val param2Value = ptaresult.pointsToSet(param2Slot, currentContext)
                    param2Value.map{
                      value => 
                        value match {
                          case ci: ClassInstance =>
                            val componentName = ci.getName
                            thisValue.map(componentNames.getOrElseUpdate(_, msetEmpty) += componentName)
                          case _ => impreciseExplicit ++= thisValue
                        }
                    }
                  case "Landroid/content/Intent;.<init>:(Landroid/content/Intent;)V" =>  //public constructor
                  case "Landroid/content/Intent;.<init>:(Landroid/content/Intent;Z)V" =>  //private constructor
                  case "Landroid/content/Intent;.<init>:(Ljava/lang/String;)V" =>  //public constructor
                    val thisSlot = VarSlot(args(0), false, true)
                    val thisValue = ptaresult.pointsToSet(thisSlot, currentContext)
                    allIntent ++= thisValue
                    val actionSlot = VarSlot(args(1), false, true)
                    val actionValue = ptaresult.pointsToSet(actionSlot, currentContext)
                    actionValue.foreach {
                      actIns =>
                        actIns match {
                          case psi: PTAConcreteStringInstance =>
                            val action = psi.string
                            thisValue.map(actions.getOrElseUpdate(_, msetEmpty) += action)
                          case _ => impreciseImplicit ++= thisValue
                        }
                    }
                  case "Landroid/content/Intent;.<init>:(Ljava/lang/String;Landroid/net/Uri;)V" =>  //public constructor
                    val thisSlot = VarSlot(args(0), false, true)
                    val thisValue = ptaresult.pointsToSet(thisSlot, currentContext)
                    allIntent ++= thisValue
                    val actionSlot = VarSlot(args(1), false, true)
                    val actionValue = ptaresult.pointsToSet(actionSlot, currentContext)
                    val dataSlot = VarSlot(args(2), false, true)
                    val dataValue = ptaresult.pointsToSet(dataSlot, currentContext)
                    actionValue.foreach {
                      actIns =>
                        actIns match {
                          case psi: PTAConcreteStringInstance =>
                            val action = psi.string
                            thisValue.map(actions.getOrElseUpdate(_, msetEmpty) += action)
                          case _ => impreciseImplicit ++= thisValue
                        }
                    }
                    thisValue.map(unresolvedData.getOrElse(_, msetEmpty) ++= dataValue)
                  case "Landroid/content/Intent;.<init>:(Ljava/lang/String;Landroid/net/Uri;Landroid/content/Context;Ljava/lang/Class;)V" =>  //public constructor
                    val thisSlot = VarSlot(args(0), false, true)
                    val thisValue = ptaresult.pointsToSet(thisSlot, currentContext)
                    allIntent ++= thisValue
                    val actionSlot = VarSlot(args(1), false, true)
                    val actionValue = ptaresult.pointsToSet(actionSlot, currentContext)
                    val dataSlot = VarSlot(args(2), false, true)
                    val dataValue = ptaresult.pointsToSet(dataSlot, currentContext)
                    val classSlot = VarSlot(args(4), false, true)
                    val classValue = ptaresult.pointsToSet(classSlot, currentContext)
                    actionValue.foreach {
                      actIns =>
                        actIns match {
                          case psi: PTAConcreteStringInstance =>
                            val action = psi.string
                            thisValue.map(actions.getOrElseUpdate(_, msetEmpty) += action)
                          case _ => impreciseImplicit ++= thisValue
                        }
                    }
                    thisValue.map(unresolvedData.getOrElse(_, msetEmpty) ++= dataValue)
                    classValue.map{
                      classIns => 
                        classIns match {
                          case ci: ClassInstance =>
                            val componentName = ci.getName
                            thisValue.map(componentNames.getOrElseUpdate(_, msetEmpty) += componentName)
                          case _ => impreciseExplicit ++= thisValue
                        }
                    }
                  case "Landroid/content/Intent;.addCategory:(Ljava/lang/String;)Landroid/content/Intent;" =>  //public
                    val thisSlot = VarSlot(args(0), false, true)
                    val thisValue = ptaresult.pointsToSet(thisSlot, currentContext)
                    allIntent ++= thisValue
                    val categorySlot = VarSlot(args(1), false, true)
                    val categoryValue = ptaresult.pointsToSet(categorySlot, currentContext)
                    categoryValue.foreach {
                      catIns =>
                        catIns match {
                          case psi: PTAConcreteStringInstance =>
                            val category = psi.string
                            thisValue.map(categories.getOrElseUpdate(_, msetEmpty) += category)
                          case _ => impreciseImplicit ++= thisValue
                        }
                    }
                  case "Landroid/content/Intent;.addFlags:(I)Landroid/content/Intent;" =>  //public
                  case "Landroid/content/Intent;.setAction:(Ljava/lang/String;)Landroid/content/Intent;" =>  //public
                    val thisSlot = VarSlot(args(0), false, true)
                    val thisValue = ptaresult.pointsToSet(thisSlot, currentContext)
                    allIntent ++= thisValue
                    val actionSlot = VarSlot(args(1), false, true)
                    val actionValue = ptaresult.pointsToSet(actionSlot, currentContext)
                    actionValue.foreach {
                      actIns =>
                        actIns match {
                          case psi: PTAConcreteStringInstance =>
                            val action = psi.string
                            thisValue.map(actions.getOrElseUpdate(_, msetEmpty) += action)
                          case _ => impreciseImplicit ++= thisValue
                        }
                    }
                  case "Landroid/content/Intent;.setClass:(Landroid/content/Context;Ljava/lang/Class;)Landroid/content/Intent;" =>  //public
                    val thisSlot = VarSlot(args(0), false, true)
                    val thisValue = ptaresult.pointsToSet(thisSlot, currentContext)
                    allIntent ++= thisValue
                    val classSlot = VarSlot(args(2), false, true)
                    val classValue = ptaresult.pointsToSet(classSlot, currentContext)
                    classValue.map{
                      classIns => 
                        classIns match {
                          case ci: ClassInstance =>
                            val componentName = ci.getName
                            thisValue.map(componentNames.getOrElseUpdate(_, msetEmpty) += componentName)
                          case _ => impreciseExplicit ++= thisValue
                        }
                    }
                  case "Landroid/content/Intent;.setClassName:(Landroid/content/Context;Ljava/lang/String;)Landroid/content/Intent;" |
                       "Landroid/content/Intent;.setClassName:(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;" =>  //public
                    val thisSlot = VarSlot(args(0), false, true)
                    val thisValue = ptaresult.pointsToSet(thisSlot, currentContext)
                    allIntent ++= thisValue
                    val classSlot = VarSlot(args(2), false, true)
                    val classValue = ptaresult.pointsToSet(classSlot, currentContext)
                    classValue.map{
                      classIns => 
                        classIns match {
                          case psi: PTAConcreteStringInstance =>
                            val componentName = psi.string
                            thisValue.map(componentNames.getOrElseUpdate(_, msetEmpty) += componentName)
                          case _ => impreciseExplicit ++= thisValue
                        }
                    }
                  case "Landroid/content/Intent;.setComponent:(Landroid/content/ComponentName;)Landroid/content/Intent;" =>  //public
                    val thisSlot = VarSlot(args(0), false, true)
                    val thisValue = ptaresult.pointsToSet(thisSlot, currentContext)
                    allIntent ++= thisValue
                    val componentSlot = VarSlot(args(1), false, true)
                    val componentValue = ptaresult.pointsToSet(componentSlot, currentContext)
                    thisValue.map(unresolvedComp.getOrElse(_, msetEmpty) ++= componentValue)
                  case "Landroid/content/Intent;.setData:(Landroid/net/Uri;)Landroid/content/Intent;" |
                       "Landroid/content/Intent;.setDataAndNormalize:(Landroid/net/Uri;)Landroid/content/Intent;" =>  //public
                    val thisSlot = VarSlot(args(0), false, true)
                    val thisValue = ptaresult.pointsToSet(thisSlot, currentContext)
                    allIntent ++= thisValue
                    val dataSlot = VarSlot(args(1), false, true)
                    val dataValue = ptaresult.pointsToSet(dataSlot, currentContext)
                    thisValue.map(unresolvedData.getOrElse(_, msetEmpty) ++= dataValue)
                  case "Landroid/content/Intent;.setDataAndType:(Landroid/net/Uri;Ljava/lang/String;)Landroid/content/Intent;" |
                       "Landroid/content/Intent;.setDataAndTypeAndNormalize:(Landroid/net/Uri;Ljava/lang/String;)Landroid/content/Intent;" =>  //public
                    val thisSlot = VarSlot(args(0), false, true)
                    val thisValue = ptaresult.pointsToSet(thisSlot, currentContext)
                    allIntent ++= thisValue
                    val dataSlot = VarSlot(args(1), false, true)
                    val dataValue = ptaresult.pointsToSet(dataSlot, currentContext)
                    val typeSlot = VarSlot(args(2), false, true)
                    val typeValue = ptaresult.pointsToSet(typeSlot, currentContext)
                    thisValue.map(unresolvedData.getOrElse(_, msetEmpty) ++= dataValue)
                    typeValue.map{
                      typeIns => 
                        typeIns match {
                          case psi: PTAConcreteStringInstance =>
                            val typeName = psi.string
                            thisValue.map(types.getOrElseUpdate(_, msetEmpty) += typeName)
                          case _ => impreciseImplicit ++= thisValue
                        }
                    }
                  case "Landroid/content/Intent;.setFlags:(I)Landroid/content/Intent;" =>  //public
                  case "Landroid/content/Intent;.setPackage:(Ljava/lang/String;)Landroid/content/Intent;" =>  //public
                  case "Landroid/content/Intent;.setType:(Ljava/lang/String;)Landroid/content/Intent;" |
                       "Landroid/content/Intent;.setTypeAndNormalize:(Ljava/lang/String;)Landroid/content/Intent;" =>  //public
                    val thisSlot = VarSlot(args(0), false, true)
                    val thisValue = ptaresult.pointsToSet(thisSlot, currentContext)
                    allIntent ++= thisValue
                    val typeSlot = VarSlot(args(1), false, true)
                    val typeValue = ptaresult.pointsToSet(typeSlot, currentContext)
                    typeValue.map{
                      typeIns => 
                        typeIns match {
                          case psi: PTAConcreteStringInstance =>
                            val typeName = psi.string
                            thisValue.map(types.getOrElseUpdate(_, msetEmpty) += typeName)
                          case _ => impreciseImplicit ++= thisValue
                        }
                    }
                  case _ => 
                }
            }
          case _ =>
        }
    }
    
    icfg.nodes.foreach {
      node =>
        node match {
          case cn: ICFGCallNode =>
            val callees = cn.getCalleeSet
            val args = cn.argNames
            val rets = cn.retNames ++ List("hack") // for safety
            val currentContext = cn.context
            callees foreach {
              callee =>
                callee.callee.getSignature.signature match {
                  case "Landroid/net/Uri;.parse:(Ljava/lang/String;)Landroid/net/Uri;" =>  //public static
                    val strSlot = VarSlot(args(0), false, true)
                    val strValue = ptaresult.pointsToSet(strSlot, currentContext)
                    val retSlot = VarSlot(rets(0), false, false)
                    val retValue = ptaresult.pointsToSet(retSlot, currentContext)
                    unresolvedData.foreach {
                      case (intent, ds) =>
                        if(!ds.intersect(retValue).isEmpty) {
                          strValue.foreach {
                            sv =>
                              sv match {
                                case cstr @ PTAConcreteStringInstance(text, c) =>
                                  val data = new UriData
                                  IntentHelper.populateByUri(data, text)
                                  datas.getOrElseUpdate(intent, msetEmpty) += data
                                case _ => impreciseImplicit += intent
                              }
                          }
                        }
                    }
                  case "Landroid/content/ComponentName;.<init>:(Landroid/content/Context;Ljava/lang/Class;)V" =>  //public constructor
                    val thisSlot = VarSlot(args(0), false, true)
                    val thisValue = ptaresult.pointsToSet(thisSlot, currentContext)
                    val componentSlot = VarSlot(args(2), false, true)
                    val componentValue = ptaresult.pointsToSet(componentSlot, currentContext)
                    unresolvedComp.foreach {
                      case (intent, comps) =>
                        if(!comps.intersect(thisValue).isEmpty) {
                          componentValue.foreach {
                            cv =>
                              cv match {
                                case ci: ClassInstance =>
                                  val component = ci.getName
                                  componentNames.getOrElseUpdate(intent, msetEmpty) += component
                                case _ => impreciseImplicit += intent
                              }
                          }
                        }
                    }
                  case "Landroid/content/ComponentName;.<init>:(Landroid/content/Context;Ljava/lang/String;)V" |
                       "Landroid/content/ComponentName;.<init>:(Ljava/lang/String;Ljava/lang/String;)V" => //public constructor
                    val thisSlot = VarSlot(args(0), false, true)
                    val thisValue = ptaresult.pointsToSet(thisSlot, currentContext)
                    val componentSlot = VarSlot(args(2), false, true)
                    val componentValue = ptaresult.pointsToSet(componentSlot, currentContext)
                    unresolvedComp.foreach {
                      case (intent, comps) =>
                        if(!comps.intersect(thisValue).isEmpty) {
                          componentValue.foreach {
                            cv =>
                              cv match {
                                case cstr @ PTAConcreteStringInstance(text, c) =>
                                  val component = text
                                  componentNames.getOrElseUpdate(intent, msetEmpty) += component
                                case _ => impreciseImplicit += intent
                              }
                          }
                        }
                    }
                  case "Landroid/content/ComponentName;.<init>:(Landroid/os/Parcel;)V" =>  //public constructor
                  case "Landroid/content/ComponentName;.<init>:(Ljava/lang/String;Landroid/os/Parcel;)V" =>  //private constructor
                  case _ =>
                }
            }
          case _ =>
        }
    }
    
    allIntent.foreach {
      intent =>
        val comps: ISet[String] = componentNames.getOrElse(intent, msetEmpty).toSet
        val acs: ISet[String] = actions.getOrElse(intent, msetEmpty).toSet
        val cas: ISet[String] = categories.getOrElse(intent, msetEmpty).toSet
        val das: ISet[UriData] = datas.getOrElse(intent, msetEmpty).toSet
        val tys: ISet[String] = types.getOrElse(intent, msetEmpty).toSet
        val preciseExplicit: Boolean = !impreciseExplicit.contains(intent)
        val preciseImplicit: Boolean = !impreciseImplicit.contains(intent)
        cpIntentmap.getOrElseUpdate(intent, msetEmpty) += IntentContent(comps, acs, cas, das, tys, preciseExplicit, preciseImplicit)
    }
  }
  
  private def buildCSTFromIDFG(component: JawaClass, idfg: InterProceduralDataFlowGraph) = {
    val csp = new ComponentSummaryProvider {
      def getIntentCaller(idfg: InterProceduralDataFlowGraph, intentValue: ISet[Instance], context: Context): ISet[IntentContent] = {
        val result = msetEmpty[IntentContent]
        val cpIntentmap = intentContents.getOrElse(component, mmapEmpty)
        intentValue.foreach {
          intent =>
            result ++= cpIntentmap.getOrElse(intent, msetEmpty)
        }
        result.toSet
      }
    }
    val summaryTable = ComponentSummaryTable.buildComponentSummaryTable(apk, component, idfg, csp)
    yard.addSummaryTable(component, summaryTable)
  }
}