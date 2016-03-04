package org.sireum.amandroid.run.security

import org.sireum.amandroid.security._
import org.sireum.amandroid.alir.pta.reachingFactsAnalysis.AndroidReachingFactsAnalysisConfig
import org.sireum.util.FileUtil
import org.sireum.amandroid.appInfo.AppInfoCollector
import org.sireum.amandroid.util.AndroidLibraryAPISummary
import org.sireum.amandroid.AndroidGlobalConfig
import org.sireum.util.FileResourceUri
import org.sireum.jawa.util.IgnoreException
import java.io.File
import java.io.FileOutputStream
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import org.sireum.amandroid.alir.taintAnalysis.DataLeakageAndroidSourceAndSinkManager
import org.sireum.jawa.util.MyTimeoutException
import org.sireum.jawa.util.MyTimer
import org.sireum.jawa.DefaultReporter
import org.sireum.jawa.Global
import org.sireum.amandroid.Apk
import org.sireum.jawa.alir.dataDependenceAnalysis.InterproceduralDataDependenceAnalysis

object CommunicationLeakage_run {
  private final val TITLE = "CommunicationLeakage_run"
//  MessageCenter.msglevel = MessageCenter.MSG_LEVEL.CRITICAL
  object CommunicationLeakageCounter {
    var total = 0
    var haveresult = 0
    var taintPathFound = 0
    var taintPathFoundList = Set[String]()
    var leakagetype = Set[String]()
    override def toString: String = "total: " + total + ", haveResult: " + haveresult + ", taintPathFound: " + taintPathFound 
  }// contact list, phone record, text message three types of leakage
  
  private class CommunicationLeakageListener(global: Global, apk: Apk, outputPath: String) extends AmandroidSocketListener {
    def onPreAnalysis: Unit = {
      CommunicationLeakageCounter.total += 1
    }

    def entryPointFilter(eps: Set[org.sireum.jawa.JawaMethod]): Set[org.sireum.jawa.JawaMethod] = {
      eps//.filter { ep => ep.getSignature.contains("envMain") }
    }

    def onTimeout: Unit = {}

    def onAnalysisSuccess: Unit = {
      if(apk.getTaintAnalysisResult[InterproceduralDataDependenceAnalysis.Node, InterproceduralDataDependenceAnalysis.Edge].exists(!_.getTaintedPaths.isEmpty)){
        CommunicationLeakageCounter.taintPathFound += 1
        CommunicationLeakageCounter.taintPathFoundList += apk.nameUri
      }
      CommunicationLeakageCounter.haveresult += 1
      val msgfile = new File(outputPath + "/msg.txt")
      val msgw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(msgfile, true)))
      msgw.write("################# " + apk.nameUri + " ################\n")
      val tRes = apk.getTaintAnalysisResult
      tRes.foreach{
        res =>
          msgw.write("Found " + res.getTaintedPaths.size + " path.")
          msgw.write(res.toString)
          msgw.write("\n\n")
      }
    }

    def onPostAnalysis: Unit = {
      global.reporter.echo(TITLE, CommunicationLeakageCounter.toString)
    }
    
    def onException(e: Exception): Unit = {
      e match{
        case ie: IgnoreException => System.err.println("Ignored!")
        case a => 
          e.printStackTrace()
      }
    }
  }
  
  def main(args: Array[String]): Unit = {
    if(args.size < 2){
      System.err.print("Usage: source_path output_path [dependence_uri]")
      return
    }
    
//    GlobalConfig.ICFG_CONTEXT_K = 1
    AndroidReachingFactsAnalysisConfig.resolve_icc = true
    AndroidReachingFactsAnalysisConfig.parallel = true
    AndroidReachingFactsAnalysisConfig.resolve_static_init = false

//    MessageCenter.msglevel = MessageCenter.MSG_LEVEL.CRITICAL
    
    val sourcePath = args(0)
    val outputPath = args(1)
    val dpsuri = try{Some(FileUtil.toUri(args(2)))} catch {case e: Exception => None}
    
    val files = FileUtil.listFiles(FileUtil.toUri(sourcePath), ".apk", true).toSet
    
    files.foreach{
      file =>
        val reporter = new DefaultReporter
        val global = new Global(file, reporter)
        global.setJavaLib(AndroidGlobalConfig.lib_files)
        val apk = new Apk(file)
        val socket = new AmandroidSocket(global, apk)
        try{
          reporter.echo(TITLE, DataLeakageTask(global, apk, outputPath, dpsuri, file, socket, Some(100)).run)   
        } catch {
          case te: MyTimeoutException => reporter.error(TITLE, te.message)
          case e: Throwable => e.printStackTrace()
        } finally{
          reporter.echo(TITLE, CommunicationLeakageCounter.toString)
          socket.cleanEnv
        }
    }
  }
  
  private case class DataLeakageTask(global: Global, apk: Apk, outputPath: String, dpsuri: Option[FileResourceUri], file: FileResourceUri, socket: AmandroidSocket, timeout: Option[Int]) {
    def run: String = {
      global.reporter.echo(TITLE, "####" + file + "#####")
      val timer = timeout match {
        case Some(t) => Some(new MyTimer(t))
        case None => None
      }
      if(timer.isDefined) timer.get.start
      val outUri = socket.loadApk(outputPath, AndroidLibraryAPISummary, dpsuri, false, false)
      val app_info = new AppInfoCollector(global, timer)
      app_info.collectInfo(apk, outUri)
      val ssm = new DataLeakageAndroidSourceAndSinkManager(global, apk, app_info.getLayoutControls, app_info.getCallbackMethods, AndroidGlobalConfig.sas_file)
      socket.plugListener(new CommunicationLeakageListener(global, apk, outputPath))
      socket.runWithDDA(ssm, false, false, timer)
      return "Done!"
    }
  }
}