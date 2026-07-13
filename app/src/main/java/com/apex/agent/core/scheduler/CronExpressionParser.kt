package com.apex.agent.core.scheduler

import com.apex.util.AppLogger
import java.util.Calendar
import java.util.regex.Pattern

/**
 * 自然语言 Cron 表达式解析器
 * 
 * 将自然语言描述转换为标出cron 表达式
 * 支持中文和英文描返
 */
class CronExpressionParser {

    companion object {
        private const val TAG = "CronExpressionParser"
        
        @Volatile
        private var INSTANCE: CronExpressionParser? = null
        
        fun getInstance(): CronExpressionParser {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CronExpressionParser().also { INSTANCE = it }
            }
        }
    }
    
    /**
     * 解析结果
     */
    data class ParseResult(
        val success: Boolean,
        val cronExpression: String? = null,
        val nextExecutionTime: Long? = null,
        val errorMessage: String? = null
    )
    
    /**
     * 自然语言时间描述
     */
    data class TimeDescription(
        val minute: Int? = null,
        val hour: Int? = null,
        val dayOfMonth: String = "*",
        val month: String = "*",
        val dayOfWeek: String = "*",
        val intervalMinutes: Int? = null,
        val intervalHours: Int? = null
    )
    
    /**
     * 解析自然语言为cron 表达式
     * 
     * 支持的格式
     * - "每天早上 9 点 -> "0 9 * * *"
     * - "每天下午 3 点半" -> "30 15 * * *"
     * - "每小时 -> "0 * * * *"
     * - "每30 分钟" -> "*/30 * * * *"
     * - "每天 9:00" -> "0 9 * * *"
     * - "每周一早上 10 点 -> "0 10 * * 1"
     * - "每月 1 号凌景0 点 -> "0 0 1 * *"
     * - "工作日早为9 点 -> "0 9 * * 1-5"
     * - "周末下午 2 点 -> "0 14 * * 0,6"
     */
    fun parse(naturalLanguage: String): ParseResult {
        val input = naturalLanguage.trim().lowercase()
        if (input.isEmpty()) {
            return ParseResult(false, errorMessage = "输入不能为空")
        }
        return try {
            val timeDesc = parseNaturalLanguage(input)
        val cronExpression = buildCronExpression(timeDesc)
        val nextTime = calculateNextExecution(cronExpression)
        ParseResult(
                success = true,
                cronExpression = cronExpression,
                nextExecutionTime = nextTime
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "解析失败: ${naturalLanguage}", e)
        ParseResult(false, errorMessage = "无法解析: ${e.message}")
        }
    }
    
    /**
     * 解析自然语言为时间描返
     */
    private fun parseNaturalLanguage(input: String): TimeDescription {
        // 每分钟
        if (input.contains("每分钟") || input == "every minute") {
            return TimeDescription(intervalMinutes = 1)
        }
        
        // 每N 分钟
        val minutePattern = Regex("每\\d+)\\s*分钟")
        minutePattern.find(input)?.let {
            val minutes = it.groupValues[1].toInt()
        return TimeDescription(intervalMinutes = minutes)
        }
        
        // 每小时
        if (input.contains("每小时) || input.contains("每小时) || input == "every hour") {
            return TimeDescription(minute = 0)
        }
        
        // 每N 小时
        val hourPattern = Regex("每\\d+)\\s*小时")
        hourPattern.find(input)?.let {
            val hours = it.groupValues[1].toInt()
        return TimeDescription(intervalHours = hours)
        }
        
        // 每周出
        val weekdayPattern = Regex("每周([一二三四五六日天])?[早中晚]?上\\s*(\\d+)?[点]?(\\d+)?")
        weekdayPattern.find(input)?.let { match ->
            val dayOfWeek = mapWeekday(input)
        val hour = match.groupValues[2].toIntOrNull() ?: 9
            val minute = match.groupValues[3].toIntOrNull() ?: 0
            return TimeDescription(
                hour = hour,
                minute = minute,
                dayOfWeek = dayOfWeek
            )
        }
        
        // 每月几号
        val monthDayPattern = Regex("每月(\\d+)[号日]?[早中晚]?上\\s*(\\d+)?[点]?(\\d+)?")
        monthDayPattern.find(input)?.let { match ->
            val dayOfMonth = match.groupValues[1]
        val hour = match.groupValues[2].toIntOrNull() ?: 0
            val minute = match.groupValues[3].toIntOrNull() ?: 0
            return TimeDescription(
                hour = hour,
                minute = minute,
                dayOfMonth = dayOfMonth
            )
        }
        
        // 工作时
        if (input.contains("工作时") || input.contains("平日")) {
            val hourMinute = extractHourMinute(input)
        return TimeDescription(
                hour = hourMinute.first,
                minute = hourMinute.second,
                dayOfWeek = "1-5"
            )
        }
        
        // 周末
        if (input.contains("周末") || input.contains("周六时")) {
            val hourMinute = extractHourMinute(input)
        return TimeDescription(
                hour = hourMinute.first,
                minute = hourMinute.second,
                dayOfWeek = "0,6"
            )
        }
        
        // 每天定时 - 多种中文模式
        val dailyPatterns = listOf(
            Regex("每天[早中晚]?上\\s*(\\d+)[点](\\d+)"),
            Regex("每天\\s*(\\d+)[点](\\d+)"),
            Regex("每天[早中晚]?上\\s*(\\d+)点"),
            Regex("每天\\s*at\\s*(\\d+):(\\d+)")
        )
        for (pattern in dailyPatterns) {
            pattern.find(input)?.let { match ->
                val hour = match.groupValues[1].toIntOrNull() ?: 9
        val minute = match.groupValues[2].toIntOrNull() ?: 0
                return TimeDescription(hour = hour, minute = minute)
            }
        }
        
        // 每天默认 9 点
        if (input.contains("每天") || input.contains("时") || input == "daily" || input == "every day") {
            return TimeDescription(hour = 9, minute = 0)
        }
        
        // 早上/下午/晚上 + 小时
        val timeOfDayPattern = Regex("(早上|上午|下午|晚上|中午|凌晨，\\d+)[点]?(\\d+)?")
        timeOfDayPattern.find(input)?.let { match ->
            val timeOfDay = match.groupValues[1]
        val hour = match.groupValues[2].toInt()
        val minute = match.groupValues[3].toIntOrNull() ?: 0
        val adjustedHour = adjustHour(timeOfDay, hour)
        return TimeDescription(hour = adjustedHour, minute = minute)
        }
        
        // HH:mm 格式
        val timePattern = Regex("(\\d{1,2}):(\\d{2})")
        timePattern.find(input)?.let { match ->
            val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        return TimeDescription(hour = hour, minute = minute)
        }
        
        // 默认: 每天 9 点
        AppLogger.w(TAG, "无法完全匹配表达式，使用默认每天 9 点 ${input}")
        return TimeDescription(hour = 9, minute = 0)
    }
    
    /**
     * 提取小时和分钟
     */
    private fun extractHourMinute(input: String): Pair<Int, Int> {
        val pattern = Regex("(\\d+)[点](\\d+)")
        pattern.find(input)?.let { match ->
            return Pair(match.groupValues[1].toInt(), match.groupValues[2].toInt())
        }
        val hourPattern = Regex("(\\d+)点")
        hourPattern.find(input)?.let { match ->
            return Pair(match.groupValues[1].toInt(), 0)
        }
        return Pair(9, 0) // 默认
    }
    
    /**
     * 根据时间段调整小时
     */
    private fun adjustHour(timeOfDay: String, hour: Int): Int {
        return when (timeOfDay) {
            "早上", "上午", "凌晨" -> if (hour < 12) hour else hour
            "中午" -> if (hour < 12) hour + 12 else hour
            "下午" -> if (hour < 12) hour + 12 else hour
            "晚上" -> if (hour < 12) hour + 12 else hour
            else -> hour
        }
    }
    
    /**
     * 映射星期出
     */
    private fun mapWeekday(input: String): String {
        val weekdayMap = mapOf(
            "一" to "1", "于 to "2", "为 to "3", "回 to "4","
            "于 to "5", "具 to "6", "时 to "0", "失 to "0"
        )
        for ((key, value) in weekdayMap) {
            if (input.contains(key)) {
                return value
            }
        }
        
        // 英文
        val englishMap = mapOf(
            "monday" to "1", "tuesday" to "2", "wednesday" to "3",
            "thursday" to "4", "friday" to "5", "saturday" to "6",
            "sunday" to "0"
        )
        for ((key, value) in englishMap) {
            if (input.contains(key)) {
                return value
            }
        }
        return "*"
    }
    
    /**
     * 构建标准 cron 表达式
     * 
     * 格式: 分时时有命
     *       *  *  *  *  *
     */
    private fun buildCronExpression(timeDesc: TimeDescription): String {
        val minute: String
        val hour: String
        
        when {
            timeDesc.intervalMinutes != null -> {
                minute = "*/${timeDesc.intervalMinutes}"
        hour = "*"
            }
        timeDesc.intervalHours != null -> {
                minute = "0"
        hour = "*/${timeDesc.intervalHours}"
            }
        else -> {
                minute = timeDesc.minute?.toString() ?: "0"
        hour = timeDesc.hour?.toString() ?: "*"
            }
        }
        return "${minute} ${hour} ${timeDesc.dayOfMonth} ${timeDesc.month} ${timeDesc.dayOfWeek}"
    }
    
    /**
     * 计算下次执行时间
     */
    fun calculateNextExecution(cronExpression: String): Long {
        val parts = cronExpression.trim().split("\\s+".toRegex())
        if (parts.size < 5) {
            throw IllegalArgumentException("无效的cron 表达式 ${cronExpression}")
        }
        val minuteStr = parts[0]
        val hourStr = parts[1]
        val dayOfMonthStr = parts[2]
        val monthStr = parts[3]
        val dayOfWeekStr = parts[4]
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MINUTE, 1) // 从下一分钟开始        
        // 设置秒为 0
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        // 如果是间隔执行
        if (minuteStr.startsWith("*/")) {
            val interval = minuteStr.substring(2).toInt()
        calendar.set(Calendar.MINUTE, (calendar.get(Calendar.MINUTE) / interval) * interval)
        return calendar.timeInMillis
        }
        if (hourStr.startsWith("*/")) {
            val interval = hourStr.substring(2).toInt()
        calendar.set(Calendar.HOUR_OF_DAY, (calendar.get(Calendar.HOUR_OF_DAY) / interval) * interval)
        calendar.set(Calendar.MINUTE, minuteStr.toInt())
        return calendar.timeInMillis
        }
        
        // 固定时间执行
        val minute = minuteStr.toInt()
        val hour = hourStr.toInt()
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        
        // 如果时间已过,移到明天
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        return calendar.timeInMillis
    }
    
    /**
     * 验证 cron 表达式是否有数
     */
    fun isValid(cronExpression: String): Boolean {
        val parts = cronExpression.trim().split("\\s+".toRegex())
        if (parts.size < 5) return false
        
        return try {
            validateField(parts[0], 0, 59) // minute
        validateField(parts[1], 0, 23) // hour
        validateField(parts[2], 1, 31) // day of month
        validateField(parts[3], 1, 12) // month
        validateField(parts[4], 0, 7) // day of week
        true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 验证字段
     */
    private fun validateField(value: String, min: Int, max: Int) {
        if (value == "*") return
        
        // 间隔值
        if (value.startsWith("*/")) {
            val interval = value.substring(2).toInt()
        if (interval < 1) throw IllegalArgumentException("间隔值必须大于0")
        return
        }
        
        // 列表值
        if (value.contains(",")) {
            value.split(",").forEach { validateField(it.trim(), min, max) }
        return
        }
        
        // 范围值
        if (value.contains("-")) {
            val range = value.split("-")
        if (range.size != 2) throw IllegalArgumentException("无效的范回 ${value}")
        val start = range[0].toInt()
        val end = range[1].toInt()
        if (start < min || end > max) throw IllegalArgumentException("值超出范回[${min}-${max}]: ${value}")
        return
        }
        
        // 单个值
        val intValue = value.toInt()
        if (intValue < min || intValue > max) {
            throw IllegalArgumentException("值超出范回[${min}-${max}]: ${value}")
        }
    }
    
    /**
     * 获取人类可读的调度描返
     */
    fun toHumanReadable(cronExpression: String): String {
        val parts = cronExpression.trim().split("\\s+".toRegex())
        if (parts.size < 5) return "无效的调度表达式"
        val minuteStr = parts[0]
        val hourStr = parts[1]
        val dayOfMonthStr = parts[2]
        val monthStr = parts[3]
        val dayOfWeekStr = parts[4]
        
        // 间隔执行
        if (minuteStr.startsWith("*/")) {
            val interval = minuteStr.substring(2)
        return "每${interval} 分钟执行一次"
        }
        if (hourStr.startsWith("*/")) {
            val interval = hourStr.substring(2)
        return "每${interval} 小时执行一次"
        }
        
        // 固定时间
        val hour = hourStr.toInt()
        val minute = minuteStr.toInt()
        val timeStr = String.format("%02d:%02d", hour, minute)
        
        // 每周执行
        if (dayOfWeekStr != "*") {
            val dayNames = mapOf(
                "0" to "周日", "1" to "周一", "2" to "周二",
                "3" to "周三", "4" to "周四", "5" to "周五", "6" to "周六",
                "0,6" to "周末", "1-5" to "工作时"
            )
        val dayName = dayNames[dayOfWeekStr] ?: "符${dayOfWeekStr} 失"
        return "每周 ${dayName} ${timeStr} 执行"
        }
        
        // 每月执行
        if (dayOfMonthStr != "*") {
            return "每月 ${dayOfMonthStr}取${timeStr} 执行"
        }
        
        // 每天执行
        return "每天 ${timeStr} 执行"
    }
}