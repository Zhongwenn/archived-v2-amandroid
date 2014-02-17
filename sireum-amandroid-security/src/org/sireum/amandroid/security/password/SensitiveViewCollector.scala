package org.sireum.amandroid.security.password

import org.sireum.jawa.JawaRecord
import org.sireum.amandroid.android.appInfo.AppInfoCollector
import org.sireum.util._
import org.sireum.jawa.util.IgnoreException
import org.sireum.jawa.Center
import org.sireum.jawa.MessageCenter._
import org.sireum.amandroid.alir.AndroidConstants
import org.sireum.amandroid.alir.AppCenter

class SensitiveViewCollector(apkUri : FileResourceUri) extends AppInfoCollector(apkUri) {
	private var sensitiveLayoutContainers : Set[JawaRecord] = Set()
	def getSensitiveLayoutContainers = this.sensitiveLayoutContainers
	
	override def collectInfo : Unit = {
	  val mfp = AppInfoCollector.analyzeManifest(apkUri)
	  this.appPackageName = mfp.getPackageName
		this.componentInfos = mfp.getComponentInfos
		this.uses_permissions = mfp.getPermissions
		this.intentFdb = mfp.getIntentDB
		
	  val afp = AppInfoCollector.analyzeARSC(apkUri)
		val lfp = AppInfoCollector.analyzeLayouts(apkUri, mfp)
		this.layoutControls = lfp.getUserControls
		if(!this.layoutControls.exists(p => p._2.isSensitive)) throw new IgnoreException
		
		val ra = AppInfoCollector.reachabilityAnalysis(mfp)
		this.sensitiveLayoutContainers = ra.getSensitiveLayoutContainer(layoutControls)
		val callbacks = AppInfoCollector.analyzeCallback(afp, lfp, ra)
		this.callbackMethods = callbacks
		var components = isetEmpty[JawaRecord]
    mfp.getComponentInfos.foreach{
      f => 
        val record = Center.resolveRecord(f.name, Center.ResolveLevel.HIERARCHY)
        if(!record.isPhantom && record.isApplicationRecord){
	        components += record
	        val clCounter = generateEnvironment(record, if(f.exported)AndroidConstants.MAINCOMP_ENV else AndroidConstants.COMP_ENV, codeLineCounter)
	        codeLineCounter = clCounter
        }
    }
		
		AppCenter.setComponents(components)
		AppCenter.updateIntentFilterDB(this.intentFdb)
		AppCenter.setAppInfo(this)
		msg_normal("Entry point calculation done.")
	}
}