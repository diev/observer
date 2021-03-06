package ru.barabo.observer.config.cbr.other.task

import ru.barabo.db.SessionSetting
import ru.barabo.observer.afina.AfinaQuery
import ru.barabo.observer.config.ConfigTask
import ru.barabo.observer.config.cbr.other.OtherCbr
import ru.barabo.observer.config.cbr.other.task.cec.FileXml
import ru.barabo.observer.config.cbr.other.task.cec.Person
import ru.barabo.observer.config.cbr.other.task.cec.XmlCecLoader
import ru.barabo.observer.config.cbr.other.task.cec.Zapros
import ru.barabo.observer.config.cbr.other.task.nbki.clob2string
import ru.barabo.observer.config.task.AccessibleData
import ru.barabo.observer.config.task.WeekAccess
import ru.barabo.observer.config.task.finder.FileFinder
import ru.barabo.observer.config.task.finder.FileFinderData
import ru.barabo.observer.config.task.template.file.FileProcessor
import ru.barabo.observer.mail.smtp.BaraboSmtp
import java.io.File
import java.sql.Clob
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object CecReportProcess : FileFinder, FileProcessor {

    override fun name(): String = "Запрос из ЦИК"

    override fun config(): ConfigTask = OtherCbr

    override val accessibleData: AccessibleData = AccessibleData(workWeek = WeekAccess.ALL_DAYS)

    override val fileFinderData: List<FileFinderData> = listOf(FileFinderData( ::xCecToday, ".*\\.xml"))

    private fun xCecToday() = File("X:/ЦИК/${todayFolder()}/Запрос")

    private fun todayFolder() :String = DateTimeFormatter.ofPattern("yyyy.MM.dd").format(LocalDate.now())

    private fun xCecResponseToday() = "X:/ЦИК/${todayFolder()}/ОТВЕТЫ"


    override fun processFile(file: File) {

        val idRequest = loadXmlData(file)

        idRequest?.let { sendMailData(sendResponseData(it, file), file)  } ?: sendMailData(null, file)
    }

    private fun loadXmlData(file: File) :Number? {

        val fileXml = XmlCecLoader<FileXml>().load(file)

        if(fileXml.persons == null || fileXml.zapros == null) return null

        val sessionSetting = AfinaQuery.uniqueSession()

        val idRequest = fileXml.zapros.saveRequest(file, sessionSetting)

        fileXml.persons?.forEach { it.save(idRequest, sessionSetting) }

        AfinaQuery.commitFree(sessionSetting)

        return idRequest
    }

    private val SUBJECT_CEC = "ЦИК ОТЧЕТ (CEC REPORT)"

    private fun bodyFileCec(file: File) = "файл для отправки данных находится по адресу ${file.absolutePath}"

    private fun bodyEmptyCec(fileRequest: File) = "нет совпадений после обработки файла ${fileRequest.absolutePath}"

    private fun sendMailData(file: File?, fileRequest: File) {

        val body = file?.let { bodyFileCec(it) } ?: bodyEmptyCec(fileRequest)

        val attachment = file?.let { arrayOf(it) } ?: emptyArray()

        BaraboSmtp.sendStubThrows(to = BaraboSmtp.MANAGERS_UOD, bcc = BaraboSmtp.AUTO, subject = SUBJECT_CEC,
                body = body, attachments = attachment)
    }

    private val SELECT_FILENAME = "select od.PTKB_CEC.getFileName(?) from dual"

    private val SELECT_DEPUTY_DATA = "select od.PTKB_CEC.getDeputyData(?, ?) from dual"

    private fun sendResponseData(idRequest :Number, file: File) :File? {

        val fileResponseName = AfinaQuery.selectValue(SELECT_FILENAME, arrayOf(file.name)) as String

        val data = AfinaQuery.selectValue(SELECT_DEPUTY_DATA, arrayOf(idRequest, file.name)) as Clob? ?: return null

        val fileResponse = File("${xCecResponseToday()}/$fileResponseName")

        fileResponse.writeText(data.clob2string())

        return fileResponse
    }

    private val INSERT_REQUEST = "insert into OD.PTKB_IZBIRKOM_REQUEST (id, FILE_NAME, ID_REQ, DATE_REQ) " +
            "values (?, ?, ?, to_date(?, 'dd.mm.yyyy') )"

    private fun Zapros.saveRequest(file: File, sessionSetting : SessionSetting) :Number {
        val sequence = AfinaQuery.nextSequence(sessionSetting)

        val params :Array<Any?> = arrayOf(sequence, file.name, this.id, this.date)

        AfinaQuery.execute(INSERT_REQUEST, params, sessionSetting)

        return sequence
    }

    private val INSERT_PERSON = "insert into OD.PTKB_IZBIRKOM ( ID_REQUEST, id, PERS_FAMILY, BIRTHDAY, DOC_CODE,  DOC_SERIA, " +
            " DOC_NUMBER, NEKONF_ADRESS,  BIRTHPLACE,  SERV_VRNKAND, SERV_CODE_SUBJ, SERV_COMPANY, SERV_SYST, SERV_NAME, " +
            "SERV_SUBJ,  SERV_DATE, PERS_ID, PERS_NAME, PERS_SURNAME, KONF_ADRESS, PERS_CODE_ADR) " +
            "values (?, classified.nextval, ?, to_date(?, 'dd.mm.yyyy'), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, " +
            "to_date(?, 'dd.mm.yyyy'), ?, ?, ?, ?, ?)"

    private fun Person.save(idRequest :Number, sessionSetting :SessionSetting) {

        val params :Array<Any?> = arrayOf(
                idRequest,
                persInfo.fio.fam,
                persInfo.fio.getbirth().trim(),
                persInfo.doc.kodVidDoc,
                persInfo.doc.seria,
                persInfo.doc.number,
                persInfo.adress.neConfAdress,
                persInfo.adress.birthPlace,
                slugaInfo.vr,
                slugaInfo.idInfo.code,
                slugaInfo.idInfo.company,
                slugaInfo.idInfo.systema,
                slugaInfo.nameInfoSluga.vibory,
                slugaInfo.nameInfoSluga.subject,
                slugaInfo.nameInfoSluga.date,
                id,
                persInfo.fio.name,
                persInfo.fio.second,
                persInfo.adress.confAdress,
                persInfo.adress.codeSubj
        )

        AfinaQuery.execute(INSERT_PERSON, params, sessionSetting)
    }
}