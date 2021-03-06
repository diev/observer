package ru.barabo.observer.afina

import org.slf4j.LoggerFactory
import ru.barabo.db.Query
import ru.barabo.db.SessionException
import ru.barabo.db.SessionSetting
import ru.barabo.observer.store.derby.StoreDerby
import java.sql.Date
import java.time.LocalDate
import java.time.ZoneId

object AfinaQuery : Query(AfinaConnect) {

    private val logger = LoggerFactory.getLogger(AfinaQuery::class.java)

    private val isWorkDay :HashMap<LocalDate, Boolean>  = HashMap()

    private val SELECT_IS_HOLIDAY = "select od.isHoliday(?, 1000131227) from dual"

    @Synchronized
    fun isWorkDayNow() :Boolean {
        val dateNow = LocalDate.now()

        val isWork = isWorkDay[dateNow]

        if(isWork != null) {
            return isWork
        }

        val dateSql = Date(dateNow.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())

        val isHoliday = try {
            selectValue(SELECT_IS_HOLIDAY, Array(1, { dateSql }))
        } catch (e :SessionException) {
            logger.error("isWorkDayNow", e)

            null
        }

        if(isHoliday != null) {
            isWorkDay[dateNow] = (isHoliday is Number) && (isHoliday.toInt() == 0)
        }

        StoreDerby.checkDate(dateNow)

        return (isHoliday != null) && (isHoliday is Number) && (isHoliday.toInt() == 0)
    }

    private val NEXT_SEQUENCE = "select classified.nextval from dual"

    @Synchronized
    fun nextSequence(sessionSetting : SessionSetting = SessionSetting(true)) :Number =
            selectValue(query = NEXT_SEQUENCE, sessionSetting = sessionSetting) as Number
}