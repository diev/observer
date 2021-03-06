package ru.barabo.observer.config.barabo.p440.out

import com.thoughtworks.xstream.XStream
import com.thoughtworks.xstream.io.xml.DomDriver
import org.slf4j.LoggerFactory
import org.xml.sax.SAXException
import ru.barabo.cmd.XmlValidator
import ru.barabo.observer.afina.AfinaQuery
import ru.barabo.observer.config.ConfigTask
import ru.barabo.observer.config.barabo.p440.P440Config
import ru.barabo.observer.config.barabo.p440.out.data.AbstractResponseData
import ru.barabo.observer.config.cbr.ticket.task.Get440pFiles
import ru.barabo.observer.config.task.AccessibleData
import ru.barabo.observer.config.task.ActionTask
import ru.barabo.observer.config.task.WeekAccess
import ru.barabo.observer.config.task.p440.load.xml.impl.*
import ru.barabo.observer.config.task.p440.out.xml.AbstractInfoPart
import ru.barabo.observer.config.task.p440.out.xml.AbstractToFns
import ru.barabo.observer.config.task.p440.out.xml.bnp.BnpInfoPart
import ru.barabo.observer.config.task.p440.out.xml.bnp.BnpXml
import ru.barabo.observer.config.task.p440.out.xml.exists.ExistInfoPart
import ru.barabo.observer.config.task.p440.out.xml.exists.ExistXml
import ru.barabo.observer.config.task.p440.out.xml.exists.ExistsAccount
import ru.barabo.observer.config.task.p440.out.xml.extract.*
import ru.barabo.observer.config.task.p440.out.xml.pb.PbInfoPart
import ru.barabo.observer.config.task.p440.out.xml.pb.PbResult
import ru.barabo.observer.config.task.p440.out.xml.pb.PbXml
import ru.barabo.observer.config.task.p440.out.xml.rest.RestAccount
import ru.barabo.observer.config.task.p440.out.xml.rest.RestInfoPart
import ru.barabo.observer.config.task.p440.out.xml.rest.RestXml
import ru.barabo.observer.config.task.template.db.DbSelector
import ru.barabo.observer.store.Elem
import ru.barabo.observer.store.State
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.Charset
import java.time.Duration
import java.time.LocalTime
import kotlin.reflect.KClass


fun String.byFolderExists(): File {
    val folder = File(this)

    if(!folder.exists()) {
        folder.mkdirs()
    }

    return folder
}

abstract class GeneralCreator<X :AbstractToFns>(protected val responseData :AbstractResponseData, private val clazzXml : KClass<X>) : DbSelector, ActionTask {

    override fun config(): ConfigTask = P440Config

    companion object {

        private val STATE_SAVED = 2

        private val UPDATE_STATE_RESPONSE = "update od.PTKB_440P_RESPONSE set state = $STATE_SAVED, updated = sysdate, sent = sysdate where id = ?"

        private val logger = LoggerFactory.getLogger(GeneralCreator::class.java)!!

        fun sendFolder440p(): File = "X:/440-П/${Get440pFiles.todayFolder()}/Отправка".byFolderExists()

        private fun failSendFolder440p() :File = "${sendFolder440p().absolutePath}/Fail".byFolderExists()

        inline fun <reified T : AbstractToFns> create(responseData :AbstractResponseData, name :String) : GeneralCreator<T> {

            return object:GeneralCreator<T>(responseData, T::class) {
                override fun name() = name
            }
        }

        private val XML_HEADER = "<?xml version=\"1.0\" encoding=\"windows-1251\" ?>\n"

        fun saveXml(fileName :String, xmlData :Any) {

            val outputStream = FileOutputStream(File("${sendFolder440p().absolutePath}/$fileName"))

            val writer = OutputStreamWriter(outputStream, Charset.forName("windows-1251"))

            writer.write(XML_HEADER)

            getXStream().toXML(xmlData, writer)

            outputStream.close()
        }

        @Throws(SAXException::class)
        fun validateXml(fileName :String, xsdSchema :String) {

            val file =  File("${sendFolder440p().absolutePath}/$fileName")

            try {
                XmlValidator.validate(file, xsdSchema)

            } catch (e :Exception) {

                logger.error("validateXml $fileName", e)

                if(file.exists()) {
                    val failFile = File("${failSendFolder440p().absolutePath}/$fileName")
                    file.copyTo(failFile, true)
                    file.delete()
                }
                throw SAXException(e.message)
            }
        }

        private fun getXStream(): XStream {
            val xstream = XStream(DomDriver("windows-1251"))

            xstream.useAttributeFor(String::class.java)
            xstream.useAttributeFor(Int::class.java)
            xstream.useAttributeFor(Boolean::class.java)

            xstream.processAnnotations(AbstractToFns::class.java)
            xstream.processAnnotations(PbInfoPart::class.java)
            xstream.processAnnotations(PbResult::class.java)
            xstream.processAnnotations(PbXml::class.java)

            xstream.processAnnotations(AbstractInfoPart::class.java)
            xstream.processAnnotations(RestInfoPart::class.java)
            xstream.processAnnotations(RestXml::class.java)

            xstream.processAnnotations(RestAccount::class.java)
            xstream.processAnnotations(FnsXml::class.java)
            xstream.processAnnotations(BankXml::class.java)
            xstream.processAnnotations(PayerJur::class.java)
            xstream.processAnnotations(PayerIp::class.java)
            xstream.processAnnotations(PayerPhysic::class.java)

            xstream.processAnnotations(ExistsAccount::class.java)
            xstream.processAnnotations(ExistInfoPart::class.java)
            xstream.processAnnotations(ExistXml::class.java)

            xstream.processAnnotations(OperationAccount::class.java)
            xstream.processAnnotations(ExtractMainAccount::class.java)
            xstream.processAnnotations(AddExtractInfoPart::class.java)
            xstream.processAnnotations(AdditionalExtractXml::class.java)
            xstream.processAnnotations(AmountInfo::class.java)
            xstream.processAnnotations(BankInfo::class.java)
            xstream.processAnnotations(DocumentInfo::class.java)
            xstream.processAnnotations(ExtractMainInfoPart::class.java)
            xstream.processAnnotations(ExtractMainXml::class.java)
            xstream.processAnnotations(PayerInfo::class.java)

            xstream.processAnnotations(BnpInfoPart::class.java)
            xstream.processAnnotations(BnpXml::class.java)

            return xstream
        }
    }

    override fun actionTask(selectorValue: Any?): ActionTask {

        if(selectorValue !is Number) throw Exception("ptkb_440p_response.IS_PB is not valid value = $selectorValue")

        val creator = OutType.creatorByDbValue( selectorValue.toInt() )

        return creator ?: throw Exception("ptkb_440p_response.IS_PB is not valid value = $selectorValue")
    }

    override fun actionTask(): ActionTask = this

    override val select: String = "select id, FILE_NAME, IS_PB from od.ptkb_440p_response where state = 0"

    override val accessibleData: AccessibleData = AccessibleData(WeekAccess.WORK_ONLY,
            false, LocalTime.of(10, 0), LocalTime.of(15, 45), Duration.ofSeconds(1))

    private fun createXml(classXml :KClass<X>, responseData :AbstractResponseData) :X {
        val construct =  classXml.constructors.iterator().next()

        return construct.call(responseData)
    }

    override fun execute(elem: Elem): State {

        val sessionSetting = AfinaQuery.uniqueSession()

        responseData.init(elem.idElem!!, sessionSetting)

        val xmlData = createXml(clazzXml, responseData)

        saveXml(responseData.fileNameResponse(), xmlData)

        validateXml(responseData.fileNameResponse(), responseData.xsdSchema())

        AfinaQuery.execute(UPDATE_STATE_RESPONSE, arrayOf(elem.idElem), sessionSetting)

        AfinaQuery.commitFree(sessionSetting)

        return State.OK
    }
}
