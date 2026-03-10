package net.marllex.waselak.backend.domain.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.*
import net.marllex.waselak.backend.data.database.RequestLogsTable
import net.marllex.waselak.backend.data.database.UsersTable
import net.marllex.waselak.backend.data.database.VendorsTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class RequestLogService {

    private val logger = LoggerFactory.getLogger("RequestLogService")
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    data class LogEntry(
        val vendorId: String?,
        val userId: String?,
        val userRole: String?,
        val method: String,
        val path: String,
        val queryParams: String?,
        val statusCode: Int,
        val durationMs: Long,
        val clientIp: String?,
        val userAgent: String?,
        val requestBody: String?,
        val responseBody: String?,
        val errorMessage: String?,
        val resource: String? = null,
        val action: String? = null,
        val tags: String? = null,
        val description: String? = null,
        val traceLog: String? = null,
    )

    fun insertAsync(entry: LogEntry) {
        scope.launch {
            try {
                transaction {
                    RequestLogsTable.insert {
                        it[vendorId] = entry.vendorId
                        it[userId] = entry.userId
                        it[userRole] = entry.userRole
                        it[method] = entry.method
                        it[path] = entry.path
                        it[queryParams] = entry.queryParams
                        it[statusCode] = entry.statusCode
                        it[durationMs] = entry.durationMs
                        it[clientIp] = entry.clientIp
                        it[userAgent] = entry.userAgent
                        it[requestBody] = entry.requestBody
                        it[responseBody] = entry.responseBody
                        it[errorMessage] = entry.errorMessage
                        it[resource] = entry.resource
                        it[action] = entry.action
                        it[tags] = entry.tags
                        it[description] = entry.description
                        it[traceLog] = entry.traceLog
                        it[createdAt] = Clock.System.now()
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to insert request log: ${e.message}")
            }
        }
    }

    data class LogQueryParams(
        val vendorId: String? = null,
        val userId: String? = null,
        val method: String? = null,
        val pathSearch: String? = null,
        val statusGroup: String? = null, // "2xx", "4xx", "5xx"
        val resource: String? = null,
        val action: String? = null,
        val startDate: Instant? = null,
        val endDate: Instant? = null,
        val page: Int = 1,
        val pageSize: Int = 50
    )

    data class PaginatedLogs(
        val logs: List<JsonObject>,
        val total: Long,
        val page: Int,
        val pageSize: Int,
        val totalPages: Int
    )

    fun queryLogs(params: LogQueryParams): PaginatedLogs {
        return transaction {
            val query = RequestLogsTable.selectAll()

            params.vendorId?.let {
                query.andWhere { RequestLogsTable.vendorId eq it }
            }
            params.userId?.let {
                query.andWhere { RequestLogsTable.userId eq it }
            }
            params.method?.let {
                query.andWhere { RequestLogsTable.method eq it }
            }
            params.pathSearch?.let { search ->
                query.andWhere { RequestLogsTable.path like "%$search%" }
            }
            params.statusGroup?.let { group ->
                when (group) {
                    "2xx" -> query.andWhere { RequestLogsTable.statusCode.between(200, 299) }
                    "4xx" -> query.andWhere { RequestLogsTable.statusCode.between(400, 499) }
                    "5xx" -> query.andWhere { RequestLogsTable.statusCode.between(500, 599) }
                }
            }
            params.resource?.let {
                query.andWhere { RequestLogsTable.resource eq it }
            }
            params.action?.let {
                query.andWhere { RequestLogsTable.action eq it }
            }
            params.startDate?.let { start ->
                query.andWhere { RequestLogsTable.createdAt greaterEq start }
            }
            params.endDate?.let { end ->
                query.andWhere { RequestLogsTable.createdAt lessEq end }
            }

            val total = query.count()
            val totalPages = ((total + params.pageSize - 1) / params.pageSize).toInt()
            val offset = ((params.page - 1) * params.pageSize).toLong()

            val logs = query
                .orderBy(RequestLogsTable.createdAt, SortOrder.DESC)
                .limit(params.pageSize)
                .offset(offset)
                .map { row ->
                    buildJsonObject {
                        put("id", row[RequestLogsTable.id].value.toString())
                        put("vendorId", row[RequestLogsTable.vendorId])
                        put("userId", row[RequestLogsTable.userId])
                        put("userRole", row[RequestLogsTable.userRole])
                        put("method", row[RequestLogsTable.method])
                        put("path", row[RequestLogsTable.path])
                        put("queryParams", row[RequestLogsTable.queryParams])
                        put("statusCode", row[RequestLogsTable.statusCode])
                        put("durationMs", row[RequestLogsTable.durationMs])
                        put("clientIp", row[RequestLogsTable.clientIp])
                        put("userAgent", row[RequestLogsTable.userAgent])
                        put("requestBody", row[RequestLogsTable.requestBody])
                        put("responseBody", row[RequestLogsTable.responseBody])
                        put("errorMessage", row[RequestLogsTable.errorMessage])
                        put("resource", row[RequestLogsTable.resource])
                        put("action", row[RequestLogsTable.action])
                        put("tags", row[RequestLogsTable.tags])
                        put("description", row[RequestLogsTable.description])
                        put("traceLog", row[RequestLogsTable.traceLog])
                        put("createdAt", row[RequestLogsTable.createdAt].toString())
                    }
                }

            PaginatedLogs(
                logs = logs,
                total = total,
                page = params.page,
                pageSize = params.pageSize,
                totalPages = totalPages
            )
        }
    }

    data class LogStats(
        val totalRequests: Long,
        val errorCount: Long,
        val errorRate: Double,
        val avgDurationMs: Double,
        val statusBreakdown: Map<String, Long>
    )

    fun getStats(vendorId: String? = null, startDate: Instant? = null, endDate: Instant? = null): LogStats {
        return transaction {
            val query = RequestLogsTable.selectAll()

            vendorId?.let {
                query.andWhere { RequestLogsTable.vendorId eq it }
            }
            startDate?.let { start ->
                query.andWhere { RequestLogsTable.createdAt greaterEq start }
            }
            endDate?.let { end ->
                query.andWhere { RequestLogsTable.createdAt lessEq end }
            }

            val total = query.count()

            val errorQuery = RequestLogsTable.selectAll()
            vendorId?.let { errorQuery.andWhere { RequestLogsTable.vendorId eq it } }
            startDate?.let { errorQuery.andWhere { RequestLogsTable.createdAt greaterEq it } }
            endDate?.let { errorQuery.andWhere { RequestLogsTable.createdAt lessEq it } }
            errorQuery.andWhere { (RequestLogsTable.statusCode greaterEq 400) and (RequestLogsTable.statusCode neq 403) and (RequestLogsTable.statusCode neq 404) }
            val errorCount = errorQuery.count()

            val avgDuration = RequestLogsTable.durationMs.avg()
            val avgQuery = RequestLogsTable.select(avgDuration)
            vendorId?.let { avgQuery.andWhere { RequestLogsTable.vendorId eq it } }
            startDate?.let { avgQuery.andWhere { RequestLogsTable.createdAt greaterEq it } }
            endDate?.let { avgQuery.andWhere { RequestLogsTable.createdAt lessEq it } }
            val avg = avgQuery.firstOrNull()?.get(avgDuration)?.toDouble() ?: 0.0

            // Status breakdown
            val breakdown = mutableMapOf<String, Long>()
            val countCol = RequestLogsTable.id.count()
            RequestLogsTable.select(RequestLogsTable.statusCode, countCol)
                .apply {
                    vendorId?.let { andWhere { RequestLogsTable.vendorId eq it } }
                    startDate?.let { andWhere { RequestLogsTable.createdAt greaterEq it } }
                    endDate?.let { andWhere { RequestLogsTable.createdAt lessEq it } }
                }
                .groupBy(RequestLogsTable.statusCode)
                .forEach { row ->
                    val code = row[RequestLogsTable.statusCode]
                    val count = row[countCol]
                    val group = when {
                        code in 200..299 -> "2xx"
                        code in 300..399 -> "3xx"
                        code in 400..499 -> "4xx"
                        code >= 500 -> "5xx"
                        else -> "other"
                    }
                    breakdown[group] = (breakdown[group] ?: 0) + count
                }

            LogStats(
                totalRequests = total,
                errorCount = errorCount,
                errorRate = if (total > 0) (errorCount.toDouble() / total * 100) else 0.0,
                avgDurationMs = avg,
                statusBreakdown = breakdown
            )
        }
    }

    data class VendorInfo(val id: String, val name: String)

    fun getLoggedVendors(): List<VendorInfo> {
        return transaction {
            val vendorIds = RequestLogsTable
                .select(RequestLogsTable.vendorId)
                .where { RequestLogsTable.vendorId.isNotNull() }
                .withDistinct()
                .mapNotNull { it[RequestLogsTable.vendorId] }

            if (vendorIds.isEmpty()) return@transaction emptyList()

            VendorsTable.selectAll()
                .where { VendorsTable.id inList vendorIds.mapNotNull { runCatching { java.util.UUID.fromString(it) }.getOrNull() } }
                .map { VendorInfo(it[VendorsTable.id].value.toString(), it[VendorsTable.name]) }
        }
    }

    data class UserInfo(val id: String, val name: String, val role: String)

    fun getVendorUsers(vendorId: String): List<UserInfo> {
        return transaction {
            val uuid = runCatching { java.util.UUID.fromString(vendorId) }.getOrNull()
                ?: return@transaction emptyList()
            UsersTable.selectAll()
                .where { UsersTable.vendorId eq uuid }
                .map { UserInfo(it[UsersTable.id].value.toString(), it[UsersTable.name], it[UsersTable.role]) }
        }
    }

    // ─── Advanced analytics queries ─────────────────────────────────

    data class EndpointStat(
        val method: String,
        val path: String,
        val count: Long,
        val avgDurationMs: Double,
        val errorCount: Long
    )

    data class TimelinePoint(
        val hour: String,
        val count: Long,
        val errorCount: Long
    )

    fun getTopEndpoints(
        limit: Int = 10,
        vendorId: String? = null,
        startDate: Instant? = null,
        endDate: Instant? = null
    ): List<EndpointStat> {
        return transaction {
            val countCol = RequestLogsTable.id.count()
            val avgDur = RequestLogsTable.durationMs.avg()

            val query = RequestLogsTable.select(
                RequestLogsTable.method,
                RequestLogsTable.path,
                countCol,
                avgDur
            )
            vendorId?.let { query.andWhere { RequestLogsTable.vendorId eq it } }
            startDate?.let { query.andWhere { RequestLogsTable.createdAt greaterEq it } }
            endDate?.let { query.andWhere { RequestLogsTable.createdAt lessEq it } }

            query.groupBy(RequestLogsTable.method, RequestLogsTable.path)
                .orderBy(countCol, SortOrder.DESC)
                .limit(limit)
                .map { row ->
                    val m = row[RequestLogsTable.method]
                    val p = row[RequestLogsTable.path]

                    // Count errors for this endpoint
                    val errorCountCol = RequestLogsTable.id.count()
                    val errorQuery = RequestLogsTable.select(errorCountCol)
                        .where { (RequestLogsTable.method eq m) and (RequestLogsTable.path eq p) and (RequestLogsTable.statusCode greaterEq 400) and (RequestLogsTable.statusCode neq 403) and (RequestLogsTable.statusCode neq 404) }
                    vendorId?.let { errorQuery.andWhere { RequestLogsTable.vendorId eq it } }
                    startDate?.let { errorQuery.andWhere { RequestLogsTable.createdAt greaterEq it } }
                    endDate?.let { errorQuery.andWhere { RequestLogsTable.createdAt lessEq it } }
                    val errCount = errorQuery.firstOrNull()?.get(errorCountCol) ?: 0L

                    EndpointStat(
                        method = m,
                        path = p,
                        count = row[countCol],
                        avgDurationMs = row[avgDur]?.toDouble() ?: 0.0,
                        errorCount = errCount
                    )
                }
        }
    }

    fun getSlowestEndpoints(
        limit: Int = 10,
        vendorId: String? = null,
        startDate: Instant? = null,
        endDate: Instant? = null
    ): List<EndpointStat> {
        return transaction {
            val countCol = RequestLogsTable.id.count()
            val avgDur = RequestLogsTable.durationMs.avg()

            val query = RequestLogsTable.select(
                RequestLogsTable.method,
                RequestLogsTable.path,
                countCol,
                avgDur
            )
            vendorId?.let { query.andWhere { RequestLogsTable.vendorId eq it } }
            startDate?.let { query.andWhere { RequestLogsTable.createdAt greaterEq it } }
            endDate?.let { query.andWhere { RequestLogsTable.createdAt lessEq it } }

            query.groupBy(RequestLogsTable.method, RequestLogsTable.path)
                .orderBy(avgDur, SortOrder.DESC)
                .limit(limit)
                .map { row ->
                    EndpointStat(
                        method = row[RequestLogsTable.method],
                        path = row[RequestLogsTable.path],
                        count = row[countCol],
                        avgDurationMs = row[avgDur]?.toDouble() ?: 0.0,
                        errorCount = 0
                    )
                }
        }
    }

    fun getErrorEndpoints(
        limit: Int = 10,
        vendorId: String? = null,
        startDate: Instant? = null,
        endDate: Instant? = null
    ): List<EndpointStat> {
        return transaction {
            val countCol = RequestLogsTable.id.count()
            val avgDur = RequestLogsTable.durationMs.avg()

            val query = RequestLogsTable.select(
                RequestLogsTable.method,
                RequestLogsTable.path,
                countCol,
                avgDur
            ).where { (RequestLogsTable.statusCode greaterEq 400) and (RequestLogsTable.statusCode neq 403) and (RequestLogsTable.statusCode neq 404) }

            vendorId?.let { query.andWhere { RequestLogsTable.vendorId eq it } }
            startDate?.let { query.andWhere { RequestLogsTable.createdAt greaterEq it } }
            endDate?.let { query.andWhere { RequestLogsTable.createdAt lessEq it } }

            query.groupBy(RequestLogsTable.method, RequestLogsTable.path)
                .orderBy(countCol, SortOrder.DESC)
                .limit(limit)
                .map { row ->
                    EndpointStat(
                        method = row[RequestLogsTable.method],
                        path = row[RequestLogsTable.path],
                        count = row[countCol],
                        avgDurationMs = row[avgDur]?.toDouble() ?: 0.0,
                        errorCount = row[countCol]
                    )
                }
        }
    }

    fun getRequestTimeline(
        hours: Int = 24,
        vendorId: String? = null
    ): List<TimelinePoint> {
        return transaction {
            val now = Clock.System.now()
            val hoursDuration = hours.hours
            val cutoff = now.minus(hoursDuration)

            val query = RequestLogsTable.selectAll()
                .where { RequestLogsTable.createdAt greaterEq cutoff }
            vendorId?.let { query.andWhere { RequestLogsTable.vendorId eq it } }

            // Group rows by hour
            val hourlyMap = mutableMapOf<String, Pair<Long, Long>>()
            query.orderBy(RequestLogsTable.createdAt, SortOrder.ASC).forEach { row ->
                val ts = row[RequestLogsTable.createdAt].toString()
                val hour = if (ts.length >= 13) ts.substring(0, 13) else ts
                val statusCode = row[RequestLogsTable.statusCode]
                val current = hourlyMap[hour] ?: (0L to 0L)
                val isError = if (statusCode >= 400 && statusCode != 403 && statusCode != 404) 1L else 0L
                hourlyMap[hour] = (current.first + 1) to (current.second + isError)
            }

            hourlyMap.entries
                .sortedBy { it.key }
                .map { (hour, counts) ->
                    TimelinePoint(
                        hour = hour,
                        count = counts.first,
                        errorCount = counts.second
                    )
                }
        }
    }

    fun cleanupOldLogs(retentionDays: Int) {
        transaction {
            val cutoff = Clock.System.now().minus(retentionDays.days)
            val deleted = RequestLogsTable.deleteWhere { createdAt lessEq cutoff }
            if (deleted > 0) {
                logger.info("Cleaned up $deleted old request logs (retention: $retentionDays days)")
            }
        }
    }

    /**
     * Clear ALL request logs from the database
     */
    fun clearAllLogs(): Int {
        return transaction {
            val deleted = RequestLogsTable.deleteAll()
            logger.info("Cleared all request logs: $deleted records deleted")
            deleted
        }
    }

    /**
     * Clear request logs for a specific vendor
     */
    fun clearVendorLogs(vendorId: String): Int {
        return transaction {
            val deleted = RequestLogsTable.deleteWhere { RequestLogsTable.vendorId eq vendorId }
            logger.info("Cleared logs for vendor $vendorId: $deleted records deleted")
            deleted
        }
    }

    /**
     * Clear request logs that have no vendor (admin/system logs)
     */
    fun clearAdminLogs(): Int {
        return transaction {
            val deleted = RequestLogsTable.deleteWhere { RequestLogsTable.vendorId.isNull() }
            logger.info("Cleared admin/system logs: $deleted records deleted")
            deleted
        }
    }

    // ─── Resource & Action Analytics ────────────────────────────────

    data class ResourceStat(
        val resource: String,
        val count: Long,
        val avgDurationMs: Double,
        val errorCount: Long
    )

    data class ActionStat(
        val action: String,
        val count: Long,
        val avgDurationMs: Double,
        val errorCount: Long
    )

    fun getResourceBreakdown(
        vendorId: String? = null,
        startDate: Instant? = null,
        endDate: Instant? = null,
        limit: Int = 20
    ): List<ResourceStat> {
        return transaction {
            val countCol = RequestLogsTable.id.count()
            val avgDur = RequestLogsTable.durationMs.avg()

            val query = RequestLogsTable.select(
                RequestLogsTable.resource,
                countCol,
                avgDur
            ).where { RequestLogsTable.resource.isNotNull() }

            vendorId?.let { query.andWhere { RequestLogsTable.vendorId eq it } }
            startDate?.let { query.andWhere { RequestLogsTable.createdAt greaterEq it } }
            endDate?.let { query.andWhere { RequestLogsTable.createdAt lessEq it } }

            query.groupBy(RequestLogsTable.resource)
                .orderBy(countCol, SortOrder.DESC)
                .limit(limit)
                .map { row ->
                    val res = row[RequestLogsTable.resource] ?: "unknown"

                    val errorCountCol = RequestLogsTable.id.count()
                    val errorQuery = RequestLogsTable.select(errorCountCol)
                        .where { (RequestLogsTable.resource eq res) and (RequestLogsTable.statusCode greaterEq 400) and (RequestLogsTable.statusCode neq 403) and (RequestLogsTable.statusCode neq 404) }
                    vendorId?.let { errorQuery.andWhere { RequestLogsTable.vendorId eq it } }
                    startDate?.let { errorQuery.andWhere { RequestLogsTable.createdAt greaterEq it } }
                    endDate?.let { errorQuery.andWhere { RequestLogsTable.createdAt lessEq it } }
                    val errCount = errorQuery.firstOrNull()?.get(errorCountCol) ?: 0L

                    ResourceStat(
                        resource = res,
                        count = row[countCol],
                        avgDurationMs = row[avgDur]?.toDouble() ?: 0.0,
                        errorCount = errCount
                    )
                }
        }
    }

    fun getActionBreakdown(
        resource: String? = null,
        vendorId: String? = null,
        startDate: Instant? = null,
        endDate: Instant? = null,
        limit: Int = 20
    ): List<ActionStat> {
        return transaction {
            val countCol = RequestLogsTable.id.count()
            val avgDur = RequestLogsTable.durationMs.avg()

            val query = RequestLogsTable.select(
                RequestLogsTable.action,
                countCol,
                avgDur
            ).where { RequestLogsTable.action.isNotNull() }

            resource?.let { query.andWhere { RequestLogsTable.resource eq it } }
            vendorId?.let { query.andWhere { RequestLogsTable.vendorId eq it } }
            startDate?.let { query.andWhere { RequestLogsTable.createdAt greaterEq it } }
            endDate?.let { query.andWhere { RequestLogsTable.createdAt lessEq it } }

            query.groupBy(RequestLogsTable.action)
                .orderBy(countCol, SortOrder.DESC)
                .limit(limit)
                .map { row ->
                    val act = row[RequestLogsTable.action] ?: "unknown"

                    val errorCountCol = RequestLogsTable.id.count()
                    val errorQuery = RequestLogsTable.select(errorCountCol)
                        .where { (RequestLogsTable.action eq act) and (RequestLogsTable.statusCode greaterEq 400) and (RequestLogsTable.statusCode neq 403) and (RequestLogsTable.statusCode neq 404) }
                    resource?.let { errorQuery.andWhere { RequestLogsTable.resource eq it } }
                    vendorId?.let { errorQuery.andWhere { RequestLogsTable.vendorId eq it } }
                    startDate?.let { errorQuery.andWhere { RequestLogsTable.createdAt greaterEq it } }
                    endDate?.let { errorQuery.andWhere { RequestLogsTable.createdAt lessEq it } }
                    val errCount = errorQuery.firstOrNull()?.get(errorCountCol) ?: 0L

                    ActionStat(
                        action = act,
                        count = row[countCol],
                        avgDurationMs = row[avgDur]?.toDouble() ?: 0.0,
                        errorCount = errCount
                    )
                }
        }
    }

    data class LiveMonitoringData(
        val requestsPerMinute: Double,
        val activeResources: List<String>,
        val recentErrors: List<JsonObject>,
        val p95DurationMs: Long,
    )

    fun getLiveMonitoring(vendorId: String? = null): LiveMonitoringData {
        return transaction {
            val now = Clock.System.now()
            val fiveMinutesAgo = now.minus(5.minutes)
            val oneHourAgo = now.minus(1.hours)

            // Requests per minute (last 5 minutes)
            val recentQuery = RequestLogsTable.selectAll()
                .where { RequestLogsTable.createdAt greaterEq fiveMinutesAgo }
            vendorId?.let { recentQuery.andWhere { RequestLogsTable.vendorId eq it } }
            val recentCount = recentQuery.count()
            val rpm = recentCount.toDouble() / 5.0

            // Active resources (last hour)
            val resourceQuery = RequestLogsTable.select(RequestLogsTable.resource)
                .where { (RequestLogsTable.createdAt greaterEq oneHourAgo) and RequestLogsTable.resource.isNotNull() }
            vendorId?.let { resourceQuery.andWhere { RequestLogsTable.vendorId eq it } }
            val activeResources = resourceQuery.withDistinct()
                .mapNotNull { it[RequestLogsTable.resource] }

            // Recent errors (last hour, up to 10)
            val errorQuery = RequestLogsTable.selectAll()
                .where { (RequestLogsTable.createdAt greaterEq oneHourAgo) and (RequestLogsTable.statusCode greaterEq 400) and (RequestLogsTable.statusCode neq 403) and (RequestLogsTable.statusCode neq 404) }
            vendorId?.let { errorQuery.andWhere { RequestLogsTable.vendorId eq it } }
            val recentErrors = errorQuery
                .orderBy(RequestLogsTable.createdAt, SortOrder.DESC)
                .limit(10)
                .map { row ->
                    buildJsonObject {
                        put("method", row[RequestLogsTable.method])
                        put("path", row[RequestLogsTable.path])
                        put("statusCode", row[RequestLogsTable.statusCode])
                        put("resource", row[RequestLogsTable.resource])
                        put("action", row[RequestLogsTable.action])
                        put("durationMs", row[RequestLogsTable.durationMs])
                        put("errorMessage", row[RequestLogsTable.errorMessage])
                        put("createdAt", row[RequestLogsTable.createdAt].toString())
                    }
                }

            // P95 duration (last hour) - approximate using sorted durations
            val durationQuery = RequestLogsTable.select(RequestLogsTable.durationMs)
                .where { RequestLogsTable.createdAt greaterEq oneHourAgo }
            vendorId?.let { durationQuery.andWhere { RequestLogsTable.vendorId eq it } }
            val durations = durationQuery
                .orderBy(RequestLogsTable.durationMs, SortOrder.ASC)
                .map { it[RequestLogsTable.durationMs] }
            val p95 = if (durations.isNotEmpty()) {
                val idx = (durations.size * 0.95).toInt().coerceAtMost(durations.size - 1)
                durations[idx]
            } else 0L

            LiveMonitoringData(
                requestsPerMinute = rpm,
                activeResources = activeResources,
                recentErrors = recentErrors,
                p95DurationMs = p95
            )
        }
    }
}
