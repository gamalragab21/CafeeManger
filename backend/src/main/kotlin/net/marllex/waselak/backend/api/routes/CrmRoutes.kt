package net.marllex.waselak.backend.api.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.*
import net.marllex.waselak.backend.domain.service.CrmService
import net.marllex.waselak.backend.plugins.CrmPrincipal
import org.koin.java.KoinJavaComponent

/**
 * All CRM timestamps are rendered in Egypt local time, regardless of where the server
 * runs (our VPS is UTC). Using a named zone lets the JVM handle DST transitions — Egypt
 * reinstated DST in 2023, so we can't safely hardcode a fixed offset like "+02:00".
 */
private val CRM_TZ = TimeZone.of("Africa/Cairo")

/**
 * Best-effort delete of a previously-uploaded file. Accepts the same URL format
 * we store in DB columns — either a relative `/uploads/...` path or an absolute
 * `http://host/uploads/...` URL leftover from before we switched to relative
 * URLs. Anything that doesn't look like an upload from this server is silently
 * ignored (we never want to nuke arbitrary paths).
 *
 * Why best-effort: the caller has already saved the new file and updated the
 * row pointing at it; whether the old bytes are still on disk doesn't change
 * correctness, only storage hygiene. A failure to delete (file already gone,
 * permission glitch, race with a backup script) shouldn't 500 the request.
 *
 * Returns true on a clean delete, false on any failure / no-op.
 */
private fun deleteUploadFile(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    // Strip absolute prefix from legacy rows; we only operate on the path.
    val path = url.removePrefix("http://").removePrefix("https://")
        .substringAfter("/", "")  // drop host portion if any
        .let { if (url.startsWith("/")) url else "/$it" }
    // Double-defence: only allow paths under /uploads/. Refuses traversal
    // (`..`) so even a malformed legacy URL can't escape the uploads dir.
    if (!path.startsWith("/uploads/") || path.contains("..")) return false
    val relative = path.removePrefix("/")  // "uploads/crm-photos/foo.png"
    return runCatching {
        val file = java.io.File(relative)
        if (file.exists() && file.isFile) file.delete() else false
    }.getOrDefault(false)
}

fun Route.crmRoutes() {
    val crmService by KoinJavaComponent.inject<CrmService>(clazz = CrmService::class.java)

    // ─── Public Routes ──────────────────────────────────────────────

    get("/crm/login") {
        val error = call.request.queryParameters["error"]
        call.respondText(crmLoginPageHtml(error), ContentType.Text.Html)
    }

    post("/crm/login") {
        val params = call.receiveParameters()
        val email = params["email"] ?: ""
        val password = params["password"] ?: ""

        val result = crmService.login(email, password)
        if (result != null) {
            call.response.cookies.append(
                Cookie(
                    name = "crm_token",
                    value = result.token,
                    path = "/crm",
                    httpOnly = true,
                    maxAge = 86400
                )
            )
            call.respondRedirect("/crm/dashboard")
        } else {
            call.respondRedirect("/crm/login?error=1")
        }
    }

    get("/crm/logout") {
        call.response.cookies.append(
            Cookie(
                name = "crm_token",
                value = "",
                path = "/crm",
                httpOnly = true,
                maxAge = 0
            )
        )
        call.respondRedirect("/crm/login")
    }

    // ─── Protected Routes ───────────────────────────────────────────

    authenticate("crm-jwt") {

        // ─── Dashboard ──────────────────────────────────────────
        get("/crm/dashboard") {
            val principal = call.principal<CrmPrincipal>()!!
            val stats = crmService.getStats(principal.organizationId, principal.agentId, principal.canSeeAll)
            val recentActivities = crmService.listActivities(principal.organizationId, principal.agentId, principal.canSeeAll, limit = 10)

            val content = if (principal.isOwner) {
                buildOwnerDashboard(stats, recentActivities)
            } else {
                buildBasicDashboard(stats, recentActivities)
            }

            call.respondText(
                crmLayout("لوحة التحكم", principal.name, principal.role, principal, "dashboard", content),
                ContentType.Text.Html
            )
        }

        // ─── Clients Page ───────────────────────────────────────
        get("/crm/clients") {
            val principal = call.principal<CrmPrincipal>()!!
            val clients = crmService.listClients(principal.organizationId, principal.agentId, principal.canSeeAll)
            val agents = if (principal.canSeeAll) crmService.listAgents(principal.organizationId) else emptyList()

            // Stable avatar palette — same name always maps to the same colour across
            // the list and the details page, making clients feel recognisable.
            val avatarPalette = listOf("#0EA5E9", "#10B981", "#F59E0B", "#EF4444", "#8B5CF6", "#EC4899", "#14B8A6", "#F97316")

            val tableRows = buildString {
                clients.forEach { c ->
                    val staleClass = if ((c.daysSinceLastContact ?: 0) >= 7) "border-r-4 border-r-red-400" else "border-r-4 border-r-transparent"
                    val statusBg = statusColor(c.status)
                    val statusTxt = statusTextColor(c.status)
                    val avatarColor = avatarPalette[(c.clientName.hashCode().let { if (it < 0) -it else it }) % avatarPalette.size]
                    val initial = c.clientName.trim().firstOrNull()?.toString() ?: "؟"
                    val escapedName = c.clientName.replace("'", "\\'")
                    val freshness = when {
                        c.daysSinceLastContact == null -> """<span class="text-xs text-gray-400">لم يُتواصل بعد</span>"""
                        c.daysSinceLastContact == 0L -> """<span class="inline-flex items-center gap-1 text-xs text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded-full"><span class="w-1.5 h-1.5 rounded-full bg-emerald-500"></span>اليوم</span>"""
                        c.daysSinceLastContact!! < 3 -> """<span class="inline-flex items-center gap-1 text-xs text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded-full"><span class="w-1.5 h-1.5 rounded-full bg-emerald-500"></span>${c.daysSinceLastContact} يوم</span>"""
                        c.daysSinceLastContact!! < 7 -> """<span class="inline-flex items-center gap-1 text-xs text-amber-700 bg-amber-50 px-2 py-0.5 rounded-full"><span class="w-1.5 h-1.5 rounded-full bg-amber-500"></span>${c.daysSinceLastContact} يوم</span>"""
                        else -> """<span class="inline-flex items-center gap-1 text-xs text-red-700 bg-red-50 px-2 py-0.5 rounded-full"><span class="w-1.5 h-1.5 rounded-full bg-red-500"></span>${c.daysSinceLastContact} يوم</span>"""
                    }
                    // data-* attributes power the smart filter panel: range filters read
                    // epoch-ms integers; multi-selects match on exact attribute values.
                    val lastContactMs = c.lastContactAt ?: 0L
                    val nextActionMs = c.nextActionDate?.let {
                        try { kotlinx.datetime.LocalDate.parse(it).atStartOfDayIn(CRM_TZ).toEpochMilliseconds() } catch (_: Throwable) { 0L }
                    } ?: 0L
                    val daysStaleAttr = c.daysSinceLastContact ?: -1L
                    append("""<tr class="$staleClass hover:bg-gray-50 cursor-pointer transition"
                        data-id="${c.id}"
                        data-created-ms="${c.createdAt}"
                        data-updated-ms="${c.updatedAt}"
                        data-last-contact-ms="$lastContactMs"
                        data-next-action-ms="$nextActionMs"
                        data-days-stale="$daysStaleAttr"
                        data-amount="${c.finalAmount}"
                        data-monthly="${c.monthlyAmount}"
                        data-discount="${c.discountPercent}"
                        data-status="${c.status}"
                        data-plan="${c.plan ?: ""}"
                        data-business-type="${c.businessType ?: ""}"
                        data-governorate="${c.governorate ?: ""}"
                        data-source="${c.source ?: ""}"
                        data-agent-id="${c.assignedTo ?: ""}"
                        data-agent-name="${c.assignedName ?: ""}"
                        data-whatsapp="${c.whatsapp}"
                        data-phone="${c.phone}"
                        data-client-name="${c.clientName}"
                        onclick="goToClient(event, '${c.id}')">""")
                    // Column 0 — identity cell: avatar + name + phone + business (rich)
                    append("""<td class='p-3'>
                        <div class="flex items-center gap-3">
                            <div class="w-10 h-10 rounded-xl flex items-center justify-center text-white font-bold shadow-sm flex-shrink-0" style="background:$avatarColor">$initial</div>
                            <div class="min-w-0">
                                <div class="font-semibold text-gray-800 truncate">${c.clientName}</div>
                                <div class="text-xs text-gray-500 flex items-center gap-2 flex-wrap">
                                    <span>📞 ${c.phone}</span>
                                    ${c.businessName?.let { """<span class="truncate">🏢 $it</span>""" } ?: ""}
                                </div>
                            </div>
                        </div>
                    </td>""")
                    // Column 1 — Business type (filterable)
                    append("<td class='p-3 text-sm text-gray-600'>${c.businessType ?: "-"}</td>")
                    // Column 2 — Governorate
                    append("<td class='p-3 text-sm text-gray-600'>${c.governorate ?: "-"}</td>")
                    // Column 3 — Status (filterable)
                    append("""<td class='p-3'><span class="inline-block px-2.5 py-1 rounded-full text-xs font-medium whitespace-nowrap" style="background:$statusBg;color:$statusTxt">${c.status}</span></td>""")
                    // Column 4 — Plan + Amount (stacked)
                    append("""<td class='p-3'>
                        <div class="text-sm font-medium text-gray-800">${String.format("%,.0f", c.finalAmount)} <span class="text-xs text-gray-500">ج.م</span></div>
                        <div class="text-xs text-gray-500">${c.plan ?: "-"}</div>
                    </td>""")
                    // Column 5 — Assigned agent (filterable)
                    append("""<td class='p-3 text-sm text-gray-700'>${c.assignedName ?: "-"}</td>""")
                    // Column 6 — Last contact freshness pill
                    append("<td class='p-3'>$freshness</td>")
                    // Column 7 — Created / Updated compact
                    append("""<td class='p-3'>
                        <div class="text-xs text-gray-600 whitespace-nowrap">➕ ${formatCrmDate(c.createdAt)}</div>
                        <div class="text-xs text-gray-400 whitespace-nowrap">✏️ ${formatCrmDate(c.updatedAt)}</div>
                    </td>""")
                    // Column 8 — Actions: stopPropagation so buttons don't navigate
                    append("""<td class='p-3' onclick="event.stopPropagation()">
                        <div class="flex items-center gap-1 justify-end">
                            <a href="/crm/clients/${c.id}" class="p-1.5 rounded hover:bg-emerald-50 text-emerald-600 transition" title="عرض التفاصيل">👁️</a>
                            <button onclick="openEditClient('${c.id}')" class="p-1.5 rounded hover:bg-blue-50 text-blue-600 transition" title="تعديل">✏️</button>
                            <button onclick="deleteClient('${c.id}', '$escapedName')" class="p-1.5 rounded hover:bg-red-50 text-red-600 transition" title="حذف">🗑️</button>
                        </div>
                    </td>""")
                    append("</tr>")
                }
            }

            val agentOptions = agents.joinToString("") { """<option value="${it.id}">${it.name}</option>""" }

            // Status chips data
            val statusCounts = clients.groupBy { it.status }.mapValues { it.value.size }
            val allStatuses = listOf("عميل جديد", "متابعة", "ديمو محجوز", "يحتاج مناقشة", "تجربة فعالة", "تجربة منتهية", "تفاوض", "مدفوع", "مشترك", "رفض", "نشاط غير مناسب", "توقف")
            val statusChipsHtml = allStatuses.filter { (statusCounts[it] ?: 0) > 0 }.joinToString("") { s ->
                val bg = statusColor(s)
                val txt = statusTextColor(s)
                val count = statusCounts[s] ?: 0
                // Status chip → toggles the corresponding multi-select checkbox.
                """<button class="status-chip px-3 py-1.5 rounded-full text-xs font-medium cursor-pointer transition-all shadow-sm hover:scale-105" style="background:$bg;color:$txt" data-ms-id="status" onclick="filterByStatusChip('$s', this)">$s <span class="opacity-70">· $count</span></button>"""
            }
            val agentNames = if (agents.isNotEmpty()) agents.map { it.name } else emptyList()
            val sourceList = listOf("واتساب", "فيسبوك", "انستجرام", "إحالة صديق", "زيارة للمحل", "الموقع", "جوجل", "تيك توك")
            val businessTypes = listOf("مطعم", "كافيه", "صيدلية", "محل تجزئة", "سوبر ماركت", "مخبز", "جزارة", "ملابس", "إلكترونيات", "موبايلات", "أخرى")

            // Summary KPIs for the rebrand hero strip.
            val totalClients = clients.size
            val activeClients = clients.count { it.status in setOf("مشترك", "مدفوع") }
            val pipelineClients = clients.count { it.status in setOf("متابعة", "ديمو محجوز", "تفاوض", "تجربة فعالة") }
            val staleClients = clients.count { (it.daysSinceLastContact ?: 0) >= 7 }
            val totalRevenue = clients.filter { it.status in setOf("مشترك", "مدفوع") }.sumOf { it.finalAmount }

            val content = """
                <!-- Hero header -->
                <div class="flex items-center justify-between mb-6 flex-wrap gap-3">
                    <div>
                        <h2 class="text-2xl font-bold text-gray-800">العملاء</h2>
                        <p class="text-sm text-gray-500 mt-1">إدارة كل العملاء وسجلاتهم في مكان واحد</p>
                    </div>
                    <button onclick="document.getElementById('addClientModal').showModal()" class="flex items-center gap-2 bg-emerald-600 hover:bg-emerald-700 text-white px-5 py-2.5 rounded-xl shadow-sm transition font-medium">
                        <span class="text-lg">＋</span><span>إضافة عميل</span>
                    </button>
                </div>

                <!-- KPI summary strip -->
                <div class="grid grid-cols-2 lg:grid-cols-4 gap-3 mb-5">
                    <div class="bg-white rounded-xl p-4 border border-gray-100 shadow-sm">
                        <div class="flex items-center justify-between">
                            <div>
                                <p class="text-xs text-gray-500">إجمالي العملاء</p>
                                <p class="text-2xl font-bold text-gray-800 mt-1"><span id="visibleCount">$totalClients</span> <span class="text-sm font-normal text-gray-400">/ $totalClients</span></p>
                            </div>
                            <div class="w-10 h-10 rounded-lg bg-slate-100 flex items-center justify-center text-xl">👥</div>
                        </div>
                    </div>
                    <div class="bg-white rounded-xl p-4 border border-gray-100 shadow-sm">
                        <div class="flex items-center justify-between">
                            <div>
                                <p class="text-xs text-gray-500">عملاء نشطين</p>
                                <p class="text-2xl font-bold text-emerald-600 mt-1">$activeClients</p>
                            </div>
                            <div class="w-10 h-10 rounded-lg bg-emerald-50 flex items-center justify-center text-xl">✅</div>
                        </div>
                    </div>
                    <div class="bg-white rounded-xl p-4 border border-gray-100 shadow-sm">
                        <div class="flex items-center justify-between">
                            <div>
                                <p class="text-xs text-gray-500">تحت المتابعة</p>
                                <p class="text-2xl font-bold text-sky-600 mt-1">$pipelineClients</p>
                            </div>
                            <div class="w-10 h-10 rounded-lg bg-sky-50 flex items-center justify-center text-xl">🔍</div>
                        </div>
                    </div>
                    <div class="bg-white rounded-xl p-4 border border-gray-100 shadow-sm">
                        <div class="flex items-center justify-between">
                            <div>
                                <p class="text-xs text-gray-500">${if (staleClients > 0) "يحتاج متابعة" else "إيراد نشط/شهر"}</p>
                                <p class="text-2xl font-bold ${if (staleClients > 0) "text-red-600" else "text-amber-600"} mt-1">${if (staleClients > 0) "$staleClients" else String.format("%,.0f", totalRevenue)}</p>
                                ${if (staleClients == 0) """<p class="text-xs text-gray-400">ج.م</p>""" else ""}
                            </div>
                            <div class="w-10 h-10 rounded-lg ${if (staleClients > 0) "bg-red-50" else "bg-amber-50"} flex items-center justify-center text-xl">${if (staleClients > 0) "⚠️" else "💰"}</div>
                        </div>
                    </div>
                </div>

                <div class="flex flex-wrap gap-2 mb-4">$statusChipsHtml</div>
                ${smartFilterPanelHtml(
                    searchPlaceholder = "بحث بالاسم أو رقم الهاتف أو اسم النشاط...",
                    primaryDateRange = SmartDateRange("data-created-ms", "تاريخ الإضافة", "clientsCreated"),
                    secondaryDateRange = SmartDateRange("data-last-contact-ms", "آخر تواصل", "clientsLastContact"),
                    numberRange = SmartNumberRange("data-amount", "المبلغ الشهري", "clientsAmount", "ج.م"),
                    multiSelects = buildList {
                        add(SmartMultiSelect("data-status", "الحالة", "status", allStatuses))
                        add(SmartMultiSelect("data-business-type", "نوع النشاط", "businessType", businessTypes))
                        add(SmartMultiSelect("data-governorate", "المحافظة", "governorate", clients.mapNotNull { it.governorate }.distinct()))
                        add(SmartMultiSelect("data-plan", "الباقة", "plan", clients.mapNotNull { it.plan }.distinct()))
                        add(SmartMultiSelect("data-source", "المصدر", "source", sourceList))
                        if (principal.canSeeAll && agentNames.isNotEmpty()) {
                            add(SmartMultiSelect("data-agent-name", "الموظف", "agentName", agentNames))
                        }
                    },
                    presets = listOf(
                        SmartPreset("today", "أُضيف اليوم", "presetRangeDays('data-created-ms', 1, true)", "📅"),
                        SmartPreset("week", "آخر 7 أيام", "presetRangeDays('data-created-ms', 7, true)", "📆"),
                        SmartPreset("month", "آخر 30 يوم", "presetRangeDays('data-created-ms', 30, true)", "🗓️"),
                        SmartPreset("stale", "لم يُتواصل +7 أيام", "presetMinDaysAgo('data-last-contact-ms', 7)", "⏰"),
                        SmartPreset("subscribed", "مشتركين فقط", "document.querySelector('.smart-ms-status[value=\"مشترك\"]').checked = true; updateMultiSelect('status'); applySmartFilters()", "✅"),
                    )
                )}
                <div class="bg-white rounded-xl shadow-sm overflow-x-auto border border-gray-100">
                    <table id="dataTable" class="w-full text-sm">
                        <thead>
                            <tr class="bg-gray-50 border-b">
                                <th class="p-3 text-right text-xs text-gray-500 uppercase tracking-wider font-medium">العميل</th>
                                <th class="p-3 text-right text-xs text-gray-500 uppercase tracking-wider font-medium">النوع</th>
                                <th class="p-3 text-right text-xs text-gray-500 uppercase tracking-wider font-medium">المحافظة</th>
                                <th class="p-3 text-right text-xs text-gray-500 uppercase tracking-wider font-medium">الحالة</th>
                                <th class="p-3 text-right text-xs text-gray-500 uppercase tracking-wider font-medium">الباقة</th>
                                <th class="p-3 text-right text-xs text-gray-500 uppercase tracking-wider font-medium">الموظف</th>
                                <th class="p-3 text-right text-xs text-gray-500 uppercase tracking-wider font-medium">آخر تواصل</th>
                                <th class="p-3 text-right text-xs text-gray-500 uppercase tracking-wider font-medium">التواريخ</th>
                                <th class="p-3 text-right text-xs text-gray-500 uppercase tracking-wider font-medium"></th>
                            </tr>
                        </thead>
                        <tbody>$tableRows</tbody>
                    </table>
                    ${if (clients.isEmpty()) """<div class="text-center py-16">
                        <div class="text-6xl mb-3">📂</div>
                        <p class="text-gray-500 mb-4">لا يوجد عملاء بعد</p>
                        <button onclick="document.getElementById('addClientModal').showModal()" class="bg-emerald-600 hover:bg-emerald-700 text-white px-4 py-2 rounded-lg">أضف أول عميل</button>
                    </div>""" else ""}
                </div>
                ${smartFilterScript()}

                ${addClientModalHtml(agentOptions, principal.canSeeAll)}
                ${editClientModalHtml(agentOptions, principal.canSeeAll)}

                <script>
                // Row-level click handler — navigates to client details unless the click
                // was on an explicit action button/link inside the action cell.
                function goToClient(event, id) {
                    const target = event.target;
                    if (target.closest('button, a, select, input')) return;
                    window.location.href = '/crm/clients/' + id;
                }

                async function openEditClient(id) {
                    const res = await fetch('/crm/api/clients');
                    const clients = await res.json();
                    const c = clients.find(x => x.id === id);
                    if (!c) return alert('عميل غير موجود');
                    document.getElementById('edit_id').value = c.id;
                    document.getElementById('edit_clientName').value = c.clientName || '';
                    document.getElementById('edit_phone').value = c.phone || '';
                    document.getElementById('edit_whatsapp').checked = c.whatsapp || false;
                    document.getElementById('edit_businessName').value = c.businessName || '';
                    document.getElementById('edit_businessType').value = c.businessType || '';
                    document.getElementById('edit_city').value = c.city || '';
                    document.getElementById('edit_governorate').value = c.governorate || '';
                    document.getElementById('edit_status').value = c.status || '';
                    document.getElementById('edit_plan').value = c.plan || '';
                    document.getElementById('edit_monthlyAmount').value = c.monthlyAmount || 0;
                    document.getElementById('edit_discountPercent').value = c.discountPercent || 0;
                    document.getElementById('edit_paymentMethod').value = c.paymentMethod || '';
                    document.getElementById('edit_source').value = c.source || '';
                    document.getElementById('edit_notes').value = c.notes || '';
                    ${if (principal.canSeeAll) "document.getElementById('edit_assignedTo').value = c.assignedTo || '';" else ""}
                    document.getElementById('editClientModal').showModal();
                }

                async function submitNewClient(e) {
                    e.preventDefault();
                    const form = e.target;
                    const data = Object.fromEntries(new FormData(form));
                    data.whatsapp = form.querySelector('[name=whatsapp]').checked;
                    data.monthlyAmount = parseFloat(data.monthlyAmount) || 0;
                    data.discountPercent = parseInt(data.discountPercent) || 0;
                    const res = await fetch('/crm/api/clients', {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(data)});
                    if (res.ok) { location.reload(); } else { alert('حدث خطأ'); }
                }

                async function submitEditClient(e) {
                    e.preventDefault();
                    const form = e.target;
                    const data = Object.fromEntries(new FormData(form));
                    const id = data.id; delete data.id;
                    data.whatsapp = form.querySelector('[name=whatsapp]').checked;
                    data.monthlyAmount = parseFloat(data.monthlyAmount) || 0;
                    data.discountPercent = parseInt(data.discountPercent) || 0;
                    const res = await fetch('/crm/api/clients/' + id, {method:'PUT', headers:{'Content-Type':'application/json'}, body:JSON.stringify(data)});
                    if (res.ok) { location.reload(); } else { alert('حدث خطأ'); }
                }
                async function deleteClient(id, name) {
                    if (!confirm('هل أنت متأكد من حذف العميل ' + name + '؟\nسيتم حذف كل بياناته وفواتيره.')) return;
                    const res = await fetch('/crm/api/clients/' + id, {method:'DELETE'});
                    if (res.ok) { location.reload(); } else { const d = await res.json(); alert(d.error || 'حدث خطأ'); }
                }
                </script>
            """.trimIndent()

            call.respondText(
                crmLayout("العملاء", principal.name, principal.role, principal, "clients", content),
                ContentType.Text.Html
            )
        }

        // ─── Client Details Page ────────────────────────────────
        // Full 360° view of a single client: header with actions, KPIs, info panels,
        // a vertical activities timeline, and the invoices section. Agents can only
        // open details for clients they own; managers/owners see everyone.
        get("/crm/clients/{id}") {
            val principal = call.principal<CrmPrincipal>()!!
            val clientId = call.parameters["id"]
                ?: return@get call.respondText("Missing id", status = HttpStatusCode.BadRequest)

            val client = crmService.getClient(principal.organizationId, clientId)
            if (client == null) {
                call.respondText(
                    crmLayout("غير موجود", principal.name, principal.role, principal, "clients",
                        """<div class="bg-white rounded-2xl p-10 text-center">
                            <div class="text-6xl mb-3">🔎</div>
                            <h2 class="text-xl font-bold mb-2">هذا العميل غير موجود</h2>
                            <p class="text-gray-500 mb-4">ربما تم حذفه أو لم يعد متاحًا لك.</p>
                            <a href="/crm/clients" class="inline-block px-4 py-2 bg-emerald-600 text-white rounded-lg hover:bg-emerald-700">العودة إلى العملاء</a>
                        </div>"""),
                    ContentType.Text.Html
                )
                return@get
            }

            // Agents may only open details for clients assigned to them.
            if (!principal.canSeeAll && !crmService.isClientOwnedBy(principal.organizationId, clientId, principal.agentId)) {
                call.respondText(
                    crmLayout("ممنوع", principal.name, principal.role, principal, "clients",
                        """<div class="bg-white rounded-2xl p-10 text-center">
                            <div class="text-6xl mb-3">🚫</div>
                            <h2 class="text-xl font-bold mb-2">ليس لديك صلاحية لعرض هذا العميل</h2>
                            <a href="/crm/clients" class="inline-block px-4 py-2 bg-emerald-600 text-white rounded-lg hover:bg-emerald-700 mt-3">العودة إلى العملاء</a>
                        </div>"""),
                    ContentType.Text.Html, HttpStatusCode.Forbidden
                )
                return@get
            }

            val activities = crmService.listActivitiesForClient(principal.organizationId, clientId)
            val invoices = crmService.listInvoices(principal.organizationId, clientId)
            val agents = if (principal.canSeeAll) crmService.listAgents(principal.organizationId) else emptyList()
            val agentOptions = agents.joinToString("") { """<option value="${it.id}">${it.name}</option>""" }

            // Stats for the hero KPIs.
            val totalActivities = activities.size
            val lastContactLabel = when {
                client.daysSinceLastContact == null -> "لم يُتواصل بعد"
                client.daysSinceLastContact == 0L -> "اليوم"
                client.daysSinceLastContact == 1L -> "أمس"
                else -> "منذ ${client.daysSinceLastContact} يوم"
            }
            val daysAsClient = ((Clock.System.now().toEpochMilliseconds() - client.createdAt) / 86_400_000L).coerceAtLeast(0)
            val totalPaid = invoices.sumOf { it.paidAmount }
            val totalOutstanding = invoices.sumOf { it.remainingAmount }

            // Avatar colour — hash-based so the same client keeps the same colour across pages.
            val avatarPalette = listOf("#0EA5E9", "#10B981", "#F59E0B", "#EF4444", "#8B5CF6", "#EC4899", "#14B8A6", "#F97316")
            val avatarColor = avatarPalette[(client.clientName.hashCode().let { if (it < 0) -it else it }) % avatarPalette.size]
            val initial = client.clientName.trim().firstOrNull()?.toString() ?: "؟"
            val statusBg = statusColor(client.status)
            val statusTxt = statusTextColor(client.status)

            // WhatsApp deep link — strip anything non-digit and default-prefix 2 (Egypt) if the
            // number looks local. Not foolproof but matches Egyptian conventions we see in data.
            val waNumber = client.phone.filter { it.isDigit() }.let {
                if (it.startsWith("2") || it.length <= 10) it else "2$it"
            }
            val waUrl = "https://wa.me/$waNumber"

            fun infoRow(label: String, value: String?, icon: String = ""): String {
                val display = value?.takeIf { it.isNotBlank() } ?: "-"
                val iconSpan = if (icon.isNotEmpty()) """<span class="text-gray-400">$icon</span>""" else ""
                return """
                    <div class="flex items-start gap-2 py-2 border-b border-gray-100 last:border-0">
                        $iconSpan
                        <div class="flex-1 min-w-0">
                            <div class="text-xs text-gray-500">$label</div>
                            <div class="text-sm font-medium text-gray-800 break-words">$display</div>
                        </div>
                    </div>
                """.trimIndent()
            }

            // Vertical activities timeline.
            val timelineHtml = if (activities.isEmpty()) """
                <div class="text-center py-10">
                    <div class="text-5xl mb-2">📭</div>
                    <p class="text-gray-500">لا توجد أنشطة مسجلة لهذا العميل بعد</p>
                </div>
            """ else buildString {
                append("""<div class="relative pr-6">""")
                // Vertical rail
                append("""<div class="absolute right-2 top-2 bottom-2 w-0.5 bg-gray-200"></div>""")
                activities.forEach { a ->
                    val dotColor = statusColor(a.newStatus ?: client.status)
                    val channelIcon = when (a.channel) {
                        "اتصال" -> "📞"
                        "واتساب" -> "💬"
                        "زيارة" -> "🏠"
                        "اجتماع" -> "🤝"
                        "بريد إلكتروني" -> "✉️"
                        else -> "📝"
                    }
                    val prevChip = a.previousStatus?.let {
                        val bg = statusColor(it); val tx = statusTextColor(it)
                        """<span class="inline-block px-2 py-0.5 rounded-full text-xs" style="background:$bg;color:$tx">$it</span>"""
                    } ?: ""
                    val newChip = a.newStatus?.let {
                        val bg = statusColor(it); val tx = statusTextColor(it)
                        """<span class="inline-block px-2 py-0.5 rounded-full text-xs" style="background:$bg;color:$tx">$it</span>"""
                    } ?: ""
                    val transition = if (a.previousStatus != null && a.newStatus != null && a.previousStatus != a.newStatus)
                        """<div class="flex items-center gap-2 mt-2 flex-wrap">$prevChip <span class="text-gray-400">←</span> $newChip</div>"""
                    else if (a.newStatus != null)
                        """<div class="mt-2">$newChip</div>"""
                    else ""
                    val notesRow = a.notes?.takeIf { it.isNotBlank() }?.let {
                        """<div class="mt-2 p-2 bg-gray-50 rounded text-xs text-gray-600">"$it"</div>"""
                    } ?: ""
                    val nextStepRow = a.nextStep?.takeIf { it.isNotBlank() }?.let {
                        """<div class="mt-2 text-xs text-emerald-700 flex items-center gap-1">
                            <span>➡️</span><span>${it} ${a.nextDate?.let { d -> "— $d" } ?: ""}</span>
                        </div>"""
                    } ?: ""
                    val amountChip = a.amount?.takeIf { it > 0 }?.let {
                        """<span class="inline-block px-2 py-0.5 rounded-full text-xs bg-emerald-50 text-emerald-700 font-medium">${String.format("%,.0f", it)} ج.م</span>"""
                    } ?: ""

                    append("""
                        <div class="relative mb-5 pr-6">
                            <div class="absolute right-0 top-2 w-4 h-4 rounded-full border-2 border-white shadow" style="background:$dotColor"></div>
                            <div class="bg-white rounded-lg border border-gray-100 p-3 shadow-sm hover:shadow-md transition">
                                <div class="flex items-start justify-between gap-2">
                                    <div class="flex items-center gap-2 min-w-0">
                                        <span class="text-xl">$channelIcon</span>
                                        <div class="min-w-0">
                                            <div class="font-medium text-sm text-gray-800 truncate">${a.actionType ?: "نشاط"} ${a.channel?.let { "· $it" } ?: ""}</div>
                                            <div class="text-xs text-gray-500">${formatCrmDate(a.createdAt)} · ${a.agentName}</div>
                                        </div>
                                    </div>
                                    $amountChip
                                </div>
                                $transition
                                ${a.result?.takeIf { it.isNotBlank() }?.let { """<div class="mt-2 text-xs text-gray-600"><strong>النتيجة:</strong> $it</div>""" } ?: ""}
                                $notesRow
                                $nextStepRow
                            </div>
                        </div>
                    """)
                }
                append("</div>")
            }

            // Invoices section.
            val invoicesHtml = if (invoices.isEmpty()) """
                <div class="text-center py-6 text-gray-400 text-sm">لا توجد فواتير</div>
            """ else buildString {
                append("""<div class="overflow-x-auto"><table class="w-full text-sm">
                    <thead><tr class="border-b">
                        <th class="p-2 text-right text-xs text-gray-500">رقم الفاتورة</th>
                        <th class="p-2 text-right text-xs text-gray-500">الفترة</th>
                        <th class="p-2 text-right text-xs text-gray-500">المبلغ</th>
                        <th class="p-2 text-right text-xs text-gray-500">المدفوع</th>
                        <th class="p-2 text-right text-xs text-gray-500">الحالة</th>
                    </tr></thead><tbody>""")
                invoices.forEach { inv ->
                    val statusClr = when (inv.status) {
                        "مدفوع" -> "bg-emerald-100 text-emerald-700"
                        "متأخر" -> "bg-red-100 text-red-700"
                        "جزئي" -> "bg-amber-100 text-amber-700"
                        else -> "bg-gray-100 text-gray-700"
                    }
                    append("""<tr class="border-b hover:bg-gray-50">
                        <td class="p-2 font-mono text-xs">${inv.invoiceNumber}</td>
                        <td class="p-2">${inv.period}</td>
                        <td class="p-2 font-medium">${String.format("%,.0f", inv.finalAmount)} ج.م</td>
                        <td class="p-2 text-emerald-700">${String.format("%,.0f", inv.paidAmount)} ج.م</td>
                        <td class="p-2"><span class="px-2 py-0.5 rounded-full text-xs $statusClr">${inv.status}</span></td>
                    </tr>""")
                }
                append("</tbody></table></div>")
            }

            val canEdit = principal.canSeeAll || client.assignedTo == principal.agentId

            // Action buttons — only shown to users with permission on this record.
            val actionButtons = buildString {
                append("""<a href="$waUrl" target="_blank" class="flex items-center gap-2 px-4 py-2 bg-emerald-600 text-white rounded-lg hover:bg-emerald-700 transition text-sm font-medium">
                    <span>💬</span><span>واتساب</span>
                </a>""")
                append("""<a href="tel:${client.phone}" class="flex items-center gap-2 px-4 py-2 bg-sky-600 text-white rounded-lg hover:bg-sky-700 transition text-sm font-medium">
                    <span>📞</span><span>اتصال</span>
                </a>""")
                if (canEdit) {
                    append("""<button onclick="openEditClient('${client.id}')" class="flex items-center gap-2 px-4 py-2 bg-white border border-gray-200 rounded-lg hover:bg-gray-50 transition text-sm font-medium text-gray-700">
                        <span>✏️</span><span>تعديل</span>
                    </button>""")
                    append("""<button onclick="document.getElementById('addActivityModal').showModal()" class="flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition text-sm font-medium">
                        <span>＋</span><span>إضافة نشاط</span>
                    </button>""")
                    append("""<button onclick="deleteClient()" class="flex items-center gap-2 px-4 py-2 bg-red-50 text-red-600 border border-red-200 rounded-lg hover:bg-red-100 transition text-sm font-medium">
                        <span>🗑️</span><span>حذف</span>
                    </button>""")
                }
            }

            // For the client-details page, build a one-element JSON array so the combobox
            // opens pre-filled with just this client. The search field is still active —
            // if the user realises they're on the wrong page they can filter/retype, but
            // with one entry that's effectively a no-op.
            val clientsJsonForActivity = JsonArray(listOf(
                buildJsonObject {
                    put("id", client.id)
                    put("name", client.clientName)
                    put("phone", client.phone)
                }
            )).toString()

            val finalAmountFormatted = String.format("%,.0f", client.finalAmount)
            val monthlyFormatted = String.format("%,.0f", client.monthlyAmount)

            val content = """
                <!-- Back link + breadcrumbs -->
                <div class="flex items-center gap-2 text-sm text-gray-500 mb-4">
                    <a href="/crm/clients" class="hover:text-emerald-600 transition flex items-center gap-1">
                        <span>←</span><span>العودة إلى العملاء</span>
                    </a>
                </div>

                <!-- Hero header card -->
                <div class="bg-gradient-to-l from-slate-900 via-slate-800 to-slate-700 rounded-2xl p-6 mb-6 text-white shadow-lg relative overflow-hidden">
                    <!-- subtle pattern -->
                    <div class="absolute inset-0 opacity-5" style="background-image: radial-gradient(circle at 20% 20%, white 1px, transparent 1px); background-size: 30px 30px;"></div>
                    <div class="relative flex flex-col lg:flex-row lg:items-center gap-6">
                        <!-- Avatar -->
                        <div class="flex-shrink-0">
                            <div class="w-20 h-20 rounded-2xl flex items-center justify-center text-3xl font-bold text-white shadow-xl" style="background:$avatarColor">
                                $initial
                            </div>
                        </div>
                        <!-- Main info -->
                        <div class="flex-1 min-w-0">
                            <div class="flex items-center gap-2 flex-wrap mb-1">
                                <h1 class="text-2xl lg:text-3xl font-bold truncate">${client.clientName}</h1>
                                <span class="px-3 py-1 rounded-full text-xs font-medium" style="background:$statusBg;color:$statusTxt">${client.status}</span>
                                ${client.plan?.let { """<span class="px-3 py-1 rounded-full text-xs font-medium bg-white/10 text-white backdrop-blur">${it}</span>""" } ?: ""}
                            </div>
                            <div class="text-sm text-white/70 flex items-center gap-4 flex-wrap">
                                <span class="flex items-center gap-1">📞 ${client.phone}</span>
                                ${client.businessName?.let { """<span class="flex items-center gap-1">🏢 ${it}</span>""" } ?: ""}
                                ${client.governorate?.let { """<span class="flex items-center gap-1">📍 ${it}</span>""" } ?: ""}
                                ${client.assignedName?.let { """<span class="flex items-center gap-1">👤 ${it}</span>""" } ?: ""}
                            </div>
                        </div>
                        <!-- Actions -->
                        <div class="flex items-center gap-2 flex-wrap">
                            $actionButtons
                        </div>
                    </div>
                </div>

                <!-- KPI row -->
                <div class="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
                    <div class="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
                        <div class="flex items-center justify-between">
                            <div>
                                <p class="text-xs text-gray-500">إجمالي الأنشطة</p>
                                <p class="text-2xl font-bold mt-1 text-gray-800">$totalActivities</p>
                            </div>
                            <div class="w-10 h-10 rounded-lg bg-indigo-50 flex items-center justify-center text-indigo-600 text-xl">📋</div>
                        </div>
                    </div>
                    <div class="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
                        <div class="flex items-center justify-between">
                            <div>
                                <p class="text-xs text-gray-500">آخر تواصل</p>
                                <p class="text-lg font-bold mt-1 text-gray-800">$lastContactLabel</p>
                            </div>
                            <div class="w-10 h-10 rounded-lg bg-sky-50 flex items-center justify-center text-sky-600 text-xl">⏱️</div>
                        </div>
                    </div>
                    <div class="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
                        <div class="flex items-center justify-between">
                            <div>
                                <p class="text-xs text-gray-500">القيمة الشهرية</p>
                                <p class="text-xl font-bold mt-1 text-emerald-600">$finalAmountFormatted ج.م</p>
                            </div>
                            <div class="w-10 h-10 rounded-lg bg-emerald-50 flex items-center justify-center text-emerald-600 text-xl">💰</div>
                        </div>
                    </div>
                    <div class="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
                        <div class="flex items-center justify-between">
                            <div>
                                <p class="text-xs text-gray-500">عميل منذ</p>
                                <p class="text-2xl font-bold mt-1 text-gray-800">$daysAsClient <span class="text-sm font-normal">يوم</span></p>
                            </div>
                            <div class="w-10 h-10 rounded-lg bg-amber-50 flex items-center justify-center text-amber-600 text-xl">📅</div>
                        </div>
                    </div>
                </div>

                <!-- Two-column grid -->
                <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
                    <!-- Left: client info + financial -->
                    <div class="lg:col-span-1 space-y-6">
                        <div class="bg-white rounded-2xl p-5 shadow-sm border border-gray-100">
                            <div class="flex items-center justify-between mb-3">
                                <h3 class="font-bold text-gray-800 flex items-center gap-2"><span>📇</span><span>معلومات العميل</span></h3>
                            </div>
                            ${infoRow("الهاتف", client.phone, "📞")}
                            ${infoRow("واتساب", if (client.whatsapp) "مفعّل" else "غير مفعّل", "💬")}
                            ${infoRow("اسم النشاط", client.businessName, "🏢")}
                            ${infoRow("نوع النشاط", client.businessType, "🏷️")}
                            ${infoRow("المدينة", client.city, "📍")}
                            ${infoRow("المحافظة", client.governorate, "🗺️")}
                            ${infoRow("مصدر العميل", client.source, "🧭")}
                            ${infoRow("الموظف المسؤول", client.assignedName, "👤")}
                            ${infoRow("تاريخ الإضافة", formatCrmDate(client.createdAt), "🗓️")}
                            ${infoRow("آخر تعديل", formatCrmDate(client.updatedAt), "✏️")}
                            ${infoRow("الإجراء القادم", client.nextActionDate, "➡️")}
                        </div>

                        <div class="bg-white rounded-2xl p-5 shadow-sm border border-gray-100">
                            <h3 class="font-bold text-gray-800 mb-3 flex items-center gap-2"><span>💳</span><span>الباقة والدفع</span></h3>
                            ${infoRow("الباقة", client.plan, "📦")}
                            ${infoRow("المبلغ الشهري", "$monthlyFormatted ج.م", "💵")}
                            ${infoRow("نسبة الخصم", "${client.discountPercent}%", "🏷️")}
                            ${infoRow("المبلغ النهائي", "$finalAmountFormatted ج.م", "💰")}
                            ${infoRow("طريقة الدفع", client.paymentMethod, "💳")}
                        </div>

                        ${if (!client.notes.isNullOrBlank()) """
                        <div class="bg-amber-50 rounded-2xl p-5 border border-amber-100">
                            <h3 class="font-bold text-amber-900 mb-2 flex items-center gap-2"><span>📝</span><span>ملاحظات</span></h3>
                            <p class="text-sm text-amber-900 whitespace-pre-wrap">${client.notes}</p>
                        </div>""" else ""}
                    </div>

                    <!-- Right: timeline + invoices -->
                    <div class="lg:col-span-2 space-y-6">
                        <div class="bg-white rounded-2xl p-5 shadow-sm border border-gray-100">
                            <div class="flex items-center justify-between mb-4">
                                <h3 class="font-bold text-gray-800 flex items-center gap-2"><span>📜</span><span>سجل الأنشطة</span></h3>
                                <span class="text-sm text-gray-500">${totalActivities} نشاط</span>
                            </div>
                            $timelineHtml
                        </div>

                        <div class="bg-white rounded-2xl p-5 shadow-sm border border-gray-100">
                            <div class="flex items-center justify-between mb-3">
                                <h3 class="font-bold text-gray-800 flex items-center gap-2"><span>🧾</span><span>الفواتير</span></h3>
                                <div class="text-sm text-gray-500">
                                    مدفوع: <span class="text-emerald-600 font-medium">${String.format("%,.0f", totalPaid)} ج.م</span>
                                    ${if (totalOutstanding > 0) """· متبقي: <span class="text-red-600 font-medium">${String.format("%,.0f", totalOutstanding)} ج.م</span>""" else ""}
                                </div>
                            </div>
                            $invoicesHtml
                        </div>
                    </div>
                </div>

                ${if (canEdit) editClientModalHtml(agentOptions, principal.canSeeAll) else ""}
                ${if (canEdit) addActivityModalHtml(clientsJsonForActivity, preselectedClientId = client.id) else ""}

                <script>
                async function openEditClient(id) {
                    const res = await fetch('/crm/api/clients');
                    const clients = await res.json();
                    const c = clients.find(x => x.id === id);
                    if (!c) return alert('عميل غير موجود');
                    document.getElementById('edit_id').value = c.id;
                    document.getElementById('edit_clientName').value = c.clientName || '';
                    document.getElementById('edit_phone').value = c.phone || '';
                    document.getElementById('edit_whatsapp').checked = c.whatsapp || false;
                    document.getElementById('edit_businessName').value = c.businessName || '';
                    document.getElementById('edit_businessType').value = c.businessType || '';
                    document.getElementById('edit_city').value = c.city || '';
                    document.getElementById('edit_governorate').value = c.governorate || '';
                    document.getElementById('edit_status').value = c.status || '';
                    document.getElementById('edit_plan').value = c.plan || '';
                    document.getElementById('edit_monthlyAmount').value = c.monthlyAmount || 0;
                    document.getElementById('edit_discountPercent').value = c.discountPercent || 0;
                    document.getElementById('edit_paymentMethod').value = c.paymentMethod || '';
                    document.getElementById('edit_source').value = c.source || '';
                    document.getElementById('edit_notes').value = c.notes || '';
                    ${if (principal.canSeeAll) "document.getElementById('edit_assignedTo').value = c.assignedTo || '';" else ""}
                    document.getElementById('editClientModal').showModal();
                }
                async function submitEditClient(e) {
                    e.preventDefault();
                    const form = e.target;
                    const data = Object.fromEntries(new FormData(form));
                    const id = data.id; delete data.id;
                    data.whatsapp = form.querySelector('[name=whatsapp]').checked;
                    data.monthlyAmount = parseFloat(data.monthlyAmount) || 0;
                    data.discountPercent = parseInt(data.discountPercent) || 0;
                    const res = await fetch('/crm/api/clients/' + id, {method:'PUT', headers:{'Content-Type':'application/json'}, body:JSON.stringify(data)});
                    if (res.ok) { location.reload(); } else { alert('حدث خطأ'); }
                }
                async function submitNewActivity(e) {
                    e.preventDefault();
                    const form = e.target;
                    const data = Object.fromEntries(new FormData(form));
                    data.amount = parseFloat(data.amount) || 0;
                    data.discountPercent = parseInt(data.discountPercent) || 0;
                    const res = await fetch('/crm/api/activities', {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(data)});
                    if (res.ok) { location.reload(); } else { alert('حدث خطأ'); }
                }
                async function deleteClient() {
                    if (!confirm('هل أنت متأكد من حذف العميل ${client.clientName.replace("'", "\\'")}?\nسيتم حذف كل بياناته وفواتيره.')) return;
                    const res = await fetch('/crm/api/clients/${client.id}', {method:'DELETE'});
                    if (res.ok) { window.location.href = '/crm/clients'; } else { const d = await res.json(); alert(d.error || 'حدث خطأ'); }
                }
                </script>
            """.trimIndent()

            call.respondText(
                crmLayout(client.clientName, principal.name, principal.role, principal, "clients", content),
                ContentType.Text.Html
            )
        }

        // ─── Activities Page ────────────────────────────────────
        get("/crm/activities") {
            val principal = call.principal<CrmPrincipal>()!!
            val activities = crmService.listActivities(principal.organizationId, principal.agentId, principal.canSeeAll)
            val clients = crmService.listClients(principal.organizationId, principal.agentId, principal.canSeeAll)

            // Consistent hashed avatar palette — same agent always gets the same color
            // across clients list, details page, activities, and their profile.
            val avatarPalette = listOf("#0EA5E9", "#10B981", "#F59E0B", "#EF4444", "#8B5CF6", "#EC4899", "#14B8A6", "#F97316")

            // Channel glyph map used across the new CRM UI — keeps the timeline, list
            // and summaries consistent.
            fun channelIcon(ch: String?): String = when (ch) {
                "مكالمة تليفون", "اتصال" -> "📞"
                "واتساب" -> "💬"
                "زيارة" -> "🏠"
                "اجتماع", "فيديو كول" -> "🤝"
                "بريد إلكتروني" -> "✉️"
                "رسالة SMS" -> "📩"
                else -> "📝"
            }

            // Today's midnight in local TZ — used to bucket today's activities for the KPI.
            val tzNow = Clock.System.now().toLocalDateTime(CRM_TZ)
            val todayStart = kotlinx.datetime.LocalDateTime(tzNow.year, tzNow.month, tzNow.dayOfMonth, 0, 0)
                .toInstant(CRM_TZ).toEpochMilliseconds()
            val sevenDaysAgo = Clock.System.now().toEpochMilliseconds() - 7L * 86_400_000L
            val activitiesToday = activities.count { it.createdAt >= todayStart }
            val activitiesWeek = activities.count { it.createdAt >= sevenDaysAgo }
            val topChannel = activities.mapNotNull { it.channel }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            val topResult = activities.mapNotNull { it.result }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

            // Fast lookup from clientId → phone. Activities don't carry the phone
            // themselves (the DTO has clientName only), so we derive it from the clients
            // list we already fetched above. Used both for display and for the search
            // filter (data-client-phone attribute below).
            val phonesByClientId = clients.associate { it.id to it.phone }

            val tableRows = buildString {
                activities.forEach { a ->
                    // Agent may act on their own activities; manager/owner may act on any.
                    val canEdit = principal.canSeeAll || a.agentId == principal.agentId
                    val agentInitial = a.agentName.trim().firstOrNull()?.toString() ?: "؟"
                    val agentColor = avatarPalette[(a.agentName.hashCode().let { if (it < 0) -it else it }) % avatarPalette.size]
                    val icon = channelIcon(a.channel)
                    val escapedClient = a.clientName.replace("'", "\\'")

                    // Colored side border tints the row by channel for quick scanning.
                    val railColor = when (a.channel) {
                        "مكالمة تليفون", "اتصال" -> "border-r-sky-400"
                        "واتساب" -> "border-r-emerald-400"
                        "زيارة" -> "border-r-amber-400"
                        "اجتماع", "فيديو كول" -> "border-r-indigo-400"
                        "بريد إلكتروني" -> "border-r-rose-400"
                        else -> "border-r-gray-200"
                    }

                    // Row data attributes feed the smart filter panel (date range, amount
                    // range, multi-select on channel/action/result/agent).
                    val nextMs = a.nextDate?.let {
                        try { kotlinx.datetime.LocalDate.parse(it).atStartOfDayIn(CRM_TZ).toEpochMilliseconds() } catch (_: Throwable) { 0L }
                    } ?: 0L
                    val clientPhone = phonesByClientId[a.clientId].orEmpty()
                    append("""<tr class="border-b border-gray-100 border-r-4 $railColor hover:bg-gray-50 transition"
                        data-created-ms="${a.createdAt}"
                        data-next-ms="$nextMs"
                        data-amount="${a.amount ?: 0.0}"
                        data-discount="${a.discountPercent ?: 0}"
                        data-channel="${a.channel ?: ""}"
                        data-action-type="${a.actionType ?: ""}"
                        data-result="${a.result ?: ""}"
                        data-new-status="${a.newStatus ?: ""}"
                        data-agent-id="${a.agentId}"
                        data-agent-name="${a.agentName}"
                        data-client-id="${a.clientId}"
                        data-client-name="${a.clientName}"
                        data-client-phone="$clientPhone">""")
                    // 0. Date (formatted)
                    append("""<td class='p-3 align-top'>
                        <div class="text-xs font-medium text-gray-700 whitespace-nowrap">${formatCrmDate(a.createdAt)}</div>
                    </td>""")
                    // 1. Agent — avatar + name; link to profile for everyone
                    append("""<td class='p-3 align-top'>
                        <a href="/crm/profile/${a.agentId}" class="inline-flex items-center gap-2 group">
                            <span class="w-8 h-8 rounded-full flex items-center justify-center text-white text-sm font-semibold flex-shrink-0" style="background:$agentColor">$agentInitial</span>
                            <span class="text-sm text-gray-800 group-hover:text-emerald-600 group-hover:underline">${a.agentName}</span>
                        </a>
                    </td>""")
                    // 2. Client — link to details, with phone under the name so cashiers
                    // and managers can scan by phone and the search filter matches on it.
                    append("""<td class='p-3 align-top'>
                        <a href="/crm/clients/${a.clientId}" class="text-sm font-medium text-gray-800 hover:text-emerald-600 hover:underline">${a.clientName}</a>
                        ${if (clientPhone.isNotBlank()) """<div class="text-xs text-gray-500 whitespace-nowrap" dir="ltr">📞 $clientPhone</div>""" else ""}
                    </td>""")
                    // 3. Action type
                    append("""<td class='p-3 align-top'>
                        <span class="inline-block px-2 py-1 rounded-md text-xs bg-gray-100 text-gray-700">${a.actionType ?: "-"}</span>
                    </td>""")
                    // 4. Channel (icon + label)
                    append("""<td class='p-3 align-top'>
                        <span class="inline-flex items-center gap-1.5 text-sm text-gray-700">
                            <span class="text-lg">$icon</span><span>${a.channel ?: "-"}</span>
                        </span>
                    </td>""")
                    // 5. Status transition (prev ← new) or just new
                    val statusCell = buildString {
                        if (a.previousStatus != null && a.newStatus != null && a.previousStatus != a.newStatus) {
                            val pBg = statusColor(a.previousStatus); val pTx = statusTextColor(a.previousStatus)
                            val nBg = statusColor(a.newStatus); val nTx = statusTextColor(a.newStatus)
                            append("""<div class="flex items-center gap-1.5 flex-wrap">
                                <span class="px-2 py-0.5 rounded-full text-xs" style="background:$pBg;color:$pTx">${a.previousStatus}</span>
                                <span class="text-gray-400 text-xs">←</span>
                                <span class="px-2 py-0.5 rounded-full text-xs" style="background:$nBg;color:$nTx">${a.newStatus}</span>
                            </div>""")
                        } else if (a.newStatus != null) {
                            val nBg = statusColor(a.newStatus); val nTx = statusTextColor(a.newStatus)
                            append("""<span class="inline-block px-2 py-0.5 rounded-full text-xs" style="background:$nBg;color:$nTx">${a.newStatus}</span>""")
                        } else {
                            append("""<span class="text-gray-400 text-xs">-</span>""")
                        }
                    }
                    append("<td class='p-3 align-top'>$statusCell</td>")
                    // 6. Result
                    append("<td class='p-3 align-top text-sm text-gray-700'>${a.result ?: "-"}</td>")
                    // 7. Notes (truncated with tooltip)
                    val notesCell = a.notes?.takeIf { it.isNotBlank() }?.let {
                        val truncated = if (it.length > 60) it.take(60) + "…" else it
                        """<div class="text-xs text-gray-600 max-w-xs" title="${it.replace("\"", "&quot;")}">"$truncated"</div>"""
                    } ?: """<span class="text-gray-400 text-xs">-</span>"""
                    append("<td class='p-3 align-top'>$notesCell</td>")
                    // 8. Next action
                    val nextCell = a.nextDate?.let {
                        val step = a.nextStep?.takeIf { s -> s.isNotBlank() }?.let { s -> " — $s" } ?: ""
                        """<span class="inline-flex items-center gap-1 text-xs text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded-full whitespace-nowrap">➡️ $it$step</span>"""
                    } ?: """<span class="text-gray-400 text-xs">-</span>"""
                    append("<td class='p-3 align-top'>$nextCell</td>")
                    // 9. Actions
                    if (canEdit) {
                        append("""<td class='p-3 align-top'>
                            <div class="flex items-center gap-1 justify-end">
                                <button onclick="openEditActivity('${a.id}')" class="p-1.5 rounded hover:bg-blue-50 text-blue-600 transition" title="تعديل">✏️</button>
                                <button onclick="deleteActivity('${a.id}', '$escapedClient')" class="p-1.5 rounded hover:bg-red-50 text-red-600 transition" title="حذف">🗑️</button>
                            </div>
                        </td>""")
                    } else {
                        append("<td class='p-3'></td>")
                    }
                    append("</tr>")
                }
            }

            // Two shapes of the clients list are needed here:
            //  - A JSON array for the NEW searchable combobox in the add-activity modal
            //    (lets the cashier filter by name or phone when there are hundreds of clients).
            //  - The legacy `<option>` HTML for the edit-activity modal, whose client field
            //    is disabled-and-locked so a searchable combobox would add no value.
            val clientsJson = JsonArray(clients.map { c ->
                buildJsonObject {
                    put("id", c.id)
                    put("name", c.clientName)
                    put("phone", c.phone)
                }
            }).toString()
            val clientOptions = clients.joinToString("") { """<option value="${it.id}">${it.clientName} - ${it.phone}</option>""" }

            val actionTypes = listOf("أول اتصال", "متابعة", "عرض توضيحي", "تفاوض", "إغلاق صفقة", "إعادة تنشيط", "دعم فني", "شكوى")
            val channels = listOf("مكالمة تليفون", "واتساب", "زيارة", "فيديو كول", "رسالة SMS")
            val results = listOf("مهتم", "غير مهتم", "طلب يرجعله", "ديمو محجوز", "بدأ تجربة", "استلم الدفع", "اشتراك مؤكد", "مردش", "مشغول", "رقم غلط")
            val activityAgents = activities.map { it.agentName }.distinct()

            val content = """
                <!-- Hero header -->
                <div class="flex items-center justify-between mb-6 flex-wrap gap-3">
                    <div>
                        <h2 class="text-2xl font-bold text-gray-800">الأنشطة</h2>
                        <p class="text-sm text-gray-500 mt-1">سجل كل التواصلات والإجراءات مع العملاء</p>
                    </div>
                    <button onclick="document.getElementById('addActivityModal').showModal()" class="flex items-center gap-2 bg-emerald-600 hover:bg-emerald-700 text-white px-5 py-2.5 rounded-xl shadow-sm transition font-medium">
                        <span class="text-lg">＋</span><span>إضافة نشاط</span>
                    </button>
                </div>

                <!-- KPI summary strip -->
                <div class="grid grid-cols-2 lg:grid-cols-4 gap-3 mb-5">
                    <div class="bg-white rounded-xl p-4 border border-gray-100 shadow-sm">
                        <div class="flex items-center justify-between">
                            <div>
                                <p class="text-xs text-gray-500">إجمالي الأنشطة</p>
                                <p class="text-2xl font-bold text-gray-800 mt-1"><span id="visibleCount">${activities.size}</span> <span class="text-sm font-normal text-gray-400">/ ${activities.size}</span></p>
                            </div>
                            <div class="w-10 h-10 rounded-lg bg-slate-100 flex items-center justify-center text-xl">📋</div>
                        </div>
                    </div>
                    <div class="bg-white rounded-xl p-4 border border-gray-100 shadow-sm">
                        <div class="flex items-center justify-between">
                            <div>
                                <p class="text-xs text-gray-500">نشاط اليوم</p>
                                <p class="text-2xl font-bold text-emerald-600 mt-1">$activitiesToday</p>
                                <p class="text-xs text-gray-400 mt-0.5">آخر 7 أيام: $activitiesWeek</p>
                            </div>
                            <div class="w-10 h-10 rounded-lg bg-emerald-50 flex items-center justify-center text-xl">⚡</div>
                        </div>
                    </div>
                    <div class="bg-white rounded-xl p-4 border border-gray-100 shadow-sm">
                        <div class="flex items-center justify-between">
                            <div>
                                <p class="text-xs text-gray-500">القناة الأكثر استخدامًا</p>
                                <p class="text-xl font-bold text-sky-600 mt-1 flex items-center gap-1">${channelIcon(topChannel)} <span>${topChannel ?: "-"}</span></p>
                            </div>
                            <div class="w-10 h-10 rounded-lg bg-sky-50 flex items-center justify-center text-xl">📡</div>
                        </div>
                    </div>
                    <div class="bg-white rounded-xl p-4 border border-gray-100 shadow-sm">
                        <div class="flex items-center justify-between">
                            <div>
                                <p class="text-xs text-gray-500">النتيجة الأكثر تكرارًا</p>
                                <p class="text-lg font-bold text-amber-700 mt-1">${topResult ?: "-"}</p>
                            </div>
                            <div class="w-10 h-10 rounded-lg bg-amber-50 flex items-center justify-center text-xl">🎯</div>
                        </div>
                    </div>
                </div>

                ${smartFilterPanelHtml(
                    searchPlaceholder = "بحث بالاسم أو رقم الهاتف أو الموظف أو الملاحظات...",
                    primaryDateRange = SmartDateRange("data-created-ms", "تاريخ النشاط", "actCreated"),
                    secondaryDateRange = SmartDateRange("data-next-ms", "تاريخ الإجراء القادم", "actNext"),
                    numberRange = SmartNumberRange("data-amount", "المبلغ", "actAmount", "ج.م"),
                    multiSelects = buildList {
                        add(SmartMultiSelect("data-action-type", "نوع الإجراء", "actionType", actionTypes))
                        add(SmartMultiSelect("data-channel", "القناة", "channel", channels))
                        add(SmartMultiSelect("data-result", "النتيجة", "result", results))
                        add(SmartMultiSelect("data-new-status", "الحالة الجديدة", "newStatus",
                            listOf("عميل جديد", "متابعة", "ديمو محجوز", "يحتاج مناقشة", "تجربة فعالة", "تجربة منتهية", "تفاوض", "مدفوع", "مشترك", "رفض", "نشاط غير مناسب", "توقف")))
                        if (principal.canSeeAll && activityAgents.isNotEmpty()) {
                            add(SmartMultiSelect("data-agent-name", "الموظف", "agentName", activityAgents))
                        }
                    },
                    presets = listOf(
                        SmartPreset("today", "اليوم", "presetRangeDays('data-created-ms', 1, true)", "📅"),
                        SmartPreset("week", "آخر 7 أيام", "presetRangeDays('data-created-ms', 7, true)", "📆"),
                        SmartPreset("month", "آخر 30 يوم", "presetRangeDays('data-created-ms', 30, true)", "🗓️"),
                        SmartPreset("whatsapp", "واتساب فقط", "document.querySelector('.smart-ms-channel[value=\"واتساب\"]').checked = true; updateMultiSelect('channel'); applySmartFilters()", "💬"),
                        SmartPreset("calls", "مكالمات فقط", "document.querySelector('.smart-ms-channel[value=\"مكالمة تليفون\"]').checked = true; updateMultiSelect('channel'); applySmartFilters()", "📞"),
                    )
                )}
                <div class="bg-white rounded-xl shadow-sm overflow-x-auto border border-gray-100">
                    <table id="dataTable" class="w-full text-sm">
                        <thead>
                            <tr class="bg-gray-50 border-b">
                                <th class="p-3 text-right text-xs text-gray-500 uppercase tracking-wider font-medium">التاريخ</th>
                                <th class="p-3 text-right text-xs text-gray-500 uppercase tracking-wider font-medium">الموظف</th>
                                <th class="p-3 text-right text-xs text-gray-500 uppercase tracking-wider font-medium">العميل</th>
                                <th class="p-3 text-right text-xs text-gray-500 uppercase tracking-wider font-medium">الإجراء</th>
                                <th class="p-3 text-right text-xs text-gray-500 uppercase tracking-wider font-medium">القناة</th>
                                <th class="p-3 text-right text-xs text-gray-500 uppercase tracking-wider font-medium">تغيير الحالة</th>
                                <th class="p-3 text-right text-xs text-gray-500 uppercase tracking-wider font-medium">النتيجة</th>
                                <th class="p-3 text-right text-xs text-gray-500 uppercase tracking-wider font-medium">ملاحظات</th>
                                <th class="p-3 text-right text-xs text-gray-500 uppercase tracking-wider font-medium">التالي</th>
                                <th class="p-3 text-right text-xs text-gray-500 uppercase tracking-wider font-medium"></th>
                            </tr>
                        </thead>
                        <tbody>$tableRows</tbody>
                    </table>
                    ${if (activities.isEmpty()) """<div class="text-center py-16">
                        <div class="text-6xl mb-3">📭</div>
                        <p class="text-gray-500 mb-4">لا توجد أنشطة مسجلة بعد</p>
                        <button onclick="document.getElementById('addActivityModal').showModal()" class="bg-emerald-600 hover:bg-emerald-700 text-white px-4 py-2 rounded-lg">سجّل أول نشاط</button>
                    </div>""" else ""}
                </div>
                ${smartFilterScript()}

                ${addActivityModalHtml(clientsJson)}
                ${editActivityModalHtml(clientOptions)}

                <script>
                // Client data for auto-fill when selecting a client in activity form
                const clientsData = {
                    ${clients.joinToString(",\n") { c -> """
                    '${c.id}': {
                        name: '${c.clientName.replace("'", "\\'")}',
                        phone: '${c.phone}',
                        status: '${c.status}',
                        plan: '${c.plan ?: ""}',
                        amount: ${c.monthlyAmount},
                        discount: ${c.discountPercent},
                        businessName: '${(c.businessName ?: "").replace("'", "\\'")}'
                    }""" }}
                };

                function onClientSelected(select) {
                    const c = clientsData[select.value];
                    if (!c) return;
                    const form = select.closest('form');
                    const setVal = (name, val) => { const el = form.querySelector('[name="'+name+'"]'); if(el) el.value = val; };
                    setVal('previousStatus', c.status);
                    setVal('newStatus', c.status);
                    setVal('planOffered', c.plan);
                    setVal('amount', c.amount);
                    setVal('discountPercent', c.discount);
                }

                async function submitNewActivity(e) {
                    e.preventDefault();
                    const form = e.target;
                    const data = Object.fromEntries(new FormData(form));
                    data.amount = parseFloat(data.amount) || 0;
                    data.discountPercent = parseInt(data.discountPercent) || 0;
                    const res = await fetch('/crm/api/activities', {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(data)});
                    if (res.ok) { location.reload(); } else { alert('حدث خطأ'); }
                }

                async function openEditActivity(id) {
                    const res = await fetch('/crm/api/activities');
                    const activities = await res.json();
                    const a = activities.find(x => x.id === id);
                    if (!a) return alert('نشاط غير موجود');
                    document.getElementById('edit_activity_id').value = a.id;
                    document.getElementById('edit_activity_clientId').value = a.clientId || '';
                    document.getElementById('edit_activity_actionType').value = a.actionType || '';
                    document.getElementById('edit_activity_channel').value = a.channel || '';
                    document.getElementById('edit_activity_previousStatus').value = a.previousStatus || '';
                    document.getElementById('edit_activity_newStatus').value = a.newStatus || '';
                    document.getElementById('edit_activity_planOffered').value = a.planOffered || '';
                    document.getElementById('edit_activity_amount').value = a.amount || 0;
                    document.getElementById('edit_activity_discountPercent').value = a.discountPercent || 0;
                    document.getElementById('edit_activity_callDuration').value = a.callDuration || '';
                    document.getElementById('edit_activity_result').value = a.result || '';
                    document.getElementById('edit_activity_nextStep').value = a.nextStep || '';
                    document.getElementById('edit_activity_nextDate').value = a.nextDate || '';
                    document.getElementById('edit_activity_notes').value = a.notes || '';
                    document.getElementById('editActivityModal').showModal();
                }

                async function submitEditActivity(e) {
                    e.preventDefault();
                    const form = e.target;
                    const data = Object.fromEntries(new FormData(form));
                    const id = data.id; delete data.id;
                    // clientId is not mutable via PUT (the row belongs to that client).
                    delete data.clientId;
                    data.amount = parseFloat(data.amount) || 0;
                    data.discountPercent = parseInt(data.discountPercent) || 0;
                    const res = await fetch('/crm/api/activities/' + id, {method:'PUT', headers:{'Content-Type':'application/json'}, body:JSON.stringify(data)});
                    if (res.ok) { location.reload(); } else { const d = await res.json().catch(()=>({})); alert(d.error || 'حدث خطأ'); }
                }

                async function deleteActivity(id, clientName) {
                    if (!confirm('هل أنت متأكد من حذف نشاط العميل ' + clientName + '؟')) return;
                    const res = await fetch('/crm/api/activities/' + id, {method:'DELETE'});
                    if (res.ok) { location.reload(); } else { const d = await res.json().catch(()=>({})); alert(d.error || 'حدث خطأ'); }
                }
                </script>
            """.trimIndent()

            call.respondText(
                crmLayout("الأنشطة", principal.name, principal.role, principal, "activities", content),
                ContentType.Text.Html
            )
        }

        // ─── Reports Page (Owner Only) ──────────────────────────
        get("/crm/reports") {
            val principal = call.principal<CrmPrincipal>()!!
            if (!principal.canSeeAnalytics) {
                call.respondRedirect("/crm/dashboard")
                return@get
            }
            val stats = crmService.getStats(principal.organizationId, null, true)

            val agentTableRows = buildString {
                stats.agentStats.forEach { a ->
                    val conversion = if (a.clients > 0) ((a.subscribed + a.paid) * 100.0 / a.clients) else 0.0
                    val photoHtml = agentPhotoHtml(a.photoUrl, a.agentName, 32)
                    append("<tr class='border-b hover:bg-gray-50'>")
                    append("<td class='p-2 font-medium'><div class='flex items-center gap-2'>$photoHtml<span>${a.agentName}</span></div></td>")
                    append("<td class='p-2'>${roleDisplayName(a.role)}</td>")
                    append("<td class='p-2'>${a.clients}</td>")
                    append("<td class='p-2'>${a.totalActivities}</td>")
                    append("<td class='p-2'>${a.subscribed}</td>")
                    append("<td class='p-2'>${a.paid}</td>")
                    append("<td class='p-2'>${String.format("%,.0f", a.revenue)} ج.م</td>")
                    append("<td class='p-2'>${String.format("%.1f", conversion)}%</td>")
                    append("</tr>")
                }
            }

            val content = """
                <div class="flex justify-between items-center mb-6">
                    <h2 class="text-xl font-bold">التقارير</h2>
                    <a href="/crm/api/export" class="bg-green-700 text-white px-4 py-2 rounded-lg hover:bg-green-800">تصدير Excel</a>
                </div>

                <div class="grid grid-cols-2 md:grid-cols-4 gap-4 mb-8">
                    ${kpiCard("إجمالي العملاء", stats.totalClients.toString(), "👥", "#1B3A5C")}
                    ${kpiCard("مشتركين", stats.subscribed.toString(), "✅", "#2E7D32")}
                    ${kpiCard("مدفوع", stats.paid.toString(), "💰", "#E65100")}
                    ${kpiCard("الإيراد الشهري", "${String.format("%,.0f", stats.monthlyRevenue)} ج.م", "📊", "#1B3A5C")}
                </div>

                <div class="bg-white rounded-xl shadow p-6 mb-8">
                    <h3 class="text-lg font-bold mb-4">أداء الموظفين</h3>
                    <div class="overflow-x-auto">
                        <table class="w-full text-sm">
                            <thead>
                                <tr class="bg-gray-100 border-b">
                                    <th class="p-2 text-right">الموظف</th>
                                    <th class="p-2 text-right">الدور</th>
                                    <th class="p-2 text-right">العملاء</th>
                                    <th class="p-2 text-right">الأنشطة</th>
                                    <th class="p-2 text-right">مشتركين</th>
                                    <th class="p-2 text-right">مدفوع</th>
                                    <th class="p-2 text-right">الإيراد</th>
                                    <th class="p-2 text-right">التحويل</th>
                                </tr>
                            </thead>
                            <tbody>$agentTableRows</tbody>
                        </table>
                    </div>
                </div>

                <div class="bg-white rounded-xl shadow p-6">
                    <h3 class="text-lg font-bold mb-4">توزيع الحالات</h3>
                    <div class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3">
                        ${stats.byStatus.entries.joinToString("") { (status, count) ->
                            val color = statusColor(status)
                            val textColor = statusTextColor(status)
                            """<div class="rounded-lg p-3 text-center" style="background:$color;color:$textColor"><div class="text-2xl font-bold">$count</div><div class="text-sm">$status</div></div>"""
                        }}
                    </div>
                </div>
            """.trimIndent()

            call.respondText(
                crmLayout("التقارير", principal.name, principal.role, principal, "reports", content),
                ContentType.Text.Html
            )
        }

        // ─── Team Page (Owner + Manager) ────────────────────────
        get("/crm/team") {
            val principal = call.principal<CrmPrincipal>()!!
            if (!principal.canSeeAll) {
                call.respondRedirect("/crm/dashboard")
                return@get
            }
            val stats = crmService.getStats(principal.organizationId, null, true)

            val agentCards = buildString {
                stats.agentStats.forEach { a ->
                    val conversion = if (a.clients > 0) ((a.subscribed + a.paid) * 100.0 / a.clients) else 0.0
                    val photoHtml = agentPhotoHtml(a.photoUrl, a.agentName, 48)
                    append("""
                        <div class="bg-white rounded-xl shadow p-6 filterable-card" data-role="${roleDisplayName(a.role)}">
                            <div class="flex justify-between items-start mb-4">
                                <div class="flex items-center gap-3">
                                    $photoHtml
                                    <div>
                                        <h3 class="font-bold text-lg">${a.agentName}</h3>
                                        <span class="text-sm text-gray-500">${roleDisplayName(a.role)}</span>
                                    </div>
                                </div>
                            </div>
                            <div class="grid grid-cols-2 gap-3 text-sm">
                                <div class="bg-gray-50 rounded p-2 text-center"><div class="font-bold text-lg">${a.clients}</div><div class="text-gray-500">عميل</div></div>
                                <div class="bg-gray-50 rounded p-2 text-center"><div class="font-bold text-lg">${a.totalActivities}</div><div class="text-gray-500">نشاط</div></div>
                                <div class="bg-green-50 rounded p-2 text-center"><div class="font-bold text-lg text-green-700">${a.subscribed}</div><div class="text-gray-500">مشترك</div></div>
                                <div class="bg-orange-50 rounded p-2 text-center"><div class="font-bold text-lg text-orange-700">${a.paid}</div><div class="text-gray-500">مدفوع</div></div>
                                <div class="bg-blue-50 rounded p-2 text-center col-span-2"><div class="font-bold text-lg text-blue-700">${String.format("%,.0f", a.revenue)} ج.م</div><div class="text-gray-500">الإيراد</div></div>
                            </div>
                            <div class="mt-3 text-center text-sm text-gray-500">معدل التحويل: <span class="font-bold">${String.format("%.1f", conversion)}%</span></div>
                            <div class="mt-3 text-center">
                                <a href="/crm/profile/${a.agentId}" class="text-blue-600 hover:underline text-sm">عرض الملف الشخصي</a>
                            </div>
                        </div>
                    """.trimIndent())
                }
            }

            val teamRoles = stats.agentStats.map { roleDisplayName(it.role) }.distinct()

            val content = """
                <h2 class="text-xl font-bold mb-4">أداء الفريق</h2>
                <div class="bg-white rounded-xl shadow p-4 mb-4">
                    <div class="flex flex-wrap gap-3 items-center">
                        <input id="searchInput" oninput="applyCardFilters()" type="text" placeholder="بحث بالاسم..." class="px-3 py-2 border rounded-lg text-sm w-full md:w-64">
                        <select class="filter-select px-3 py-2 border rounded-lg text-sm" data-field="role" onchange="applyCardFilters()">
                            <option value="">كل الأدوار</option>
                            ${teamRoles.joinToString("") { """<option value="$it">$it</option>""" }}
                        </select>
                        <button onclick="clearCardFilters()" class="text-sm text-red-600 hover:underline">مسح الفلاتر</button>
                    </div>
                </div>
                <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">$agentCards</div>
                ${filterScript()}
            """.trimIndent()

            call.respondText(
                crmLayout("الفريق", principal.name, principal.role, principal, "team", content),
                ContentType.Text.Html
            )
        }

        // ─── Settings Page (Owner Only) ─────────────────────────
        get("/crm/settings") {
            val principal = call.principal<CrmPrincipal>()!!
            if (!principal.canManageAgents) {
                call.respondRedirect("/crm/dashboard")
                return@get
            }
            val agents = crmService.listAgents(principal.organizationId)
            val ownBranding = crmService.getOrgBranding(principal.organizationId)

            val agentRows = buildString {
                agents.forEach { a ->
                    val statusBadge = if (a.active)
                        """<span class="bg-green-100 text-green-800 px-2 py-1 rounded-full text-xs">نشط</span>"""
                    else
                        """<span class="bg-red-100 text-red-800 px-2 py-1 rounded-full text-xs">معطل</span>"""
                    val photoHtml = agentPhotoHtml(a.photoUrl, a.name, 32)
                    append("<tr class='border-b hover:bg-gray-50'>")
                    append("<td class='p-2 font-medium'><div class='flex items-center gap-2'>$photoHtml<span>${a.name}</span></div></td>")
                    append("<td class='p-2'>${a.email}</td>")
                    append("<td class='p-2'>${roleDisplayName(a.role)}</td>")
                    append("<td class='p-2'>$statusBadge</td>")
                    append("""<td class='p-2'>
                        <div class='flex flex-wrap gap-1'>
                        ${if (a.role != "owner") """<button onclick="openEditAgent('${a.id}', '${a.name.replace("'","\\'")}', '${a.email}', '${a.role}')" class="text-sm px-2 py-1 rounded bg-blue-100 text-blue-700 hover:bg-blue-200">تعديل</button>""" else ""}
                        <button onclick="openResetPassword('${a.id}', '${a.name.replace("'","\\'")}') " class="text-sm px-2 py-1 rounded bg-yellow-100 text-yellow-700 hover:bg-yellow-200">كلمة السر</button>
                        <button onclick="toggleAgent('${a.id}', ${!a.active})" class="text-sm px-2 py-1 rounded ${if (a.active) "bg-red-100 text-red-700 hover:bg-red-200" else "bg-green-100 text-green-700 hover:bg-green-200"}">${if (a.active) "تعطيل" else "تفعيل"}</button>
                        ${if (a.role != "owner") """<button onclick="deleteAgent('${a.id}', '${a.name.replace("'","\\'")}') " class="text-sm px-2 py-1 rounded bg-red-600 text-white hover:bg-red-700">حذف</button>""" else ""}
                        </div>
                    </td>""")
                    append("</tr>")
                }
            }

            // Branding card — same UX as the super-admin Edit modal but
            // scoped to the caller's own org (POSTs to /crm/api/organization/logo
            // which trusts principal.organizationId, so a manager can't switch
            // someone else's logo by URL-tampering the form).
            val currentLogo = ownBranding?.logoUrl?.takeIf { it.isNotBlank() }
            val brandingCard = """
                <div class="bg-white rounded-xl shadow p-5 mb-6">
                    <h3 class="text-lg font-bold mb-3">شعار المنظمة</h3>
                    <div class="flex items-center gap-4">
                        ${if (currentLogo != null)
                            """<img id="orgLogoPreview" src="${currentLogo}?v=${System.currentTimeMillis() / 60_000}" alt="logo" class="w-20 h-20 rounded-xl object-cover border border-gray-200">"""
                        else
                            """<div id="orgLogoPreview" class="w-20 h-20 rounded-xl flex items-center justify-center text-3xl bg-gray-100 text-gray-400">⌂</div>"""
                        }
                        <div class="flex-1">
                            <p class="text-sm text-gray-500 mb-2">يظهر الشعار في القائمة الجانبية لكل المستخدمين في منظمتك. PNG/JPG/SVG حتى 5MB.</p>
                            <div class="flex flex-wrap gap-2 items-center">
                                <label class="inline-block bg-emerald-600 hover:bg-emerald-700 text-white text-sm px-4 py-2 rounded-lg cursor-pointer">
                                    📷 رفع شعار جديد
                                    <input type="file" accept="image/*" class="hidden" onchange="uploadMyOrgLogo(this)">
                                </label>
                                ${if (currentLogo != null) """<button onclick="removeMyOrgLogo()" class="text-sm px-4 py-2 rounded-lg border border-red-200 text-red-600 hover:bg-red-50">إزالة الشعار</button>""" else ""}
                            </div>
                        </div>
                    </div>
                </div>
                <script>
                async function uploadMyOrgLogo(input) {
                    if (!input.files || !input.files[0]) return;
                    const f = input.files[0];
                    if (f.size > 5 * 1024 * 1024) { alert('الحجم أكبر من 5MB. اضغط الصورة قبل الرفع.'); input.value = ''; return; }
                    const fd = new FormData(); fd.append('file', f);
                    try {
                        const res = await fetch('/crm/api/organization/logo', { method:'POST', body: fd });
                        if (!res.ok) {
                            const err = await res.json().catch(() => ({}));
                            alert(err.error || ('فشل رفع الشعار (' + res.status + ')'));
                            return;
                        }
                        // Reload so the sidebar header picks up the new logo too.
                        location.reload();
                    } catch (e) {
                        alert('خطأ في الشبكة أثناء رفع الشعار');
                    }
                }
                async function removeMyOrgLogo() {
                    if (!confirm('إزالة شعار المنظمة والعودة للشعار الافتراضي؟')) return;
                    const res = await fetch('/crm/api/organization/logo', { method:'DELETE' });
                    if (res.ok) location.reload();
                    else alert('حدث خطأ');
                }
                </script>
            """.trimIndent()

            val content = """
                $brandingCard
                <div class="flex justify-between items-center mb-6">
                    <h2 class="text-xl font-bold">الإعدادات - إدارة الموظفين</h2>
                    <button onclick="document.getElementById('addAgentModal').showModal()" class="bg-green-700 text-white px-4 py-2 rounded-lg hover:bg-green-800">+ إضافة موظف</button>
                </div>
                <div class="bg-white rounded-xl shadow overflow-x-auto">
                    <table class="w-full text-sm">
                        <thead>
                            <tr class="bg-gray-100 border-b">
                                <th class="p-2 text-right">الاسم</th>
                                <th class="p-2 text-right">البريد</th>
                                <th class="p-2 text-right">الدور</th>
                                <th class="p-2 text-right">الحالة</th>
                                <th class="p-2 text-right">إجراء</th>
                            </tr>
                        </thead>
                        <tbody>$agentRows</tbody>
                    </table>
                </div>

                ${addAgentModalHtml()}

                <!-- Edit Agent Modal -->
                <dialog id="editAgentModal" class="rounded-2xl">
                    <div class="p-6">
                        <div class="flex justify-between items-center mb-4">
                            <h3 class="text-lg font-bold">تعديل بيانات الموظف</h3>
                            <button onclick="document.getElementById('editAgentModal').close()" class="text-gray-400 hover:text-gray-600 text-xl">&times;</button>
                        </div>
                        <form onsubmit="submitEditAgent(event)" class="space-y-3">
                            <input type="hidden" name="editAgentId" id="editAgentId">
                            <div>
                                <label class="block text-sm font-medium text-gray-700 mb-1">الاسم</label>
                                <input type="text" name="name" id="editAgentName" required class="w-full px-3 py-2 border rounded-lg text-sm">
                            </div>
                            <div>
                                <label class="block text-sm font-medium text-gray-700 mb-1">الدور</label>
                                <select name="role" id="editAgentRole" class="w-full px-3 py-2 border rounded-lg text-sm">
                                    <option value="مدير مبيعات">مدير مبيعات</option>
                                    <option value="مندوب مبيعات">مندوب مبيعات</option>
                                    <option value="كول سنتر">كول سنتر</option>
                                </select>
                            </div>
                            <div class="flex justify-end gap-2 pt-2">
                                <button type="button" onclick="document.getElementById('editAgentModal').close()" class="px-4 py-2 text-sm border rounded-lg">إلغاء</button>
                                <button type="submit" class="px-4 py-2 text-sm text-white rounded-lg" style="background:#1B3A5C">حفظ التعديلات</button>
                            </div>
                        </form>
                    </div>
                </dialog>

                <!-- Reset Password Modal -->
                <dialog id="resetPasswordModal" class="rounded-2xl">
                    <div class="p-6">
                        <div class="flex justify-between items-center mb-4">
                            <h3 class="text-lg font-bold">تغيير كلمة السر</h3>
                            <button onclick="document.getElementById('resetPasswordModal').close()" class="text-gray-400 hover:text-gray-600 text-xl">&times;</button>
                        </div>
                        <p class="text-sm text-gray-600 mb-3" id="resetPasswordAgentName"></p>
                        <form onsubmit="submitResetPassword(event)" class="space-y-3">
                            <input type="hidden" name="resetAgentId" id="resetAgentId">
                            <div>
                                <label class="block text-sm font-medium text-gray-700 mb-1">كلمة السر الجديدة</label>
                                <input type="password" name="password" id="resetNewPassword" required minlength="6" class="w-full px-3 py-2 border rounded-lg text-sm">
                            </div>
                            <div>
                                <label class="block text-sm font-medium text-gray-700 mb-1">تأكيد كلمة السر</label>
                                <input type="password" name="confirmPassword" id="resetConfirmPassword" required minlength="6" class="w-full px-3 py-2 border rounded-lg text-sm">
                            </div>
                            <div class="flex justify-end gap-2 pt-2">
                                <button type="button" onclick="document.getElementById('resetPasswordModal').close()" class="px-4 py-2 text-sm border rounded-lg">إلغاء</button>
                                <button type="submit" class="px-4 py-2 text-sm text-white rounded-lg bg-yellow-600 hover:bg-yellow-700">تغيير كلمة السر</button>
                            </div>
                        </form>
                    </div>
                </dialog>

                <script>
                async function submitNewAgent(e) {
                    e.preventDefault();
                    const form = e.target;
                    const data = Object.fromEntries(new FormData(form));
                    const res = await fetch('/crm/api/agents', {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(data)});
                    if (res.ok) { location.reload(); } else { alert('حدث خطأ'); }
                }

                function openEditAgent(id, name, email, role) {
                    document.getElementById('editAgentId').value = id;
                    document.getElementById('editAgentName').value = name;
                    document.getElementById('editAgentRole').value = role;
                    document.getElementById('editAgentModal').showModal();
                }

                async function submitEditAgent(e) {
                    e.preventDefault();
                    const id = document.getElementById('editAgentId').value;
                    const name = document.getElementById('editAgentName').value;
                    const role = document.getElementById('editAgentRole').value;
                    const res = await fetch('/crm/api/agents/' + id, {method:'PUT', headers:{'Content-Type':'application/json'}, body:JSON.stringify({name, role})});
                    if (res.ok) { location.reload(); } else { alert('حدث خطأ'); }
                }

                function openResetPassword(id, name) {
                    document.getElementById('resetAgentId').value = id;
                    document.getElementById('resetPasswordAgentName').textContent = 'تغيير كلمة سر: ' + name;
                    document.getElementById('resetNewPassword').value = '';
                    document.getElementById('resetConfirmPassword').value = '';
                    document.getElementById('resetPasswordModal').showModal();
                }

                async function submitResetPassword(e) {
                    e.preventDefault();
                    const id = document.getElementById('resetAgentId').value;
                    const password = document.getElementById('resetNewPassword').value;
                    const confirm = document.getElementById('resetConfirmPassword').value;
                    if (password !== confirm) { alert('كلمة السر غير متطابقة'); return; }
                    if (password.length < 6) { alert('كلمة السر يجب أن تكون 6 أحرف على الأقل'); return; }
                    const res = await fetch('/crm/api/agents/' + id, {method:'PUT', headers:{'Content-Type':'application/json'}, body:JSON.stringify({password})});
                    if (res.ok) { document.getElementById('resetPasswordModal').close(); alert('تم تغيير كلمة السر بنجاح'); } else { alert('حدث خطأ'); }
                }

                async function toggleAgent(id, active) {
                    const res = await fetch('/crm/api/agents/' + id, {method:'PUT', headers:{'Content-Type':'application/json'}, body:JSON.stringify({active:active})});
                    if (res.ok) { location.reload(); } else { alert('حدث خطأ'); }
                }
                async function deleteAgent(id, name) {
                    if (!confirm('هل أنت متأكد من حذف ' + name + '؟\nسيتم إلغاء تعيين كل عملائه.')) return;
                    const res = await fetch('/crm/api/agents/' + id, {method:'DELETE'});
                    if (res.ok) { location.reload(); } else { const d = await res.json(); alert(d.error || 'حدث خطأ'); }
                }
                </script>
            """.trimIndent()

            call.respondText(
                crmLayout("الإعدادات", principal.name, principal.role, principal, "settings", content),
                ContentType.Text.Html
            )
        }

        // ─── Billing Page (Owner Only) ──────────────────────────
        get("/crm/billing") {
            val principal = call.principal<CrmPrincipal>()!!
            if (!principal.canSeeAnalytics) { call.respondRedirect("/crm/dashboard"); return@get }

            val billingStats = crmService.getBillingStats(principal.organizationId)
            val invoices = crmService.listInvoices(principal.organizationId, null, null)
            val clients = crmService.listClients(principal.organizationId, null, true).filter { it.status in setOf("مشترك", "مدفوع") }

            val clientOptions = clients.joinToString("") { """<option value="${it.id}">${it.clientName} - ${it.phone}</option>""" }

            fun invoiceStatusBadge(status: String, isOverdue: Boolean): String {
                val (bg, txt, cls) = when {
                    isOverdue -> Triple("#EF5350", "#FFFFFF", "overdue")
                    status == "مدفوع" -> Triple("#4CAF50", "#FFFFFF", "")
                    status == "مدفوع جزئي" -> Triple("#FF9800", "#FFFFFF", "")
                    else -> Triple("#9E9E9E", "#FFFFFF", "")
                }
                val label = if (isOverdue) "متأخر" else status
                return """<span class="px-2 py-1 rounded-full text-xs font-medium $cls" style="background:$bg;color:$txt">$label</span>"""
            }

            val tableRows = buildString {
                invoices.forEach { inv ->
                    val badge = invoiceStatusBadge(inv.status, inv.isOverdue)
                    val rowClass = if (inv.isOverdue) "bg-red-50" else ""
                    append("""<tr class="border-b hover:bg-gray-50 $rowClass">""")
                    append("""<td class='p-2 font-medium'><a href="#" onclick="showPayments('${inv.id}','${inv.invoiceNumber}');return false" class="text-blue-600 hover:underline">${inv.invoiceNumber}</a></td>""")
                    append("<td class='p-2'>${inv.clientName}</td>")
                    append("<td class='p-2'>${inv.plan}</td>")
                    append("<td class='p-2'>${inv.period}</td>")
                    append("<td class='p-2'>${String.format("%,.0f", inv.amount)} ج.م</td>")
                    append("<td class='p-2'>${inv.discountPercent}%</td>")
                    append("<td class='p-2 font-medium'>${String.format("%,.0f", inv.finalAmount)} ج.م</td>")
                    append("<td class='p-2 text-green-700'>${String.format("%,.0f", inv.paidAmount)} ج.م</td>")
                    append("<td class='p-2 text-red-600'>${String.format("%,.0f", inv.remainingAmount)} ج.م</td>")
                    append("<td class='p-2'>$badge</td>")
                    append("<td class='p-2'>${inv.dueDate ?: "-"}</td>")
                    append("""<td class='p-2'>${if (inv.status != "مدفوع") """<button onclick="openPayModal('${inv.id}','${inv.invoiceNumber}',${inv.remainingAmount})" class="text-sm px-2 py-1 bg-green-100 text-green-700 rounded hover:bg-green-200">تسجيل دفعة</button>""" else "-"}</td>""")
                    append("</tr>")
                }
            }

            val periodOptions = listOf(
                "أبريل 2026", "مايو 2026", "يونيو 2026", "يوليو 2026",
                "أغسطس 2026", "سبتمبر 2026", "أكتوبر 2026", "نوفمبر 2026", "ديسمبر 2026"
            ).joinToString("") { """<option value="$it">$it</option>""" }

            val content = """
                <div class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-3 mb-6">
                    ${kpiCard("إجمالي الفواتير", billingStats.totalInvoices.toString(), "\uD83E\uDDFE", "#1B3A5C")}
                    ${kpiCard("إجمالي الإيراد", "${String.format("%,.0f", billingStats.totalRevenue)} ج.م", "💰", "#1B3A5C")}
                    ${kpiCard("إجمالي المدفوع", "${String.format("%,.0f", billingStats.totalPaid)} ج.م", "✅", "#2E7D32")}
                    ${kpiCard("إجمالي المتأخر", "${String.format("%,.0f", billingStats.totalOverdue)} ج.م", "🔴", "#C62828")}
                    ${kpiCard("غير مدفوع", billingStats.unpaidCount.toString(), "⏳", "#E65100")}
                </div>

                <div class="flex justify-between items-center mb-4">
                    <h2 class="text-xl font-bold">الفواتير (${invoices.size})</h2>
                    <button onclick="document.getElementById('addInvoiceModal').showModal()" class="bg-green-700 text-white px-4 py-2 rounded-lg hover:bg-green-800">+ إنشاء فاتورة</button>
                </div>

                <div class="flex flex-wrap gap-2 mb-4">
                    <button onclick="filterInvoices('')" class="px-3 py-1 rounded-full text-sm bg-blue-100 text-blue-800 hover:bg-blue-200 filter-btn active-filter">الكل</button>
                    <button onclick="filterInvoices('غير مدفوع')" class="px-3 py-1 rounded-full text-sm bg-gray-100 text-gray-800 hover:bg-gray-200 filter-btn">غير مدفوع</button>
                    <button onclick="filterInvoices('مدفوع جزئي')" class="px-3 py-1 rounded-full text-sm bg-orange-100 text-orange-800 hover:bg-orange-200 filter-btn">مدفوع جزئي</button>
                    <button onclick="filterInvoices('مدفوع')" class="px-3 py-1 rounded-full text-sm bg-green-100 text-green-800 hover:bg-green-200 filter-btn">مدفوع</button>
                    <button onclick="filterInvoices('متأخر')" class="px-3 py-1 rounded-full text-sm bg-red-100 text-red-800 hover:bg-red-200 filter-btn">متأخر</button>
                </div>

                <div class="bg-white rounded-xl shadow p-4 mb-4">
                    <div class="flex flex-wrap gap-3 items-center">
                        <input id="billingSearch" oninput="applyBillingSearch()" type="text" placeholder="بحث بالاسم أو رقم الفاتورة..." class="px-3 py-2 border rounded-lg text-sm w-full md:w-64">
                        <button onclick="clearBillingFilters()" class="text-sm text-red-600 hover:underline">مسح الفلاتر</button>
                    </div>
                </div>

                <div class="bg-white rounded-xl shadow overflow-x-auto">
                    <table class="w-full text-sm" id="invoicesTable">
                        <thead>
                            <tr class="bg-gray-100 border-b">
                                <th class="p-2 text-right">رقم الفاتورة</th>
                                <th class="p-2 text-right">العميل</th>
                                <th class="p-2 text-right">الباقة</th>
                                <th class="p-2 text-right">الفترة</th>
                                <th class="p-2 text-right">المبلغ</th>
                                <th class="p-2 text-right">الخصم</th>
                                <th class="p-2 text-right">المبلغ النهائي</th>
                                <th class="p-2 text-right">المدفوع</th>
                                <th class="p-2 text-right">المتبقي</th>
                                <th class="p-2 text-right">الحالة</th>
                                <th class="p-2 text-right">تاريخ الاستحقاق</th>
                                <th class="p-2 text-right">إجراءات</th>
                            </tr>
                        </thead>
                        <tbody id="invoicesBody">$tableRows</tbody>
                    </table>
                </div>

                <!-- Create Invoice Modal -->
                <dialog id="addInvoiceModal" class="rounded-2xl">
                    <div class="p-6 w-full max-w-2xl max-h-[90vh] overflow-y-auto">
                        <div class="flex justify-between items-center mb-4">
                            <h3 class="text-lg font-bold">إنشاء فاتورة</h3>
                            <button onclick="document.getElementById('addInvoiceModal').close()" class="text-gray-400 hover:text-gray-600 text-xl">&times;</button>
                        </div>
                        <form onsubmit="submitNewInvoice(event)" class="space-y-3">
                            <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
                                <div class="col-span-1 md:col-span-2">
                                    <label class="block text-sm font-medium text-gray-700 mb-1">العميل *</label>
                                    <select name="clientId" required class="w-full px-3 py-2 border rounded-lg text-sm">
                                        <option value="">-- اختر العميل --</option>
                                        $clientOptions
                                    </select>
                                </div>
                                <div>
                                    <label class="block text-sm font-medium text-gray-700 mb-1">الباقة *</label>
                                    <select name="plan" required class="w-full px-3 py-2 border rounded-lg text-sm">
                                        <option value="">-- اختر --</option>
                                        ${planOptions()}
                                    </select>
                                </div>
                                <div>
                                    <label class="block text-sm font-medium text-gray-700 mb-1">الفترة *</label>
                                    <select name="period" required class="w-full px-3 py-2 border rounded-lg text-sm">
                                        <option value="">-- اختر الشهر --</option>
                                        $periodOptions
                                    </select>
                                </div>
                                <div>
                                    <label class="block text-sm font-medium text-gray-700 mb-1">المبلغ *</label>
                                    <input type="number" name="amount" required min="0" step="0.01" class="w-full px-3 py-2 border rounded-lg text-sm">
                                </div>
                                <div>
                                    <label class="block text-sm font-medium text-gray-700 mb-1">نسبة الخصم %</label>
                                    <input type="number" name="discountPercent" value="0" min="0" max="100" class="w-full px-3 py-2 border rounded-lg text-sm">
                                </div>
                                <div>
                                    <label class="block text-sm font-medium text-gray-700 mb-1">تاريخ الاستحقاق *</label>
                                    <input type="date" name="dueDate" required class="w-full px-3 py-2 border rounded-lg text-sm">
                                </div>
                                <div>
                                    <label class="block text-sm font-medium text-gray-700 mb-1">طريقة الدفع</label>
                                    <select name="paymentMethod" class="w-full px-3 py-2 border rounded-lg text-sm">
                                        <option value="">-- اختر --</option>
                                        ${paymentMethodOptions()}
                                    </select>
                                </div>
                                <div class="col-span-1 md:col-span-2">
                                    <label class="block text-sm font-medium text-gray-700 mb-1">ملاحظات</label>
                                    <textarea name="notes" rows="2" class="w-full px-3 py-2 border rounded-lg text-sm"></textarea>
                                </div>
                            </div>
                            <div class="flex justify-end gap-2 pt-3">
                                <button type="button" onclick="document.getElementById('addInvoiceModal').close()" class="px-4 py-2 text-sm border rounded-lg hover:bg-gray-50">إلغاء</button>
                                <button type="submit" class="px-4 py-2 text-sm text-white rounded-lg hover:opacity-90" style="background:#2E7D32">إنشاء</button>
                            </div>
                        </form>
                    </div>
                </dialog>

                <!-- Record Payment Modal -->
                <dialog id="payModal" class="rounded-2xl">
                    <div class="p-6 w-full max-w-md max-h-[90vh] overflow-y-auto">
                        <div class="flex justify-between items-center mb-4">
                            <h3 class="text-lg font-bold">تسجيل دفعة - <span id="payInvoiceNum"></span></h3>
                            <button onclick="document.getElementById('payModal').close()" class="text-gray-400 hover:text-gray-600 text-xl">&times;</button>
                        </div>
                        <form onsubmit="submitPayment(event)" class="space-y-3">
                            <input type="hidden" name="invoiceId" id="payInvoiceId">
                            <div>
                                <label class="block text-sm font-medium text-gray-700 mb-1">المبلغ * <span class="text-gray-400" id="payRemaining"></span></label>
                                <input type="number" name="amount" id="payAmount" required min="0" step="0.01" class="w-full px-3 py-2 border rounded-lg text-sm">
                            </div>
                            <div>
                                <label class="block text-sm font-medium text-gray-700 mb-1">طريقة الدفع *</label>
                                <select name="paymentMethod" required class="w-full px-3 py-2 border rounded-lg text-sm">
                                    <option value="">-- اختر --</option>
                                    ${paymentMethodOptions()}
                                </select>
                            </div>
                            <div>
                                <label class="block text-sm font-medium text-gray-700 mb-1">ملاحظات</label>
                                <textarea name="notes" rows="2" class="w-full px-3 py-2 border rounded-lg text-sm"></textarea>
                            </div>
                            <div class="flex justify-end gap-2 pt-3">
                                <button type="button" onclick="document.getElementById('payModal').close()" class="px-4 py-2 text-sm border rounded-lg hover:bg-gray-50">إلغاء</button>
                                <button type="submit" class="px-4 py-2 text-sm text-white rounded-lg hover:opacity-90" style="background:#2E7D32">تسجيل الدفعة</button>
                            </div>
                        </form>
                    </div>
                </dialog>

                <!-- Payment History Modal -->
                <dialog id="paymentsModal" class="rounded-2xl">
                    <div class="p-6 w-full max-w-2xl max-h-[90vh] overflow-y-auto">
                        <div class="flex justify-between items-center mb-4">
                            <h3 class="text-lg font-bold">سجل المدفوعات - <span id="paymentsInvoiceNum"></span></h3>
                            <button onclick="document.getElementById('paymentsModal').close()" class="text-gray-400 hover:text-gray-600 text-xl">&times;</button>
                        </div>
                        <div class="overflow-x-auto">
                            <table class="w-full text-sm">
                                <thead>
                                    <tr class="bg-gray-100 border-b">
                                        <th class="p-2 text-right">المبلغ</th>
                                        <th class="p-2 text-right">طريقة الدفع</th>
                                        <th class="p-2 text-right">ملاحظات</th>
                                        <th class="p-2 text-right">استلمها</th>
                                        <th class="p-2 text-right">التاريخ</th>
                                    </tr>
                                </thead>
                                <tbody id="paymentsBody"></tbody>
                            </table>
                        </div>
                    </div>
                </dialog>

                <script>
                let currentBillingStatus = '';
                function filterInvoices(status) {
                    currentBillingStatus = status;
                    document.querySelectorAll('.filter-btn').forEach(b => b.classList.remove('active-filter'));
                    event.target.classList.add('active-filter');
                    applyBillingCombined();
                }

                function applyBillingSearch() {
                    applyBillingCombined();
                }

                function applyBillingCombined() {
                    const search = (document.getElementById('billingSearch')?.value || '').toLowerCase();
                    const rows = document.querySelectorAll('#invoicesBody tr');
                    rows.forEach(row => {
                        const text = row.textContent.toLowerCase();
                        const matchSearch = !search || text.includes(search);
                        let matchStatus = true;
                        if (currentBillingStatus) {
                            const badge = row.querySelector('.rounded-full');
                            const badgeText = badge ? badge.textContent.trim() : '';
                            matchStatus = badgeText === currentBillingStatus;
                        }
                        row.style.display = (matchSearch && matchStatus) ? '' : 'none';
                    });
                }

                function clearBillingFilters() {
                    const searchEl = document.getElementById('billingSearch');
                    if (searchEl) searchEl.value = '';
                    currentBillingStatus = '';
                    document.querySelectorAll('.filter-btn').forEach(b => b.classList.remove('active-filter'));
                    document.querySelector('.filter-btn')?.classList.add('active-filter');
                    applyBillingCombined();
                }

                function openPayModal(invoiceId, invoiceNum, remaining) {
                    document.getElementById('payInvoiceId').value = invoiceId;
                    document.getElementById('payInvoiceNum').textContent = invoiceNum;
                    document.getElementById('payRemaining').textContent = '(المتبقي: ' + remaining.toLocaleString() + ' ج.م)';
                    document.getElementById('payAmount').max = remaining;
                    document.getElementById('payAmount').value = remaining;
                    document.getElementById('payModal').showModal();
                }

                async function submitNewInvoice(e) {
                    e.preventDefault();
                    const form = e.target;
                    const data = Object.fromEntries(new FormData(form));
                    data.amount = parseFloat(data.amount) || 0;
                    data.discountPercent = parseInt(data.discountPercent) || 0;
                    const res = await fetch('/crm/api/billing/invoices', {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(data)});
                    if (res.ok) { location.reload(); } else { const err = await res.text(); alert('حدث خطأ: ' + err); }
                }

                async function submitPayment(e) {
                    e.preventDefault();
                    const form = e.target;
                    const data = Object.fromEntries(new FormData(form));
                    data.amount = parseFloat(data.amount) || 0;
                    const invoiceId = data.invoiceId; delete data.invoiceId;
                    const res = await fetch('/crm/api/billing/invoices/' + invoiceId + '/pay', {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(data)});
                    if (res.ok) { location.reload(); } else { alert('حدث خطأ'); }
                }

                async function showPayments(invoiceId, invoiceNum) {
                    document.getElementById('paymentsInvoiceNum').textContent = invoiceNum;
                    const res = await fetch('/crm/api/billing/invoices/' + invoiceId + '/payments');
                    if (!res.ok) { alert('حدث خطأ'); return; }
                    const payments = await res.json();
                    const tbody = document.getElementById('paymentsBody');
                    if (payments.length === 0) {
                        tbody.innerHTML = '<tr><td colspan="5" class="p-4 text-center text-gray-500">لا توجد مدفوعات</td></tr>';
                    } else {
                        tbody.innerHTML = payments.map(p =>
                            '<tr class="border-b hover:bg-gray-50">' +
                            '<td class="p-2 font-medium text-green-700">' + Number(p.amount).toLocaleString() + ' ج.م</td>' +
                            '<td class="p-2">' + (p.paymentMethod || '-') + '</td>' +
                            '<td class="p-2">' + (p.notes || '-') + '</td>' +
                            '<td class="p-2">' + (p.receivedBy || '-') + '</td>' +
                            '<td class="p-2">' + (p.createdAt || '-') + '</td>' +
                            '</tr>'
                        ).join('');
                    }
                    document.getElementById('paymentsModal').showModal();
                }
                </script>
            """.trimIndent()

            call.respondText(
                crmLayout("الفواتير", principal.name, principal.role, principal, "billing", content),
                ContentType.Text.Html
            )
        }

        // ─── Salaries Page (Owner Only) ────────────────────────
        get("/crm/salaries") {
            val principal = call.principal<CrmPrincipal>()!!
            if (!principal.canSeeAnalytics) {
                call.respondRedirect("/crm/dashboard")
                return@get
            }
            val now = Clock.System.now().toLocalDateTime(CRM_TZ)
            val currentMonth = call.request.queryParameters["month"] ?: "${now.year}-${now.monthNumber.toString().padStart(2, '0')}"
            val agents = crmService.listAgents(principal.organizationId).filter { it.active }
            val salaryRecords = crmService.listAllSalaryRecords(currentMonth)

            // KPI summary
            val totalBaseSalaries = salaryRecords.sumOf { it.baseSalary }
            val totalCommissions = salaryRecords.sumOf { it.commissionTotal }
            val totalBonuses = salaryRecords.sumOf { it.bonus }
            val totalDeductions = salaryRecords.sumOf { it.deductions }

            val salaryRows = buildString {
                salaryRecords.forEach { r ->
                    val rowBg = if (r.status == "مدفوع") "bg-green-50" else "bg-yellow-50"
                    val statusBadge = if (r.status == "مدفوع")
                        """<span class="px-2 py-1 rounded-full text-xs font-medium bg-green-200 text-green-800">مدفوع</span>"""
                    else
                        """<span class="px-2 py-1 rounded-full text-xs font-medium bg-yellow-200 text-yellow-800">معلق</span>"""
                    val agentObj = agents.find { it.id == r.agentId }
                    val photoHtml = agentPhotoHtml(agentObj?.photoUrl, r.agentName, 32)
                    append("""<tr class="border-b hover:bg-gray-100 $rowBg">""")
                    append("""<td class="p-3"><div class="flex items-center gap-2">$photoHtml<span class="font-medium">${r.agentName}</span></div></td>""")
                    append("""<td class="p-3">${String.format("%,.1f", r.baseSalary)} ج.م</td>""")
                    append("""<td class="p-3">${String.format("%,.1f", r.commissionTotal)} ج.م</td>""")
                    append("""<td class="p-3">${String.format("%,.1f", r.bonus)} ج.م</td>""")
                    append("""<td class="p-3">${String.format("%,.1f", r.deductions)} ج.م</td>""")
                    append("""<td class="p-3 font-bold text-green-700">${String.format("%,.1f", r.finalSalary)} ج.م</td>""")
                    append("""<td class="p-3">$statusBadge</td>""")
                    append("""<td class="p-3">
                        <div class="flex gap-1 flex-wrap">
                            <button onclick="openCommissionDetails('${r.id}', '${r.agentName}')" class="text-blue-600 hover:underline text-xs px-2 py-1 bg-blue-50 rounded">تفاصيل</button>
                            <button onclick="openBonusDeduction('${r.id}', '${r.agentName}', ${r.bonus}, '${r.bonusReason ?: ""}', ${r.deductions}, '${r.deductionReason ?: ""}')" class="text-purple-600 hover:underline text-xs px-2 py-1 bg-purple-50 rounded">بونص/خصم</button>
                            ${if (r.status != "مدفوع") """<button onclick="confirmPay('${r.id}', '${r.agentName}')" class="text-green-700 hover:underline text-xs px-2 py-1 bg-green-50 rounded">تأكيد الصرف</button>""" else ""}
                        </div>
                    </td>""")
                    append("</tr>")
                }
            }

            val content = """
                <div class="flex flex-col md:flex-row justify-between items-start md:items-center gap-4 mb-6">
                    <h2 class="text-xl font-bold">المرتبات</h2>
                    <div class="flex items-center gap-3 flex-wrap">
                        <select id="monthSelector" onchange="location.href='/crm/salaries?month='+this.value" class="px-3 py-2 border rounded-lg text-sm">
                            ${monthOptions(currentMonth)}
                        </select>
                        <button onclick="calculateAll()" class="bg-green-700 text-white px-4 py-2 rounded-lg hover:bg-green-800 text-sm">احسب مرتبات الشهر</button>
                    </div>
                </div>

                <div class="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
                    ${kpiCard("إجمالي المرتبات", "${String.format("%,.1f", totalBaseSalaries)} ج.م", "💰", "#1B3A5C")}
                    ${kpiCard("إجمالي العمولات", "${String.format("%,.1f", totalCommissions)} ج.م", "📊", "#2E7D32")}
                    ${kpiCard("إجمالي البونص", "${String.format("%,.1f", totalBonuses)} ج.م", "🎁", "#E65100")}
                    ${kpiCard("إجمالي الخصومات", "${String.format("%,.1f", totalDeductions)} ج.م", "📉", "#C62828")}
                </div>

                ${filterBarHtml(listOf(
                    Triple("الحالات", 6, listOf("معلق", "مدفوع"))
                ), "بحث بالاسم...")}
                <div class="bg-white rounded-xl shadow overflow-x-auto">
                    <table id="dataTable" class="w-full text-sm">
                        <thead>
                            <tr class="bg-gray-100 border-b">
                                <th class="p-3 text-right">الاسم</th>
                                <th class="p-3 text-right">المرتب الثابت</th>
                                <th class="p-3 text-right">العمولة</th>
                                <th class="p-3 text-right">البونص</th>
                                <th class="p-3 text-right">الخصم</th>
                                <th class="p-3 text-right">النهائي</th>
                                <th class="p-3 text-right">الحالة</th>
                                <th class="p-3 text-right">إجراءات</th>
                            </tr>
                        </thead>
                        <tbody>${salaryRows}</tbody>
                    </table>
                    ${if (salaryRecords.isEmpty()) """<p class="text-gray-400 text-center py-8">لا توجد مرتبات لهذا الشهر. اضغط "احسب مرتبات الشهر" لحساب المرتبات.</p>""" else ""}
                </div>
                ${filterScript()}

                <!-- Commission Details Modal -->
                <dialog id="commissionModal" class="rounded-2xl w-full max-w-2xl">
                    <div class="p-6 max-h-[90vh] overflow-y-auto">
                        <div class="flex justify-between items-center mb-4">
                            <h3 class="text-lg font-bold" id="commissionModalTitle">تفاصيل العمولة</h3>
                            <button onclick="document.getElementById('commissionModal').close()" class="text-gray-400 hover:text-gray-600 text-xl">&times;</button>
                        </div>
                        <div id="commissionDetailsContent" class="overflow-x-auto">
                            <p class="text-center text-gray-400 py-4">جاري التحميل...</p>
                        </div>
                    </div>
                </dialog>

                <!-- Bonus/Deduction Modal -->
                <dialog id="bonusModal" class="rounded-2xl w-full max-w-md">
                    <div class="p-6 max-h-[90vh] overflow-y-auto">
                        <div class="flex justify-between items-center mb-4">
                            <h3 class="text-lg font-bold" id="bonusModalTitle">بونص / خصم</h3>
                            <button onclick="document.getElementById('bonusModal').close()" class="text-gray-400 hover:text-gray-600 text-xl">&times;</button>
                        </div>
                        <form onsubmit="submitBonusDeduction(event)" class="space-y-3">
                            <input type="hidden" id="bonusRecordId" name="recordId">
                            <div>
                                <label class="block text-sm font-medium text-gray-700 mb-1">بونص (ج.م)</label>
                                <input type="number" id="bonusAmount" name="bonus" min="0" step="0.01" class="w-full px-3 py-2 border rounded-lg text-sm">
                            </div>
                            <div>
                                <label class="block text-sm font-medium text-gray-700 mb-1">سبب البونص</label>
                                <textarea id="bonusReason" name="bonusReason" rows="2" class="w-full px-3 py-2 border rounded-lg text-sm"></textarea>
                            </div>
                            <div>
                                <label class="block text-sm font-medium text-gray-700 mb-1">خصم (ج.م)</label>
                                <input type="number" id="deductionAmount" name="deductions" min="0" step="0.01" class="w-full px-3 py-2 border rounded-lg text-sm">
                            </div>
                            <div>
                                <label class="block text-sm font-medium text-gray-700 mb-1">سبب الخصم</label>
                                <textarea id="deductionReason" name="deductionReason" rows="2" class="w-full px-3 py-2 border rounded-lg text-sm"></textarea>
                            </div>
                            <div class="flex justify-end gap-2 pt-3">
                                <button type="button" onclick="document.getElementById('bonusModal').close()" class="px-4 py-2 text-sm border rounded-lg hover:bg-gray-50">إلغاء</button>
                                <button type="submit" class="px-4 py-2 text-sm text-white rounded-lg hover:opacity-90" style="background:#2E7D32">حفظ</button>
                            </div>
                        </form>
                    </div>
                </dialog>

                <script>
                async function calculateAll() {
                    const month = document.getElementById('monthSelector').value;
                    if (!confirm('هل تريد حساب مرتبات جميع الموظفين لشهر ' + month + '؟')) return;
                    const res = await fetch('/crm/api/salaries/calculate-all', {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({month:month})});
                    if (res.ok) { location.reload(); } else { alert('حدث خطأ في حساب المرتبات'); }
                }

                async function openCommissionDetails(recordId, agentName) {
                    document.getElementById('commissionModalTitle').textContent = 'تفاصيل عمولة ' + agentName;
                    document.getElementById('commissionDetailsContent').innerHTML = '<p class="text-center text-gray-400 py-4">جاري التحميل...</p>';
                    document.getElementById('commissionModal').showModal();
                    const month = document.getElementById('monthSelector').value;
                    // Find agent id from salary records — use the record endpoint
                    const res = await fetch('/crm/api/salaries?month=' + month);
                    if (!res.ok) { document.getElementById('commissionDetailsContent').innerHTML = '<p class="text-center text-red-500">حدث خطأ</p>'; return; }
                    const records = await res.json();
                    const record = records.find(r => r.id === recordId);
                    if (!record || !record.commissionDetails || record.commissionDetails.length === 0) {
                        document.getElementById('commissionDetailsContent').innerHTML = '<p class="text-center text-gray-400 py-4">لا توجد تفاصيل عمولة</p>';
                        return;
                    }
                    const commTypes = {'FIRST_ONLY':'مرة واحدة','FIXED_MONTHS':'عدد محدد','FOREVER':'دائم','NONE':'بدون'};
                    let html = '<table class="w-full text-sm"><thead><tr class="bg-gray-100 border-b">';
                    html += '<th class="p-2 text-right">العميل</th><th class="p-2 text-right">الباقة</th><th class="p-2 text-right">مبلغ العميل</th>';
                    html += '<th class="p-2 text-right">نسبة العمولة</th><th class="p-2 text-right">مبلغ العمولة</th>';
                    html += '<th class="p-2 text-right">نوع العمولة</th><th class="p-2 text-right">شهر رقم</th><th class="p-2 text-right">نشط</th>';
                    html += '</tr></thead><tbody>';
                    record.commissionDetails.forEach(d => {
                        const activeDot = d.isActive ? '<span class="w-2 h-2 rounded-full bg-green-500 inline-block"></span>' : '<span class="w-2 h-2 rounded-full bg-red-500 inline-block"></span>';
                        html += '<tr class="border-b hover:bg-gray-50">';
                        html += '<td class="p-2">' + d.clientName + '</td>';
                        html += '<td class="p-2">' + (d.plan || '-') + '</td>';
                        html += '<td class="p-2">' + d.clientAmount.toLocaleString('ar-EG', {minimumFractionDigits:1}) + ' ج.م</td>';
                        html += '<td class="p-2">' + d.commissionPercent + '%</td>';
                        html += '<td class="p-2 font-bold">' + d.commissionAmount.toLocaleString('ar-EG', {minimumFractionDigits:1}) + ' ج.م</td>';
                        html += '<td class="p-2">' + (commTypes[d.commissionType] || d.commissionType) + '</td>';
                        html += '<td class="p-2">' + d.monthNumber + '</td>';
                        html += '<td class="p-2">' + activeDot + '</td>';
                        html += '</tr>';
                    });
                    html += '</tbody></table>';
                    document.getElementById('commissionDetailsContent').innerHTML = html;
                }

                function openBonusDeduction(recordId, agentName, bonus, bonusReason, deductions, deductionReason) {
                    document.getElementById('bonusModalTitle').textContent = 'بونص / خصم - ' + agentName;
                    document.getElementById('bonusRecordId').value = recordId;
                    document.getElementById('bonusAmount').value = bonus || 0;
                    document.getElementById('bonusReason').value = bonusReason || '';
                    document.getElementById('deductionAmount').value = deductions || 0;
                    document.getElementById('deductionReason').value = deductionReason || '';
                    document.getElementById('bonusModal').showModal();
                }

                async function submitBonusDeduction(e) {
                    e.preventDefault();
                    const recordId = document.getElementById('bonusRecordId').value;
                    const data = {
                        bonus: parseFloat(document.getElementById('bonusAmount').value) || 0,
                        bonusReason: document.getElementById('bonusReason').value || null,
                        deductions: parseFloat(document.getElementById('deductionAmount').value) || 0,
                        deductionReason: document.getElementById('deductionReason').value || null,
                    };
                    const res = await fetch('/crm/api/salary-records/' + recordId, {method:'PUT', headers:{'Content-Type':'application/json'}, body:JSON.stringify(data)});
                    if (res.ok) { location.reload(); } else { alert('حدث خطأ'); }
                }

                async function confirmPay(recordId, agentName) {
                    if (!confirm('هل تريد تأكيد صرف مرتب ' + agentName + '؟')) return;
                    const res = await fetch('/crm/api/salary-records/' + recordId + '/pay', {method:'POST'});
                    if (res.ok) { location.reload(); } else { alert('حدث خطأ'); }
                }
                </script>
            """.trimIndent()

            call.respondText(
                crmLayout("المرتبات", principal.name, principal.role, principal, "salaries", content),
                ContentType.Text.Html
            )
        }

        // ─── Profile Redirect ───────────────────────────────────
        get("/crm/profile") {
            val principal = call.principal<CrmPrincipal>()!!
            call.respondRedirect("/crm/profile/${principal.agentId}")
        }

        // ─── Profile Page ───────────────────────────────────────
        get("/crm/profile/{agentId}") {
            val principal = call.principal<CrmPrincipal>()!!
            val agentId = call.parameters["agentId"] ?: return@get call.respondRedirect("/crm/dashboard")

            // Permission check: owner/manager can see any, agent/cc can only see own
            if (!principal.canSeeAll && principal.agentId != agentId) {
                call.respondRedirect("/crm/dashboard")
                return@get
            }

            val profile = crmService.getAgentProfile(principal.organizationId, agentId) ?: run {
                call.respondRedirect("/crm/dashboard")
                return@get
            }

            val now = Clock.System.now().toLocalDateTime(CRM_TZ)
            val agent = profile.agent
            val (roleBg, roleTxt) = roleBadgeColor(agent.role)
            val photoHtml = agentPhotoHtml(agent.photoUrl, agent.name, 96)
            val statusDot = if (agent.active) """<span class="inline-flex items-center gap-1 text-sm text-green-700"><span class="w-2 h-2 rounded-full bg-green-500 inline-block"></span>نشط</span>"""
                else """<span class="inline-flex items-center gap-1 text-sm text-red-600"><span class="w-2 h-2 rounded-full bg-red-500 inline-block"></span>غير نشط</span>"""

            val canEditPhoto = principal.isOwner || principal.agentId == agentId

            // Section 1: Rebranded hero card — gradient background, big photo, inline
            // KPI chips, role badge and action row. Matches the client-details hero so
            // the CRM feels like one consistent product.
            val roleTag = """<span class="inline-flex items-center gap-1 px-3 py-1 rounded-full text-xs font-medium" style="background:$roleBg;color:$roleTxt">${roleDisplayName(agent.role)}</span>"""
            val heroStatusDot = if (agent.active)
                """<span class="inline-flex items-center gap-1.5 text-xs text-emerald-200"><span class="w-2 h-2 rounded-full bg-emerald-400 inline-block"></span>نشط</span>"""
            else
                """<span class="inline-flex items-center gap-1.5 text-xs text-red-200"><span class="w-2 h-2 rounded-full bg-red-400 inline-block"></span>غير نشط</span>"""
            val agentCard = """
                <!-- Hero header card -->
                <div class="bg-gradient-to-l from-slate-900 via-slate-800 to-slate-700 rounded-2xl p-6 md:p-8 mb-6 text-white shadow-lg relative overflow-hidden">
                    <!-- subtle pattern -->
                    <div class="absolute inset-0 opacity-5" style="background-image: radial-gradient(circle at 20% 20%, white 1px, transparent 1px); background-size: 30px 30px;"></div>
                    <div class="relative flex flex-col md:flex-row items-center md:items-start gap-6">
                        <!-- Photo (or initial avatar) with optional hover-to-change overlay -->
                        <div class="relative group flex-shrink-0">
                            <div class="p-1 rounded-full bg-gradient-to-br from-white/30 to-white/5 shadow-xl">
                                $photoHtml
                            </div>
                            ${if (canEditPhoto) """
                            <button onclick="document.getElementById('photoInput').click()" class="absolute inset-1 flex items-center justify-center bg-black/60 rounded-full opacity-0 group-hover:opacity-100 transition cursor-pointer">
                                <span class="text-white text-sm">📷 تغيير</span>
                            </button>
                            <input type="file" id="photoInput" accept="image/*" class="hidden" onchange="uploadPhoto(this)">
                            """ else ""}
                        </div>

                        <!-- Identity -->
                        <div class="text-center md:text-right flex-1 min-w-0">
                            <div class="flex items-center justify-center md:justify-start gap-2 flex-wrap mb-2">
                                <h1 class="text-2xl md:text-3xl font-bold truncate">${agent.name}</h1>
                                $roleTag
                                $heroStatusDot
                            </div>
                            <p class="text-white/70 text-sm mb-3 flex items-center justify-center md:justify-start gap-1">
                                <span>✉️</span><span>${agent.email}</span>
                            </p>
                            <!-- Inline quick stats -->
                            <div class="flex flex-wrap gap-2 justify-center md:justify-start">
                                <span class="inline-flex items-center gap-1 px-3 py-1 rounded-full bg-white/10 text-xs backdrop-blur">👥 ${profile.totalClients} عميل</span>
                                <span class="inline-flex items-center gap-1 px-3 py-1 rounded-full bg-white/10 text-xs backdrop-blur">✅ ${profile.totalSubscriptions} اشتراك</span>
                                <span class="inline-flex items-center gap-1 px-3 py-1 rounded-full bg-white/10 text-xs backdrop-blur">📋 ${profile.totalActivities} نشاط</span>
                                <span class="inline-flex items-center gap-1 px-3 py-1 rounded-full bg-white/10 text-xs backdrop-blur">💰 ${String.format("%,.0f", profile.totalRevenue)} ج.م</span>
                            </div>
                        </div>

                        <!-- Self-service actions (only on own profile) -->
                        ${if (principal.agentId == agentId) """<div class="flex gap-2 flex-wrap items-start">
                            <button onclick="document.getElementById('changeMyPasswordModal').showModal()" class="flex items-center gap-2 text-sm px-4 py-2 bg-white/10 text-white rounded-lg hover:bg-white/20 transition backdrop-blur">
                                <span>🔒</span><span>تغيير كلمة السر</span>
                            </button>
                            <a href="/crm/logout" class="flex items-center gap-2 text-sm px-4 py-2 bg-red-500/20 text-red-100 rounded-lg hover:bg-red-500/30 transition backdrop-blur">
                                <span>🚪</span><span>تسجيل الخروج</span>
                            </a>
                        </div>
                        <dialog id="changeMyPasswordModal" class="rounded-2xl">
                            <div class="p-6">
                                <div class="flex justify-between items-center mb-4">
                                    <h3 class="text-lg font-bold">تغيير كلمة السر</h3>
                                    <button onclick="document.getElementById('changeMyPasswordModal').close()" class="text-gray-400 hover:text-gray-600 text-xl">&times;</button>
                                </div>
                                <form onsubmit="changeMyPassword(event)" class="space-y-3">
                                    <div><label class="block text-sm font-medium text-gray-700 mb-1">كلمة السر الجديدة</label>
                                    <input type="password" id="myNewPass" required minlength="6" class="w-full px-3 py-2 border rounded-lg text-sm"></div>
                                    <div><label class="block text-sm font-medium text-gray-700 mb-1">تأكيد كلمة السر</label>
                                    <input type="password" id="myConfirmPass" required minlength="6" class="w-full px-3 py-2 border rounded-lg text-sm"></div>
                                    <div class="flex justify-end gap-2 pt-2">
                                        <button type="button" onclick="document.getElementById('changeMyPasswordModal').close()" class="px-4 py-2 text-sm border rounded-lg">إلغاء</button>
                                        <button type="submit" class="px-4 py-2 text-sm text-white rounded-lg bg-emerald-600 hover:bg-emerald-700">تغيير</button>
                                    </div>
                                </form>
                            </div>
                        </dialog>
                        <script>
                        async function changeMyPassword(e) {
                            e.preventDefault();
                            const p = document.getElementById('myNewPass').value;
                            const c = document.getElementById('myConfirmPass').value;
                            if (p !== c) { alert('كلمة السر غير متطابقة'); return; }
                            if (p.length < 6) { alert('يجب أن تكون 6 أحرف على الأقل'); return; }
                            const res = await fetch('/crm/api/agents/$agentId', {method:'PUT', headers:{'Content-Type':'application/json'}, body:JSON.stringify({password:p})});
                            if (res.ok) { document.getElementById('changeMyPasswordModal').close(); alert('تم تغيير كلمة السر بنجاح'); } else { alert('حدث خطأ'); }
                        }
                        </script>""" else ""}
                    </div>
                </div>
                <script>
                async function uploadPhoto(input) {
                    if (!input.files[0]) return;
                    const formData = new FormData();
                    formData.append('file', input.files[0]);
                    try {
                        const uploadRes = await fetch('/crm/api/upload', { method: 'POST', body: formData });
                        if (!uploadRes.ok) {
                            // Try simple base64 approach via agent update
                            const reader = new FileReader();
                            reader.onload = async () => {
                                // Just update with a placeholder - we need the upload endpoint
                                alert('حدث خطأ في رفع الصورة');
                            };
                            reader.readAsDataURL(input.files[0]);
                            return;
                        }
                        const data = await uploadRes.json();
                        const res = await fetch('/crm/api/agents/$agentId', {
                            method: 'PUT',
                            headers: {'Content-Type': 'application/json'},
                            body: JSON.stringify({ photoUrl: data.url })
                        });
                        if (res.ok) location.reload();
                        else alert('حدث خطأ في تحديث الصورة');
                    } catch(e) { alert('حدث خطأ: ' + e.message); }
                }
                </script>
            """.trimIndent()

            // Section 2: Monthly Target + Progress — same progressBar but in the refreshed
            // card shell (subtle border, softer shadow, icon in the heading).
            val currentMonthLabel = arabicMonth(profile.currentMonth)
            val targetSection = if (profile.target != null) {
                val p = profile.progress
                """
                <div class="bg-white rounded-2xl shadow-sm border border-gray-100 p-5 mb-6">
                    <div class="flex justify-between items-center mb-4">
                        <h2 class="font-bold text-gray-800 flex items-center gap-2"><span>🎯</span><span>هدف $currentMonthLabel</span></h2>
                        ${if (principal.isOwner) """<button onclick="document.getElementById('setTargetModal').showModal()" class="flex items-center gap-1 text-sm px-3 py-1.5 bg-sky-50 text-sky-700 rounded-lg hover:bg-sky-100 transition">
                            <span>✏️</span><span>تعديل الهدف</span>
                        </button>""" else ""}
                    </div>
                    ${progressBar("عملاء جدد", p.actualClients.toString(), p.targetClients.toString(), p.clientsPercent)}
                    ${progressBar("اشتراكات", p.actualSubscriptions.toString(), p.targetSubscriptions.toString(), p.subscriptionsPercent)}
                    ${progressBar("الإيراد", "${String.format("%,.0f", p.actualRevenue)} ج.م", "${String.format("%,.0f", p.targetRevenue)} ج.م", p.revenuePercent)}
                    ${if (!profile.target?.notes.isNullOrBlank()) """
                    <div class="mt-4 p-3 bg-amber-50 rounded-lg border border-amber-200">
                        <p class="text-sm font-bold text-amber-800 mb-1">📝 ملاحظات الهدف</p>
                        <p class="text-sm text-amber-900 whitespace-pre-wrap">${profile.target?.notes}</p>
                    </div>
                    """ else ""}
                </div>
                """.trimIndent()
            } else {
                """
                <div class="bg-white rounded-2xl shadow-sm border border-gray-100 p-5 mb-6">
                    <div class="flex justify-between items-center mb-4">
                        <h2 class="font-bold text-gray-800 flex items-center gap-2"><span>🎯</span><span>هدف $currentMonthLabel</span></h2>
                        ${if (principal.isOwner) """<button onclick="document.getElementById('setTargetModal').showModal()" class="flex items-center gap-1 text-sm px-3 py-1.5 bg-sky-50 text-sky-700 rounded-lg hover:bg-sky-100 transition">
                            <span>✏️</span><span>تعديل الهدف</span>
                        </button>""" else ""}
                    </div>
                    <div class="text-center py-6">
                        <div class="text-4xl mb-2">🎯</div>
                        <p class="text-gray-400 text-sm">لم يتم تحديد هدف لهذا الشهر</p>
                    </div>
                </div>
                """.trimIndent()
            }

            // Section 2.5: Salary & Commission
            val canViewSalary = principal.isOwner || principal.agentId == agentId
            val salarySection = if (canViewSalary) {
                val salaryConfig = crmService.getSalaryConfig(agentId)
                val currentSalaryMonth = "${now.year}-${now.monthNumber.toString().padStart(2, '0')}"
                val salaryRecord = crmService.getSalaryRecord(agentId, currentSalaryMonth)

                // Previous 6 months salary history
                val salaryHistory = (1..6).mapNotNull { i ->
                    var hYear = now.year
                    var hMonth = now.monthNumber - i
                    if (hMonth <= 0) { hMonth += 12; hYear -= 1 }
                    val mStr = "${hYear}-${hMonth.toString().padStart(2, '0')}"
                    crmService.getSalaryRecord(agentId, mStr)
                }

                val configDisplay = """
                    <div class="bg-white rounded-2xl shadow p-6 mb-6">
                        <div class="flex justify-between items-center mb-4">
                            <h2 class="text-lg font-bold">المرتب والعمولة</h2>
                            ${if (principal.isOwner) """<button onclick="document.getElementById('salaryConfigModal').showModal()" class="text-sm px-3 py-1 bg-blue-100 text-blue-700 rounded-lg hover:bg-blue-200">تعديل الإعدادات</button>""" else ""}
                        </div>
                        <div class="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
                            <div class="bg-gray-50 rounded-xl p-4 text-center">
                                <div class="text-sm text-gray-500 mb-1">المرتب الثابت</div>
                                <div class="text-xl font-bold">${String.format("%,.1f", salaryConfig.baseSalary)} ج.م</div>
                            </div>
                            <div class="bg-gray-50 rounded-xl p-4 text-center">
                                <div class="text-sm text-gray-500 mb-1">نسبة العمولة</div>
                                <div class="text-xl font-bold">${salaryConfig.commissionPercent}%</div>
                            </div>
                            <div class="bg-gray-50 rounded-xl p-4 text-center">
                                <div class="text-sm text-gray-500 mb-1">نوع العمولة</div>
                                <div class="text-xl font-bold">${commissionTypeDisplayName(salaryConfig.commissionType)}</div>
                            </div>
                        </div>

                        ${if (salaryRecord != null) """
                        <h3 class="font-bold mb-3">${arabicMonth(currentSalaryMonth)} - تفاصيل المرتب</h3>
                        <div class="grid grid-cols-2 md:grid-cols-5 gap-3 mb-4">
                            <div class="bg-blue-50 rounded-lg p-3 text-center">
                                <div class="text-xs text-gray-500">المرتب الثابت</div>
                                <div class="font-bold">${String.format("%,.1f", salaryRecord.baseSalary)} ج.م</div>
                            </div>
                            <div class="bg-blue-50 rounded-lg p-3 text-center">
                                <div class="text-xs text-gray-500">العمولة (${salaryRecord.commissionDetails.size} عملاء)</div>
                                <div class="font-bold">${String.format("%,.1f", salaryRecord.commissionTotal)} ج.م</div>
                            </div>
                            <div class="bg-green-50 rounded-lg p-3 text-center">
                                <div class="text-xs text-gray-500">البونص${if (!salaryRecord.bonusReason.isNullOrBlank()) " (${salaryRecord.bonusReason})" else ""}</div>
                                <div class="font-bold">${String.format("%,.1f", salaryRecord.bonus)} ج.م</div>
                            </div>
                            <div class="bg-red-50 rounded-lg p-3 text-center">
                                <div class="text-xs text-gray-500">الخصم${if (!salaryRecord.deductionReason.isNullOrBlank()) " (${salaryRecord.deductionReason})" else ""}</div>
                                <div class="font-bold">${String.format("%,.1f", salaryRecord.deductions)} ج.م</div>
                            </div>
                            <div class="bg-green-100 rounded-lg p-3 text-center">
                                <div class="text-xs text-gray-500">النهائي</div>
                                <div class="text-xl font-bold text-green-700">${String.format("%,.1f", salaryRecord.finalSalary)} ج.م</div>
                            </div>
                        </div>

                        ${if (salaryRecord.commissionDetails.isNotEmpty()) """
                        <h4 class="font-medium text-sm mb-2">تفاصيل العمولة</h4>
                        <div class="overflow-x-auto mb-4">
                            <table class="w-full text-sm">
                                <thead>
                                    <tr class="bg-gray-100 border-b">
                                        <th class="p-2 text-right">العميل</th>
                                        <th class="p-2 text-right">الباقة</th>
                                        <th class="p-2 text-right">مبلغ العميل</th>
                                        <th class="p-2 text-right">نسبة العمولة</th>
                                        <th class="p-2 text-right">مبلغ العمولة</th>
                                        <th class="p-2 text-right">نوع العمولة</th>
                                        <th class="p-2 text-right">شهر رقم</th>
                                        <th class="p-2 text-right">نشط</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    ${salaryRecord.commissionDetails.joinToString("") { d ->
                                        val activeDot = if (d.isActive) """<span class="w-2 h-2 rounded-full bg-green-500 inline-block"></span>""" else """<span class="w-2 h-2 rounded-full bg-red-500 inline-block"></span>"""
                                        """<tr class="border-b hover:bg-gray-50">
                                            <td class="p-2">${d.clientName}</td>
                                            <td class="p-2">${d.plan ?: "-"}</td>
                                            <td class="p-2">${String.format("%,.1f", d.clientAmount)} ج.م</td>
                                            <td class="p-2">${d.commissionPercent}%</td>
                                            <td class="p-2 font-bold">${String.format("%,.1f", d.commissionAmount)} ج.م</td>
                                            <td class="p-2">${commissionTypeDisplayName(d.commissionType)}</td>
                                            <td class="p-2">${d.monthNumber}</td>
                                            <td class="p-2">$activeDot</td>
                                        </tr>"""
                                    }}
                                </tbody>
                            </table>
                        </div>
                        """ else ""}
                        """ else """<p class="text-gray-400 text-center py-4">لم يتم حساب مرتب ${arabicMonth(currentSalaryMonth)} بعد</p>"""}

                        ${if (salaryHistory.isNotEmpty()) """
                        <h3 class="font-bold mb-3 mt-4">سجل المرتبات السابقة</h3>
                        <div class="overflow-x-auto">
                            <table class="w-full text-sm">
                                <thead>
                                    <tr class="bg-gray-100 border-b">
                                        <th class="p-2 text-right">الشهر</th>
                                        <th class="p-2 text-right">المرتب الثابت</th>
                                        <th class="p-2 text-right">العمولة</th>
                                        <th class="p-2 text-right">البونص</th>
                                        <th class="p-2 text-right">الخصم</th>
                                        <th class="p-2 text-right">النهائي</th>
                                        <th class="p-2 text-right">الحالة</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    ${salaryHistory.joinToString("") { h ->
                                        val hRowBg = if (h.status == "مدفوع") "bg-green-50" else "bg-yellow-50"
                                        val hStatusBadge = if (h.status == "مدفوع")
                                            """<span class="px-2 py-1 rounded-full text-xs font-medium bg-green-200 text-green-800">مدفوع</span>"""
                                        else
                                            """<span class="px-2 py-1 rounded-full text-xs font-medium bg-yellow-200 text-yellow-800">معلق</span>"""
                                        """<tr class="border-b $hRowBg">
                                            <td class="p-2 font-medium">${arabicMonth(h.month)}</td>
                                            <td class="p-2">${String.format("%,.1f", h.baseSalary)} ج.م</td>
                                            <td class="p-2">${String.format("%,.1f", h.commissionTotal)} ج.م</td>
                                            <td class="p-2">${String.format("%,.1f", h.bonus)} ج.م</td>
                                            <td class="p-2">${String.format("%,.1f", h.deductions)} ج.م</td>
                                            <td class="p-2 font-bold">${String.format("%,.1f", h.finalSalary)} ج.م</td>
                                            <td class="p-2">$hStatusBadge</td>
                                        </tr>"""
                                    }}
                                </tbody>
                            </table>
                        </div>
                        """ else ""}
                    </div>
                """.trimIndent()

                val salaryConfigModal = if (principal.isOwner) """
                    <!-- Salary Config Modal -->
                    <dialog id="salaryConfigModal" class="rounded-2xl">
                        <div class="p-6 w-full max-w-md max-h-[90vh] overflow-y-auto">
                            <div class="flex justify-between items-center mb-4">
                                <h3 class="text-lg font-bold">إعدادات المرتب والعمولة</h3>
                                <button onclick="document.getElementById('salaryConfigModal').close()" class="text-gray-400 hover:text-gray-600 text-xl">&times;</button>
                            </div>
                            <form onsubmit="submitSalaryConfig(event)" class="space-y-3">
                                <div>
                                    <label class="block text-sm font-medium text-gray-700 mb-1">المرتب الثابت (ج.م)</label>
                                    <input type="number" name="baseSalary" value="${salaryConfig.baseSalary}" min="0" step="0.01" class="w-full px-3 py-2 border rounded-lg text-sm">
                                </div>
                                <div>
                                    <label class="block text-sm font-medium text-gray-700 mb-1">نسبة العمولة %</label>
                                    <input type="number" name="commissionPercent" value="${salaryConfig.commissionPercent}" min="0" max="100" step="0.1" class="w-full px-3 py-2 border rounded-lg text-sm">
                                </div>
                                <div>
                                    <label class="block text-sm font-medium text-gray-700 mb-1">نوع العمولة</label>
                                    <select name="commissionType" id="salaryCommType" onchange="toggleCommMonths()" class="w-full px-3 py-2 border rounded-lg text-sm">
                                        <option value="NONE" ${if (salaryConfig.commissionType == "NONE") "selected" else ""}>بدون عمولة</option>
                                        <option value="FIRST_ONLY" ${if (salaryConfig.commissionType == "FIRST_ONLY") "selected" else ""}>مرة واحدة</option>
                                        <option value="FIXED_MONTHS" ${if (salaryConfig.commissionType == "FIXED_MONTHS") "selected" else ""}>عدد شهور محدد</option>
                                        <option value="FOREVER" ${if (salaryConfig.commissionType == "FOREVER") "selected" else ""}>دائم</option>
                                    </select>
                                </div>
                                <div id="commMonthsDiv" style="${if (salaryConfig.commissionType == "FIXED_MONTHS") "" else "display:none"}">
                                    <label class="block text-sm font-medium text-gray-700 mb-1">عدد الشهور</label>
                                    <input type="number" name="commissionMonths" value="${salaryConfig.commissionMonths}" min="0" class="w-full px-3 py-2 border rounded-lg text-sm">
                                </div>
                                <div>
                                    <label class="block text-sm font-medium text-gray-700 mb-1">أساس الحساب</label>
                                    <select name="commissionBase" class="w-full px-3 py-2 border rounded-lg text-sm">
                                        <option value="FINAL" ${if (salaryConfig.commissionBase == "FINAL") "selected" else ""}>المبلغ النهائي</option>
                                        <option value="ORIGINAL" ${if (salaryConfig.commissionBase == "ORIGINAL") "selected" else ""}>المبلغ الأصلي</option>
                                    </select>
                                </div>
                                <div>
                                    <label class="block text-sm font-medium text-gray-700 mb-1">ملاحظات</label>
                                    <textarea name="notes" rows="2" class="w-full px-3 py-2 border rounded-lg text-sm">${salaryConfig.notes ?: ""}</textarea>
                                </div>
                                <div class="flex justify-end gap-2 pt-3">
                                    <button type="button" onclick="document.getElementById('salaryConfigModal').close()" class="px-4 py-2 text-sm border rounded-lg hover:bg-gray-50">إلغاء</button>
                                    <button type="submit" class="px-4 py-2 text-sm text-white rounded-lg hover:opacity-90" style="background:#2E7D32">حفظ</button>
                                </div>
                            </form>
                        </div>
                    </dialog>

                    <script>
                    function toggleCommMonths() {
                        document.getElementById('commMonthsDiv').style.display = document.getElementById('salaryCommType').value === 'FIXED_MONTHS' ? '' : 'none';
                    }
                    async function submitSalaryConfig(e) {
                        e.preventDefault();
                        const form = e.target;
                        const data = Object.fromEntries(new FormData(form));
                        data.baseSalary = parseFloat(data.baseSalary) || 0;
                        data.commissionPercent = parseFloat(data.commissionPercent) || 0;
                        data.commissionMonths = parseInt(data.commissionMonths) || 0;
                        const res = await fetch('/crm/api/agents/$agentId/salary-config', {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(data)});
                        if (res.ok) { location.reload(); } else { alert('حدث خطأ'); }
                    }
                    </script>
                """.trimIndent() else ""

                "$configDisplay\n$salaryConfigModal"
            } else ""

            // Section 3: All-time Stats — modern KPI cards consistent with the rest of the rebrand.
            val statsSection = """
                <div class="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
                    <div class="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
                        <div class="flex items-center justify-between">
                            <div>
                                <p class="text-xs text-gray-500">إجمالي العملاء</p>
                                <p class="text-2xl font-bold mt-1 text-slate-800">${profile.totalClients}</p>
                            </div>
                            <div class="w-10 h-10 rounded-lg bg-slate-100 flex items-center justify-center text-slate-700 text-xl">👥</div>
                        </div>
                    </div>
                    <div class="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
                        <div class="flex items-center justify-between">
                            <div>
                                <p class="text-xs text-gray-500">إجمالي الاشتراكات</p>
                                <p class="text-2xl font-bold mt-1 text-emerald-600">${profile.totalSubscriptions}</p>
                            </div>
                            <div class="w-10 h-10 rounded-lg bg-emerald-50 flex items-center justify-center text-emerald-600 text-xl">✅</div>
                        </div>
                    </div>
                    <div class="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
                        <div class="flex items-center justify-between">
                            <div>
                                <p class="text-xs text-gray-500">إجمالي الإيراد</p>
                                <p class="text-xl font-bold mt-1 text-amber-600">${String.format("%,.0f", profile.totalRevenue)} <span class="text-sm font-normal">ج.م</span></p>
                            </div>
                            <div class="w-10 h-10 rounded-lg bg-amber-50 flex items-center justify-center text-amber-600 text-xl">💰</div>
                        </div>
                    </div>
                    <div class="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
                        <div class="flex items-center justify-between">
                            <div>
                                <p class="text-xs text-gray-500">إجمالي الأنشطة</p>
                                <p class="text-2xl font-bold mt-1 text-indigo-600">${profile.totalActivities}</p>
                            </div>
                            <div class="w-10 h-10 rounded-lg bg-indigo-50 flex items-center justify-center text-indigo-600 text-xl">📋</div>
                        </div>
                    </div>
                </div>
            """.trimIndent()

            // Section 4: Pinned Reviews
            val pinnedSection = if (profile.pinnedReviews.isNotEmpty()) {
                val cards = profile.pinnedReviews.joinToString("") { r ->
                    """
                    <div class="border-2 border-yellow-400 rounded-xl p-4 bg-yellow-50">
                        <div class="flex justify-between items-center mb-2">
                            <span class="text-sm font-bold text-gray-700">📌 ${arabicMonth(r.month)}</span>
                            <span class="text-sm">${starsHtml(r.score)} <span class="text-gray-500 font-bold">(${r.score}/10)</span></span>
                        </div>
                        <div class="bg-white/70 rounded-lg p-3 mt-2">
                            <p class="text-sm text-gray-800 leading-relaxed whitespace-pre-line">${r.review}</p>
                        </div>
                    </div>
                    """
                }
                """
                <div class="mb-6">
                    <h2 class="text-lg font-bold mb-4">التقييمات المثبتة</h2>
                    <div class="grid grid-cols-1 md:grid-cols-2 gap-4">$cards</div>
                </div>
                """.trimIndent()
            } else ""

            // Section 5: Review History
            val reviewRows = profile.reviews.joinToString("") { r ->
                val pinnedIcon = if (r.pinned) "📌" else ""
                val pinAction = if (principal.isOwner) {
                    val pinLabel = if (r.pinned) "إلغاء التثبيت" else "تثبيت"
                    val pinClass = if (r.pinned) "text-red-600" else "text-yellow-600"
                    """<button onclick="togglePin('${r.id}', ${!r.pinned})" class="$pinClass hover:underline text-xs ml-2">$pinLabel</button>"""
                } else ""
                val editAction = if (principal.isOwner) """<button onclick="openEditReview('${r.id}', ${r.score}, '${r.review.replace("'", "\\'")}')" class="text-blue-600 hover:underline text-xs">تعديل</button>""" else ""
                """
                <tr class="border-b hover:bg-gray-50">
                    <td class="p-2 font-medium">${arabicMonth(r.month)}</td>
                    <td class="p-2">${starsHtml(r.score)} <span class="text-gray-500 text-xs">(${r.score}/10)</span></td>
                    <td class="p-2">
                        <div class="bg-gray-50 rounded p-2 text-sm leading-relaxed whitespace-pre-line max-w-md">${r.review}</div>
                    </td>
                    <td class="p-2 text-center">$pinnedIcon</td>
                    <td class="p-2">$pinAction $editAction</td>
                </tr>
                """
            }

            val reviewsSection = """
                <div class="bg-white rounded-2xl shadow-sm border border-gray-100 p-5 mb-6">
                    <div class="flex justify-between items-center mb-4">
                        <h2 class="font-bold text-gray-800 flex items-center gap-2"><span>⭐</span><span>سجل التقييمات</span></h2>
                        ${if (principal.isOwner) """<button onclick="document.getElementById('addReviewModal').showModal()" class="flex items-center gap-1 text-sm px-3 py-1.5 bg-emerald-50 text-emerald-700 rounded-lg hover:bg-emerald-100 transition">
                            <span>＋</span><span>إضافة تقييم</span>
                        </button>""" else ""}
                    </div>
                    ${if (profile.reviews.isEmpty()) """
                    <div class="text-center py-8">
                        <div class="text-4xl mb-2">🌟</div>
                        <p class="text-gray-400 text-sm">لا توجد تقييمات بعد</p>
                    </div>""" else """
                    <div class="overflow-x-auto">
                        <table class="w-full text-sm">
                            <thead>
                                <tr class="bg-gray-50 border-b">
                                    <th class="p-3 text-right text-xs text-gray-500 uppercase tracking-wider font-medium">الشهر</th>
                                    <th class="p-3 text-right text-xs text-gray-500 uppercase tracking-wider font-medium">التقييم</th>
                                    <th class="p-3 text-right text-xs text-gray-500 uppercase tracking-wider font-medium">الملاحظات</th>
                                    <th class="p-3 text-center text-xs text-gray-500 uppercase tracking-wider font-medium">مثبت</th>
                                    <th class="p-3 text-right text-xs text-gray-500 uppercase tracking-wider font-medium">إجراءات</th>
                                </tr>
                            </thead>
                            <tbody>$reviewRows</tbody>
                        </table>
                    </div>
                    """}
                </div>
            """.trimIndent()

            // Section 6: Recent Activities — styled to match the main /crm/activities screen,
            // with channel icons, colored rails, status transition chips and linked clients.
            fun profileChannelIcon(ch: String?): String = when (ch) {
                "مكالمة تليفون", "اتصال" -> "📞"
                "واتساب" -> "💬"
                "زيارة" -> "🏠"
                "اجتماع", "فيديو كول" -> "🤝"
                "بريد إلكتروني" -> "✉️"
                "رسالة SMS" -> "📩"
                else -> "📝"
            }
            val activityRows = profile.recentActivities.joinToString("") { a ->
                val icon = profileChannelIcon(a.channel)
                val rail = when (a.channel) {
                    "مكالمة تليفون", "اتصال" -> "border-r-sky-400"
                    "واتساب" -> "border-r-emerald-400"
                    "زيارة" -> "border-r-amber-400"
                    "اجتماع", "فيديو كول" -> "border-r-indigo-400"
                    "بريد إلكتروني" -> "border-r-rose-400"
                    else -> "border-r-gray-200"
                }
                val statusCell = when {
                    a.previousStatus != null && a.newStatus != null && a.previousStatus != a.newStatus -> {
                        val pBg = statusColor(a.previousStatus); val pTx = statusTextColor(a.previousStatus)
                        val nBg = statusColor(a.newStatus); val nTx = statusTextColor(a.newStatus)
                        """<div class="flex items-center gap-1.5 flex-wrap">
                            <span class="px-2 py-0.5 rounded-full text-xs" style="background:$pBg;color:$pTx">${a.previousStatus}</span>
                            <span class="text-gray-400 text-xs">←</span>
                            <span class="px-2 py-0.5 rounded-full text-xs" style="background:$nBg;color:$nTx">${a.newStatus}</span>
                        </div>"""
                    }
                    a.newStatus != null -> {
                        val nBg = statusColor(a.newStatus); val nTx = statusTextColor(a.newStatus)
                        """<span class="inline-block px-2 py-0.5 rounded-full text-xs" style="background:$nBg;color:$nTx">${a.newStatus}</span>"""
                    }
                    else -> """<span class="text-gray-400 text-xs">-</span>"""
                }
                """
                <tr class="border-b border-gray-100 border-r-4 $rail hover:bg-gray-50 transition">
                    <td class="p-3 align-top"><div class="text-xs font-medium text-gray-700 whitespace-nowrap">${formatCrmDate(a.createdAt)}</div></td>
                    <td class="p-3 align-top"><a href="/crm/clients/${a.clientId}" class="text-sm font-medium text-gray-800 hover:text-emerald-600 hover:underline">${a.clientName}</a></td>
                    <td class="p-3 align-top"><span class="inline-block px-2 py-1 rounded-md text-xs bg-gray-100 text-gray-700">${a.actionType ?: "-"}</span></td>
                    <td class="p-3 align-top"><span class="inline-flex items-center gap-1.5 text-sm text-gray-700"><span class="text-lg">$icon</span><span>${a.channel ?: "-"}</span></span></td>
                    <td class="p-3 align-top">$statusCell</td>
                    <td class="p-3 align-top text-sm text-gray-700">${a.result ?: "-"}</td>
                </tr>
                """
            }

            val activitiesSection = """
                <div class="bg-white rounded-2xl shadow-sm border border-gray-100 p-5 mb-6">
                    <div class="flex items-center justify-between mb-4">
                        <h2 class="font-bold text-gray-800 flex items-center gap-2"><span>📜</span><span>آخر الأنشطة</span></h2>
                        <a href="/crm/activities" class="text-xs text-emerald-600 hover:underline">عرض الكل ←</a>
                    </div>
                    ${if (profile.recentActivities.isEmpty()) """
                    <div class="text-center py-8">
                        <div class="text-4xl mb-2">📭</div>
                        <p class="text-gray-400 text-sm">لا توجد أنشطة بعد</p>
                    </div>""" else """
                    <div class="overflow-x-auto">
                        <table class="w-full text-sm">
                            <thead>
                                <tr class="bg-gray-50 border-b">
                                    <th class="p-3 text-right text-xs text-gray-500 uppercase tracking-wider font-medium">التاريخ</th>
                                    <th class="p-3 text-right text-xs text-gray-500 uppercase tracking-wider font-medium">العميل</th>
                                    <th class="p-3 text-right text-xs text-gray-500 uppercase tracking-wider font-medium">الإجراء</th>
                                    <th class="p-3 text-right text-xs text-gray-500 uppercase tracking-wider font-medium">القناة</th>
                                    <th class="p-3 text-right text-xs text-gray-500 uppercase tracking-wider font-medium">تغيير الحالة</th>
                                    <th class="p-3 text-right text-xs text-gray-500 uppercase tracking-wider font-medium">النتيجة</th>
                                </tr>
                            </thead>
                            <tbody>$activityRows</tbody>
                        </table>
                    </div>
                    """}
                </div>
            """.trimIndent()

            // Modals (owner only)
            val modals = if (principal.isOwner) """
                <!-- Set Target Modal -->
                <dialog id="setTargetModal" class="rounded-2xl">
                    <div class="p-6 w-full max-w-md max-h-[90vh] overflow-y-auto">
                        <div class="flex justify-between items-center mb-4">
                            <h3 class="text-lg font-bold">تحديد الهدف الشهري</h3>
                            <button onclick="document.getElementById('setTargetModal').close()" class="text-gray-400 hover:text-gray-600 text-xl">&times;</button>
                        </div>
                        <form onsubmit="submitTarget(event)" class="space-y-3">
                            <input type="hidden" name="month" value="${profile.currentMonth}">
                            <div>
                                <label class="block text-sm font-medium text-gray-700 mb-1">عدد العملاء المستهدف</label>
                                <input type="number" name="targetClients" value="${profile.target?.targetClients ?: 0}" min="0" class="w-full px-3 py-2 border rounded-lg text-sm">
                            </div>
                            <div>
                                <label class="block text-sm font-medium text-gray-700 mb-1">عدد الاشتراكات المستهدف</label>
                                <input type="number" name="targetSubscriptions" value="${profile.target?.targetSubscriptions ?: 0}" min="0" class="w-full px-3 py-2 border rounded-lg text-sm">
                            </div>
                            <div>
                                <label class="block text-sm font-medium text-gray-700 mb-1">الإيراد المستهدف (ج.م)</label>
                                <input type="number" name="targetRevenue" value="${profile.target?.targetRevenue ?: 0.0}" min="0" step="0.01" class="w-full px-3 py-2 border rounded-lg text-sm">
                            </div>
                            <div>
                                <label class="block text-sm font-medium text-gray-700 mb-1">ملاحظات</label>
                                <textarea name="notes" rows="2" class="w-full px-3 py-2 border rounded-lg text-sm">${profile.target?.notes ?: ""}</textarea>
                            </div>
                            <div class="flex justify-end gap-2 pt-3">
                                <button type="button" onclick="document.getElementById('setTargetModal').close()" class="px-4 py-2 text-sm border rounded-lg hover:bg-gray-50">إلغاء</button>
                                <button type="submit" class="px-4 py-2 text-sm text-white rounded-lg hover:opacity-90" style="background:#2E7D32">حفظ</button>
                            </div>
                        </form>
                    </div>
                </dialog>

                <!-- Add Review Modal -->
                <dialog id="addReviewModal" class="rounded-2xl">
                    <div class="p-6 w-full max-w-md max-h-[90vh] overflow-y-auto">
                        <div class="flex justify-between items-center mb-4">
                            <h3 class="text-lg font-bold">إضافة تقييم</h3>
                            <button onclick="document.getElementById('addReviewModal').close()" class="text-gray-400 hover:text-gray-600 text-xl">&times;</button>
                        </div>
                        <form onsubmit="submitReview(event)" class="space-y-3">
                            <div>
                                <label class="block text-sm font-medium text-gray-700 mb-1">الشهر *</label>
                                <select name="month" required class="w-full px-3 py-2 border rounded-lg text-sm">
                                    ${monthOptions()}
                                </select>
                            </div>
                            <div>
                                <label class="block text-sm font-medium text-gray-700 mb-1">التقييم (1-10) *</label>
                                <select name="score" required class="w-full px-3 py-2 border rounded-lg text-sm">
                                    ${(1..10).joinToString("") { """<option value="$it">$it</option>""" }}
                                </select>
                            </div>
                            <div>
                                <label class="block text-sm font-medium text-gray-700 mb-1">الملاحظات *</label>
                                <textarea name="review" required rows="3" class="w-full px-3 py-2 border rounded-lg text-sm"></textarea>
                            </div>
                            <div class="flex justify-end gap-2 pt-3">
                                <button type="button" onclick="document.getElementById('addReviewModal').close()" class="px-4 py-2 text-sm border rounded-lg hover:bg-gray-50">إلغاء</button>
                                <button type="submit" class="px-4 py-2 text-sm text-white rounded-lg hover:opacity-90" style="background:#2E7D32">حفظ</button>
                            </div>
                        </form>
                    </div>
                </dialog>

                <!-- Edit Review Modal -->
                <dialog id="editReviewModal" class="rounded-2xl">
                    <div class="p-6 w-full max-w-md max-h-[90vh] overflow-y-auto">
                        <div class="flex justify-between items-center mb-4">
                            <h3 class="text-lg font-bold">تعديل التقييم</h3>
                            <button onclick="document.getElementById('editReviewModal').close()" class="text-gray-400 hover:text-gray-600 text-xl">&times;</button>
                        </div>
                        <form onsubmit="submitEditReview(event)" class="space-y-3">
                            <input type="hidden" name="reviewId" id="editReviewId">
                            <div>
                                <label class="block text-sm font-medium text-gray-700 mb-1">التقييم (1-10) *</label>
                                <select name="score" id="editReviewScore" required class="w-full px-3 py-2 border rounded-lg text-sm">
                                    ${(1..10).joinToString("") { """<option value="$it">$it</option>""" }}
                                </select>
                            </div>
                            <div>
                                <label class="block text-sm font-medium text-gray-700 mb-1">الملاحظات *</label>
                                <textarea name="review" id="editReviewText" required rows="3" class="w-full px-3 py-2 border rounded-lg text-sm"></textarea>
                            </div>
                            <div class="flex justify-end gap-2 pt-3">
                                <button type="button" onclick="document.getElementById('editReviewModal').close()" class="px-4 py-2 text-sm border rounded-lg hover:bg-gray-50">إلغاء</button>
                                <button type="submit" class="px-4 py-2 text-sm text-white rounded-lg hover:opacity-90" style="background:#1B3A5C">حفظ التعديلات</button>
                            </div>
                        </form>
                    </div>
                </dialog>

                <script>
                async function submitTarget(e) {
                    e.preventDefault();
                    const form = e.target;
                    const data = Object.fromEntries(new FormData(form));
                    data.targetClients = parseInt(data.targetClients) || 0;
                    data.targetSubscriptions = parseInt(data.targetSubscriptions) || 0;
                    data.targetRevenue = parseFloat(data.targetRevenue) || 0;
                    const res = await fetch('/crm/api/agents/$agentId/target', {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(data)});
                    if (res.ok) { location.reload(); } else { alert('حدث خطأ'); }
                }

                async function submitReview(e) {
                    e.preventDefault();
                    const form = e.target;
                    const data = Object.fromEntries(new FormData(form));
                    data.score = parseInt(data.score);
                    const res = await fetch('/crm/api/agents/$agentId/review', {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(data)});
                    if (res.ok) { location.reload(); } else { alert('حدث خطأ'); }
                }

                function openEditReview(id, score, review) {
                    document.getElementById('editReviewId').value = id;
                    document.getElementById('editReviewScore').value = score;
                    document.getElementById('editReviewText').value = review;
                    document.getElementById('editReviewModal').showModal();
                }

                async function submitEditReview(e) {
                    e.preventDefault();
                    const form = e.target;
                    const data = Object.fromEntries(new FormData(form));
                    const reviewId = data.reviewId; delete data.reviewId;
                    data.score = parseInt(data.score);
                    const res = await fetch('/crm/api/agents/$agentId/review/' + reviewId, {method:'PUT', headers:{'Content-Type':'application/json'}, body:JSON.stringify(data)});
                    if (res.ok) { location.reload(); } else { alert('حدث خطأ'); }
                }

                async function togglePin(reviewId, pinned) {
                    const res = await fetch('/crm/api/agents/$agentId/review/' + reviewId, {method:'PUT', headers:{'Content-Type':'application/json'}, body:JSON.stringify({pinned:pinned})});
                    if (res.ok) { location.reload(); } else { alert('حدث خطأ'); }
                }
                </script>
            """.trimIndent() else ""

            val content = """
                $agentCard
                $targetSection
                $salarySection
                $statsSection
                $pinnedSection
                $reviewsSection
                $activitiesSection
                $modals
            """.trimIndent()

            call.respondText(
                crmLayout("الملف الشخصي - ${agent.name}", principal.name, principal.role, principal, "profile", content),
                ContentType.Text.Html
            )
        }

        // ─── System Docs Page ────────────────────────────────────
        get("/crm/docs") {
            val principal = call.principal<CrmPrincipal>()!!
            val content = systemDocsHtml()
            call.respondText(
                crmLayout("دليل النظام", principal.name, principal.role, principal, "docs", content),
                ContentType.Text.Html
            )
        }

        // ─── Super-admin: Organizations management ──────────────────
        // The Waselak ops team manages every CRM tenant from this page. Hidden from
        // every other role — every endpoint here checks `principal.isSuperAdmin`
        // before doing anything.

        get("/crm/super/organizations") {
            val principal = call.principal<CrmPrincipal>()!!
            if (!principal.isSuperAdmin) {
                call.respondRedirect("/crm/dashboard")
                return@get
            }
            val orgs = crmService.listAllOrganizations()
            // KPI strip computes from the same list — one query, no extra DB hits.
            val totalOrgs = orgs.size
            val activeOrgs = orgs.count { it.active }
            val totalAgents = orgs.sumOf { it.agentCount }
            val totalClients = orgs.sumOf { it.clientCount }

            val rows = buildString {
                orgs.forEach { o ->
                    val isWaselak = o.name == "Waselak"
                    val statusBadge = if (o.active)
                        """<span class="px-2 py-0.5 rounded-full text-xs bg-emerald-50 text-emerald-700">نشط</span>"""
                    else
                        """<span class="px-2 py-0.5 rounded-full text-xs bg-gray-100 text-gray-600">معلّق</span>"""
                    val deleteBtn = if (isWaselak) {
                        """<span class="text-xs text-gray-400" title="لا يمكن حذف منظمة Waselak الافتراضية">🔒</span>"""
                    } else {
                        """<button onclick="deleteOrg('${o.id}', '${o.name.replace("'", "\\'")}', ${o.clientCount}, ${o.agentCount})"
                                class="p-1.5 rounded hover:bg-red-50 text-red-600 transition" title="حذف نهائي">🗑️</button>"""
                    }
                    val toggleBtn = if (isWaselak) "" else if (o.active)
                        """<button onclick="toggleActive('${o.id}', false)" class="p-1.5 rounded hover:bg-amber-50 text-amber-600" title="تعليق">⏸️</button>"""
                    else
                        """<button onclick="toggleActive('${o.id}', true)" class="p-1.5 rounded hover:bg-emerald-50 text-emerald-600" title="تفعيل">▶️</button>"""
                    // Logo / fallback initial avatar — same colour palette idea as the
                    // clients list. Stable hash → consistent colour per org.
                    val palette = listOf("#0EA5E9", "#10B981", "#F59E0B", "#EF4444", "#8B5CF6", "#EC4899", "#14B8A6", "#F97316")
                    val avatarColor = palette[(o.name.hashCode().let { if (it < 0) -it else it }) % palette.size]
                    val initial = o.name.trim().firstOrNull()?.toString() ?: "؟"
                    // Cache-bust the image URL keyed by org id + (now / 60s) so a
                    // freshly-uploaded logo appears on the next page load even if the
                    // browser cached the previous version of the same file path. The
                    // resolution is one minute — fine for a UI refresh, doesn't bust
                    // the cache for visitors who aren't editing logos.
                    val cacheBust = System.currentTimeMillis() / 60_000
                    val logoHtml = if (!o.logoUrl.isNullOrBlank())
                        """<img src="${o.logoUrl}?v=$cacheBust" alt="${o.name}" class="w-9 h-9 rounded-lg object-cover border border-gray-200">"""
                    else
                        """<div class="w-9 h-9 rounded-lg flex items-center justify-center text-white font-bold text-sm" style="background:$avatarColor">$initial</div>"""
                    val safeName = o.name.replace("'", "\\'").replace("\"", "&quot;")
                    val safeEmail = (o.contactEmail ?: "").replace("'", "\\'").replace("\"", "&quot;")
                    val safePhone = (o.contactPhone ?: "").replace("'", "\\'").replace("\"", "&quot;")
                    val safeLogo  = (o.logoUrl ?: "").replace("'", "\\'").replace("\"", "&quot;")
                    val ownerEmailDisplay = o.ownerEmail?.let {
                        """<div class="text-xs text-sky-600 mt-0.5" dir="ltr">👤 $it</div>"""
                    } ?: ""
                    // Edit + reset-password — disabled for Waselak (we don't reset our
                    // own super-admin from this UI; we own the database directly).
                    val detailsBtn = """<a href="/crm/super/organizations/${o.id}"
                            class="p-1.5 rounded hover:bg-indigo-50 text-indigo-600" title="عرض التفاصيل والموظفين">👁️</a>"""
                    val editBtn = """<button onclick="openEditOrg('${o.id}','$safeName','$safeEmail','$safePhone','${o.planTier}','$safeLogo')"
                            class="p-1.5 rounded hover:bg-sky-50 text-sky-600" title="تعديل">✏️</button>"""
                    val resetBtn = if (isWaselak) "" else
                        """<button onclick="openResetPwd('${o.id}','$safeName','${o.ownerEmail ?: ""}')"
                            class="p-1.5 rounded hover:bg-purple-50 text-purple-600" title="تغيير كلمة سر صاحب الحساب">🔑</button>"""
                    append("""<tr class="border-b border-gray-100 hover:bg-gray-50">
                        <td class='p-3'>
                            <div class="flex items-center gap-3">
                                $logoHtml
                                <div>
                                    <div class="font-semibold text-gray-800">${o.name}</div>
                                    ${o.contactEmail?.let { """<div class="text-xs text-gray-500" dir="ltr">$it</div>""" } ?: ""}
                                    ${o.contactPhone?.let { """<div class="text-xs text-gray-500" dir="ltr">📞 $it</div>""" } ?: ""}
                                    $ownerEmailDisplay
                                </div>
                            </div>
                        </td>
                        <td class='p-3'><span class="text-xs px-2 py-0.5 rounded bg-slate-100 text-slate-700">${o.planTier}</span></td>
                        <td class='p-3'>$statusBadge</td>
                        <td class='p-3 text-sm text-gray-700'>${o.agentCount} موظف</td>
                        <td class='p-3 text-sm text-gray-700'>${o.clientCount} عميل</td>
                        <td class='p-3 text-xs text-gray-600 whitespace-nowrap'>${formatCrmDate(o.createdAt)}</td>
                        <td class='p-3'>
                            <div class="flex items-center gap-1 justify-end">$detailsBtn $editBtn $resetBtn $toggleBtn $deleteBtn</div>
                        </td>
                    </tr>""")
                }
            }

            val content = """
                <!-- Header -->
                <div class="flex items-center justify-between mb-6 flex-wrap gap-3">
                    <div>
                        <h2 class="text-2xl font-bold text-gray-800">المنظمات (Tenants)</h2>
                        <p class="text-sm text-gray-500 mt-1">إدارة كل منظمات الـ CRM وحساباتها</p>
                    </div>
                    <button onclick="document.getElementById('createOrgModal').showModal()"
                            class="flex items-center gap-2 bg-emerald-600 hover:bg-emerald-700 text-white px-5 py-2.5 rounded-xl shadow-sm font-medium">
                        <span class="text-lg">＋</span><span>منظمة جديدة</span>
                    </button>
                </div>

                <!-- KPI strip -->
                <div class="grid grid-cols-2 lg:grid-cols-4 gap-3 mb-5">
                    <div class="bg-white rounded-xl p-4 border border-gray-100 shadow-sm">
                        <p class="text-xs text-gray-500">إجمالي المنظمات</p>
                        <p class="text-2xl font-bold text-gray-800 mt-1">$totalOrgs</p>
                    </div>
                    <div class="bg-white rounded-xl p-4 border border-gray-100 shadow-sm">
                        <p class="text-xs text-gray-500">منظمات نشطة</p>
                        <p class="text-2xl font-bold text-emerald-600 mt-1">$activeOrgs</p>
                    </div>
                    <div class="bg-white rounded-xl p-4 border border-gray-100 shadow-sm">
                        <p class="text-xs text-gray-500">إجمالي الموظفين</p>
                        <p class="text-2xl font-bold text-sky-600 mt-1">$totalAgents</p>
                    </div>
                    <div class="bg-white rounded-xl p-4 border border-gray-100 shadow-sm">
                        <p class="text-xs text-gray-500">إجمالي العملاء</p>
                        <p class="text-2xl font-bold text-amber-600 mt-1">$totalClients</p>
                    </div>
                </div>

                <!-- Table -->
                <div class="bg-white rounded-xl shadow-sm overflow-x-auto border border-gray-100">
                    <table class="w-full text-sm">
                        <thead>
                            <tr class="bg-gray-50 border-b">
                                <th class="p-3 text-right text-xs text-gray-500 uppercase tracking-wider font-medium">المنظمة</th>
                                <th class="p-3 text-right text-xs text-gray-500 uppercase tracking-wider font-medium">الباقة</th>
                                <th class="p-3 text-right text-xs text-gray-500 uppercase tracking-wider font-medium">الحالة</th>
                                <th class="p-3 text-right text-xs text-gray-500 uppercase tracking-wider font-medium">الموظفين</th>
                                <th class="p-3 text-right text-xs text-gray-500 uppercase tracking-wider font-medium">العملاء</th>
                                <th class="p-3 text-right text-xs text-gray-500 uppercase tracking-wider font-medium">تاريخ الإضافة</th>
                                <th class="p-3 text-right text-xs text-gray-500 uppercase tracking-wider font-medium"></th>
                            </tr>
                        </thead>
                        <tbody>$rows</tbody>
                    </table>
                </div>

                <!-- Create Modal -->
                <dialog id="createOrgModal" class="rounded-2xl">
                    <div class="p-6">
                        <div class="flex justify-between items-center mb-4">
                            <h3 class="text-lg font-bold">إنشاء منظمة جديدة</h3>
                            <button onclick="document.getElementById('createOrgModal').close()" class="text-gray-400 hover:text-gray-600 text-xl">&times;</button>
                        </div>
                        <form onsubmit="submitNewOrg(event)" class="space-y-3 max-h-[75vh] overflow-y-auto">
                            <!-- Logo upload — happens BEFORE the org is created via
                                 a pre-creation upload endpoint. The returned URL is
                                 stashed in the hidden #createLogoUrl input and sent
                                 as part of the create JSON. Cancelling the dialog
                                 leaves the orphan file under uploads/crm-photos/
                                 (named pending-*.png) — a tiny known leak that's
                                 fine until we add a periodic janitor. -->
                            <div class="flex items-center gap-4 p-3 bg-gray-50 rounded-lg">
                                <img id="createLogoPreview" src="" alt="logo" class="w-16 h-16 rounded-lg object-cover border border-gray-200 hidden">
                                <div id="createLogoFallback" class="w-16 h-16 rounded-lg flex items-center justify-center text-gray-400 bg-gray-200 text-2xl">⌂</div>
                                <div class="flex-1">
                                    <label class="block text-xs text-gray-600 mb-1">شعار المنظمة (اختياري)</label>
                                    <input type="file" id="createLogoFile" accept="image/*" class="text-xs" onchange="uploadPendingLogo(this)">
                                    <input type="hidden" id="createLogoUrl" name="logoUrl">
                                    <p class="text-xs text-gray-400 mt-1">PNG/JPG/SVG حتى 5MB</p>
                                </div>
                            </div>
                            <div class="grid grid-cols-2 gap-3">
                                <div class="col-span-2">
                                    <label class="block text-sm font-medium text-gray-700 mb-1">اسم المنظمة *</label>
                                    <input type="text" name="name" required class="w-full px-3 py-2 border rounded-lg text-sm">
                                </div>
                                <div>
                                    <label class="block text-sm font-medium text-gray-700 mb-1">إيميل المنظمة</label>
                                    <input type="email" name="contactEmail" class="w-full px-3 py-2 border rounded-lg text-sm" dir="ltr">
                                </div>
                                <div>
                                    <label class="block text-sm font-medium text-gray-700 mb-1">رقم المنظمة</label>
                                    <input type="text" name="contactPhone" class="w-full px-3 py-2 border rounded-lg text-sm" dir="ltr">
                                </div>
                                <div class="col-span-2">
                                    <label class="block text-sm font-medium text-gray-700 mb-1">الباقة</label>
                                    <select name="planTier" class="w-full px-3 py-2 border rounded-lg text-sm">
                                        <option value="starter">Starter</option>
                                        <option value="pro">Pro</option>
                                        <option value="enterprise">Enterprise</option>
                                        <option value="internal">Internal</option>
                                    </select>
                                </div>
                                <div class="col-span-2 mt-2 border-t pt-3">
                                    <h4 class="text-sm font-bold text-gray-700 mb-2">صاحب الحساب</h4>
                                </div>
                                <div>
                                    <label class="block text-sm font-medium text-gray-700 mb-1">اسم صاحب الحساب *</label>
                                    <input type="text" name="ownerName" required class="w-full px-3 py-2 border rounded-lg text-sm">
                                </div>
                                <div>
                                    <label class="block text-sm font-medium text-gray-700 mb-1">رقم صاحب الحساب</label>
                                    <input type="text" name="ownerPhone" class="w-full px-3 py-2 border rounded-lg text-sm" dir="ltr">
                                </div>
                                <div class="col-span-2">
                                    <label class="block text-sm font-medium text-gray-700 mb-1">إيميل صاحب الحساب *</label>
                                    <input type="email" name="ownerEmail" required class="w-full px-3 py-2 border rounded-lg text-sm" dir="ltr" autocomplete="off" autocapitalize="none">
                                </div>
                                <div class="col-span-2">
                                    <label class="block text-sm font-medium text-gray-700 mb-1">كلمة سر مبدئية * (6 حروف على الأقل)</label>
                                    <input type="text" name="ownerPassword" required minlength="6" class="w-full px-3 py-2 border rounded-lg text-sm font-mono" dir="ltr" autocomplete="off">
                                </div>
                            </div>
                            <div class="flex justify-end gap-2 pt-3">
                                <button type="button" onclick="document.getElementById('createOrgModal').close()" class="px-4 py-2 text-sm border rounded-lg hover:bg-gray-50">إلغاء</button>
                                <button type="submit" class="px-4 py-2 text-sm text-white rounded-lg bg-emerald-600 hover:bg-emerald-700">إنشاء</button>
                            </div>
                        </form>
                    </div>
                </dialog>

                <!-- Edit Modal -->
                <dialog id="editOrgModal" class="rounded-2xl">
                    <div class="p-6">
                        <div class="flex justify-between items-center mb-4">
                            <h3 class="text-lg font-bold">تعديل المنظمة</h3>
                            <button onclick="document.getElementById('editOrgModal').close()" class="text-gray-400 hover:text-gray-600 text-xl">&times;</button>
                        </div>
                        <form onsubmit="submitEditOrg(event)" class="space-y-3 max-h-[75vh] overflow-y-auto">
                            <input type="hidden" id="editOrgId" name="id">
                            <!-- Logo preview + upload -->
                            <div class="flex items-center gap-4 p-3 bg-gray-50 rounded-lg">
                                <img id="editLogoPreview" src="" alt="logo" class="w-16 h-16 rounded-lg object-cover border border-gray-200 hidden">
                                <div id="editLogoFallback" class="w-16 h-16 rounded-lg flex items-center justify-center text-white font-bold text-xl bg-gray-300">⌂</div>
                                <div class="flex-1">
                                    <label class="block text-xs text-gray-600 mb-1">شعار المنظمة (اختياري)</label>
                                    <input type="file" id="editLogoFile" accept="image/*" class="text-xs" onchange="uploadOrgLogo(this)">
                                    <input type="hidden" id="editLogoUrl" name="logoUrl">
                                    <p class="text-xs text-gray-400 mt-1">PNG/JPG/SVG حتى 2MB</p>
                                </div>
                            </div>
                            <div class="grid grid-cols-2 gap-3">
                                <div class="col-span-2">
                                    <label class="block text-sm font-medium text-gray-700 mb-1">اسم المنظمة *</label>
                                    <input type="text" id="editOrgName" name="name" required class="w-full px-3 py-2 border rounded-lg text-sm">
                                </div>
                                <div>
                                    <label class="block text-sm font-medium text-gray-700 mb-1">إيميل التواصل</label>
                                    <input type="email" id="editOrgContactEmail" name="contactEmail" class="w-full px-3 py-2 border rounded-lg text-sm" dir="ltr">
                                </div>
                                <div>
                                    <label class="block text-sm font-medium text-gray-700 mb-1">رقم التواصل</label>
                                    <input type="text" id="editOrgContactPhone" name="contactPhone" class="w-full px-3 py-2 border rounded-lg text-sm" dir="ltr">
                                </div>
                                <div class="col-span-2">
                                    <label class="block text-sm font-medium text-gray-700 mb-1">الباقة</label>
                                    <select id="editOrgPlanTier" name="planTier" class="w-full px-3 py-2 border rounded-lg text-sm">
                                        <option value="starter">Starter</option>
                                        <option value="pro">Pro</option>
                                        <option value="enterprise">Enterprise</option>
                                        <option value="internal">Internal</option>
                                    </select>
                                </div>
                            </div>
                            <div class="flex justify-end gap-2 pt-3">
                                <button type="button" onclick="document.getElementById('editOrgModal').close()" class="px-4 py-2 text-sm border rounded-lg hover:bg-gray-50">إلغاء</button>
                                <button type="submit" class="px-4 py-2 text-sm text-white rounded-lg bg-sky-600 hover:bg-sky-700">حفظ التعديلات</button>
                            </div>
                        </form>
                    </div>
                </dialog>

                <!-- Reset password modal -->
                <dialog id="resetPwdModal" class="rounded-2xl">
                    <div class="p-6 max-w-md">
                        <div class="flex justify-between items-center mb-4">
                            <h3 class="text-lg font-bold">تغيير كلمة سر صاحب الحساب</h3>
                            <button onclick="document.getElementById('resetPwdModal').close()" class="text-gray-400 hover:text-gray-600 text-xl">&times;</button>
                        </div>
                        <form onsubmit="submitResetPwd(event)" class="space-y-3">
                            <input type="hidden" id="resetOrgId">
                            <div class="text-sm text-gray-600 bg-purple-50 p-3 rounded-lg">
                                <div>المنظمة: <span id="resetOrgName" class="font-bold"></span></div>
                                <div class="mt-1">إيميل صاحب الحساب: <span id="resetOwnerEmail" class="font-mono text-xs" dir="ltr"></span></div>
                            </div>
                            <div>
                                <label class="block text-sm font-medium text-gray-700 mb-1">كلمة السر الجديدة (6 حروف على الأقل)</label>
                                <input type="text" id="resetNewPwd" required minlength="6" class="w-full px-3 py-2 border rounded-lg text-sm font-mono" dir="ltr" autocomplete="off">
                                <p class="text-xs text-gray-500 mt-1">سيُسجَّل صاحب الحساب بالكلمة الجديدة بعد الحفظ مباشرة. أرسلها له بشكل آمن.</p>
                            </div>
                            <div class="flex justify-end gap-2 pt-2">
                                <button type="button" onclick="document.getElementById('resetPwdModal').close()" class="px-4 py-2 text-sm border rounded-lg hover:bg-gray-50">إلغاء</button>
                                <button type="submit" class="px-4 py-2 text-sm text-white rounded-lg bg-purple-600 hover:bg-purple-700">حفظ كلمة السر الجديدة</button>
                            </div>
                        </form>
                    </div>
                </dialog>

                <!-- Success modal: shows the freshly-created credentials with a copy
                     button. Replaces the cramped browser alert() that some users
                     dismiss before reading. -->
                <dialog id="newOrgSuccessModal" class="rounded-2xl">
                    <div class="p-6 max-w-md">
                        <div class="text-center">
                            <div class="text-4xl mb-2">✅</div>
                            <h3 class="text-lg font-bold text-gray-800">تم إنشاء المنظمة بنجاح</h3>
                            <p class="text-sm text-gray-500 mt-1">أرسل بيانات الدخول للعميل بشكل آمن</p>
                        </div>
                        <div class="bg-gradient-to-br from-emerald-50 to-sky-50 border border-emerald-100 rounded-xl p-4 mt-4 space-y-2">
                            <div>
                                <div class="text-xs text-gray-500">اسم المنظمة</div>
                                <div id="successOrgName" class="font-bold text-gray-800"></div>
                            </div>
                            <div>
                                <div class="text-xs text-gray-500">اسم صاحب الحساب</div>
                                <div id="successOwnerName" class="font-bold text-gray-800"></div>
                            </div>
                            <div>
                                <div class="text-xs text-gray-500">الإيميل</div>
                                <div id="successOwnerEmail" class="font-mono text-sm text-sky-700" dir="ltr"></div>
                            </div>
                            <div>
                                <div class="text-xs text-gray-500">كلمة السر</div>
                                <div id="successOwnerPassword" class="font-mono text-sm text-purple-700" dir="ltr"></div>
                            </div>
                        </div>
                        <div class="flex justify-end gap-2 pt-4">
                            <button onclick="copySuccessCredentials()" class="px-4 py-2 text-sm border rounded-lg hover:bg-gray-50">📋 نسخ البيانات</button>
                            <button onclick="closeSuccessModal()" class="px-4 py-2 text-sm text-white rounded-lg bg-emerald-600 hover:bg-emerald-700">تم</button>
                        </div>
                    </div>
                </dialog>

                <script>
                // ── Create ────────────────────────────────────────────
                async function uploadPendingLogo(input) {
                    if (!input.files || !input.files[0]) return;
                    const file = input.files[0];
                    if (file.size > 5 * 1024 * 1024) { alert('الحجم أكبر من 5MB. اضغط الصورة قبل الرفع.'); input.value = ''; return; }
                    const fd = new FormData();
                    fd.append('file', file);
                    try {
                        const res = await fetch('/crm/api/super/uploads/logo', { method:'POST', body: fd });
                        if (!res.ok) {
                            const err = await res.json().catch(() => ({}));
                            alert(err.error || ('فشل رفع الشعار (' + res.status + ')'));
                            input.value = '';
                            return;
                        }
                        const body = await res.json();
                        document.getElementById('createLogoUrl').value = body.url;
                        const preview = document.getElementById('createLogoPreview');
                        preview.src = body.url + '?t=' + Date.now();
                        preview.classList.remove('hidden');
                        document.getElementById('createLogoFallback').classList.add('hidden');
                    } catch (e) {
                        alert('خطأ في الشبكة أثناء رفع الشعار');
                        input.value = '';
                    }
                }
                async function submitNewOrg(e) {
                    e.preventDefault();
                    const form = e.target;
                    const data = Object.fromEntries(new FormData(form));
                    // Normalise the email client-side so what the operator sees in
                    // the success modal is exactly what the server stored.
                    if (data.ownerEmail) data.ownerEmail = data.ownerEmail.trim().toLowerCase();
                    const submitted = data.ownerPassword;
                    const res = await fetch('/crm/api/super/organizations', {
                        method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify(data)
                    });
                    if (res.ok) {
                        const body = await res.json();
                        document.getElementById('createOrgModal').close();
                        document.getElementById('successOrgName').textContent = body.organization_name || data.name;
                        document.getElementById('successOwnerName').textContent = body.owner_name || data.ownerName;
                        document.getElementById('successOwnerEmail').textContent = body.owner_email || data.ownerEmail;
                        document.getElementById('successOwnerPassword').textContent = submitted;
                        document.getElementById('newOrgSuccessModal').showModal();
                        form.reset();
                    } else {
                        const err = await res.json().catch(() => ({}));
                        alert(err.error || err.message || 'حدث خطأ أثناء إنشاء المنظمة');
                    }
                }
                function closeSuccessModal() {
                    document.getElementById('newOrgSuccessModal').close();
                    location.reload();
                }
                function copySuccessCredentials() {
                    const txt = 'منظمة: ' + document.getElementById('successOrgName').textContent +
                        '\n' + 'اسم صاحب الحساب: ' + document.getElementById('successOwnerName').textContent +
                        '\n' + 'الإيميل: ' + document.getElementById('successOwnerEmail').textContent +
                        '\n' + 'كلمة السر: ' + document.getElementById('successOwnerPassword').textContent +
                        '\n' + 'رابط الدخول: ' + window.location.origin + '/crm/login';
                    navigator.clipboard.writeText(txt).then(
                        () => { alert('تم النسخ ✅'); },
                        () => { alert('فشل النسخ — انسخ يدويًا'); }
                    );
                }

                // ── Edit ──────────────────────────────────────────────
                function openEditOrg(id, name, contactEmail, contactPhone, planTier, logoUrl) {
                    document.getElementById('editOrgId').value = id;
                    document.getElementById('editOrgName').value = name || '';
                    document.getElementById('editOrgContactEmail').value = contactEmail || '';
                    document.getElementById('editOrgContactPhone').value = contactPhone || '';
                    document.getElementById('editOrgPlanTier').value = planTier || 'starter';
                    document.getElementById('editLogoUrl').value = logoUrl || '';
                    const preview = document.getElementById('editLogoPreview');
                    const fallback = document.getElementById('editLogoFallback');
                    if (logoUrl) {
                        preview.src = logoUrl;
                        preview.classList.remove('hidden');
                        fallback.classList.add('hidden');
                    } else {
                        preview.classList.add('hidden');
                        fallback.classList.remove('hidden');
                        fallback.textContent = (name || '؟').trim().charAt(0);
                    }
                    document.getElementById('editLogoFile').value = '';
                    document.getElementById('editOrgModal').showModal();
                }
                async function uploadOrgLogo(input) {
                    if (!input.files || !input.files[0]) return;
                    const file = input.files[0];
                    if (file.size > 5 * 1024 * 1024) { alert('الحجم أكبر من 5MB. اضغط الصورة قبل الرفع.'); input.value = ''; return; }
                    const orgId = document.getElementById('editOrgId').value;
                    if (!orgId) { alert('برجاء فتح المنظمة من زر التعديل أولاً'); return; }
                    const btn = document.querySelector('#editOrgModal button[type=submit]');
                    if (btn) { btn.disabled = true; btn.textContent = '⏳ جاري الرفع...'; }
                    try {
                        const fd = new FormData();
                        fd.append('file', file);
                        const res = await fetch('/crm/api/super/organizations/' + orgId + '/logo', { method:'POST', body: fd });
                        if (!res.ok) {
                            const err = await res.json().catch(() => ({}));
                            alert(err.error || ('فشل رفع الشعار (' + res.status + ')'));
                            return;
                        }
                        const body = await res.json();
                        document.getElementById('editLogoUrl').value = body.url;
                        const preview = document.getElementById('editLogoPreview');
                        // Cache-buster (?t=...) so the browser refetches even if it
                        // cached an earlier version of the same path.
                        preview.src = body.url + '?t=' + Date.now();
                        preview.classList.remove('hidden');
                        document.getElementById('editLogoFallback').classList.add('hidden');
                    } catch (e) {
                        alert('خطأ في الشبكة أثناء رفع الشعار');
                    } finally {
                        if (btn) { btn.disabled = false; btn.textContent = 'حفظ التعديلات'; }
                    }
                }
                async function submitEditOrg(e) {
                    e.preventDefault();
                    const form = e.target;
                    const data = Object.fromEntries(new FormData(form));
                    const id = data.id;
                    delete data.id;
                    const res = await fetch('/crm/api/super/organizations/' + id, {
                        method:'PATCH', headers:{'Content-Type':'application/json'}, body: JSON.stringify(data)
                    });
                    if (res.ok) { document.getElementById('editOrgModal').close(); location.reload(); }
                    else { const err = await res.json().catch(()=>({})); alert(err.error || 'حدث خطأ'); }
                }

                // ── Reset owner password ─────────────────────────────
                function openResetPwd(id, name, ownerEmail) {
                    document.getElementById('resetOrgId').value = id;
                    document.getElementById('resetOrgName').textContent = name;
                    document.getElementById('resetOwnerEmail').textContent = ownerEmail || '(لا يوجد)';
                    document.getElementById('resetNewPwd').value = '';
                    document.getElementById('resetPwdModal').showModal();
                }
                async function submitResetPwd(e) {
                    e.preventDefault();
                    const id = document.getElementById('resetOrgId').value;
                    const pwd = document.getElementById('resetNewPwd').value.trim();
                    if (pwd.length < 6) { alert('كلمة السر يجب أن تكون 6 حروف على الأقل'); return; }
                    const res = await fetch('/crm/api/super/organizations/' + id + '/reset-password', {
                        method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({password: pwd})
                    });
                    if (res.ok) {
                        const body = await res.json();
                        document.getElementById('resetPwdModal').close();
                        alert('تم تحديث كلمة السر ✅\n\nالإيميل: ' + body.owner_email + '\nكلمة السر الجديدة: ' + pwd + '\n\nأرسلها للعميل بشكل آمن.');
                    } else {
                        const err = await res.json().catch(()=>({}));
                        alert(err.error || 'حدث خطأ');
                    }
                }

                // ── Active toggle / Delete (unchanged) ────────────────
                async function toggleActive(id, makeActive) {
                    const verb = makeActive ? 'تفعيل' : 'تعليق';
                    if (!confirm(verb + ' هذه المنظمة؟')) return;
                    const res = await fetch('/crm/api/super/organizations/' + id + '/active', {
                        method:'PATCH', headers:{'Content-Type':'application/json'}, body: JSON.stringify({active: makeActive})
                    });
                    if (res.ok) location.reload();
                    else alert('حدث خطأ');
                }
                async function deleteOrg(id, name, clientCount, agentCount) {
                    if (!confirm('⚠️ حذف نهائي للمنظمة "' + name + '"؟\n\nسيتم مسح:\n• ' + agentCount + ' موظف\n• ' + clientCount + ' عميل\n• كل الأنشطة والفواتير والمدفوعات والرواتب\n\nهذا الإجراء لا يمكن التراجع عنه.')) return;
                    const typed = prompt('للتأكيد، اكتب اسم المنظمة:\n\n' + name);
                    if (typed !== name) { alert('الاسم غير مطابق - تم الإلغاء'); return; }
                    const res = await fetch('/crm/api/super/organizations/' + id, {method:'DELETE'});
                    if (res.ok) { alert('تم الحذف'); location.reload(); }
                    else { const err = await res.json().catch(()=>({})); alert(err.error || err.message || 'حدث خطأ'); }
                }
                </script>
            """.trimIndent()

            call.respondText(
                crmLayout("المنظمات", principal.name, principal.role, principal, "super_orgs", content),
                ContentType.Text.Html,
            )
        }

        // ─── Super-admin: single-org detail page ────────────────
        // A focused dashboard for one tenant org. Shows the org metadata,
        // KPI strip, every agent with per-row actions (reset password,
        // suspend/activate, delete), and a billing summary. Reachable from
        // an "تفاصيل" link on the main organizations table.
        get("/crm/super/organizations/{id}") {
            val principal = call.principal<CrmPrincipal>()!!
            if (!principal.isSuperAdmin) {
                call.respondRedirect("/crm/dashboard")
                return@get
            }
            val orgId = call.parameters["id"] ?: return@get call.respondRedirect("/crm/super/organizations")
            val detail = crmService.getOrganizationDetail(orgId)
            if (detail == null) {
                call.respondRedirect("/crm/super/organizations")
                return@get
            }

            val cacheBust = System.currentTimeMillis() / 60_000
            val palette = listOf("#0EA5E9", "#10B981", "#F59E0B", "#EF4444", "#8B5CF6", "#EC4899", "#14B8A6", "#F97316")
            val initialColor = palette[(detail.name.hashCode().let { if (it < 0) -it else it }) % palette.size]
            val initial = detail.name.trim().firstOrNull()?.toString() ?: "؟"
            val orgLogoHtml = if (!detail.logoUrl.isNullOrBlank())
                """<img src="${detail.logoUrl}?v=$cacheBust" alt="${detail.name}" class="w-20 h-20 rounded-2xl object-cover border border-gray-200">"""
            else
                """<div class="w-20 h-20 rounded-2xl flex items-center justify-center text-white font-bold text-3xl" style="background:$initialColor">$initial</div>"""

            val statusBadge = if (detail.active)
                """<span class="px-3 py-1 rounded-full text-xs bg-emerald-50 text-emerald-700 font-medium">نشط</span>"""
            else
                """<span class="px-3 py-1 rounded-full text-xs bg-gray-100 text-gray-600 font-medium">معلّق</span>"""

            // Currency formatter — Egyptian pounds, no fractional digits for
            // the dashboard chrome (we still keep cents internally; the user
            // doesn't need sub-pound precision in the KPI strip).
            fun money(v: Double) = "${"%,d".format(v.toLong())} ج.م"

            // Agents block
            val agentRows = buildString {
                detail.agents.forEach { a ->
                    val isOwner = a.role == "owner" || a.role == "super_admin"
                    val photo = agentPhotoHtml(a.photoUrl, a.name, 36)
                    val activeBadge = if (a.active)
                        """<span class="px-2 py-0.5 rounded-full text-xs bg-emerald-50 text-emerald-700">نشط</span>"""
                    else
                        """<span class="px-2 py-0.5 rounded-full text-xs bg-red-50 text-red-700">معطل</span>"""
                    val ownerBadge = if (isOwner)
                        """<span class="ml-1 px-1.5 py-0.5 rounded text-[10px] bg-amber-50 text-amber-700 font-medium">${if (a.role == "super_admin") "مدير عام" else "مالك"}</span>"""
                    else ""
                    val safeName = a.name.replace("'", "\\'").replace("\"", "&quot;")
                    val resetBtn = """<button onclick="resetAgentPwd('${a.id}','$safeName')"
                            class="p-1.5 rounded hover:bg-purple-50 text-purple-600" title="تغيير كلمة السر">🔑</button>"""
                    val toggleBtn = if (isOwner) "" else if (a.active)
                        """<button onclick="toggleAgentActive('${a.id}', false)" class="p-1.5 rounded hover:bg-amber-50 text-amber-600" title="تعطيل">⏸️</button>"""
                    else
                        """<button onclick="toggleAgentActive('${a.id}', true)" class="p-1.5 rounded hover:bg-emerald-50 text-emerald-600" title="تفعيل">▶️</button>"""
                    val deleteBtn = if (isOwner)
                        """<span class="p-1.5 text-gray-300" title="لا يمكن حذف المالك من هنا — احذف المنظمة كاملة">🔒</span>"""
                    else
                        """<button onclick="deleteAgent('${a.id}','$safeName')" class="p-1.5 rounded hover:bg-red-50 text-red-600" title="حذف الموظف">🗑️</button>"""
                    val phoneLine = a.phone?.let { """<div class="text-xs text-gray-500" dir="ltr">📞 $it</div>""" } ?: ""
                    append("""<tr class="border-b border-gray-100 hover:bg-gray-50">
                        <td class='p-3'>
                            <div class="flex items-center gap-3">
                                $photo
                                <div>
                                    <div class="font-semibold text-gray-800">${a.name}$ownerBadge</div>
                                    <div class="text-xs text-gray-500" dir="ltr">${a.email}</div>
                                    $phoneLine
                                </div>
                            </div>
                        </td>
                        <td class='p-3 text-sm text-gray-700'>${roleDisplayName(a.role)}</td>
                        <td class='p-3'>$activeBadge</td>
                        <td class='p-3 text-xs text-gray-500 whitespace-nowrap'>${formatCrmDate(a.createdAt)}</td>
                        <td class='p-3'>
                            <div class="flex items-center gap-1 justify-end">$resetBtn $toggleBtn $deleteBtn</div>
                        </td>
                    </tr>""")
                }
            }

            val content = """
                <!-- Back link -->
                <a href="/crm/super/organizations" class="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 mb-4">
                    <span>→</span><span>كل المنظمات</span>
                </a>

                <!-- Org header card -->
                <div class="bg-white rounded-2xl shadow-sm p-6 mb-5 border border-gray-100">
                    <div class="flex items-start gap-5 flex-wrap">
                        $orgLogoHtml
                        <div class="flex-1 min-w-[260px]">
                            <div class="flex items-center gap-2 flex-wrap">
                                <h2 class="text-2xl font-bold text-gray-800">${detail.name}</h2>
                                $statusBadge
                                <span class="px-2 py-0.5 rounded text-xs bg-slate-100 text-slate-700">${detail.planTier}</span>
                            </div>
                            <div class="mt-3 grid grid-cols-1 md:grid-cols-2 gap-2 text-sm">
                                ${detail.contactEmail?.let { """<div class="text-gray-600"><span class="text-gray-400">إيميل المنظمة:</span> <span dir="ltr">$it</span></div>""" } ?: ""}
                                ${detail.contactPhone?.let { """<div class="text-gray-600"><span class="text-gray-400">رقم المنظمة:</span> <span dir="ltr">$it</span></div>""" } ?: ""}
                                <div class="text-gray-600"><span class="text-gray-400">تاريخ الإنشاء:</span> ${formatCrmDate(detail.createdAt)}</div>
                                <div class="text-gray-600"><span class="text-gray-400">آخر تعديل:</span> ${formatCrmDate(detail.updatedAt)}</div>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- KPI strip -->
                <div class="grid grid-cols-2 lg:grid-cols-6 gap-3 mb-5">
                    <div class="bg-white rounded-xl p-4 border border-gray-100 shadow-sm">
                        <p class="text-xs text-gray-500">الموظفين</p>
                        <p class="text-2xl font-bold text-sky-600 mt-1">${detail.agents.size}</p>
                    </div>
                    <div class="bg-white rounded-xl p-4 border border-gray-100 shadow-sm">
                        <p class="text-xs text-gray-500">العملاء</p>
                        <p class="text-2xl font-bold text-amber-600 mt-1">${detail.clientCount}</p>
                    </div>
                    <div class="bg-white rounded-xl p-4 border border-gray-100 shadow-sm">
                        <p class="text-xs text-gray-500">الأنشطة</p>
                        <p class="text-2xl font-bold text-purple-600 mt-1">${detail.activityCount}</p>
                    </div>
                    <div class="bg-white rounded-xl p-4 border border-gray-100 shadow-sm">
                        <p class="text-xs text-gray-500">عدد الفواتير</p>
                        <p class="text-2xl font-bold text-gray-800 mt-1">${detail.invoiceCount}</p>
                    </div>
                    <div class="bg-white rounded-xl p-4 border border-gray-100 shadow-sm">
                        <p class="text-xs text-gray-500">إجمالي الفواتير</p>
                        <p class="text-lg font-bold text-emerald-600 mt-1">${money(detail.invoiceTotal)}</p>
                    </div>
                    <div class="bg-white rounded-xl p-4 border border-gray-100 shadow-sm">
                        <p class="text-xs text-gray-500">المتأخرات</p>
                        <p class="text-lg font-bold ${if (detail.outstandingBalance > 0) "text-red-600" else "text-emerald-600"} mt-1">${money(detail.outstandingBalance)}</p>
                    </div>
                </div>

                <!-- Agents table -->
                <div class="bg-white rounded-xl shadow-sm overflow-x-auto border border-gray-100 mb-5">
                    <div class="px-4 py-3 border-b border-gray-100 flex items-center justify-between">
                        <h3 class="font-bold text-gray-800">موظفي المنظمة</h3>
                        <span class="text-xs text-gray-500">${detail.agents.size} موظف</span>
                    </div>
                    <table class="w-full text-sm">
                        <thead>
                            <tr class="bg-gray-50 border-b">
                                <th class="p-3 text-right text-xs text-gray-500 uppercase tracking-wider font-medium">الموظف</th>
                                <th class="p-3 text-right text-xs text-gray-500 uppercase tracking-wider font-medium">الدور</th>
                                <th class="p-3 text-right text-xs text-gray-500 uppercase tracking-wider font-medium">الحالة</th>
                                <th class="p-3 text-right text-xs text-gray-500 uppercase tracking-wider font-medium">تاريخ الإضافة</th>
                                <th class="p-3 text-right text-xs text-gray-500 uppercase tracking-wider font-medium"></th>
                            </tr>
                        </thead>
                        <tbody>${if (agentRows.isBlank()) "<tr><td colspan='5' class='p-6 text-center text-gray-400'>لا يوجد موظفين</td></tr>" else agentRows}</tbody>
                    </table>
                </div>

                <script>
                async function resetAgentPwd(agentId, name) {
                    const pwd = prompt('كلمة سر جديدة لـ ' + name + ' (6 حروف على الأقل):');
                    if (!pwd) return;
                    if (pwd.length < 6) { alert('كلمة السر يجب أن تكون 6 حروف على الأقل'); return; }
                    const res = await fetch('/crm/api/super/organizations/${detail.id}/agents/' + agentId + '/reset-password', {
                        method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({password: pwd})
                    });
                    if (res.ok) {
                        const body = await res.json();
                        alert('تم تحديث كلمة السر ✅\n\nإيميل الموظف: ' + body.agent_email + '\nكلمة السر الجديدة: ' + pwd + '\n\nأرسلها له بشكل آمن.');
                    } else {
                        const err = await res.json().catch(()=>({}));
                        alert(err.error || 'حدث خطأ');
                    }
                }
                async function toggleAgentActive(agentId, makeActive) {
                    const verb = makeActive ? 'تفعيل' : 'تعطيل';
                    if (!confirm(verb + ' هذا الموظف؟')) return;
                    const res = await fetch('/crm/api/super/organizations/${detail.id}/agents/' + agentId + '/active', {
                        method:'PATCH', headers:{'Content-Type':'application/json'}, body: JSON.stringify({active: makeActive})
                    });
                    if (res.ok) location.reload();
                    else alert('حدث خطأ');
                }
                async function deleteAgent(agentId, name) {
                    if (!confirm('⚠️ حذف الموظف "' + name + '" نهائيًا؟\n\nسيتم مسح كل أنشطته وعمولاته ومرتباته. هذا الإجراء لا يمكن التراجع عنه.')) return;
                    const res = await fetch('/crm/api/super/organizations/${detail.id}/agents/' + agentId, { method:'DELETE' });
                    if (res.ok) { alert('تم الحذف'); location.reload(); }
                    else { const err = await res.json().catch(()=>({})); alert(err.error || 'حدث خطأ'); }
                }
                </script>
            """.trimIndent()

            call.respondText(
                crmLayout("تفاصيل ${detail.name}", principal.name, principal.role, principal, "super_orgs", content),
                ContentType.Text.Html,
            )
        }

        // ─── JSON API Endpoints ─────────────────────────────────
        route("/crm/api") {

            // Photo upload for CRM agents. Same Netty-event-loop trap as the
            // /super/organizations/{id}/logo endpoint above — file copy on the
            // event loop blocks every other request and leaks Netty channels
            // when the client disconnects mid-upload. Push the I/O onto
            // Dispatchers.IO so the event loop stays free.
            post("/upload") {
                val principal = call.principal<CrmPrincipal>()!!
                val multipart = call.receiveMultipart()
                var uploadedUrl: String? = null
                multipart.forEachPart { part ->
                    if (part is PartData.FileItem) {
                        val ext = (part.originalFileName ?: "image.jpg").substringAfterLast('.', "jpg").lowercase().takeIf { it in listOf("jpg","jpeg","png","webp","gif") } ?: "jpg"
                        val fileName = "${java.util.UUID.randomUUID()}.$ext"
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val dir = java.io.File("uploads/crm-photos")
                            if (!dir.exists()) dir.mkdirs()
                            val file = java.io.File(dir, fileName)
                            part.streamProvider().use { input -> file.outputStream().buffered().use { output -> input.copyTo(output) } }
                        }
                        // Relative URL — see the org-logo endpoint for why we
                        // dropped the Host-header prefix.
                        uploadedUrl = "/uploads/crm-photos/$fileName"
                    }
                    part.dispose()
                }
                if (uploadedUrl != null) {
                    call.respondText("""{"url":"$uploadedUrl"}""", ContentType.Application.Json)
                } else {
                    call.respondText("""{"error":"no file"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                }
            }

            // GET /crm/api/clients
            get("/clients") {
                val principal = call.principal<CrmPrincipal>()!!
                val clients = crmService.listClients(principal.organizationId, principal.agentId, principal.canSeeAll)
                val json = buildJsonArray {
                    clients.forEach { c ->
                        add(buildJsonObject {
                            put("id", c.id)
                            put("clientName", c.clientName)
                            put("phone", c.phone)
                            put("whatsapp", c.whatsapp)
                            put("businessName", c.businessName?.let { JsonPrimitive(it) } ?: JsonNull)
                            put("businessType", c.businessType?.let { JsonPrimitive(it) } ?: JsonNull)
                            put("city", c.city?.let { JsonPrimitive(it) } ?: JsonNull)
                            put("governorate", c.governorate?.let { JsonPrimitive(it) } ?: JsonNull)
                            put("status", c.status)
                            put("plan", c.plan?.let { JsonPrimitive(it) } ?: JsonNull)
                            put("monthlyAmount", c.monthlyAmount)
                            put("discountPercent", c.discountPercent)
                            put("finalAmount", c.finalAmount)
                            put("paymentMethod", c.paymentMethod?.let { JsonPrimitive(it) } ?: JsonNull)
                            put("assignedTo", c.assignedTo?.let { JsonPrimitive(it) } ?: JsonNull)
                            put("assignedName", c.assignedName?.let { JsonPrimitive(it) } ?: JsonNull)
                            put("source", c.source?.let { JsonPrimitive(it) } ?: JsonNull)
                            put("notes", c.notes?.let { JsonPrimitive(it) } ?: JsonNull)
                            put("nextActionDate", c.nextActionDate?.let { JsonPrimitive(it) } ?: JsonNull)
                            put("daysSinceLastContact", c.daysSinceLastContact?.let { JsonPrimitive(it) } ?: JsonNull)
                        })
                    }
                }
                call.respondText(json.toString(), ContentType.Application.Json)
            }

            // POST /crm/api/clients
            post("/clients") {
                val principal = call.principal<CrmPrincipal>()!!
                val body = call.receiveText()
                val obj = Json.parseToJsonElement(body).jsonObject
                val client = crmService.createClient(
                    orgId = principal.organizationId,
                    clientName = obj["clientName"]?.jsonPrimitive?.content ?: "",
                    phone = obj["phone"]?.jsonPrimitive?.content ?: "",
                    whatsapp = obj["whatsapp"]?.jsonPrimitive?.booleanOrNull ?: false,
                    businessName = obj["businessName"]?.jsonPrimitive?.contentOrNull,
                    businessType = obj["businessType"]?.jsonPrimitive?.contentOrNull,
                    city = obj["city"]?.jsonPrimitive?.contentOrNull,
                    governorate = obj["governorate"]?.jsonPrimitive?.contentOrNull,
                    status = obj["status"]?.jsonPrimitive?.content ?: "عميل جديد",
                    plan = obj["plan"]?.jsonPrimitive?.contentOrNull,
                    monthlyAmount = obj["monthlyAmount"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    discountPercent = obj["discountPercent"]?.jsonPrimitive?.intOrNull ?: 0,
                    paymentMethod = obj["paymentMethod"]?.jsonPrimitive?.contentOrNull,
                    assignedTo = if (principal.canSeeAll) obj["assignedTo"]?.jsonPrimitive?.contentOrNull else principal.agentId,
                    source = obj["source"]?.jsonPrimitive?.contentOrNull,
                    notes = obj["notes"]?.jsonPrimitive?.contentOrNull,
                    nextActionDate = obj["nextActionDate"]?.jsonPrimitive?.contentOrNull,
                )
                val result = buildJsonObject { put("id", client.id); put("status", "ok") }
                call.respondText(result.toString(), ContentType.Application.Json, HttpStatusCode.Created)
            }

            // PUT /crm/api/clients/{id}
            put("/clients/{id}") {
                val principal = call.principal<CrmPrincipal>()!!
                val id = call.parameters["id"] ?: return@put call.respondText("{\"error\":\"missing id\"}", ContentType.Application.Json, HttpStatusCode.BadRequest)
                // Agents may only edit clients they own. Managers/owners edit anyone.
                if (!principal.canSeeAll && !crmService.isClientOwnedBy(principal.organizationId, id, principal.agentId)) {
                    call.respondText("""{"error":"forbidden"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                    return@put
                }
                val body = call.receiveText()
                val obj = Json.parseToJsonElement(body).jsonObject
                val ok = crmService.updateClient(
                    orgId = principal.organizationId,
                    id = id,
                    clientName = obj["clientName"]?.jsonPrimitive?.contentOrNull,
                    phone = obj["phone"]?.jsonPrimitive?.contentOrNull,
                    whatsapp = obj["whatsapp"]?.jsonPrimitive?.booleanOrNull,
                    businessName = obj["businessName"]?.jsonPrimitive?.contentOrNull,
                    businessType = obj["businessType"]?.jsonPrimitive?.contentOrNull,
                    city = obj["city"]?.jsonPrimitive?.contentOrNull,
                    governorate = obj["governorate"]?.jsonPrimitive?.contentOrNull,
                    status = obj["status"]?.jsonPrimitive?.contentOrNull,
                    plan = obj["plan"]?.jsonPrimitive?.contentOrNull,
                    monthlyAmount = obj["monthlyAmount"]?.jsonPrimitive?.doubleOrNull,
                    discountPercent = obj["discountPercent"]?.jsonPrimitive?.intOrNull,
                    paymentMethod = obj["paymentMethod"]?.jsonPrimitive?.contentOrNull,
                    assignedTo = if (principal.canSeeAll) obj["assignedTo"]?.jsonPrimitive?.contentOrNull else null,
                    source = obj["source"]?.jsonPrimitive?.contentOrNull,
                    notes = obj["notes"]?.jsonPrimitive?.contentOrNull,
                    nextActionDate = obj["nextActionDate"]?.jsonPrimitive?.contentOrNull,
                )
                if (ok) {
                    call.respondText("""{"status":"ok"}""", ContentType.Application.Json)
                } else {
                    call.respondText("""{"error":"not found"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
                }
            }

            delete("/clients/{id}") {
                val principal = call.principal<CrmPrincipal>()!!
                val id = call.parameters["id"] ?: return@delete
                // Agents may delete only clients assigned to them. Managers/owners delete anyone.
                if (!principal.canSeeAll && !crmService.isClientOwnedBy(principal.organizationId, id, principal.agentId)) {
                    call.respondText("""{"error":"forbidden"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                    return@delete
                }
                if (crmService.deleteClient(principal.organizationId, id)) {
                    call.respondText("""{"status":"ok"}""", ContentType.Application.Json)
                } else {
                    call.respondText("""{"error":"not found"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
                }
            }

            // GET /crm/api/activities
            get("/activities") {
                val principal = call.principal<CrmPrincipal>()!!
                val activities = crmService.listActivities(principal.organizationId, principal.agentId, principal.canSeeAll)
                val json = buildJsonArray {
                    activities.forEach { a ->
                        add(buildJsonObject {
                            put("id", a.id)
                            put("agentId", a.agentId)
                            put("agentName", a.agentName)
                            put("clientId", a.clientId)
                            put("clientName", a.clientName)
                            put("actionType", a.actionType?.let { JsonPrimitive(it) } ?: JsonNull)
                            put("channel", a.channel?.let { JsonPrimitive(it) } ?: JsonNull)
                            put("previousStatus", a.previousStatus?.let { JsonPrimitive(it) } ?: JsonNull)
                            put("newStatus", a.newStatus?.let { JsonPrimitive(it) } ?: JsonNull)
                            put("result", a.result?.let { JsonPrimitive(it) } ?: JsonNull)
                            put("notes", a.notes?.let { JsonPrimitive(it) } ?: JsonNull)
                            put("nextDate", a.nextDate?.let { JsonPrimitive(it) } ?: JsonNull)
                            put("createdAt", a.createdAt)
                        })
                    }
                }
                call.respondText(json.toString(), ContentType.Application.Json)
            }

            // POST /crm/api/activities
            post("/activities") {
                val principal = call.principal<CrmPrincipal>()!!
                val body = call.receiveText()
                val obj = Json.parseToJsonElement(body).jsonObject
                val id = crmService.createActivity(
                    orgId = principal.organizationId,
                    agentId = principal.agentId,
                    clientId = obj["clientId"]?.jsonPrimitive?.content ?: "",
                    actionType = obj["actionType"]?.jsonPrimitive?.contentOrNull,
                    channel = obj["channel"]?.jsonPrimitive?.contentOrNull,
                    previousStatus = obj["previousStatus"]?.jsonPrimitive?.contentOrNull,
                    newStatus = obj["newStatus"]?.jsonPrimitive?.contentOrNull,
                    planOffered = obj["planOffered"]?.jsonPrimitive?.contentOrNull,
                    amount = obj["amount"]?.jsonPrimitive?.doubleOrNull,
                    discountPercent = obj["discountPercent"]?.jsonPrimitive?.intOrNull,
                    callDuration = obj["callDuration"]?.jsonPrimitive?.contentOrNull,
                    result = obj["result"]?.jsonPrimitive?.contentOrNull,
                    nextStep = obj["nextStep"]?.jsonPrimitive?.contentOrNull,
                    nextDate = obj["nextDate"]?.jsonPrimitive?.contentOrNull,
                    notes = obj["notes"]?.jsonPrimitive?.contentOrNull,
                )
                val result = buildJsonObject { put("id", id); put("status", "ok") }
                call.respondText(result.toString(), ContentType.Application.Json, HttpStatusCode.Created)
            }

            // PUT /crm/api/activities/{id}
            put("/activities/{id}") {
                val principal = call.principal<CrmPrincipal>()!!
                val id = call.parameters["id"] ?: return@put call.respondText("{\"error\":\"missing id\"}", ContentType.Application.Json, HttpStatusCode.BadRequest)
                // Agents may only edit activities they recorded. Managers/owners edit anyone's.
                if (!principal.canSeeAll && !crmService.isActivityOwnedBy(principal.organizationId, id, principal.agentId)) {
                    call.respondText("""{"error":"forbidden"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                    return@put
                }
                val body = call.receiveText()
                val obj = Json.parseToJsonElement(body).jsonObject
                val ok = crmService.updateActivity(
                    orgId = principal.organizationId,
                    id = id,
                    actionType = obj["actionType"]?.jsonPrimitive?.contentOrNull,
                    channel = obj["channel"]?.jsonPrimitive?.contentOrNull,
                    previousStatus = obj["previousStatus"]?.jsonPrimitive?.contentOrNull,
                    newStatus = obj["newStatus"]?.jsonPrimitive?.contentOrNull,
                    planOffered = obj["planOffered"]?.jsonPrimitive?.contentOrNull,
                    amount = obj["amount"]?.jsonPrimitive?.doubleOrNull,
                    discountPercent = obj["discountPercent"]?.jsonPrimitive?.intOrNull,
                    callDuration = obj["callDuration"]?.jsonPrimitive?.contentOrNull,
                    result = obj["result"]?.jsonPrimitive?.contentOrNull,
                    nextStep = obj["nextStep"]?.jsonPrimitive?.contentOrNull,
                    nextDate = obj["nextDate"]?.jsonPrimitive?.contentOrNull,
                    notes = obj["notes"]?.jsonPrimitive?.contentOrNull,
                )
                if (ok) {
                    call.respondText("""{"status":"ok"}""", ContentType.Application.Json)
                } else {
                    call.respondText("""{"error":"not found"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
                }
            }

            // DELETE /crm/api/activities/{id}
            delete("/activities/{id}") {
                val principal = call.principal<CrmPrincipal>()!!
                val id = call.parameters["id"] ?: return@delete
                // Agents may only delete activities they recorded. Managers/owners delete anyone's.
                if (!principal.canSeeAll && !crmService.isActivityOwnedBy(principal.organizationId, id, principal.agentId)) {
                    call.respondText("""{"error":"forbidden"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                    return@delete
                }
                if (crmService.deleteActivity(principal.organizationId, id)) {
                    call.respondText("""{"status":"ok"}""", ContentType.Application.Json)
                } else {
                    call.respondText("""{"error":"not found"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
                }
            }

            // GET /crm/api/stats
            get("/stats") {
                val principal = call.principal<CrmPrincipal>()!!
                val stats = crmService.getStats(principal.organizationId, principal.agentId, principal.canSeeAll)
                val json = buildJsonObject {
                    put("totalClients", stats.totalClients)
                    put("totalActivities", stats.totalActivities)
                    put("subscribed", stats.subscribed)
                    put("paid", stats.paid)
                    put("monthlyRevenue", stats.monthlyRevenue)
                    put("byStatus", buildJsonObject { stats.byStatus.forEach { (k, v) -> put(k, v) } })
                    put("byBusinessType", buildJsonObject { stats.byBusinessType.forEach { (k, v) -> put(k, v) } })
                }
                call.respondText(json.toString(), ContentType.Application.Json)
            }

            // ─── Super-admin: Organizations API ─────────────────
            // Cookie-authed parallels of the admin-jwt endpoints in CrmSuperAdminRoutes.kt.
            // Lets the CRM dashboard's /crm/super/organizations page manage tenants
            // without requiring a separate admin login. Every endpoint enforces
            // `principal.isSuperAdmin` — ordinary org owners and agents get 403.

            post("/super/organizations") {
                val principal = call.principal<CrmPrincipal>()!!
                if (!principal.isSuperAdmin) {
                    call.respondText("""{"error":"forbidden"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                    return@post
                }
                val body = call.receiveText()
                val obj = Json.parseToJsonElement(body).jsonObject
                val result = crmService.provisionOrganization(
                    name = obj["name"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing name"),
                    contactEmail = obj["contactEmail"]?.jsonPrimitive?.contentOrNull,
                    contactPhone = obj["contactPhone"]?.jsonPrimitive?.contentOrNull,
                    planTier = obj["planTier"]?.jsonPrimitive?.contentOrNull ?: "starter",
                    // Optional logo URL captured by the create-org modal: the
                    // file was already POSTed to /crm/api/super/uploads/logo
                    // (a pre-creation upload that doesn't need an orgId yet),
                    // and the client passes back the resulting URL here so
                    // we persist it atomically with the org row instead of
                    // requiring a follow-up PATCH.
                    logoUrl = obj["logoUrl"]?.jsonPrimitive?.contentOrNull,
                    ownerName = obj["ownerName"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing ownerName"),
                    ownerEmail = obj["ownerEmail"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing ownerEmail"),
                    ownerPhone = obj["ownerPhone"]?.jsonPrimitive?.contentOrNull,
                    ownerPassword = obj["ownerPassword"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing ownerPassword"),
                )
                val response = buildJsonObject {
                    put("organization_id", result.organizationId)
                    put("organization_name", result.organizationName)
                    put("owner_agent_id", result.ownerAgentId)
                    put("owner_email", result.ownerEmail)
                    put("owner_name", result.ownerName)
                }
                call.respondText(response.toString(), ContentType.Application.Json, HttpStatusCode.Created)
            }

            patch("/super/organizations/{id}/active") {
                val principal = call.principal<CrmPrincipal>()!!
                if (!principal.isSuperAdmin) {
                    call.respondText("""{"error":"forbidden"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                    return@patch
                }
                val id = call.parameters["id"] ?: return@patch call.respondText(
                    """{"error":"missing id"}""", ContentType.Application.Json, HttpStatusCode.BadRequest
                )
                val body = call.receiveText()
                val active = Json.parseToJsonElement(body).jsonObject["active"]?.jsonPrimitive?.boolean ?: true
                val ok = crmService.setOrganizationActive(id, active)
                if (ok) call.respondText("""{"status":"ok","active":$active}""", ContentType.Application.Json)
                else call.respondText("""{"error":"not found"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
            }

            // Edit org metadata (name / contact / plan tier / logo). Each field is
            // optional — null leaves the column untouched, an empty string clears
            // the optional ones (email/phone/logo).
            patch("/super/organizations/{id}") {
                val principal = call.principal<CrmPrincipal>()!!
                if (!principal.isSuperAdmin) {
                    call.respondText("""{"error":"forbidden"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                    return@patch
                }
                val id = call.parameters["id"] ?: return@patch call.respondText(
                    """{"error":"missing id"}""", ContentType.Application.Json, HttpStatusCode.BadRequest
                )
                val body = call.receiveText()
                val obj = Json.parseToJsonElement(body).jsonObject
                val ok = crmService.updateOrganization(
                    orgId = id,
                    name = obj["name"]?.jsonPrimitive?.contentOrNull,
                    contactEmail = obj["contactEmail"]?.jsonPrimitive?.contentOrNull,
                    contactPhone = obj["contactPhone"]?.jsonPrimitive?.contentOrNull,
                    planTier = obj["planTier"]?.jsonPrimitive?.contentOrNull,
                    logoUrl = obj["logoUrl"]?.jsonPrimitive?.contentOrNull,
                )
                if (ok) call.respondText("""{"status":"ok"}""", ContentType.Application.Json)
                else call.respondText("""{"error":"not found"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
            }

            // Reset the org owner's password. Returns the owner email so the
            // operator can re-deliver it to the customer alongside the new
            // password they typed in the modal.
            post("/super/organizations/{id}/reset-password") {
                val principal = call.principal<CrmPrincipal>()!!
                if (!principal.isSuperAdmin) {
                    call.respondText("""{"error":"forbidden"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                    return@post
                }
                val id = call.parameters["id"] ?: return@post call.respondText(
                    """{"error":"missing id"}""", ContentType.Application.Json, HttpStatusCode.BadRequest
                )
                val body = call.receiveText()
                val newPassword = Json.parseToJsonElement(body).jsonObject["password"]?.jsonPrimitive?.contentOrNull
                if (newPassword == null || newPassword.length < 6) {
                    call.respondText(
                        """{"error":"كلمة السر يجب أن تكون 6 حروف على الأقل"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.BadRequest,
                    )
                    return@post
                }
                val ownerEmail = try {
                    crmService.resetOwnerPassword(id, newPassword)
                } catch (e: IllegalArgumentException) {
                    call.respondText(
                        """{"error":"${e.message ?: "bad request"}"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.BadRequest,
                    )
                    return@post
                }
                if (ownerEmail != null) {
                    val resp = buildJsonObject {
                        put("status", "ok")
                        put("owner_email", ownerEmail)
                    }
                    call.respondText(resp.toString(), ContentType.Application.Json)
                } else {
                    call.respondText(
                        """{"error":"المنظمة لا تحتوي على حساب مالك"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.NotFound,
                    )
                }
            }

            // Logo upload — accepts multipart, saves to uploads/crm-photos/, returns
            // a public URL. Same storage layout as the agent-photo upload above so
            // the static file route already serves it. Caller writes the URL back
            // via the PATCH /super/organizations/{id} endpoint.
            //
            // Threading: Netty's event-loop thread runs route handlers, and blocking
            // I/O on it freezes the whole server. We learned this the hard way —
            // a 1MB file copy + a JDBC transaction in the same handler made the
            // event loop go quiet for 5+ seconds; if the browser's fetch() timed
            // out and dropped the connection during that window, Netty raised
            // ChannelWriteException and (worst of all) leaked the channel slot.
            // After half a dozen such uploads the loop was full of zombie channels
            // and the instance stopped accepting any new requests. Both halves of
            // the work — file write and DB update — now run on Dispatchers.IO so
            // the event loop stays responsive throughout.
            post("/super/organizations/{id}/logo") {
                val principal = call.principal<CrmPrincipal>()!!
                if (!principal.isSuperAdmin) {
                    call.respondText("""{"error":"forbidden"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                    return@post
                }
                val id = call.parameters["id"] ?: return@post call.respondText(
                    """{"error":"missing id"}""", ContentType.Application.Json, HttpStatusCode.BadRequest
                )
                // Capture the previous logo URL so we can delete the file
                // after the new one is committed. Doing it before write means
                // a failed upload would leave the org with no logo at all;
                // doing it after means we never orphan files when the new
                // upload succeeds.
                val previousLogoUrl = crmService.getOrgBranding(id)?.logoUrl
                val multipart = call.receiveMultipart()
                var uploadedUrl: String? = null
                multipart.forEachPart { part ->
                    if (part is PartData.FileItem) {
                        val ext = (part.originalFileName ?: "logo.png")
                            .substringAfterLast('.', "png")
                            .lowercase()
                            .takeIf { it in listOf("jpg", "jpeg", "png", "webp", "gif", "svg") } ?: "png"
                        val fileName = "org-${id}-${System.currentTimeMillis()}.$ext"
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val dir = java.io.File("uploads/crm-photos")
                            if (!dir.exists()) dir.mkdirs()
                            val file = java.io.File(dir, fileName)
                            part.streamProvider().use { input -> file.outputStream().buffered().use { out -> input.copyTo(out) } }
                        }
                        // Relative URL — the browser resolves it against whatever
                        // port loaded the page, so the same DB row renders on both
                        // 8080 (release) and 8081 (debug) without us hard-coding a
                        // host that will go stale the moment the operator switches
                        // instances.
                        uploadedUrl = "/uploads/crm-photos/$fileName"
                    }
                    part.dispose()
                }
                if (uploadedUrl != null) {
                    // The DB call also blocks (synchronous JDBC), so push it onto
                    // the IO pool too — this is on the same handler that the event
                    // loop is waiting to flush a response from.
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        crmService.updateOrganization(orgId = id, name = null, contactEmail = null, contactPhone = null, planTier = null, logoUrl = uploadedUrl)
                        // Now that the new logo is the source of truth, garbage-collect
                        // the previous file. We compare paths so a no-op re-upload of
                        // the same URL never deletes the file that's still referenced.
                        if (!previousLogoUrl.isNullOrBlank() && previousLogoUrl != uploadedUrl) {
                            deleteUploadFile(previousLogoUrl)
                        }
                    }
                    call.respondText("""{"url":"$uploadedUrl"}""", ContentType.Application.Json)
                } else {
                    call.respondText("""{"error":"no file"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                }
            }

            delete("/super/organizations/{id}") {
                val principal = call.principal<CrmPrincipal>()!!
                if (!principal.isSuperAdmin) {
                    call.respondText("""{"error":"forbidden"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                    return@delete
                }
                val id = call.parameters["id"] ?: return@delete call.respondText(
                    """{"error":"missing id"}""", ContentType.Application.Json, HttpStatusCode.BadRequest
                )
                // Capture the logo URL before the row is gone so we can delete
                // the file on disk too — the DB cascade only takes care of
                // table rows, not uploaded bytes.
                val orgLogoUrl = crmService.getOrgBranding(id)?.logoUrl
                val ok = crmService.deleteOrganization(id)
                if (ok) {
                    if (!orgLogoUrl.isNullOrBlank()) deleteUploadFile(orgLogoUrl)
                    call.respondText("""{"status":"ok"}""", ContentType.Application.Json)
                } else call.respondText("""{"error":"not found"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
            }

            // Pre-creation logo upload — used by the create-org modal so the
            // operator can pick a logo BEFORE the org exists. We don't have an
            // orgId yet so the file is named with a `pending-<random>` prefix;
            // the URL is then passed back to the POST /super/organizations
            // request as `logoUrl` and persisted alongside the new org row.
            // No DB write here.
            post("/super/uploads/logo") {
                val principal = call.principal<CrmPrincipal>()!!
                if (!principal.isSuperAdmin) {
                    call.respondText("""{"error":"forbidden"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                    return@post
                }
                val multipart = call.receiveMultipart()
                var uploadedUrl: String? = null
                multipart.forEachPart { part ->
                    if (part is PartData.FileItem) {
                        val ext = (part.originalFileName ?: "logo.png")
                            .substringAfterLast('.', "png")
                            .lowercase()
                            .takeIf { it in listOf("jpg", "jpeg", "png", "webp", "gif", "svg") } ?: "png"
                        val fileName = "pending-${java.util.UUID.randomUUID()}.$ext"
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val dir = java.io.File("uploads/crm-photos")
                            if (!dir.exists()) dir.mkdirs()
                            val file = java.io.File(dir, fileName)
                            part.streamProvider().use { input -> file.outputStream().buffered().use { out -> input.copyTo(out) } }
                        }
                        uploadedUrl = "/uploads/crm-photos/$fileName"
                    }
                    part.dispose()
                }
                if (uploadedUrl != null) {
                    call.respondText("""{"url":"$uploadedUrl"}""", ContentType.Application.Json)
                } else {
                    call.respondText("""{"error":"no file"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                }
            }

            // ─── Super-admin: agent management inside any org ─────
            // These three endpoints let the platform super-admin reset, suspend,
            // or delete any agent under any tenant org. The service-layer
            // methods all verify (orgId, agentId) belong together so a typo
            // can never operate on a different tenant by accident.

            post("/super/organizations/{orgId}/agents/{agentId}/reset-password") {
                val principal = call.principal<CrmPrincipal>()!!
                if (!principal.isSuperAdmin) {
                    call.respondText("""{"error":"forbidden"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                    return@post
                }
                val orgId = call.parameters["orgId"] ?: return@post call.respondText(
                    """{"error":"missing orgId"}""", ContentType.Application.Json, HttpStatusCode.BadRequest
                )
                val agentId = call.parameters["agentId"] ?: return@post call.respondText(
                    """{"error":"missing agentId"}""", ContentType.Application.Json, HttpStatusCode.BadRequest
                )
                val body = call.receiveText()
                val newPassword = Json.parseToJsonElement(body).jsonObject["password"]?.jsonPrimitive?.contentOrNull
                if (newPassword == null || newPassword.length < 6) {
                    call.respondText(
                        """{"error":"كلمة السر يجب أن تكون 6 حروف على الأقل"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.BadRequest,
                    )
                    return@post
                }
                val email = try {
                    crmService.resetAgentPasswordInOrg(orgId, agentId, newPassword)
                } catch (e: IllegalArgumentException) {
                    call.respondText(
                        """{"error":"${e.message ?: "bad request"}"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.BadRequest,
                    )
                    return@post
                }
                if (email != null) {
                    val resp = buildJsonObject {
                        put("status", "ok")
                        put("agent_email", email)
                    }
                    call.respondText(resp.toString(), ContentType.Application.Json)
                } else {
                    call.respondText(
                        """{"error":"الموظف غير موجود في هذه المنظمة"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.NotFound,
                    )
                }
            }

            patch("/super/organizations/{orgId}/agents/{agentId}/active") {
                val principal = call.principal<CrmPrincipal>()!!
                if (!principal.isSuperAdmin) {
                    call.respondText("""{"error":"forbidden"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                    return@patch
                }
                val orgId = call.parameters["orgId"] ?: return@patch call.respondText(
                    """{"error":"missing orgId"}""", ContentType.Application.Json, HttpStatusCode.BadRequest
                )
                val agentId = call.parameters["agentId"] ?: return@patch call.respondText(
                    """{"error":"missing agentId"}""", ContentType.Application.Json, HttpStatusCode.BadRequest
                )
                val body = call.receiveText()
                val active = Json.parseToJsonElement(body).jsonObject["active"]?.jsonPrimitive?.boolean ?: true
                val ok = crmService.setAgentActiveInOrg(orgId, agentId, active)
                if (ok) call.respondText("""{"status":"ok","active":$active}""", ContentType.Application.Json)
                else call.respondText("""{"error":"not found"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
            }

            delete("/super/organizations/{orgId}/agents/{agentId}") {
                val principal = call.principal<CrmPrincipal>()!!
                if (!principal.isSuperAdmin) {
                    call.respondText("""{"error":"forbidden"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                    return@delete
                }
                val orgId = call.parameters["orgId"] ?: return@delete call.respondText(
                    """{"error":"missing orgId"}""", ContentType.Application.Json, HttpStatusCode.BadRequest
                )
                val agentId = call.parameters["agentId"] ?: return@delete call.respondText(
                    """{"error":"missing agentId"}""", ContentType.Application.Json, HttpStatusCode.BadRequest
                )
                val photoUrl = crmService.getAgentPhotoUrl(agentId)
                val ok = try {
                    crmService.deleteAgentInOrg(orgId, agentId)
                } catch (e: IllegalStateException) {
                    call.respondText(
                        """{"error":"${e.message ?: "cannot delete"}"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.BadRequest,
                    )
                    return@delete
                }
                if (ok) {
                    if (!photoUrl.isNullOrBlank()) deleteUploadFile(photoUrl)
                    call.respondText("""{"status":"ok"}""", ContentType.Application.Json)
                } else {
                    call.respondText("""{"error":"not found"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
                }
            }

            // Tenant-owner-scoped logo upload. The org's owner — not the
            // platform super-admin — can change THEIR OWN logo from the
            // settings page. We trust principal.organizationId as the only
            // org id this request can touch (verified in the service via
            // resolveOrgId so a manipulated request body still can't leak).
            // canManageAgents is the same gate that protects /crm/settings.
            post("/organization/logo") {
                val principal = call.principal<CrmPrincipal>()!!
                if (!principal.canManageAgents) {
                    call.respondText("""{"error":"forbidden"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                    return@post
                }
                val orgId = principal.organizationId
                if (orgId == null) {
                    call.respondText("""{"error":"missing organization on session"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                    return@post
                }
                // Stash the existing logo URL — we'll delete the file *after* the
                // new one is committed, so a failed upload leaves the previous
                // logo intact. (Same belt-and-suspenders order as the super-admin
                // endpoint above — see comments there.)
                val previousLogoUrl = crmService.getOrgBranding(orgId)?.logoUrl
                val multipart = call.receiveMultipart()
                var uploadedUrl: String? = null
                multipart.forEachPart { part ->
                    if (part is PartData.FileItem) {
                        val ext = (part.originalFileName ?: "logo.png")
                            .substringAfterLast('.', "png")
                            .lowercase()
                            .takeIf { it in listOf("jpg", "jpeg", "png", "webp", "gif", "svg") } ?: "png"
                        val fileName = "org-${orgId}-${System.currentTimeMillis()}.$ext"
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val dir = java.io.File("uploads/crm-photos")
                            if (!dir.exists()) dir.mkdirs()
                            val file = java.io.File(dir, fileName)
                            part.streamProvider().use { input -> file.outputStream().buffered().use { out -> input.copyTo(out) } }
                        }
                        uploadedUrl = "/uploads/crm-photos/$fileName"
                    }
                    part.dispose()
                }
                if (uploadedUrl != null) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        crmService.updateOwnOrgLogo(orgId, uploadedUrl)
                        if (!previousLogoUrl.isNullOrBlank() && previousLogoUrl != uploadedUrl) {
                            deleteUploadFile(previousLogoUrl)
                        }
                    }
                    call.respondText("""{"url":"$uploadedUrl"}""", ContentType.Application.Json)
                } else {
                    call.respondText("""{"error":"no file"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                }
            }

            // Tenant-owner removes their own org logo (sets to NULL) and
            // garbage-collects the file so we don't leave bytes on disk that
            // nothing in the DB refers to. Falls back to the default Waselak
            // art on the next page load.
            delete("/organization/logo") {
                val principal = call.principal<CrmPrincipal>()!!
                if (!principal.canManageAgents) {
                    call.respondText("""{"error":"forbidden"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                    return@delete
                }
                val orgId = principal.organizationId ?: return@delete call.respondText(
                    """{"error":"missing organization on session"}""", ContentType.Application.Json, HttpStatusCode.BadRequest
                )
                val previousLogoUrl = crmService.getOrgBranding(orgId)?.logoUrl
                crmService.updateOwnOrgLogo(orgId, null)
                if (!previousLogoUrl.isNullOrBlank()) deleteUploadFile(previousLogoUrl)
                call.respondText("""{"status":"ok"}""", ContentType.Application.Json)
            }

            // GET /crm/api/agents (owner only)
            get("/agents") {
                val principal = call.principal<CrmPrincipal>()!!
                if (!principal.canManageAgents) {
                    call.respondText("""{"error":"forbidden"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                    return@get
                }
                val agents = crmService.listAgents(principal.organizationId)
                val json = buildJsonArray {
                    agents.forEach { a ->
                        add(buildJsonObject {
                            put("id", a.id)
                            put("name", a.name)
                            put("email", a.email)
                            put("role", a.role)
                            put("photoUrl", a.photoUrl?.let { JsonPrimitive(it) } ?: JsonNull)
                            put("active", a.active)
                        })
                    }
                }
                call.respondText(json.toString(), ContentType.Application.Json)
            }

            // POST /crm/api/agents (owner only)
            post("/agents") {
                val principal = call.principal<CrmPrincipal>()!!
                if (!principal.canManageAgents) {
                    call.respondText("""{"error":"forbidden"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                    return@post
                }
                val body = call.receiveText()
                val obj = Json.parseToJsonElement(body).jsonObject
                val role = obj["role"]?.jsonPrimitive?.content ?: "مندوب مبيعات"
                // Prevent creating owner role via API
                val safeRole = if (role == "owner") "مندوب مبيعات" else role
                val agent = crmService.createAgent(
                    orgId = principal.organizationId,
                    name = obj["name"]?.jsonPrimitive?.content ?: "",
                    email = obj["email"]?.jsonPrimitive?.content ?: "",
                    password = obj["password"]?.jsonPrimitive?.content ?: "",
                    role = safeRole,
                )
                val result = buildJsonObject { put("id", agent.id); put("status", "ok") }
                call.respondText(result.toString(), ContentType.Application.Json, HttpStatusCode.Created)
            }

            // PUT /crm/api/agents/{id} (owner only)
            put("/agents/{id}") {
                val principal = call.principal<CrmPrincipal>()!!
                val id = call.parameters["id"] ?: return@put call.respondText("{\"error\":\"missing id\"}", ContentType.Application.Json, HttpStatusCode.BadRequest)
                val isSelf = principal.agentId == id
                // Non-owner can only update their own photo
                if (!principal.canManageAgents && !isSelf) {
                    call.respondText("""{"error":"forbidden"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                    return@put
                }
                val body = call.receiveText()
                val obj = Json.parseToJsonElement(body).jsonObject
                val role = obj["role"]?.jsonPrimitive?.contentOrNull
                val safeRole = if (role == "owner") null else role
                val newPhotoUrl = obj["photoUrl"]?.jsonPrimitive?.contentOrNull
                // Snapshot the existing photo so we can garbage-collect it once
                // the new one is committed. Only relevant when the caller is
                // actually changing the photo — leaving photoUrl absent in the
                // PATCH body skips both the DB write and the file delete.
                val previousPhotoUrl = if (newPhotoUrl != null) crmService.getAgentPhotoUrl(id) else null
                val ok = crmService.updateAgent(
                    orgId = principal.organizationId,
                    id = id,
                    name = if (principal.canManageAgents) obj["name"]?.jsonPrimitive?.contentOrNull else null,
                    role = if (principal.canManageAgents) safeRole else null,
                    active = if (principal.canManageAgents) obj["active"]?.jsonPrimitive?.booleanOrNull else null,
                    password = if (principal.canManageAgents || isSelf) obj["password"]?.jsonPrimitive?.contentOrNull else null,
                    photoUrl = newPhotoUrl,
                )
                if (ok) {
                    if (newPhotoUrl != null && !previousPhotoUrl.isNullOrBlank() && previousPhotoUrl != newPhotoUrl) {
                        deleteUploadFile(previousPhotoUrl)
                    }
                    call.respondText("""{"status":"ok"}""", ContentType.Application.Json)
                } else {
                    call.respondText("""{"error":"not found"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
                }
            }

            delete("/agents/{id}") {
                val principal = call.principal<CrmPrincipal>()!!
                if (!principal.canManageAgents) {
                    call.respondText("""{"error":"forbidden"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                    return@delete
                }
                val id = call.parameters["id"] ?: return@delete
                if (id == principal.agentId) {
                    call.respondText("""{"error":"لا يمكنك حذف حسابك"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                    return@delete
                }
                if (crmService.deleteAgent(id)) {
                    call.respondText("""{"status":"ok"}""", ContentType.Application.Json)
                } else {
                    call.respondText("""{"error":"not found"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
                }
            }

            // ─── Billing API (Owner Only) ───────────────────────
            get("/billing/invoices") {
                val principal = call.principal<CrmPrincipal>()!!
                if (!principal.canSeeAnalytics) {
                    call.respondText("""{"error":"forbidden"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                    return@get
                }
                val clientId = call.request.queryParameters["clientId"]
                val status = call.request.queryParameters["status"]
                val invoices = crmService.listInvoices(principal.organizationId, clientId, status)
                val json = buildJsonArray {
                    invoices.forEach { inv ->
                        add(buildJsonObject {
                            put("id", inv.id)
                            put("clientId", inv.clientId)
                            put("clientName", inv.clientName)
                            put("clientPhone", inv.clientPhone)
                            put("invoiceNumber", inv.invoiceNumber)
                            put("plan", inv.plan)
                            put("period", inv.period)
                            put("amount", inv.amount)
                            put("discountPercent", inv.discountPercent)
                            put("finalAmount", inv.finalAmount)
                            put("paidAmount", inv.paidAmount)
                            put("remainingAmount", inv.remainingAmount)
                            put("status", inv.status)
                            put("dueDate", inv.dueDate?.let { JsonPrimitive(it) } ?: JsonNull)
                            put("paidDate", inv.paidDate?.let { JsonPrimitive(it) } ?: JsonNull)
                            put("paymentMethod", inv.paymentMethod?.let { JsonPrimitive(it) } ?: JsonNull)
                            put("notes", inv.notes?.let { JsonPrimitive(it) } ?: JsonNull)
                            put("createdBy", inv.createdBy?.let { JsonPrimitive(it) } ?: JsonNull)
                            put("createdAt", inv.createdAt)
                            put("isOverdue", inv.isOverdue)
                        })
                    }
                }
                call.respondText(json.toString(), ContentType.Application.Json)
            }

            post("/billing/invoices") {
                val principal = call.principal<CrmPrincipal>()!!
                if (!principal.canSeeAnalytics) {
                    call.respondText("""{"error":"forbidden"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                    return@post
                }
                val body = call.receiveText()
                val obj = Json.parseToJsonElement(body).jsonObject
                val invoice = crmService.createInvoice(
                    orgId = principal.organizationId,
                    clientId = obj["clientId"]?.jsonPrimitive?.content ?: "",
                    plan = obj["plan"]?.jsonPrimitive?.content ?: "",
                    period = obj["period"]?.jsonPrimitive?.content ?: "",
                    amount = obj["amount"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    discountPercent = obj["discountPercent"]?.jsonPrimitive?.intOrNull ?: 0,
                    dueDate = obj["dueDate"]?.jsonPrimitive?.content ?: "",
                    paymentMethod = obj["paymentMethod"]?.jsonPrimitive?.contentOrNull,
                    notes = obj["notes"]?.jsonPrimitive?.contentOrNull,
                    createdBy = principal.name,
                )
                val result = buildJsonObject { put("id", invoice.id); put("status", "ok") }
                call.respondText(result.toString(), ContentType.Application.Json, HttpStatusCode.Created)
            }

            post("/billing/invoices/{id}/pay") {
                val principal = call.principal<CrmPrincipal>()!!
                if (!principal.canSeeAnalytics) {
                    call.respondText("""{"error":"forbidden"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                    return@post
                }
                val invoiceId = call.parameters["id"] ?: return@post call.respondText("""{"error":"missing id"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                val body = call.receiveText()
                val obj = Json.parseToJsonElement(body).jsonObject
                val ok = crmService.recordPayment(
                    orgId = principal.organizationId,
                    invoiceId = invoiceId,
                    amount = obj["amount"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    paymentMethod = obj["paymentMethod"]?.jsonPrimitive?.content ?: "",
                    notes = obj["notes"]?.jsonPrimitive?.contentOrNull,
                    receivedBy = principal.name,
                )
                if (ok) {
                    call.respondText("""{"status":"ok"}""", ContentType.Application.Json)
                } else {
                    call.respondText("""{"error":"failed"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                }
            }

            get("/billing/invoices/{id}/payments") {
                val principal = call.principal<CrmPrincipal>()!!
                if (!principal.canSeeAnalytics) {
                    call.respondText("""{"error":"forbidden"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                    return@get
                }
                val invoiceId = call.parameters["id"] ?: return@get call.respondText("""{"error":"missing id"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                val payments = crmService.getPaymentsForInvoice(principal.organizationId, invoiceId)
                val json = buildJsonArray {
                    payments.forEach { p ->
                        add(buildJsonObject {
                            put("id", p.id)
                            put("invoiceId", p.invoiceId)
                            put("clientName", p.clientName)
                            put("amount", p.amount)
                            put("paymentMethod", p.paymentMethod)
                            put("notes", p.notes?.let { JsonPrimitive(it) } ?: JsonNull)
                            put("receivedBy", p.receivedBy?.let { JsonPrimitive(it) } ?: JsonNull)
                            put("createdAt", p.createdAt)
                        })
                    }
                }
                call.respondText(json.toString(), ContentType.Application.Json)
            }

            get("/billing/stats") {
                val principal = call.principal<CrmPrincipal>()!!
                if (!principal.canSeeAnalytics) {
                    call.respondText("""{"error":"forbidden"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                    return@get
                }
                val stats = crmService.getBillingStats(principal.organizationId)
                val json = buildJsonObject {
                    put("totalInvoices", stats.totalInvoices)
                    put("totalRevenue", stats.totalRevenue)
                    put("totalPaid", stats.totalPaid)
                    put("totalOverdue", stats.totalOverdue)
                    put("overdueCount", stats.overdueCount)
                    put("unpaidCount", stats.unpaidCount)
                }
                call.respondText(json.toString(), ContentType.Application.Json)
            }

            // GET /crm/api/export (owner only)
            get("/export") {
                val principal = call.principal<CrmPrincipal>()!!
                if (!principal.canSeeAnalytics) {
                    call.respondText("""{"error":"forbidden"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                    return@get
                }
                val clients = crmService.listClients(principal.organizationId, null, true)
                // CSV export
                val csv = buildString {
                    append("الاسم,الهاتف,النشاط,النوع,المحافظة,الحالة,الباقة,المبلغ,المندوب\n")
                    clients.forEach { c ->
                        append("${c.clientName},${c.phone},${c.businessName ?: ""},${c.businessType ?: ""},${c.governorate ?: ""},${c.status},${c.plan ?: ""},${c.finalAmount},${c.assignedName ?: ""}\n")
                    }
                }
                call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"crm-clients.csv\"")
                call.respondText(csv, ContentType.Text.CSV)
            }

            // ─── Profile API Endpoints ──────────────────────────
            get("/agents/{id}/profile") {
                val principal = call.principal<CrmPrincipal>()!!
                val id = call.parameters["id"] ?: return@get call.respondText("""{"error":"missing id"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                if (!principal.canSeeAll && principal.agentId != id) {
                    call.respondText("""{"error":"forbidden"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                    return@get
                }
                val profile = crmService.getAgentProfile(principal.organizationId, id)
                if (profile == null) {
                    call.respondText("""{"error":"not found"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
                    return@get
                }
                val json = buildJsonObject {
                    put("agent", buildJsonObject {
                        put("id", profile.agent.id)
                        put("name", profile.agent.name)
                        put("email", profile.agent.email)
                        put("role", profile.agent.role)
                        put("photoUrl", profile.agent.photoUrl?.let { JsonPrimitive(it) } ?: JsonNull)
                        put("active", profile.agent.active)
                    })
                    put("currentMonth", profile.currentMonth)
                    put("target", if (profile.target != null) buildJsonObject {
                        put("id", profile.target.id)
                        put("month", profile.target.month)
                        put("targetClients", profile.target.targetClients)
                        put("targetSubscriptions", profile.target.targetSubscriptions)
                        put("targetRevenue", profile.target.targetRevenue)
                        put("notes", profile.target.notes?.let { JsonPrimitive(it) } ?: JsonNull)
                    } else JsonNull)
                    put("progress", buildJsonObject {
                        put("month", profile.progress.month)
                        put("actualClients", profile.progress.actualClients)
                        put("targetClients", profile.progress.targetClients)
                        put("clientsPercent", profile.progress.clientsPercent)
                        put("actualSubscriptions", profile.progress.actualSubscriptions)
                        put("targetSubscriptions", profile.progress.targetSubscriptions)
                        put("subscriptionsPercent", profile.progress.subscriptionsPercent)
                        put("actualRevenue", profile.progress.actualRevenue)
                        put("targetRevenue", profile.progress.targetRevenue)
                        put("revenuePercent", profile.progress.revenuePercent)
                    })
                    put("reviews", buildJsonArray {
                        profile.reviews.forEach { r ->
                            add(buildJsonObject {
                                put("id", r.id)
                                put("month", r.month)
                                put("score", r.score)
                                put("review", r.review)
                                put("pinned", r.pinned)
                                put("createdAt", r.createdAt)
                            })
                        }
                    })
                    put("totalClients", profile.totalClients)
                    put("totalSubscriptions", profile.totalSubscriptions)
                    put("totalRevenue", profile.totalRevenue)
                    put("totalActivities", profile.totalActivities)
                }
                call.respondText(json.toString(), ContentType.Application.Json)
            }

            post("/agents/{id}/target") {
                val principal = call.principal<CrmPrincipal>()!!
                if (!principal.isOwner) {
                    call.respondText("""{"error":"forbidden"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                    return@post
                }
                val id = call.parameters["id"] ?: return@post call.respondText("""{"error":"missing id"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                val body = call.receiveText()
                val obj = Json.parseToJsonElement(body).jsonObject
                val target = crmService.setTarget(
                    agentId = id,
                    month = obj["month"]?.jsonPrimitive?.content ?: "",
                    clients = obj["targetClients"]?.jsonPrimitive?.intOrNull ?: 0,
                    subscriptions = obj["targetSubscriptions"]?.jsonPrimitive?.intOrNull ?: 0,
                    revenue = obj["targetRevenue"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    notes = obj["notes"]?.jsonPrimitive?.contentOrNull,
                )
                val result = buildJsonObject { put("id", target.id); put("status", "ok") }
                call.respondText(result.toString(), ContentType.Application.Json)
            }

            post("/agents/{id}/review") {
                val principal = call.principal<CrmPrincipal>()!!
                if (!principal.isOwner) {
                    call.respondText("""{"error":"forbidden"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                    return@post
                }
                val id = call.parameters["id"] ?: return@post call.respondText("""{"error":"missing id"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                val body = call.receiveText()
                val obj = Json.parseToJsonElement(body).jsonObject
                val review = crmService.createReview(
                    agentId = id,
                    month = obj["month"]?.jsonPrimitive?.content ?: "",
                    score = obj["score"]?.jsonPrimitive?.intOrNull ?: 5,
                    review = obj["review"]?.jsonPrimitive?.content ?: "",
                    createdBy = principal.agentId,
                )
                val result = buildJsonObject { put("id", review.id); put("status", "ok") }
                call.respondText(result.toString(), ContentType.Application.Json, HttpStatusCode.Created)
            }

            put("/agents/{id}/review/{reviewId}") {
                val principal = call.principal<CrmPrincipal>()!!
                if (!principal.isOwner) {
                    call.respondText("""{"error":"forbidden"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                    return@put
                }
                val reviewId = call.parameters["reviewId"] ?: return@put call.respondText("""{"error":"missing reviewId"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                val body = call.receiveText()
                val obj = Json.parseToJsonElement(body).jsonObject
                val ok = crmService.updateReview(
                    reviewId = reviewId,
                    score = obj["score"]?.jsonPrimitive?.intOrNull,
                    review = obj["review"]?.jsonPrimitive?.contentOrNull,
                    pinned = obj["pinned"]?.jsonPrimitive?.booleanOrNull,
                )
                if (ok) {
                    call.respondText("""{"status":"ok"}""", ContentType.Application.Json)
                } else {
                    call.respondText("""{"error":"not found"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
                }
            }

            get("/agents/{id}/progress") {
                val principal = call.principal<CrmPrincipal>()!!
                val id = call.parameters["id"] ?: return@get call.respondText("""{"error":"missing id"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                if (!principal.canSeeAll && principal.agentId != id) {
                    call.respondText("""{"error":"forbidden"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                    return@get
                }
                val month = call.request.queryParameters["month"] ?: run {
                    val now = Clock.System.now().toLocalDateTime(CRM_TZ)
                    "${now.year}-${now.monthNumber.toString().padStart(2, '0')}"
                }
                val progress = crmService.getProgress(id, month)
                val json = buildJsonObject {
                    put("month", progress.month)
                    put("actualClients", progress.actualClients)
                    put("targetClients", progress.targetClients)
                    put("clientsPercent", progress.clientsPercent)
                    put("actualSubscriptions", progress.actualSubscriptions)
                    put("targetSubscriptions", progress.targetSubscriptions)
                    put("subscriptionsPercent", progress.subscriptionsPercent)
                    put("actualRevenue", progress.actualRevenue)
                    put("targetRevenue", progress.targetRevenue)
                    put("revenuePercent", progress.revenuePercent)
                }
                call.respondText(json.toString(), ContentType.Application.Json)
            }

            // ─── Salary API Endpoints ───────────────────────────────

            get("/agents/{id}/salary-config") {
                val principal = call.principal<CrmPrincipal>()!!
                val id = call.parameters["id"] ?: return@get call.respondText("""{"error":"missing id"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                if (!principal.canSeeAll && principal.agentId != id) {
                    call.respondText("""{"error":"forbidden"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                    return@get
                }
                val config = crmService.getSalaryConfig(id)
                val json = buildJsonObject {
                    put("id", config.id?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("agentId", config.agentId)
                    put("baseSalary", config.baseSalary)
                    put("commissionPercent", config.commissionPercent)
                    put("commissionType", config.commissionType)
                    put("commissionMonths", config.commissionMonths)
                    put("commissionBase", config.commissionBase)
                    put("notes", config.notes?.let { JsonPrimitive(it) } ?: JsonNull)
                }
                call.respondText(json.toString(), ContentType.Application.Json)
            }

            post("/agents/{id}/salary-config") {
                val principal = call.principal<CrmPrincipal>()!!
                if (!principal.isOwner) {
                    call.respondText("""{"error":"forbidden"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                    return@post
                }
                val id = call.parameters["id"] ?: return@post call.respondText("""{"error":"missing id"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                val body = call.receiveText()
                val obj = Json.parseToJsonElement(body).jsonObject
                val config = crmService.setSalaryConfig(
                    agentId = id,
                    baseSalary = obj["baseSalary"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    commissionPercent = obj["commissionPercent"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    commissionType = obj["commissionType"]?.jsonPrimitive?.contentOrNull ?: "NONE",
                    commissionMonths = obj["commissionMonths"]?.jsonPrimitive?.intOrNull ?: 0,
                    commissionBase = obj["commissionBase"]?.jsonPrimitive?.contentOrNull ?: "FINAL",
                    notes = obj["notes"]?.jsonPrimitive?.contentOrNull,
                )
                val json = buildJsonObject {
                    put("id", config.id?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("status", "ok")
                }
                call.respondText(json.toString(), ContentType.Application.Json)
            }

            get("/agents/{id}/salary") {
                val principal = call.principal<CrmPrincipal>()!!
                val id = call.parameters["id"] ?: return@get call.respondText("""{"error":"missing id"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                if (!principal.canSeeAll && principal.agentId != id) {
                    call.respondText("""{"error":"forbidden"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                    return@get
                }
                val month = call.request.queryParameters["month"] ?: run {
                    val now = Clock.System.now().toLocalDateTime(CRM_TZ)
                    "${now.year}-${now.monthNumber.toString().padStart(2, '0')}"
                }
                val record = crmService.getSalaryRecord(id, month)
                if (record == null) {
                    call.respondText("""{"error":"not found"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
                    return@get
                }
                call.respondText(salaryRecordToJson(record).toString(), ContentType.Application.Json)
            }

            post("/agents/{id}/salary") {
                val principal = call.principal<CrmPrincipal>()!!
                if (!principal.isOwner) {
                    call.respondText("""{"error":"forbidden"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                    return@post
                }
                val id = call.parameters["id"] ?: return@post call.respondText("""{"error":"missing id"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                val body = call.receiveText()
                val obj = Json.parseToJsonElement(body).jsonObject
                val month = obj["month"]?.jsonPrimitive?.content ?: return@post call.respondText("""{"error":"missing month"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                val record = crmService.calculateMonthlySalary(id, month, principal.agentId)
                call.respondText(salaryRecordToJson(record).toString(), ContentType.Application.Json)
            }

            put("/salary-records/{id}") {
                val principal = call.principal<CrmPrincipal>()!!
                if (!principal.isOwner) {
                    call.respondText("""{"error":"forbidden"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                    return@put
                }
                val recordId = call.parameters["id"] ?: return@put call.respondText("""{"error":"missing id"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                val body = call.receiveText()
                val obj = Json.parseToJsonElement(body).jsonObject
                crmService.updateSalaryBonusDeduction(
                    recordId = recordId,
                    bonus = obj["bonus"]?.jsonPrimitive?.doubleOrNull,
                    bonusReason = obj["bonusReason"]?.jsonPrimitive?.contentOrNull,
                    deductions = obj["deductions"]?.jsonPrimitive?.doubleOrNull,
                    deductionReason = obj["deductionReason"]?.jsonPrimitive?.contentOrNull,
                    notes = obj["notes"]?.jsonPrimitive?.contentOrNull,
                )
                call.respondText("""{"status":"ok"}""", ContentType.Application.Json)
            }

            post("/salary-records/{id}/pay") {
                val principal = call.principal<CrmPrincipal>()!!
                if (!principal.isOwner) {
                    call.respondText("""{"error":"forbidden"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                    return@post
                }
                val recordId = call.parameters["id"] ?: return@post call.respondText("""{"error":"missing id"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                val ok = crmService.markSalaryPaid(recordId)
                if (ok) {
                    call.respondText("""{"status":"ok"}""", ContentType.Application.Json)
                } else {
                    call.respondText("""{"error":"not found"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
                }
            }

            post("/salaries/calculate-all") {
                val principal = call.principal<CrmPrincipal>()!!
                if (!principal.isOwner) {
                    call.respondText("""{"error":"forbidden"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                    return@post
                }
                val body = call.receiveText()
                val obj = Json.parseToJsonElement(body).jsonObject
                val month = obj["month"]?.jsonPrimitive?.content ?: return@post call.respondText("""{"error":"missing month"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                val agents = crmService.listAgents(principal.organizationId).filter { it.active }
                val results = agents.map { agent ->
                    crmService.calculateMonthlySalary(agent.id, month, principal.agentId)
                }
                val json = buildJsonArray {
                    results.forEach { add(salaryRecordToJson(it)) }
                }
                call.respondText(json.toString(), ContentType.Application.Json)
            }

            get("/salaries") {
                val principal = call.principal<CrmPrincipal>()!!
                if (!principal.isOwner) {
                    call.respondText("""{"error":"forbidden"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                    return@get
                }
                val month = call.request.queryParameters["month"] ?: run {
                    val now = Clock.System.now().toLocalDateTime(CRM_TZ)
                    "${now.year}-${now.monthNumber.toString().padStart(2, '0')}"
                }
                val records = crmService.listAllSalaryRecords(month)
                val json = buildJsonArray {
                    records.forEach { add(salaryRecordToJson(it)) }
                }
                call.respondText(json.toString(), ContentType.Application.Json)
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════
// Role Display Name Helper
// ════════════════════════════════════════════════════════════════

private fun roleDisplayName(role: String) = when (role) {
    "owner" -> "صاحب الحساب"
    else -> role
}

// ════════════════════════════════════════════════════════════════
// Commission Type Display Name Helper
// ════════════════════════════════════════════════════════════════

private fun commissionTypeDisplayName(type: String) = when (type) {
    "FIRST_ONLY" -> "مرة واحدة"
    "FIXED_MONTHS" -> "عدد شهور محدد"
    "FOREVER" -> "دائم"
    "NONE" -> "بدون عمولة"
    else -> type
}

// ════════════════════════════════════════════════════════════════
// Salary Record JSON Helper
// ════════════════════════════════════════════════════════════════

private fun salaryRecordToJson(r: CrmService.SalaryRecordDto): JsonObject = buildJsonObject {
    put("id", r.id)
    put("agentId", r.agentId)
    put("agentName", r.agentName)
    put("month", r.month)
    put("baseSalary", r.baseSalary)
    put("commissionTotal", r.commissionTotal)
    put("bonus", r.bonus)
    put("deductions", r.deductions)
    put("deductionReason", r.deductionReason?.let { JsonPrimitive(it) } ?: JsonNull)
    put("bonusReason", r.bonusReason?.let { JsonPrimitive(it) } ?: JsonNull)
    put("finalSalary", r.finalSalary)
    put("status", r.status)
    put("paidDate", r.paidDate?.let { JsonPrimitive(it) } ?: JsonNull)
    put("notes", r.notes?.let { JsonPrimitive(it) } ?: JsonNull)
    put("commissionDetails", buildJsonArray {
        r.commissionDetails.forEach { d ->
            add(buildJsonObject {
                put("clientId", d.clientId)
                put("clientName", d.clientName)
                put("plan", d.plan?.let { JsonPrimitive(it) } ?: JsonNull)
                put("clientAmount", d.clientAmount)
                put("commissionPercent", d.commissionPercent)
                put("commissionAmount", d.commissionAmount)
                put("commissionType", d.commissionType)
                put("monthNumber", d.monthNumber)
                put("isActive", d.isActive)
            })
        }
    })
}

// ════════════════════════════════════════════════════════════════
// Agent Photo Helper
// ════════════════════════════════════════════════════════════════

private fun agentPhotoHtml(photoUrl: String?, name: String, size: Int): String {
    val firstLetter = name.firstOrNull()?.toString() ?: "?"
    val colors = listOf("#1B3A5C", "#2E7D32", "#E65100", "#6A1B9A", "#C62828", "#00838F", "#4E342E")
    val colorIndex = name.hashCode().let { if (it < 0) -it else it } % colors.size
    val bgColor = colors[colorIndex]
    val fontSize = (size * 0.45).toInt()
    return if (photoUrl != null && photoUrl.isNotBlank()) {
        """<img src="$photoUrl" alt="$name" class="rounded-full object-cover" style="width:${size}px;height:${size}px;min-width:${size}px">"""
    } else {
        """<div class="rounded-full flex items-center justify-center text-white font-bold" style="width:${size}px;height:${size}px;min-width:${size}px;background:$bgColor;font-size:${fontSize}px">$firstLetter</div>"""
    }
}

// ════════════════════════════════════════════════════════════════
// Client Status Indicator
// ════════════════════════════════════════════════════════════════

private val activeStatuses = setOf("عميل جديد", "متابعة", "ديمو محجوز", "يحتاج مناقشة", "تجربة فعالة", "تفاوض")
private val convertedStatuses = setOf("مدفوع", "مشترك")
private val inactiveStatuses = setOf("رفض", "نشاط غير مناسب", "توقف", "تجربة منتهية")

private fun clientStatusIndicator(status: String): String = when (status) {
    in activeStatuses -> """<span class="inline-flex items-center gap-1 text-xs"><span class="w-2 h-2 rounded-full bg-blue-500 inline-block"></span>نشط</span>"""
    in convertedStatuses -> """<span class="inline-flex items-center gap-1 text-xs"><span class="w-2 h-2 rounded-full bg-green-600 inline-block"></span>محول</span>"""
    in inactiveStatuses -> """<span class="inline-flex items-center gap-1 text-xs"><span class="w-2 h-2 rounded-full bg-red-500 inline-block"></span>غير نشط</span>"""
    else -> """<span class="inline-flex items-center gap-1 text-xs"><span class="w-2 h-2 rounded-full bg-gray-400 inline-block"></span>-</span>"""
}

// ════════════════════════════════════════════════════════════════
// Dashboard Builders
// ════════════════════════════════════════════════════════════════

private fun buildOwnerDashboard(stats: CrmService.CrmStats, recentActivities: List<CrmService.ActivityDto>): String {
    // Calculate derived KPIs
    val activeClients = stats.byStatus.filterKeys { it in activeStatuses }.values.sum()
    val subscribedPaid = stats.subscribed + stats.paid
    val conversionRate = if (stats.totalClients > 0) (subscribedPaid * 100.0 / stats.totalClients) else 0.0

    // Clients not contacted 7+ days (estimated from stats — we show as a KPI)
    // This uses the agentStats to approximate, but ideally the service provides this
    val noContactClients = stats.byStatus.filterKeys { it in activeStatuses }.values.sum() // placeholder count

    // Revenue analytics
    val avgDeal = if (subscribedPaid > 0) stats.monthlyRevenue / subscribedPaid else 0.0
    val totalDiscounts = stats.agentStats.sumOf { it.revenue } - stats.monthlyRevenue
    val pipelineStatuses = setOf("متابعة", "تفاوض", "ديمو محجوز", "تجربة فعالة")
    // Pipeline value is approximated

    // Row 1 - Big KPI Cards
    val row1 = buildString {
        append(kpiCard("إجمالي العملاء", stats.totalClients.toString(), "👥", "#1B3A5C"))
        append(kpiCard("العملاء النشطين", activeClients.toString(), "🟢", "#2E7D32"))
        append(kpiCard("المشتركين", subscribedPaid.toString(), "✅", "#2E7D32"))
        append(kpiCard("الإيراد الشهري", "${String.format("%,.0f", stats.monthlyRevenue)} ج.م", "💰", "#1B3A5C"))
        append(kpiCard("نسبة التحويل", "${String.format("%.1f", conversionRate)}%", "📈", "#E65100"))
    }

    // Row 2 - Status Pipeline
    val pipelineHtml = buildString {
        append("<div class='bg-white rounded-xl shadow p-6 mb-8'>")
        append("<h3 class='text-lg font-bold mb-4'>مسار حالات العملاء</h3>")
        append("<div class='grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3'>")
        stats.byStatus.forEach { (status, count) ->
            val color = statusColor(status)
            val textColor = statusTextColor(status)
            append("""<div class="rounded-lg p-3 text-center" style="background:$color;color:$textColor"><div class="text-2xl font-bold">$count</div><div class="text-sm">$status</div></div>""")
        }
        append("</div></div>")
    }

    // Row 3 - Revenue Analytics
    val revenueHtml = buildString {
        append("<div class='grid grid-cols-2 md:grid-cols-4 gap-4 mb-8'>")
        append(kpiCard("إجمالي الإيراد الشهري", "${String.format("%,.0f", stats.monthlyRevenue)} ج.م", "💵", "#1B3A5C"))
        append(kpiCard("متوسط قيمة الصفقة", "${String.format("%,.0f", avgDeal)} ج.م", "📊", "#6A1B9A"))
        val pipelineValue = stats.byStatus.filterKeys { it in pipelineStatuses }.values.sum()
        append(kpiCard("العملاء في المسار", pipelineValue.toString(), "🔄", "#E65100"))
        append(kpiCard("إجمالي الأنشطة", stats.totalActivities.toString(), "📋", "#00838F"))
        append("</div>")
    }

    // Row 4 - Team Performance Summary
    val teamSummaryHtml = buildString {
        append("<div class='bg-white rounded-xl shadow p-6 mb-8'>")
        append("<h3 class='text-lg font-bold mb-4'>ملخص أداء الفريق</h3>")
        append("<div class='overflow-x-auto'><table class='w-full text-sm'><thead><tr class='border-b bg-gray-50'>")
        append("<th class='p-2 text-right'>الموظف</th><th class='p-2 text-right'>الأنشطة</th><th class='p-2 text-right'>العملاء</th><th class='p-2 text-right'>الاشتراكات</th><th class='p-2 text-right'>الإيراد</th>")
        append("</tr></thead><tbody>")
        stats.agentStats.forEach { a ->
            val photoHtml = agentPhotoHtml(a.photoUrl, a.agentName, 28)
            append("<tr class='border-b hover:bg-gray-50'>")
            append("<td class='p-2'><div class='flex items-center gap-2'>$photoHtml<span class='font-medium'>${a.agentName}</span></div></td>")
            append("<td class='p-2'>${a.totalActivities}</td>")
            append("<td class='p-2'>${a.clients}</td>")
            append("<td class='p-2'>${a.subscribed + a.paid}</td>")
            append("<td class='p-2'>${String.format("%,.0f", a.revenue)} ج.م</td>")
            append("</tr>")
        }
        append("</tbody></table></div></div>")
    }

    // Row 5 - Recent Activities
    val activitiesHtml = buildString {
        append("<div class='bg-white rounded-xl shadow p-6'>")
        append("<h3 class='text-lg font-bold mb-4'>آخر الأنشطة</h3>")
        append("<div class='overflow-x-auto'><table class='w-full text-sm'><thead><tr class='border-b'>")
        append("<th class='p-2 text-right'>التاريخ</th><th class='p-2 text-right'>الموظف</th><th class='p-2 text-right'>العميل</th><th class='p-2 text-right'>الإجراء</th><th class='p-2 text-right'>القناة</th><th class='p-2 text-right'>النتيجة</th>")
        append("</tr></thead><tbody>")
        recentActivities.forEach { act ->
            append("<tr class='border-b hover:bg-gray-50'>")
            append("<td class='p-2 text-xs text-gray-600 whitespace-nowrap'>${formatCrmDate(act.createdAt)}</td>")
            append("<td class='p-2'>${act.agentName}</td>")
            append("<td class='p-2'>${act.clientName}</td>")
            append("<td class='p-2'>${act.actionType ?: "-"}</td>")
            append("<td class='p-2'>${act.channel ?: "-"}</td>")
            append("<td class='p-2'>${act.result ?: "-"}</td>")
            append("</tr>")
        }
        append("</tbody></table></div></div>")
    }

    return """
        <div class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-4 mb-8">$row1</div>
        $pipelineHtml
        $revenueHtml
        $teamSummaryHtml
        $activitiesHtml
    """.trimIndent()
}

private fun buildBasicDashboard(stats: CrmService.CrmStats, recentActivities: List<CrmService.ActivityDto>): String {
    val kpiCards = buildString {
        append(kpiCard("إجمالي العملاء", stats.totalClients.toString(), "👥", "#1B3A5C"))
        append(kpiCard("مشتركين", stats.subscribed.toString(), "✅", "#2E7D32"))
        append(kpiCard("مدفوع", stats.paid.toString(), "💰", "#E65100"))
        append(kpiCard("الإيراد الشهري", "${String.format("%,.0f", stats.monthlyRevenue)} ج.م", "📊", "#1B3A5C"))
        val conversionRate = if (stats.totalClients > 0) ((stats.subscribed + stats.paid) * 100.0 / stats.totalClients) else 0.0
        append(kpiCard("معدل التحويل", "${String.format("%.1f", conversionRate)}%", "📈", "#2E7D32"))
    }

    val pipelineHtml = buildString {
        append("<div class='grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3 mb-8'>")
        stats.byStatus.forEach { (status, count) ->
            val color = statusColor(status)
            val textColor = statusTextColor(status)
            append("""<div class="rounded-lg p-3 text-center" style="background:$color;color:$textColor"><div class="text-2xl font-bold">$count</div><div class="text-sm">$status</div></div>""")
        }
        append("</div>")
    }

    val activitiesHtml = buildString {
        append("<div class='bg-white rounded-xl shadow p-6'>")
        append("<h3 class='text-lg font-bold mb-4'>آخر الأنشطة</h3>")
        append("<div class='overflow-x-auto'><table class='w-full text-sm'><thead><tr class='border-b'>")
        append("<th class='p-2 text-right'>التاريخ</th><th class='p-2 text-right'>الموظف</th><th class='p-2 text-right'>العميل</th><th class='p-2 text-right'>الإجراء</th><th class='p-2 text-right'>القناة</th><th class='p-2 text-right'>النتيجة</th>")
        append("</tr></thead><tbody>")
        recentActivities.forEach { act ->
            append("<tr class='border-b hover:bg-gray-50'>")
            append("<td class='p-2 text-xs text-gray-600 whitespace-nowrap'>${formatCrmDate(act.createdAt)}</td>")
            append("<td class='p-2'>${act.agentName}</td>")
            append("<td class='p-2'>${act.clientName}</td>")
            append("<td class='p-2'>${act.actionType ?: "-"}</td>")
            append("<td class='p-2'>${act.channel ?: "-"}</td>")
            append("<td class='p-2'>${act.result ?: "-"}</td>")
            append("</tr>")
        }
        append("</tbody></table></div></div>")
    }

    return """
        <div class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-4 mb-8">$kpiCards</div>
        <h3 class="text-lg font-bold mb-4">حالة العملاء</h3>
        $pipelineHtml
        $activitiesHtml
    """.trimIndent()
}

// ════════════════════════════════════════════════════════════════
// HTML Helper Functions
// ════════════════════════════════════════════════════════════════

/**
 * Formats an epoch-milliseconds timestamp for display in the dashboard.
 * Renders as `yyyy-MM-dd hh:mm ص|م` (12-hour clock with Arabic AM/PM markers) in Egypt
 * local time (CRM_TZ) — the VPS itself runs UTC, so we must never fall back to the
 * JVM's default zone. Returns "-" for null/zero so tables stay tidy when the value is
 * missing.
 */
private fun formatCrmDate(epochMs: Long?): String {
    if (epochMs == null || epochMs <= 0L) return "-"
    val ldt = kotlinx.datetime.Instant.fromEpochMilliseconds(epochMs)
        .toLocalDateTime(CRM_TZ)
    val mm = ldt.monthNumber.toString().padStart(2, '0')
    val dd = ldt.dayOfMonth.toString().padStart(2, '0')
    // 12-hour clock: 0 → 12 ص, 1–11 → ص, 12 → 12 م, 13–23 → 1–11 م.
    val hour24 = ldt.hour
    val hour12 = when {
        hour24 == 0 -> 12
        hour24 > 12 -> hour24 - 12
        else -> hour24
    }
    val ampm = if (hour24 < 12) "ص" else "م"
    val hh = hour12.toString().padStart(2, '0')
    val mi = ldt.minute.toString().padStart(2, '0')
    return "${ldt.year}-$mm-$dd $hh:$mi $ampm"
}

private fun statusColor(status: String): String = when (status) {
    "عميل جديد" -> "#E0E0E0"
    "متابعة" -> "#FFF9C4"
    "ديمو محجوز" -> "#FFE0B2"
    "يحتاج مناقشة" -> "#FFCCBC"
    "تجربة فعالة" -> "#C8E6C9"
    "تجربة منتهية" -> "#FFCDD2"
    "تفاوض" -> "#B3E5FC"
    "مدفوع" -> "#4CAF50"
    "مشترك" -> "#2E7D32"
    "رفض" -> "#EF5350"
    "نشاط غير مناسب" -> "#B71C1C"
    "توقف" -> "#9E9E9E"
    else -> "#E0E0E0"
}

private fun statusTextColor(status: String): String = when (status) {
    "مشترك", "رفض", "نشاط غير مناسب", "توقف" -> "#FFFFFF"
    "مدفوع" -> "#FFFFFF"
    else -> "#333333"
}

private fun kpiCard(label: String, value: String, icon: String, color: String): String = """
    <div class="bg-white rounded-xl shadow p-4">
        <div class="flex justify-between items-start">
            <div>
                <p class="text-sm text-gray-500">$label</p>
                <p class="text-2xl font-bold mt-1" style="color:$color">$value</p>
            </div>
            <span class="text-2xl">$icon</span>
        </div>
    </div>
""".trimIndent()

private fun crmLayout(title: String, agentName: String, agentRole: String, principal: CrmPrincipal, activeTab: String, content: String): String {
    val displayRole = roleDisplayName(agentRole)

    // Fetch current user's photo from DB
    val crmService by KoinJavaComponent.inject<CrmService>(clazz = CrmService::class.java)
    val myPhotoUrl = crmService.getAgentPhotoUrl(principal.agentId)
    val sidebarPhotoHtml = agentPhotoHtml(myPhotoUrl, agentName, 36)

    // Tenant branding — every CRM tenant sees their own logo + name in the
    // sidebar header. Logic:
    //   • If the org has a custom logo set → use it (for ALL orgs including
    //     Waselak; the super-admin uploading a Waselak logo expects it to
    //     replace the default art too).
    //   • Otherwise → fall back to the bundled Waselak art so brand-new orgs
    //     still get a sensible image instead of a broken `<img>`.
    // The brand *name* still falls back to "وصلك" for the Waselak org so we
    // don't redundantly write "Waselak CRM" — but a tenant org always shows
    // its own name. Sub-line "نظام إدارة المبيعات" describes the product so
    // it's the same for everyone.
    val branding = crmService.getOrgBranding(principal.organizationId)
    val isWaselakOrg = branding == null || branding.name == "Waselak"
    val brandLogoUrl = branding?.logoUrl?.takeIf { it.isNotBlank() }
        ?: "/landing/waslek_logo_sm.png"
    val brandName = if (isWaselakOrg) "وصلك" else (branding?.name ?: "وصلك")
    val brandFullName = if (isWaselakOrg) "وصلك CRM" else "${branding!!.name} CRM"
    val pageTitleSuffix = if (isWaselakOrg) " - وصلك CRM" else " - ${branding!!.name} CRM"

    fun navLink(tab: String, label: String, icon: String, href: String): String {
        val active = if (tab == activeTab) "bg-emerald-500/10 text-emerald-400 font-medium" else "text-zinc-400 hover:text-white hover:bg-white/5"
        return """<a href="$href" class="flex items-center gap-3 px-3 py-2 rounded-md $active transition-all text-sm">
            <span class="text-sm">$icon</span><span>$label</span>
        </a>"""
    }

    val navLinks = buildString {
        // Dashboard - everyone sees
        append(navLink("dashboard", "لوحة التحكم", "📊", "/crm/dashboard"))
        // Clients - everyone sees
        append(navLink("clients", "العملاء", "👥", "/crm/clients"))
        // Activities - everyone sees
        append(navLink("activities", "الأنشطة", "📋", "/crm/activities"))
        // Reports - owner only
        if (principal.canSeeAnalytics) {
            append(navLink("reports", "التقارير", "📈", "/crm/reports"))
        }
        // Billing - owner only
        if (principal.canSeeAnalytics) {
            append(navLink("billing", "الفواتير", "\uD83E\uDDFE", "/crm/billing"))
        }
        // Salaries - owner only
        if (principal.canSeeAnalytics) {
            append(navLink("salaries", "المرتبات", "💰", "/crm/salaries"))
        }
        // Team - owner + manager
        if (principal.canSeeAll) {
            append(navLink("team", "الفريق", "👤", "/crm/team"))
        }
        // Settings - owner only
        if (principal.canManageAgents) {
            append(navLink("settings", "الإعدادات", "⚙️", "/crm/settings"))
        }
        // Super-admin: organization (tenant) management. Only the Waselak ops team
        // sees this — any normal org owner has isSuperAdmin = false.
        if (principal.isSuperAdmin) {
            append(navLink("super_orgs", "المنظمات", "🏢", "/crm/super/organizations"))
        }
        // Profile - everyone can see their own profile
        append(navLink("profile", "ملفي", "👤", "/crm/profile"))
        // System docs - everyone
        append(navLink("docs", "دليل النظام", "📖", "/crm/docs"))
    }

    return """<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>$title$pageTitleSuffix</title>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">
    <script src="https://cdn.tailwindcss.com"></script>
    <script>
    tailwind.config = { darkMode: 'class' };
    if (localStorage.getItem('darkMode') === 'true') document.documentElement.classList.add('dark');
    </script>
    <style>
        * { font-family: 'Inter', -apple-system, 'Segoe UI', sans-serif; }
        dialog::backdrop { background: rgba(0,0,0,0.3); }
        dialog { border: none; border-radius: 0.75rem; padding: 0; max-width: 600px; width: 90%; box-shadow: 0 20px 60px -15px rgba(0,0,0,0.2); }
        ::-webkit-scrollbar { width: 5px; }
        ::-webkit-scrollbar-thumb { background: #d4d4d8; border-radius: 10px; }
        @keyframes pulse { 0%,100%{opacity:1} 50%{opacity:0.5} }
        .overdue { animation: pulse 2s infinite; }
        @media (max-width: 767px) { dialog { max-width: 100%; width: 100%; margin: 0; border-radius: 0.5rem; } }

        /* Stripe-inspired clean design */
        body { background: #f6f8fa; }
        .sidebar { background: #0f172a; }
        table { border-collapse: separate; border-spacing: 0; }
        table thead th { font-weight: 500; font-size: 0.75rem; text-transform: uppercase; letter-spacing: 0.05em; color: #6b7280; padding: 0.75rem 1rem; }
        table tbody td { padding: 0.75rem 1rem; }
        table tbody tr { transition: background 0.1s; border-bottom: 1px solid #f3f4f6; }
        table tbody tr:hover { background: #f9fafb; }
        input, select, textarea { border-radius: 0.5rem; border: 1px solid #d1d5db; padding: 0.5rem 0.75rem; font-size: 0.875rem; }
        input:focus, select:focus, textarea:focus { border-color: #059669; box-shadow: 0 0 0 3px rgba(5,150,105,0.1); outline: none; }

        /* Dark mode */
        html.dark body { background: #0a0a0a; color: #e5e7eb; }
        html.dark .sidebar { background: #0a0a0a; border-left: 1px solid #1f2937; }
        html.dark .bg-white { background: #111111 !important; color: #e5e7eb; border: 1px solid #1f2937; }
        html.dark .bg-gray-100, html.dark .bg-gray-50 { background: #0a0a0a !important; }
        html.dark .text-gray-800 { color: #f3f4f6 !important; }
        html.dark .text-gray-700 { color: #e5e7eb !important; }
        html.dark .text-gray-600 { color: #d1d5db !important; }
        html.dark .text-gray-500 { color: #9ca3af !important; }
        html.dark .border, html.dark .border-b { border-color: #1f2937 !important; }
        html.dark .shadow { box-shadow: none !important; }
        html.dark table thead th { color: #9ca3af !important; background: #111111 !important; }
        html.dark table tbody tr { border-color: #1f2937 !important; }
        html.dark table tbody tr:hover { background: #1a1a1a !important; }
        html.dark input, html.dark select, html.dark textarea { background: #1a1a1a !important; color: #e5e7eb !important; border-color: #374151 !important; }
        html.dark input:focus, html.dark select:focus { border-color: #059669 !important; box-shadow: 0 0 0 3px rgba(5,150,105,0.2) !important; }
        html.dark dialog { background: #111111 !important; color: #e5e7eb !important; border: 1px solid #1f2937; }
        html.dark h1, html.dark h2, html.dark h3 { color: #f3f4f6 !important; }
    </style>
</head>
<body class="min-h-screen transition-colors">
    ${productivityBannerHtml()}
    <!-- Mobile header -->
    <div class="md:hidden fixed top-0 right-0 left-0 z-50 sidebar flex items-center justify-between p-3">
        <div class="flex items-center gap-2">
            <img src="$brandLogoUrl?v=${System.currentTimeMillis() / 60_000}" class="w-7 h-7 rounded-lg bg-white p-0.5 object-cover">
            <span class="text-white font-bold text-sm">$brandName</span>
        </div>
        <a href="/crm/profile" class="flex items-center gap-2">
            $sidebarPhotoHtml
            <div class="text-white text-xs">
                <div class="font-medium leading-tight">$agentName</div>
                <div class="text-white/60 leading-tight">$displayRole</div>
            </div>
        </a>
        <button onclick="document.getElementById('mobileSidebar').classList.toggle('hidden')" class="text-white text-2xl">☰</button>
    </div>
    <!-- Mobile sidebar overlay -->
    <div id="mobileSidebar" class="hidden fixed inset-0 z-40 md:hidden">
        <div class="absolute inset-0 bg-black/50" onclick="this.parentElement.classList.add('hidden')"></div>
        <aside class="sidebar absolute right-0 top-0 bottom-0 w-64 text-white flex flex-col">
            <div class="p-6 border-b border-white/10 flex items-center gap-3">
                <img src="$brandLogoUrl?v=${System.currentTimeMillis() / 60_000}" alt="$brandName" class="w-10 h-10 rounded-lg bg-white p-1 object-cover">
                <div>
                    <h1 class="text-xl font-bold">$brandFullName</h1>
                    <p class="text-sm text-white/60">نظام إدارة المبيعات</p>
                </div>
            </div>
            <nav class="flex-1 p-4 space-y-1">
                $navLinks
            </nav>
            <div class="p-4 border-t border-white/10">
                <a href="/crm/profile" class="flex items-center gap-3 mb-3 hover:bg-white/5 rounded-lg p-2 -m-2 transition cursor-pointer">
                    $sidebarPhotoHtml
                    <div class="text-sm">
                        <p class="font-medium">$agentName</p>
                        <p class="text-white/60">$displayRole</p>
                    </div>
                </a>
                <a href="/crm/logout" class="flex items-center gap-2 text-sm text-white/60 hover:text-white transition mt-2">
                    <span>🚪</span><span>تسجيل الخروج</span>
                </a>
            </div>
        </aside>
    </div>

    <div class="flex min-h-screen">
        <!-- Desktop Sidebar -->
        <aside class="sidebar w-64 min-h-screen text-white hidden md:flex flex-col flex-shrink-0">
            <div class="p-6 border-b border-white/10 flex items-center gap-3">
                <img src="$brandLogoUrl?v=${System.currentTimeMillis() / 60_000}" alt="$brandName" class="w-10 h-10 rounded-lg bg-white p-1 object-cover">
                <div>
                    <h1 class="text-xl font-bold">$brandFullName</h1>
                    <p class="text-sm text-white/60">نظام إدارة المبيعات</p>
                </div>
            </div>
            <nav class="flex-1 p-4 space-y-1">
                $navLinks
            </nav>
            <div class="p-4 border-t border-white/10">
                <a href="/crm/profile" class="flex items-center gap-3 mb-3 hover:bg-white/5 rounded-lg p-2 -m-2 transition cursor-pointer">
                    $sidebarPhotoHtml
                    <div class="text-sm">
                        <p class="font-medium">$agentName</p>
                        <p class="text-white/60">$displayRole</p>
                    </div>
                </a>
                <div class="flex gap-2 mt-2">
                    <button onclick="toggleDarkMode()" class="flex items-center gap-1 text-xs text-white/60 hover:text-white transition px-2 py-1 rounded bg-white/10" title="Dark Mode">
                        <span id="darkIcon">🌙</span>
                    </button>
                    <button onclick="toggleLang()" class="flex items-center gap-1 text-xs text-white/60 hover:text-white transition px-2 py-1 rounded bg-white/10" title="Language">
                        <span id="langIcon">EN</span>
                    </button>
                    <a href="/crm/logout" class="flex items-center gap-1 text-xs text-white/60 hover:text-white transition px-2 py-1 rounded bg-white/10">
                        🚪
                    </a>
                </div>
            </div>
        </aside>

        <!-- Main Content -->
        <main class="flex-1 p-4 md:p-8 pt-16 md:pt-8 overflow-auto">
            <div class="mb-6 flex justify-between items-center">
                <h2 class="text-2xl font-bold text-gray-800 dark:text-gray-100">$title</h2>
            </div>
            $content
        </main>
    </div>
    <script>
    function toggleDarkMode() {
        const html = document.documentElement;
        html.classList.toggle('dark');
        const isDark = html.classList.contains('dark');
        localStorage.setItem('darkMode', isDark);
        document.getElementById('darkIcon').textContent = isDark ? '☀️' : '🌙';
    }
    // Set icon on load
    if (localStorage.getItem('darkMode') === 'true') {
        const di = document.getElementById('darkIcon');
        if (di) di.textContent = '☀️';
    }

    // Language toggle with full text replacement
    const t = {
        'لوحة التحكم':'Dashboard','العملاء':'Clients','الأنشطة':'Activities',
        'التقارير':'Reports','الفواتير':'Invoices','المرتبات':'Salaries',
        'الفريق':'Team','الإعدادات':'Settings','ملفي':'My Profile',
        'دليل النظام':'System Guide','تسجيل الخروج':'Logout',
        'نظام إدارة المبيعات':'Sales Management System',
        'وصلك CRM':'Waselak CRM','وصلك':'Waselak',
        '+ إضافة عميل':'+ Add Client','+ إضافة نشاط':'+ Add Activity',
        'إضافة عميل جديد':'Add New Client','إضافة نشاط جديد':'Add New Activity',
        'بحث بالاسم أو الرقم...':'Search by name or number...',
        'بحث...':'Search...','الحالة':'Status','الاسم':'Name',
        'الهاتف':'Phone','الباقة':'Plan','المبلغ':'Amount',
        'نوع النشاط':'Business Type','المدينة':'City','المحافظة':'Governorate',
        'الموظف المسؤول':'Assigned To','المصدر':'Source','ملاحظات':'Notes',
        'حفظ':'Save','إلغاء':'Cancel','تعديل':'Edit','حذف':'Delete',
        'اسم العميل':'Client Name','رقم الموبايل':'Phone Number',
        'اسم المحل':'Business Name','طريقة الدفع':'Payment',
        'المبلغ الشهري':'Monthly Amount','نسبة الخصم':'Discount %',
        'واتساب':'WhatsApp','لديه واتساب':'Has WhatsApp',
        'كل الحالات':'All Statuses','أنواع النشاط':'Business Types',
        'الموظفين':'Agents','المصادر':'Sources','مسح الفلاتر':'Clear Filters',
        'صاحب الحساب':'Owner','مدير مبيعات':'Sales Manager',
        'مندوب مبيعات':'Sales Agent','كول سنتر':'Call Center',
        'عميل جديد':'New Lead','متابعة':'Following Up',
        'ديمو محجوز':'Demo Scheduled','يحتاج مناقشة':'Needs Discussion',
        'تجربة فعالة':'Active Trial','تجربة منتهية':'Expired Trial',
        'تفاوض':'Negotiating','مدفوع':'Paid','مشترك':'Subscribed',
        'رفض':'Rejected','نشاط غير مناسب':'Invalid Business','توقف':'Churned',
        'نوع الإجراء':'Action Type','القناة':'Channel','النتيجة':'Result',
        'الخطوة القادمة':'Next Step','الموظف':'Agent','العميل':'Client',
        'الإجراء':'Action','الحالة الجديدة':'New Status','الحالة السابقة':'Previous Status',
        'أول اتصال':'First Call','عرض توضيحي':'Demo','إغلاق صفقة':'Closing',
        'إعادة تنشيط':'Re-activation','دعم فني':'Support','شكوى':'Complaint',
        'مكالمة تليفون':'Phone Call','زيارة':'Visit','فيديو كول':'Video Call',
        'رسالة SMS':'SMS','مهتم':'Interested','غير مهتم':'Not Interested',
        'طلب يرجعله':'Callback','بدأ تجربة':'Trial Started',
        'استلم الدفع':'Payment Received','اشتراك مؤكد':'Subscription Confirmed',
        'مردش':'No Answer','مشغول':'Busy','رقم غلط':'Wrong Number',
        'البريد الإلكتروني':'Email','كلمة المرور':'Password',
        'تسجيل الدخول':'Login','الدور':'Role',
        'إجمالي العملاء':'Total Clients','المشتركين':'Subscribed',
        'الإيراد الشهري':'Monthly Revenue','نسبة التحويل':'Conversion Rate',
        'عملاء نشطين':'Active Clients','بدون تواصل ٧+ أيام':'No Contact 7+ Days',
        'خط المبيعات':'Sales Pipeline','أداء الموظفين':'Team Performance',
        'آخر الأنشطة':'Recent Activities','أنشطة':'Activities',
        'عميل':'Client','اشتراكات':'Subscriptions','الإيراد':'Revenue',
    };
    // Build reverse map
    const tRev = {};
    for (const [ar, en] of Object.entries(t)) tRev[en] = ar;
    let isArabic = true;
    const origTexts = new Map(); // Store original node texts

    function translatePage(toEn) {
        const map = toEn ? t : tRev;
        // Translate text nodes
        const walk = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null, false);
        while (walk.nextNode()) {
            const node = walk.currentNode;
            const trimmed = node.textContent.trim();
            if (!trimmed) continue;
            // Try exact match
            if (map[trimmed]) {
                node.textContent = node.textContent.replace(trimmed, map[trimmed]);
                continue;
            }
            // Try partial matches for longer strings
            let txt = node.textContent;
            for (const [from, to] of Object.entries(map)) {
                if (txt.includes(from)) txt = txt.replaceAll(from, to);
            }
            node.textContent = txt;
        }
        // Translate placeholders
        document.querySelectorAll('input[placeholder], textarea[placeholder]').forEach(el => {
            const ph = el.placeholder.trim();
            if (map[ph]) el.placeholder = map[ph];
        });
        // Translate select options
        document.querySelectorAll('select option').forEach(opt => {
            const txt = opt.textContent.trim();
            if (map[txt]) opt.textContent = map[txt];
        });
        // Translate button text
        document.querySelectorAll('button, a').forEach(el => {
            if (el.children.length > 0) return;
            const txt = el.textContent.trim();
            if (map[txt]) el.textContent = map[txt];
        });
    }

    function toggleLang() {
        isArabic = !isArabic;
        document.documentElement.dir = isArabic ? 'rtl' : 'ltr';
        document.documentElement.lang = isArabic ? 'ar' : 'en';
        document.body.style.fontFamily = isArabic ? "'Segoe UI', Tahoma, Arial, sans-serif" : "Inter, 'Segoe UI', system-ui, sans-serif";
        document.querySelectorAll('#langIcon').forEach(b => b.textContent = isArabic ? 'EN' : 'عربي');
        localStorage.setItem('lang', isArabic ? 'ar' : 'en');
        translatePage(!isArabic);
    }
    if (localStorage.getItem('lang') === 'en') {
        isArabic = true; // will flip to false in toggleLang
        setTimeout(() => toggleLang(), 100);
    }
    </script>
</body>
</html>"""
}

private fun crmLoginPageHtml(error: String?): String {
    val errorBlock = if (error != null) {
        """<div class="bg-red-100 text-red-700 p-3 rounded-lg text-sm mb-4">البريد الإلكتروني أو كلمة المرور غير صحيحة</div>"""
    } else ""

    return """<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>تسجيل الدخول - وصلك CRM</title>
    <script src="https://cdn.tailwindcss.com"></script>
    <style>body { font-family: 'Segoe UI', Tahoma, Arial, sans-serif; }</style>
</head>
<body class="bg-gray-100 min-h-screen flex items-center justify-center">
    <div class="bg-white rounded-2xl shadow-xl p-8 w-full max-w-md">
        <div class="text-center mb-8">
            <img src="/landing/waslek_logo_sm.png" alt="وصلك" class="w-20 h-20 mx-auto mb-4 rounded-xl">
            <h1 class="text-2xl font-bold" style="color:#1B3A5C">وصلك CRM</h1>
            <p class="text-gray-500 mt-2">نظام إدارة المبيعات</p>
        </div>
        $errorBlock
        <form method="POST" action="/crm/login">
            <div class="mb-4">
                <label class="block text-sm font-medium text-gray-700 mb-2">البريد الإلكتروني</label>
                <input type="email" name="email" required class="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none" placeholder="example@waselak.com">
            </div>
            <div class="mb-6">
                <label class="block text-sm font-medium text-gray-700 mb-2">كلمة المرور</label>
                <input type="password" name="password" required class="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none" placeholder="••••••••">
            </div>
            <button type="submit" class="w-full text-white py-3 rounded-lg font-medium hover:opacity-90 transition" style="background:#1B3A5C">تسجيل الدخول</button>
        </form>
    </div>
</body>
</html>"""
}

// ════════════════════════════════════════════════════════════════
// Modal HTML Helpers
// ════════════════════════════════════════════════════════════════

private fun addClientModalHtml(agentOptions: String, canSeeAll: Boolean): String {
    val assignedField = if (canSeeAll) """
        <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">الموظف المسؤول</label>
            <select name="assignedTo" class="w-full px-3 py-2 border rounded-lg text-sm">
                <option value="">-- اختر --</option>
                $agentOptions
            </select>
        </div>
    """ else ""

    return """
    <dialog id="addClientModal" class="rounded-2xl">
        <div class="p-6">
            <div class="flex justify-between items-center mb-4">
                <h3 class="text-lg font-bold">إضافة عميل جديد</h3>
                <button onclick="document.getElementById('addClientModal').close()" class="text-gray-400 hover:text-gray-600 text-xl">&times;</button>
            </div>
            <form onsubmit="submitNewClient(event)" class="space-y-3 max-h-[70vh] overflow-y-auto">
                <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">اسم العميل *</label>
                        <input type="text" name="clientName" required class="w-full px-3 py-2 border rounded-lg text-sm">
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">رقم الهاتف</label>
                        <input type="text" name="phone" class="w-full px-3 py-2 border rounded-lg text-sm">
                    </div>
                    <div class="flex items-center gap-2 col-span-1 md:col-span-2">
                        <input type="checkbox" name="whatsapp" id="add_whatsapp" class="w-4 h-4">
                        <label for="add_whatsapp" class="text-sm">لديه واتساب</label>
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">اسم النشاط</label>
                        <input type="text" name="businessName" class="w-full px-3 py-2 border rounded-lg text-sm">
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">نوع النشاط</label>
                        <select name="businessType" class="w-full px-3 py-2 border rounded-lg text-sm">
                            <option value="">-- اختر --</option>
                            ${businessTypeOptions()}
                        </select>
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">المدينة</label>
                        <input type="text" name="city" class="w-full px-3 py-2 border rounded-lg text-sm">
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">المحافظة</label>
                        <select name="governorate" class="w-full px-3 py-2 border rounded-lg text-sm">
                            <option value="">-- اختر --</option>
                            ${governorateOptions()}
                        </select>
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">الحالة</label>
                        <select name="status" class="w-full px-3 py-2 border rounded-lg text-sm">
                            ${statusOptions()}
                        </select>
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">المصدر</label>
                        <select name="source" class="w-full px-3 py-2 border rounded-lg text-sm">
                            <option value="">-- اختر --</option>
                            ${sourceOptions()}
                        </select>
                    </div>
                    $assignedField
                    <div class="col-span-1 md:col-span-2">
                        <label class="block text-sm font-medium text-gray-700 mb-1">ملاحظات</label>
                        <textarea name="notes" rows="2" class="w-full px-3 py-2 border rounded-lg text-sm"></textarea>
                    </div>
                </div>
                <div class="flex justify-end gap-2 pt-3">
                    <button type="button" onclick="document.getElementById('addClientModal').close()" class="px-4 py-2 text-sm border rounded-lg hover:bg-gray-50">إلغاء</button>
                    <button type="submit" class="px-4 py-2 text-sm text-white rounded-lg hover:opacity-90" style="background:#2E7D32">حفظ</button>
                </div>
            </form>
        </div>
    </dialog>
    """
}

private fun editClientModalHtml(agentOptions: String, canSeeAll: Boolean): String {
    val assignedField = if (canSeeAll) """
        <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">الموظف المسؤول</label>
            <select name="assignedTo" id="edit_assignedTo" class="w-full px-3 py-2 border rounded-lg text-sm">
                <option value="">-- اختر --</option>
                $agentOptions
            </select>
        </div>
    """ else ""

    return """
    <dialog id="editClientModal" class="rounded-2xl">
        <div class="p-6">
            <div class="flex justify-between items-center mb-4">
                <h3 class="text-lg font-bold">تعديل بيانات العميل</h3>
                <button onclick="document.getElementById('editClientModal').close()" class="text-gray-400 hover:text-gray-600 text-xl">&times;</button>
            </div>
            <form onsubmit="submitEditClient(event)" class="space-y-3 max-h-[70vh] overflow-y-auto">
                <input type="hidden" name="id" id="edit_id">
                <div class="grid grid-cols-2 gap-3">
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">اسم العميل *</label>
                        <input type="text" name="clientName" id="edit_clientName" required class="w-full px-3 py-2 border rounded-lg text-sm">
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">رقم الهاتف *</label>
                        <input type="text" name="phone" id="edit_phone" required class="w-full px-3 py-2 border rounded-lg text-sm">
                    </div>
                    <div class="flex items-center gap-2 col-span-2">
                        <input type="checkbox" name="whatsapp" id="edit_whatsapp" class="w-4 h-4">
                        <label for="edit_whatsapp" class="text-sm">لديه واتساب</label>
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">اسم النشاط</label>
                        <input type="text" name="businessName" id="edit_businessName" class="w-full px-3 py-2 border rounded-lg text-sm">
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">نوع النشاط</label>
                        <select name="businessType" id="edit_businessType" class="w-full px-3 py-2 border rounded-lg text-sm">
                            <option value="">-- اختر --</option>
                            ${businessTypeOptions()}
                        </select>
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">المدينة</label>
                        <input type="text" name="city" id="edit_city" class="w-full px-3 py-2 border rounded-lg text-sm">
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">المحافظة</label>
                        <select name="governorate" id="edit_governorate" class="w-full px-3 py-2 border rounded-lg text-sm">
                            <option value="">-- اختر --</option>
                            ${governorateOptions()}
                        </select>
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">الحالة</label>
                        <select name="status" id="edit_status" class="w-full px-3 py-2 border rounded-lg text-sm">
                            ${statusOptions()}
                        </select>
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">الباقة</label>
                        <select name="plan" id="edit_plan" class="w-full px-3 py-2 border rounded-lg text-sm">
                            <option value="">-- اختر --</option>
                            ${planOptions()}
                        </select>
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">المبلغ الشهري</label>
                        <input type="number" name="monthlyAmount" id="edit_monthlyAmount" value="0" class="w-full px-3 py-2 border rounded-lg text-sm">
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">نسبة الخصم %</label>
                        <input type="number" name="discountPercent" id="edit_discountPercent" value="0" min="0" max="100" class="w-full px-3 py-2 border rounded-lg text-sm">
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">طريقة الدفع</label>
                        <select name="paymentMethod" id="edit_paymentMethod" class="w-full px-3 py-2 border rounded-lg text-sm">
                            <option value="">-- اختر --</option>
                            ${paymentMethodOptions()}
                        </select>
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">المصدر</label>
                        <select name="source" id="edit_source" class="w-full px-3 py-2 border rounded-lg text-sm">
                            <option value="">-- اختر --</option>
                            ${sourceOptions()}
                        </select>
                    </div>
                    $assignedField
                    <div class="col-span-2">
                        <label class="block text-sm font-medium text-gray-700 mb-1">ملاحظات</label>
                        <textarea name="notes" id="edit_notes" rows="2" class="w-full px-3 py-2 border rounded-lg text-sm"></textarea>
                    </div>
                </div>
                <div class="flex justify-end gap-2 pt-3">
                    <button type="button" onclick="document.getElementById('editClientModal').close()" class="px-4 py-2 text-sm border rounded-lg hover:bg-gray-50">إلغاء</button>
                    <button type="submit" class="px-4 py-2 text-sm text-white rounded-lg hover:opacity-90" style="background:#1B3A5C">حفظ التعديلات</button>
                </div>
            </form>
        </div>
    </dialog>
    """
}

/**
 * Searchable client combobox for the add-activity modal. Replaces the plain native
 * `<select>` which was unusable once a vendor had more than a dozen clients:
 *  - Type in the visible search input to filter by name OR phone number
 *  - Arabic-Indic digits (٠-٩) are normalised to Western so `٠١٠١٢٣٤` matches `01012345`
 *  - Partial matches on both name and phone; non-digit characters in the phone are
 *    ignored (so spaces/dashes in typed input don't break the match)
 *  - Click an item to select; the hidden `clientId` input carries the UUID the backend
 *    expects while the visible field shows a human-readable "Name · Phone" line
 *  - Click outside, press Escape, or clear the input to close the dropdown
 *
 * Drop-in: existing flows like `onClientSelected(...)` still fire on selection, so
 * the "auto-prefill status/plan/amount from client data" side-effect keeps working.
 */
private fun addActivityModalHtml(clientsJson: String, preselectedClientId: String? = null): String = """
    <dialog id="addActivityModal" class="rounded-2xl">
        <div class="p-6">
            <div class="flex justify-between items-center mb-4">
                <h3 class="text-lg font-bold">إضافة نشاط جديد</h3>
                <button onclick="document.getElementById('addActivityModal').close()" class="text-gray-400 hover:text-gray-600 text-xl">&times;</button>
            </div>
            <form onsubmit="submitNewActivity(event)" class="space-y-3 max-h-[70vh] overflow-y-auto">
                <div class="grid grid-cols-2 gap-3">
                    <div class="col-span-2 relative">
                        <label class="block text-sm font-medium text-gray-700 mb-1">العميل *</label>
                        <!-- Searchable client combobox. The visible <input> carries the search
                             string only; the real UUID goes into the hidden <input name="clientId">
                             so the form POST contract with the backend is unchanged. -->
                        <input type="hidden" name="clientId" id="addActivityClientId" required value="${preselectedClientId ?: ""}">
                        <input type="text" id="addActivityClientSearch"
                               placeholder="ابحث بالاسم أو رقم الهاتف..."
                               autocomplete="off"
                               oninput="crmFilterClientCombo('addActivity')"
                               onfocus="crmOpenClientCombo('addActivity')"
                               class="w-full px-3 py-2 border rounded-lg text-sm pr-8">
                        <span class="absolute left-3 top-9 text-gray-400 pointer-events-none">🔍</span>
                        <div id="addActivityClientList"
                             class="hidden absolute z-20 mt-1 w-full max-h-64 overflow-y-auto bg-white border rounded-lg shadow-lg"></div>
                        <div id="addActivityClientEmpty" class="hidden mt-1 text-xs text-gray-500">لا يوجد عميل مطابق</div>
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">نوع الإجراء</label>
                        <select name="actionType" class="w-full px-3 py-2 border rounded-lg text-sm">
                            <option value="">-- اختر --</option>
                            ${actionTypeOptions()}
                        </select>
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">القناة</label>
                        <select name="channel" class="w-full px-3 py-2 border rounded-lg text-sm">
                            <option value="">-- اختر --</option>
                            ${channelOptions()}
                        </select>
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">الحالة السابقة</label>
                        <select name="previousStatus" class="w-full px-3 py-2 border rounded-lg text-sm">
                            <option value="">-- اختر --</option>
                            ${statusOptions()}
                        </select>
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">الحالة الجديدة</label>
                        <select name="newStatus" class="w-full px-3 py-2 border rounded-lg text-sm">
                            <option value="">-- اختر --</option>
                            ${statusOptions()}
                        </select>
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">الباقة المعروضة</label>
                        <select name="planOffered" class="w-full px-3 py-2 border rounded-lg text-sm">
                            <option value="">-- اختر --</option>
                            ${planOptions()}
                        </select>
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">المبلغ</label>
                        <input type="number" name="amount" value="0" class="w-full px-3 py-2 border rounded-lg text-sm">
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">نسبة الخصم %</label>
                        <input type="number" name="discountPercent" value="0" min="0" max="100" class="w-full px-3 py-2 border rounded-lg text-sm">
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">مدة المكالمة</label>
                        <input type="text" name="callDuration" placeholder="مثال: 5 دقائق" class="w-full px-3 py-2 border rounded-lg text-sm">
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">النتيجة</label>
                        <select name="result" class="w-full px-3 py-2 border rounded-lg text-sm">
                            <option value="">-- اختر --</option>
                            ${resultOptions()}
                        </select>
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">الخطوة القادمة</label>
                        <input type="text" name="nextStep" class="w-full px-3 py-2 border rounded-lg text-sm">
                    </div>
                    <div>
                    <div class="col-span-2">
                        <label class="block text-sm font-medium text-gray-700 mb-1">ملاحظات</label>
                        <textarea name="notes" rows="2" class="w-full px-3 py-2 border rounded-lg text-sm"></textarea>
                    </div>
                </div>
                <div class="flex justify-end gap-2 pt-3">
                    <button type="button" onclick="document.getElementById('addActivityModal').close()" class="px-4 py-2 text-sm border rounded-lg hover:bg-gray-50">إلغاء</button>
                    <button type="submit" class="px-4 py-2 text-sm text-white rounded-lg hover:opacity-90" style="background:#2E7D32">حفظ</button>
                </div>
            </form>
        </div>
    </dialog>
    <script>
    // One-time setup per page load. We scope everything under a namespace so multiple
    // modals on the same page (rare, but possible) don't collide.
    (function() {
        if (window.__crmClientCombos) { return; }
        window.__crmClientCombos = {};
        // Map Arabic-Indic digits 0-9 to their Western equivalents so searches match
        // regardless of keyboard layout.
        var AR_DIGITS = { '٠':'0','١':'1','٢':'2','٣':'3','٤':'4','٥':'5','٦':'6','٧':'7','٨':'8','٩':'9' };
        function digitsOnly(s) {
            if (!s) return '';
            var out = '';
            for (var i = 0; i < s.length; i++) {
                var c = s[i];
                if (AR_DIGITS[c] !== undefined) out += AR_DIGITS[c];
                else if (c >= '0' && c <= '9') out += c;
            }
            return out;
        }
        function lowerNormalized(s) { return (s || '').toString().toLowerCase(); }

        /** Render filtered items into the dropdown for `prefix` using its clients list. */
        function renderCombo(prefix) {
            var state = window.__crmClientCombos[prefix];
            if (!state) return;
            var searchRaw = state.search.value || '';
            var searchLower = lowerNormalized(searchRaw);
            var searchDigits = digitsOnly(searchRaw);
            var matches = state.clients.filter(function(c) {
                if (!searchLower) return true;
                if (lowerNormalized(c.name).indexOf(searchLower) !== -1) return true;
                if (searchDigits && digitsOnly(c.phone).indexOf(searchDigits) !== -1) return true;
                return false;
            });
            // Cap the visible list so a huge vendor (thousands of clients) doesn't blow
            // up the DOM. Users narrow with the search anyway.
            var capped = matches.slice(0, 50);
            state.list.innerHTML = '';
            capped.forEach(function(c) {
                var btn = document.createElement('button');
                btn.type = 'button';
                btn.className = 'w-full text-right p-2.5 hover:bg-emerald-50 border-b border-gray-100 last:border-0 flex items-center justify-between gap-3';
                btn.setAttribute('data-id', c.id);
                btn.onclick = function() { selectClientInCombo(prefix, c); };
                var left = document.createElement('div');
                left.className = 'min-w-0';
                var name = document.createElement('div');
                name.className = 'text-sm font-medium text-gray-800 truncate';
                name.textContent = c.name;
                var phone = document.createElement('div');
                phone.className = 'text-xs text-gray-500 font-mono';
                phone.textContent = c.phone;
                phone.setAttribute('dir', 'ltr');
                left.appendChild(name); left.appendChild(phone);
                btn.appendChild(left);
                state.list.appendChild(btn);
            });
            state.empty.classList.toggle('hidden', matches.length > 0);
            state.list.classList.toggle('hidden', capped.length === 0);
            // Show a soft "and N more" hint when we cap the list so users know there's more.
            if (matches.length > capped.length) {
                var more = document.createElement('div');
                more.className = 'p-2 text-xs text-gray-400 text-center bg-gray-50';
                more.textContent = '… و ' + (matches.length - capped.length) + ' نتيجة أخرى — اكتب المزيد للتضييق';
                state.list.appendChild(more);
            }
        }

        window.crmFilterClientCombo = function(prefix) { renderCombo(prefix); };
        window.crmOpenClientCombo = function(prefix) {
            renderCombo(prefix);
        };
        window.crmCloseClientCombos = function() {
            Object.keys(window.__crmClientCombos).forEach(function(p) {
                var state = window.__crmClientCombos[p];
                if (state && state.list) state.list.classList.add('hidden');
            });
        };

        /** Commit a selection: fill hidden ID + visible text, close list, fire prefill callback. */
        function selectClientInCombo(prefix, client) {
            var state = window.__crmClientCombos[prefix];
            if (!state) return;
            state.hidden.value = client.id;
            state.search.value = client.name + ' · ' + client.phone;
            state.list.classList.add('hidden');
            state.empty.classList.add('hidden');
            // Preserve the side-effect the old <select onchange="onClientSelected(this)">
            // had: prefill status/plan/amount from the selected client's current values.
            if (typeof window.onClientSelected === 'function') {
                // Fake a <select>-like object that onClientSelected understands.
                window.onClientSelected({ value: client.id, closest: function() { return document.getElementById('addActivityModal'); } });
            }
        }
        window.crmSelectClientInCombo = selectClientInCombo;

        /** Register + optionally pre-select a client. Called inline after the dialog is in the DOM. */
        window.crmRegisterClientCombo = function(prefix, clients, preselectedId) {
            var state = {
                clients: clients,
                hidden: document.getElementById(prefix + 'ClientId'),
                search: document.getElementById(prefix + 'ClientSearch'),
                list: document.getElementById(prefix + 'ClientList'),
                empty: document.getElementById(prefix + 'ClientEmpty'),
            };
            window.__crmClientCombos[prefix] = state;
            if (preselectedId) {
                var pre = clients.find(function(c) { return c.id === preselectedId; });
                if (pre) {
                    state.hidden.value = pre.id;
                    state.search.value = pre.name + ' · ' + pre.phone;
                }
            }
        };

        // Clicking anywhere outside an open combo closes it.
        document.addEventListener('click', function(e) {
            Object.keys(window.__crmClientCombos).forEach(function(p) {
                var state = window.__crmClientCombos[p];
                if (!state) return;
                if (state.search && !state.search.parentElement.contains(e.target)) {
                    state.list.classList.add('hidden');
                }
            });
        });
        // Escape closes all combos.
        document.addEventListener('keydown', function(e) {
            if (e.key === 'Escape') window.crmCloseClientCombos();
        });
    })();
    // Register this modal's combo with its clients list. Runs immediately since the
    // <input>/<div> elements are already in the DOM by the time this <script> executes.
    (function() {
        var CLIENTS = $clientsJson;
        var PRESELECTED = ${preselectedClientId?.let { "\"$it\"" } ?: "null"};
        if (document.getElementById('addActivityClientSearch')) {
            window.crmRegisterClientCombo('addActivity', CLIENTS, PRESELECTED);
        }
    })();
    </script>
"""

private fun editActivityModalHtml(clientOptions: String): String = """
    <dialog id="editActivityModal" class="rounded-2xl">
        <div class="p-6">
            <div class="flex justify-between items-center mb-4">
                <h3 class="text-lg font-bold">تعديل النشاط</h3>
                <button onclick="document.getElementById('editActivityModal').close()" class="text-gray-400 hover:text-gray-600 text-xl">&times;</button>
            </div>
            <form onsubmit="submitEditActivity(event)" class="space-y-3 max-h-[70vh] overflow-y-auto">
                <input type="hidden" name="id" id="edit_activity_id">
                <div class="grid grid-cols-2 gap-3">
                    <div class="col-span-2">
                        <label class="block text-sm font-medium text-gray-700 mb-1">العميل</label>
                        <select name="clientId" id="edit_activity_clientId" disabled class="w-full px-3 py-2 border rounded-lg text-sm bg-gray-50">
                            <option value="">-- اختر العميل --</option>
                            $clientOptions
                        </select>
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">نوع الإجراء</label>
                        <select name="actionType" id="edit_activity_actionType" class="w-full px-3 py-2 border rounded-lg text-sm">
                            <option value="">-- اختر --</option>
                            ${actionTypeOptions()}
                        </select>
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">القناة</label>
                        <select name="channel" id="edit_activity_channel" class="w-full px-3 py-2 border rounded-lg text-sm">
                            <option value="">-- اختر --</option>
                            ${channelOptions()}
                        </select>
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">الحالة السابقة</label>
                        <select name="previousStatus" id="edit_activity_previousStatus" class="w-full px-3 py-2 border rounded-lg text-sm">
                            <option value="">-- اختر --</option>
                            ${statusOptions()}
                        </select>
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">الحالة الجديدة</label>
                        <select name="newStatus" id="edit_activity_newStatus" class="w-full px-3 py-2 border rounded-lg text-sm">
                            <option value="">-- اختر --</option>
                            ${statusOptions()}
                        </select>
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">الباقة المعروضة</label>
                        <select name="planOffered" id="edit_activity_planOffered" class="w-full px-3 py-2 border rounded-lg text-sm">
                            <option value="">-- اختر --</option>
                            ${planOptions()}
                        </select>
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">المبلغ</label>
                        <input type="number" name="amount" id="edit_activity_amount" value="0" class="w-full px-3 py-2 border rounded-lg text-sm">
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">نسبة الخصم %</label>
                        <input type="number" name="discountPercent" id="edit_activity_discountPercent" value="0" min="0" max="100" class="w-full px-3 py-2 border rounded-lg text-sm">
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">مدة المكالمة</label>
                        <input type="text" name="callDuration" id="edit_activity_callDuration" class="w-full px-3 py-2 border rounded-lg text-sm">
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">النتيجة</label>
                        <select name="result" id="edit_activity_result" class="w-full px-3 py-2 border rounded-lg text-sm">
                            <option value="">-- اختر --</option>
                            ${resultOptions()}
                        </select>
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">الخطوة القادمة</label>
                        <input type="text" name="nextStep" id="edit_activity_nextStep" class="w-full px-3 py-2 border rounded-lg text-sm">
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">تاريخ الإجراء القادم</label>
                        <input type="date" name="nextDate" id="edit_activity_nextDate" class="w-full px-3 py-2 border rounded-lg text-sm">
                    </div>
                    <div class="col-span-2">
                        <label class="block text-sm font-medium text-gray-700 mb-1">ملاحظات</label>
                        <textarea name="notes" id="edit_activity_notes" rows="2" class="w-full px-3 py-2 border rounded-lg text-sm"></textarea>
                    </div>
                </div>
                <div class="flex justify-end gap-2 pt-3">
                    <button type="button" onclick="document.getElementById('editActivityModal').close()" class="px-4 py-2 text-sm border rounded-lg hover:bg-gray-50">إلغاء</button>
                    <button type="submit" class="px-4 py-2 text-sm text-white rounded-lg hover:opacity-90" style="background:#2E7D32">حفظ</button>
                </div>
            </form>
        </div>
    </dialog>
"""

private fun addAgentModalHtml(): String = """
    <dialog id="addAgentModal" class="rounded-2xl">
        <div class="p-6">
            <div class="flex justify-between items-center mb-4">
                <h3 class="text-lg font-bold">إضافة موظف جديد</h3>
                <button onclick="document.getElementById('addAgentModal').close()" class="text-gray-400 hover:text-gray-600 text-xl">&times;</button>
            </div>
            <form onsubmit="submitNewAgent(event)" class="space-y-3">
                <div>
                    <label class="block text-sm font-medium text-gray-700 mb-1">الاسم *</label>
                    <input type="text" name="name" required class="w-full px-3 py-2 border rounded-lg text-sm">
                </div>
                <div>
                    <label class="block text-sm font-medium text-gray-700 mb-1">البريد الإلكتروني *</label>
                    <input type="email" name="email" required class="w-full px-3 py-2 border rounded-lg text-sm">
                </div>
                <div>
                    <label class="block text-sm font-medium text-gray-700 mb-1">كلمة المرور *</label>
                    <input type="password" name="password" required class="w-full px-3 py-2 border rounded-lg text-sm">
                </div>
                <div>
                    <label class="block text-sm font-medium text-gray-700 mb-1">الدور</label>
                    <select name="role" class="w-full px-3 py-2 border rounded-lg text-sm">
                        ${roleOptions()}
                    </select>
                </div>
                <div class="flex justify-end gap-2 pt-3">
                    <button type="button" onclick="document.getElementById('addAgentModal').close()" class="px-4 py-2 text-sm border rounded-lg hover:bg-gray-50">إلغاء</button>
                    <button type="submit" class="px-4 py-2 text-sm text-white rounded-lg hover:opacity-90" style="background:#2E7D32">حفظ</button>
                </div>
            </form>
        </div>
    </dialog>
"""

// ════════════════════════════════════════════════════════════════
// Dropdown Options Helpers
// ════════════════════════════════════════════════════════════════

// ─── Filter Bar Helper ──────────────────────────────────────────
// Each filter: Pair(label, listOf(options)), data-col = column index
private fun filterBarHtml(filters: List<Triple<String, Int, List<String>>>, searchPlaceholder: String): String {
    val dropdowns = filters.joinToString("\n") { (label, colIndex, options) ->
        val opts = options.joinToString("") { """<option value="$it">$it</option>""" }
        """<select class="filter-select px-3 py-2 border rounded-lg text-sm" data-col="$colIndex" onchange="applyFilters()">
            <option value="">كل $label</option>
            $opts
        </select>"""
    }
    return """
        <div class="bg-white rounded-xl shadow p-4 mb-4">
            <div class="flex flex-wrap gap-3 items-center">
                <input id="searchInput" oninput="applyFilters()" type="text" placeholder="$searchPlaceholder" class="px-3 py-2 border rounded-lg text-sm w-full md:w-64">
                $dropdowns
                <button onclick="clearFilters()" class="text-sm text-red-600 hover:underline">مسح الفلاتر</button>
            </div>
        </div>
    """
}

private fun filterScript(): String = """
    <script>
    function applyFilters() {
        const search = document.getElementById('searchInput').value.toLowerCase();
        const table = document.getElementById('dataTable');
        if (!table) return;
        const rows = table.querySelectorAll('tbody tr');
        const filters = {};
        document.querySelectorAll('.filter-select').forEach(s => { filters[s.dataset.col] = s.value; });

        rows.forEach(row => {
            const text = row.textContent.toLowerCase();
            const matchSearch = !search || text.includes(search);
            let matchFilters = true;
            for (const [col, val] of Object.entries(filters)) {
                if (!val) continue;
                const colIdx = parseInt(col);
                if (colIdx < 0) { if (!text.includes(val.toLowerCase())) matchFilters = false; }
                else if (!row.children[colIdx]?.textContent?.includes(val)) matchFilters = false;
            }
            row.style.display = (matchSearch && matchFilters) ? '' : 'none';
        });
        updateVisibleCount();
    }

    function clearFilters() {
        document.getElementById('searchInput').value = '';
        document.querySelectorAll('.filter-select').forEach(s => s.value = '');
        document.querySelectorAll('.status-chip').forEach(c => c.classList.remove('ring-2', 'ring-offset-1'));
        applyFilters();
    }

    function filterByStatusChip(status, el) {
        // Toggle: if already selected, clear it
        const statusSelect = document.querySelector('.filter-select[data-col="' + el.dataset.statusCol + '"]');
        if (statusSelect) {
            if (statusSelect.value === status) {
                statusSelect.value = '';
            } else {
                statusSelect.value = status;
            }
        }
        document.querySelectorAll('.status-chip').forEach(c => c.classList.remove('ring-2', 'ring-offset-1'));
        if (statusSelect && statusSelect.value) {
            el.classList.add('ring-2', 'ring-offset-1');
        }
        applyFilters();
    }

    function updateVisibleCount() {
        const table = document.getElementById('dataTable');
        if (!table) return;
        const rows = table.querySelectorAll('tbody tr');
        let visible = 0;
        rows.forEach(r => { if (r.style.display !== 'none') visible++; });
        const counter = document.getElementById('visibleCount');
        if (counter) counter.textContent = visible;
    }

    // For card-based pages (team)
    function applyCardFilters() {
        const search = document.getElementById('searchInput').value.toLowerCase();
        const cards = document.querySelectorAll('.filterable-card');
        const filters = {};
        document.querySelectorAll('.filter-select').forEach(s => { filters[s.dataset.field] = s.value; });

        cards.forEach(card => {
            const text = card.textContent.toLowerCase();
            const matchSearch = !search || text.includes(search);
            let matchFilters = true;
            for (const [field, val] of Object.entries(filters)) {
                if (val && card.dataset[field] !== val) matchFilters = false;
            }
            card.style.display = (matchSearch && matchFilters) ? '' : 'none';
        });
    }

    function clearCardFilters() {
        document.getElementById('searchInput').value = '';
        document.querySelectorAll('.filter-select').forEach(s => s.value = '');
        applyCardFilters();
    }
    </script>
"""

/**
 * Config objects for the smart filter panel. Rows in the filtered table must expose
 * the referenced data attributes (e.g. `data-created-ms="1713600000000"`) so we can
 * filter on real values rather than parsed text.
 */
internal data class SmartDateRange(val dataAttr: String, val label: String, val idPrefix: String)
internal data class SmartNumberRange(val dataAttr: String, val label: String, val idPrefix: String, val suffix: String = "")
internal data class SmartMultiSelect(
    val dataAttr: String,
    val label: String,
    val id: String,
    val options: List<String>,
)
internal data class SmartPreset(
    val id: String,
    val label: String,
    /** Raw JS snippet that mutates panel inputs then calls applySmartFilters(). */
    val onClickJs: String,
    val emoji: String = "",
)

/**
 * Smart filter panel: full-text search + quick presets + optional date ranges +
 * optional numeric range + multi-select dropdowns + active-filter chips + clear-all.
 * Works with `smartFilterScript()` which reads each row's `data-*` attributes.
 */
internal fun smartFilterPanelHtml(
    searchPlaceholder: String,
    primaryDateRange: SmartDateRange? = null,
    secondaryDateRange: SmartDateRange? = null,
    numberRange: SmartNumberRange? = null,
    multiSelects: List<SmartMultiSelect> = emptyList(),
    presets: List<SmartPreset> = emptyList(),
): String {
    fun dateRangeHtml(r: SmartDateRange): String = """
        <div class="flex flex-col gap-1 min-w-[220px]">
            <label class="text-xs text-gray-500">${r.label}</label>
            <div class="flex items-center gap-1">
                <input type="date" id="${r.idPrefix}From" data-attr="${r.dataAttr}" oninput="applySmartFilters()"
                       class="smart-range-from px-2 py-1.5 border rounded-lg text-xs flex-1">
                <span class="text-gray-400 text-xs">←</span>
                <input type="date" id="${r.idPrefix}To" data-attr="${r.dataAttr}" oninput="applySmartFilters()"
                       class="smart-range-to px-2 py-1.5 border rounded-lg text-xs flex-1">
            </div>
        </div>
    """

    fun numberRangeHtml(r: SmartNumberRange): String = """
        <div class="flex flex-col gap-1 min-w-[180px]">
            <label class="text-xs text-gray-500">${r.label}</label>
            <div class="flex items-center gap-1">
                <input type="number" id="${r.idPrefix}Min" data-attr="${r.dataAttr}" oninput="applySmartFilters()"
                       placeholder="من" class="smart-number-min px-2 py-1.5 border rounded-lg text-xs w-24">
                <span class="text-gray-400 text-xs">←</span>
                <input type="number" id="${r.idPrefix}Max" data-attr="${r.dataAttr}" oninput="applySmartFilters()"
                       placeholder="إلى" class="smart-number-max px-2 py-1.5 border rounded-lg text-xs w-24">
                ${if (r.suffix.isNotBlank()) """<span class="text-xs text-gray-500">${r.suffix}</span>""" else ""}
            </div>
        </div>
    """

    fun multiSelectHtml(ms: SmartMultiSelect): String {
        val checkboxes = ms.options.joinToString("") { opt ->
            """<label class="flex items-center gap-2 px-2 py-1 hover:bg-gray-50 rounded cursor-pointer">
                <input type="checkbox" value="${opt.replace("\"", "&quot;")}" onchange="updateMultiSelect('${ms.id}'); applySmartFilters()"
                       class="rounded text-emerald-600 focus:ring-emerald-500 smart-ms-${ms.id}">
                <span class="text-xs">${opt}</span>
            </label>"""
        }
        return """
            <div class="flex flex-col gap-1 min-w-[180px] relative">
                <label class="text-xs text-gray-500">${ms.label}</label>
                <button type="button" onclick="toggleMultiSelect('${ms.id}')"
                        class="px-3 py-1.5 border rounded-lg text-xs text-right bg-white hover:bg-gray-50 flex items-center justify-between gap-2">
                    <span id="${ms.id}Label" class="truncate">الكل</span>
                    <span class="text-gray-400 text-xs">▾</span>
                </button>
                <div id="${ms.id}Panel" data-attr="${ms.dataAttr}" data-field="${ms.id}"
                     class="smart-ms-panel hidden absolute top-full right-0 mt-1 w-56 max-h-64 overflow-y-auto bg-white border rounded-lg shadow-lg p-2 z-20">
                    $checkboxes
                </div>
            </div>
        """
    }

    val presetButtons = presets.joinToString("") { p ->
        """<button type="button" id="preset_${p.id}" onclick="${p.onClickJs}"
               class="smart-preset px-3 py-1.5 rounded-full text-xs font-medium bg-slate-100 text-slate-700 hover:bg-slate-200 transition whitespace-nowrap">
            ${if (p.emoji.isNotEmpty()) p.emoji + " " else ""}${p.label}
        </button>"""
    }

    return """
        <div class="bg-white rounded-xl shadow-sm border border-gray-100 p-4 mb-4">
            <!-- Search row -->
            <div class="flex items-center gap-3 mb-3">
                <div class="flex-1 relative">
                    <span class="absolute right-3 top-2.5 text-gray-400 text-sm">🔍</span>
                    <input id="searchInput" oninput="applySmartFilters()" type="text" placeholder="$searchPlaceholder"
                           class="pr-9 pl-3 py-2 border rounded-lg text-sm w-full">
                </div>
                <button type="button" onclick="clearSmartFilters()"
                        class="flex items-center gap-1 text-xs text-red-600 hover:bg-red-50 px-3 py-2 rounded-lg transition whitespace-nowrap">
                    <span>✕</span><span>مسح الكل</span>
                </button>
            </div>

            <!-- Quick presets -->
            ${if (presets.isNotEmpty()) """<div class="flex flex-wrap gap-2 mb-3 pb-3 border-b border-gray-100">$presetButtons</div>""" else ""}

            <!-- Filter inputs row -->
            <div class="flex flex-wrap gap-3 items-start">
                ${primaryDateRange?.let { dateRangeHtml(it) } ?: ""}
                ${secondaryDateRange?.let { dateRangeHtml(it) } ?: ""}
                ${numberRange?.let { numberRangeHtml(it) } ?: ""}
                ${multiSelects.joinToString("") { multiSelectHtml(it) }}
            </div>

            <!-- Active filter chips (populated by JS) -->
            <div id="activeFilterChips" class="flex flex-wrap gap-2 mt-3 empty:hidden"></div>
        </div>
    """
}

/**
 * JS engine for the smart filter panel. Works by reading `data-*` attributes on each
 * row. Supports: text search, date ranges, number ranges, multi-select checkbox
 * groups, and preset-driven programmatic filter mutation.
 */
internal fun smartFilterScript(): String = """
<script>
(function() {
    // Global functions exposed on window for inline onclick handlers.
    window.toggleMultiSelect = function(id) {
        document.querySelectorAll('.smart-ms-panel').forEach(p => {
            if (p.id !== id + 'Panel') p.classList.add('hidden');
        });
        document.getElementById(id + 'Panel').classList.toggle('hidden');
    };

    window.updateMultiSelect = function(id) {
        const checked = Array.from(document.querySelectorAll('.smart-ms-' + id + ':checked')).map(x => x.value);
        const label = document.getElementById(id + 'Label');
        if (checked.length === 0) label.textContent = 'الكل';
        else if (checked.length === 1) label.textContent = checked[0];
        else label.textContent = checked.length + ' محدد';
    };

    // Close open panels when clicking outside.
    document.addEventListener('click', function(e) {
        if (!e.target.closest('.smart-ms-panel') && !e.target.closest('[onclick^="toggleMultiSelect"]')) {
            document.querySelectorAll('.smart-ms-panel').forEach(p => p.classList.add('hidden'));
        }
    });

    function dateInputToEpoch(v, endOfDay) {
        if (!v) return null;
        // Interpret the date input as Cairo local time. Add 00:00 or 23:59:59.999.
        const [y, m, d] = v.split('-').map(Number);
        const suffix = endOfDay ? 'T23:59:59.999' : 'T00:00:00.000';
        return new Date(y + '-' + String(m).padStart(2,'0') + '-' + String(d).padStart(2,'0') + suffix + '+02:00').getTime();
    }

    window.applySmartFilters = function() {
        const search = (document.getElementById('searchInput')?.value || '').toLowerCase();
        const table = document.getElementById('dataTable');
        if (!table) return;

        // Gather active filters.
        const dateRanges = [];
        document.querySelectorAll('.smart-range-from').forEach(from => {
            const attr = from.dataset.attr;
            const to = document.querySelector('.smart-range-to[data-attr="' + attr + '"]');
            const fromMs = dateInputToEpoch(from.value, false);
            const toMs = dateInputToEpoch(to?.value, true);
            if (fromMs || toMs) dateRanges.push({ attr, fromMs, toMs });
        });

        const numberRanges = [];
        document.querySelectorAll('.smart-number-min').forEach(min => {
            const attr = min.dataset.attr;
            const max = document.querySelector('.smart-number-max[data-attr="' + attr + '"]');
            const minV = min.value !== '' ? Number(min.value) : null;
            const maxV = max && max.value !== '' ? Number(max.value) : null;
            if (minV !== null || maxV !== null) numberRanges.push({ attr, minV, maxV });
        });

        const multiSelects = [];
        document.querySelectorAll('.smart-ms-panel').forEach(panel => {
            const id = panel.dataset.field;
            const attr = panel.dataset.attr;
            const checked = Array.from(panel.querySelectorAll('input:checked')).map(x => x.value);
            if (checked.length > 0) multiSelects.push({ attr, checked });
        });

        // Digit-only normalisation lets "010 1234" match "01012345" and Eastern-Arabic
        // digits match Western ones. Strip everything except [0-9] and map Arabic-Indic.
        const arabicDigits = { '٠':'0','١':'1','٢':'2','٣':'3','٤':'4','٥':'5','٦':'6','٧':'7','٨':'8','٩':'9' };
        function normalizeDigits(s) {
            if (!s) return '';
            let out = '';
            for (const ch of s) {
                if (arabicDigits[ch] !== undefined) out += arabicDigits[ch];
                else if (ch >= '0' && ch <= '9') out += ch;
            }
            return out;
        }
        const searchDigits = normalizeDigits(search);

        const rows = table.querySelectorAll('tbody tr');
        rows.forEach(row => {
            if (!row.dataset) { row.style.display = ''; return; }
            const text = row.textContent.toLowerCase();
            // Phone matching: if the query has any digits, also try digits-only match
            // against data-phone / data-client-phone so "010 1234" finds "01012345"
            // (and vice versa) even when the UI shows phones with spaces/emojis.
            let matchesSearch = !search || text.includes(search);
            if (!matchesSearch && searchDigits.length >= 3) {
                const phoneFields = [row.dataset.phone, row.dataset.clientPhone].filter(Boolean);
                for (const p of phoneFields) {
                    if (normalizeDigits(p).includes(searchDigits)) { matchesSearch = true; break; }
                }
            }
            if (!matchesSearch) { row.style.display = 'none'; return; }

            // Row's data-* attributes are accessed via dataset. Each HTML `data-foo-bar`
            // becomes dataset.fooBar; we convert the `data-foo-bar` attribute name back.
            const getAttr = (attr) => {
                const key = attr.replace(/^data-/, '').replace(/-([a-z])/g, (m, c) => c.toUpperCase());
                return row.dataset[key];
            };

            let ok = true;
            for (const { attr, fromMs, toMs } of dateRanges) {
                const v = Number(getAttr(attr));
                if (!v) { ok = false; break; }
                if (fromMs && v < fromMs) { ok = false; break; }
                if (toMs && v > toMs) { ok = false; break; }
            }
            if (ok) for (const { attr, minV, maxV } of numberRanges) {
                const v = Number(getAttr(attr));
                if (minV !== null && v < minV) { ok = false; break; }
                if (maxV !== null && v > maxV) { ok = false; break; }
            }
            if (ok) for (const { attr, checked } of multiSelects) {
                const v = getAttr(attr) || '';
                if (!checked.includes(v)) { ok = false; break; }
            }
            row.style.display = ok ? '' : 'none';
        });

        updateVisibleCount();
        renderActiveFilterChips();
    };

    function renderActiveFilterChips() {
        const container = document.getElementById('activeFilterChips');
        if (!container) return;
        container.innerHTML = '';
        function chip(label, onClickJs) {
            const b = document.createElement('button');
            b.type = 'button';
            b.className = 'inline-flex items-center gap-1 px-2 py-1 bg-emerald-50 text-emerald-700 rounded-full text-xs hover:bg-emerald-100 transition';
            b.innerHTML = label + ' <span>✕</span>';
            b.onclick = () => { eval(onClickJs); applySmartFilters(); };
            container.appendChild(b);
        }
        const search = document.getElementById('searchInput');
        if (search && search.value) chip('بحث: ' + search.value, "document.getElementById('searchInput').value = ''");
        document.querySelectorAll('.smart-range-from').forEach(from => {
            const to = document.querySelector('.smart-range-to[data-attr="' + from.dataset.attr + '"]');
            if (from.value || (to && to.value)) {
                const label = (from.value || '...') + ' ← ' + (to?.value || '...');
                chip(label, "document.getElementById('" + from.id + "').value = ''; document.getElementById('" + (to?.id||'') + "').value = ''");
            }
        });
        document.querySelectorAll('.smart-number-min').forEach(min => {
            const max = document.querySelector('.smart-number-max[data-attr="' + min.dataset.attr + '"]');
            if (min.value || (max && max.value)) {
                const label = (min.value || '...') + ' ← ' + (max?.value || '...');
                chip(label, "document.getElementById('" + min.id + "').value = ''; document.getElementById('" + (max?.id||'') + "').value = ''");
            }
        });
        document.querySelectorAll('.smart-ms-panel').forEach(panel => {
            const id = panel.dataset.field;
            const checked = Array.from(panel.querySelectorAll('input:checked')).map(x => x.value);
            checked.forEach(v => {
                chip(v, "document.querySelector('.smart-ms-" + id + "[value=\"' + " + JSON.stringify(v) + " + '\"]').checked = false; updateMultiSelect('" + id + "')");
            });
        });
    }

    window.clearSmartFilters = function() {
        const si = document.getElementById('searchInput'); if (si) si.value = '';
        document.querySelectorAll('.smart-range-from, .smart-range-to, .smart-number-min, .smart-number-max').forEach(i => i.value = '');
        document.querySelectorAll('.smart-ms-panel input:checked').forEach(cb => cb.checked = false);
        document.querySelectorAll('.smart-ms-panel').forEach(p => {
            const id = p.dataset.field;
            if (id) updateMultiSelect(id);
        });
        document.querySelectorAll('.status-chip').forEach(c => c.classList.remove('ring-2', 'ring-offset-1'));
        applySmartFilters();
    };

    // Preset helpers — set a relative date range on the named attr.
    window.presetRangeDays = function(attr, days, inclusiveToday) {
        const now = new Date();
        const to = now.toISOString().slice(0, 10);
        const start = new Date(now.getTime() - (days - (inclusiveToday ? 1 : 0)) * 86400000);
        const from = start.toISOString().slice(0, 10);
        const fromEl = document.querySelector('.smart-range-from[data-attr="' + attr + '"]');
        const toEl = document.querySelector('.smart-range-to[data-attr="' + attr + '"]');
        if (fromEl) fromEl.value = from;
        if (toEl) toEl.value = to;
        applySmartFilters();
    };

    window.presetMinDaysAgo = function(attr, days) {
        const now = new Date();
        const to = new Date(now.getTime() - days * 86400000).toISOString().slice(0, 10);
        const toEl = document.querySelector('.smart-range-to[data-attr="' + attr + '"]');
        if (toEl) toEl.value = to;
        applySmartFilters();
    };

    window.updateVisibleCount = function() {
        const table = document.getElementById('dataTable');
        if (!table) return;
        const rows = table.querySelectorAll('tbody tr');
        let visible = 0;
        rows.forEach(r => { if (r.style.display !== 'none') visible++; });
        const counter = document.getElementById('visibleCount');
        if (counter) counter.textContent = visible;
    };

    // Status chip (existing helper repurposed for multi-select)
    window.filterByStatusChip = function(status, el) {
        const id = el.dataset.msId || 'status';
        const cb = document.querySelector('.smart-ms-' + id + '[value="' + status + '"]');
        if (cb) {
            cb.checked = !cb.checked;
            updateMultiSelect(id);
        }
        document.querySelectorAll('.status-chip').forEach(c => c.classList.remove('ring-2', 'ring-offset-1'));
        if (cb && cb.checked) el.classList.add('ring-2', 'ring-offset-1');
        applySmartFilters();
    };
})();
</script>
"""

private fun businessTypeOptions(): String = listOf(
    "مطعم", "كافيه", "صيدلية", "محل تجزئة", "سوبر ماركت", "مخبز", "جزارة", "ملابس", "إلكترونيات", "موبايلات", "أخرى"
).joinToString("") { """<option value="$it">$it</option>""" }

private fun statusOptions(): String = listOf(
    "عميل جديد", "متابعة", "ديمو محجوز", "يحتاج مناقشة", "تجربة فعالة", "تجربة منتهية", "تفاوض", "مدفوع", "مشترك", "رفض", "نشاط غير مناسب", "توقف"
).joinToString("") { """<option value="$it">$it</option>""" }

private fun actionTypeOptions(): String = listOf(
    "أول اتصال", "متابعة", "عرض توضيحي", "تفاوض", "إغلاق صفقة", "إعادة تنشيط", "دعم فني", "شكوى"
).joinToString("") { """<option value="$it">$it</option>""" }

private fun channelOptions(): String = listOf(
    "مكالمة تليفون", "واتساب", "زيارة", "فيديو كول", "رسالة SMS"
).joinToString("") { """<option value="$it">$it</option>""" }

private fun planOptions(): String = listOf(
    "أساسي (٢٩٩ ج)", "احترافي (٥٩٩ ج)", "مؤسسات (٩٩٩ ج)", "مخصص"
).joinToString("") { """<option value="$it">$it</option>""" }

private fun resultOptions(): String = listOf(
    "مهتم", "غير مهتم", "طلب يرجعله", "ديمو محجوز", "بدأ تجربة", "استلم الدفع", "اشتراك مؤكد", "مردش", "مشغول", "رقم غلط"
).joinToString("") { """<option value="$it">$it</option>""" }

private fun paymentMethodOptions(): String = listOf(
    "كاش", "فودافون كاش", "إنستا باي", "تحويل بنكي", "فوري"
).joinToString("") { """<option value="$it">$it</option>""" }

private fun sourceOptions(): String = listOf(
    "واتساب", "فيسبوك", "انستجرام", "إحالة صديق", "زيارة للمحل", "الموقع", "جوجل", "تيك توك"
).joinToString("") { """<option value="$it">$it</option>""" }

private fun governorateOptions(): String = listOf(
    "القاهرة", "الجيزة", "الإسكندرية", "القليوبية", "الشرقية", "الدقهلية", "البحيرة", "المنوفية",
    "الغربية", "كفر الشيخ", "دمياط", "بورسعيد", "الإسماعيلية", "السويس", "الفيوم", "بني سويف",
    "المنيا", "أسيوط", "سوهاج", "قنا", "الأقصر", "أسوان"
).joinToString("") { """<option value="$it">$it</option>""" }

private fun roleOptions(): String = listOf(
    "مدير مبيعات", "مندوب مبيعات", "كول سنتر"
).joinToString("") { """<option value="$it">$it</option>""" }

// ════════════════════════════════════════════════════════════════
// Profile Page Helpers
// ════════════════════════════════════════════════════════════════

private fun arabicMonth(month: String): String {
    val parts = month.split("-")
    val monthNames = listOf("", "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")
    return "${monthNames[parts[1].toInt()]} ${parts[0]}"
}

private fun progressBar(label: String, actual: String, target: String, percent: Int): String {
    // Tailwind-friendly gradient + softer background. Color thresholds stay the same so
    // existing behaviour (red < 50% < amber < 75% < green) is preserved.
    val (grad, pillBg, pillTxt) = when {
        percent >= 75 -> Triple("linear-gradient(90deg,#059669,#10b981)", "bg-emerald-50", "text-emerald-700")
        percent >= 50 -> Triple("linear-gradient(90deg,#d97706,#f59e0b)", "bg-amber-50", "text-amber-700")
        else -> Triple("linear-gradient(90deg,#dc2626,#ef4444)", "bg-red-50", "text-red-700")
    }
    return """
    <div class="mb-4">
        <div class="flex justify-between items-center text-sm mb-2">
            <span class="font-medium text-gray-800">$label</span>
            <div class="flex items-center gap-2">
                <span class="text-gray-500 text-xs">$actual / $target</span>
                <span class="px-2 py-0.5 rounded-full text-xs font-medium $pillBg $pillTxt">${percent}%</span>
            </div>
        </div>
        <div class="w-full bg-gray-100 rounded-full h-2 overflow-hidden">
            <div class="h-2 rounded-full transition-all duration-700" style="width:${percent.coerceAtMost(100)}%;background:$grad"></div>
        </div>
    </div>
    """
}

private fun starsHtml(score: Int): String = "⭐".repeat(score) + "☆".repeat(10 - score)

private fun roleBadgeColor(role: String): Pair<String, String> = when (role) {
    "owner" -> "#1B3A5C" to "#FFFFFF"
    "مدير مبيعات" -> "#2E7D32" to "#FFFFFF"
    "مندوب مبيعات" -> "#E65100" to "#FFFFFF"
    "كول سنتر" -> "#6A1B9A" to "#FFFFFF"
    else -> "#9E9E9E" to "#FFFFFF"
}

private fun monthOptions(): String = monthOptions(null)

private fun monthOptions(selected: String?): String {
    val now = Clock.System.now().toLocalDateTime(CRM_TZ)
    val months = mutableListOf<String>()
    for (i in 0..11) {
        var y = now.year
        var m = now.monthNumber - i
        if (m <= 0) { m += 12; y -= 1 }
        months.add("${y}-${m.toString().padStart(2, '0')}")
    }
    return months.joinToString("") {
        val sel = if (it == selected) " selected" else ""
        """<option value="$it"$sel>${arabicMonth(it)}</option>"""
    }
}

private fun systemDocsHtml(): String = """
<div class="space-y-6 max-w-4xl mx-auto">

<!-- Section 1 -->
<div class="bg-white rounded-2xl shadow p-6">
    <h2 class="text-2xl font-bold text-gray-800 mb-4 border-b pb-3" style="color:#1B3A5C">١. إيه هو نظام وصلك؟</h2>
    <p class="text-gray-700 mb-3 leading-relaxed">وصلك هو نظام نقاط بيع (POS) متكامل بيخدم كل أنواع الأنشطة التجارية: مطاعم، كافيهات، صيدليات، محلات تجزئة، سوبر ماركت، مخابز، وغيرهم.</p>
    <p class="font-bold text-gray-800 mb-2">النظام بيوفرلك:</p>
    <div class="grid grid-cols-1 md:grid-cols-2 gap-2">
        <div class="flex items-center gap-2 bg-gray-50 rounded-lg p-2"><span class="text-green-600">✅</span><span>نقطة بيع (POS) - شاشة الكاشير</span></div>
        <div class="flex items-center gap-2 bg-gray-50 rounded-lg p-2"><span class="text-green-600">✅</span><span>إدارة الطلبات والمتابعة</span></div>
        <div class="flex items-center gap-2 bg-gray-50 rounded-lg p-2"><span class="text-green-600">✅</span><span>إدارة المنيو/المنتجات</span></div>
        <div class="flex items-center gap-2 bg-gray-50 rounded-lg p-2"><span class="text-green-600">✅</span><span>إدارة المخزون وتنبيهات النقص</span></div>
        <div class="flex items-center gap-2 bg-gray-50 rounded-lg p-2"><span class="text-green-600">✅</span><span>تحليلات وتقارير شاملة</span></div>
        <div class="flex items-center gap-2 bg-gray-50 rounded-lg p-2"><span class="text-green-600">✅</span><span>إدارة الموظفين والمرتبات</span></div>
        <div class="flex items-center gap-2 bg-gray-50 rounded-lg p-2"><span class="text-green-600">✅</span><span>إدارة العملاء ونقاط الولاء</span></div>
        <div class="flex items-center gap-2 bg-gray-50 rounded-lg p-2"><span class="text-green-600">✅</span><span>عروض وخصومات مخصصة</span></div>
    </div>
</div>

<!-- Section 2 -->
<div class="bg-white rounded-2xl shadow p-6">
    <h2 class="text-2xl font-bold mb-4 border-b pb-3" style="color:#1B3A5C">٢. التطبيقات المتاحة</h2>
    <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div class="border-r-4 rounded-lg p-4 bg-gray-50" style="border-color:#2E7D32">
            <h3 class="font-bold text-lg mb-1">تطبيق المدير</h3>
            <p class="text-green-700 font-medium text-sm mb-1">لمين: صاحب المحل أو المدير</p>
            <p class="text-gray-500 text-sm mb-1">بيشتغل على: Android + Desktop</p>
            <p class="text-gray-700 text-sm">إدارة كل حاجة - المنتجات، الطلبات، الموظفين، المخزون، التحليلات</p>
        </div>
        <div class="border-r-4 rounded-lg p-4 bg-gray-50" style="border-color:#2E7D32">
            <h3 class="font-bold text-lg mb-1">تطبيق الكاشير</h3>
            <p class="text-green-700 font-medium text-sm mb-1">لمين: الكاشير / البائع</p>
            <p class="text-gray-500 text-sm mb-1">بيشتغل على: Android + Desktop</p>
            <p class="text-gray-700 text-sm">عمل طلبات، استلام فلوس، طباعة فواتير، فتح/قفل درج الكاش</p>
        </div>
        <div class="border-r-4 rounded-lg p-4 bg-gray-50" style="border-color:#2E7D32">
            <h3 class="font-bold text-lg mb-1">تطبيق التوصيل</h3>
            <p class="text-green-700 font-medium text-sm mb-1">لمين: عامل التوصيل</p>
            <p class="text-gray-500 text-sm mb-1">بيشتغل على: Android</p>
            <p class="text-gray-700 text-sm">استلام طلبات التوصيل، تتبع الحالة، إيصال التوصيل</p>
        </div>
        <div class="border-r-4 rounded-lg p-4 bg-gray-50" style="border-color:#2E7D32">
            <h3 class="font-bold text-lg mb-1">شاشة المطبخ (KDS)</h3>
            <p class="text-green-700 font-medium text-sm mb-1">لمين: المطبخ</p>
            <p class="text-gray-500 text-sm mb-1">بيشتغل على: Android + Desktop</p>
            <p class="text-gray-700 text-sm">عرض الطلبات الجديدة تلقائياً، تحديث حالة التحضير</p>
        </div>
    </div>
</div>

<!-- Section 3 -->
<div class="bg-white rounded-2xl shadow p-6">
    <h2 class="text-2xl font-bold mb-4 border-b pb-3" style="color:#1B3A5C">٣. الفرق بين المستخدمين والعمال</h2>
    <div class="grid grid-cols-1 md:grid-cols-2 gap-4 mb-4">
        <div class="bg-blue-50 rounded-xl p-4">
            <h3 class="font-bold text-lg mb-2 text-blue-800">المستخدمين (Users)</h3>
            <ul class="space-y-1 text-sm text-gray-700">
                <li>• دول اللي بيدخلوا على التطبيقات</li>
                <li>• كل مستخدم ليه رقم موبايل وباسورد</li>
                <li>• <b>مدير</b> - بيدخل تطبيق المدير</li>
                <li>• <b>كاشير</b> - بيدخل تطبيق الكاشير</li>
                <li>• <b>توصيل</b> - بيدخل تطبيق التوصيل</li>
                <li>• <b>مطبخ</b> - بيدخل شاشة المطبخ</li>
            </ul>
        </div>
        <div class="bg-green-50 rounded-xl p-4">
            <h3 class="font-bold text-lg mb-2 text-green-800">العمال (Workers)</h3>
            <ul class="space-y-1 text-sm text-gray-700">
                <li>• موظفين بيتسجلوا في النظام للإدارة</li>
                <li>• ممكن يكون عامل من غير تسجيل دخول</li>
                <li>• ممكن تفعله كمستخدم كاشير أو توصيل</li>
                <li>• ليهم: مرتب، حضور وانصراف، بصمة PIN</li>
            </ul>
        </div>
    </div>
    <div class="bg-yellow-50 border border-yellow-200 rounded-lg p-3 text-center">
        <p class="font-bold text-yellow-800">💡 الفرق ببساطة: كل مستخدم ممكن يكون عامل، بس مش كل عامل لازم يكون مستخدم.</p>
    </div>
</div>

<!-- Section 4 -->
<div class="bg-white rounded-2xl shadow p-6">
    <h2 class="text-2xl font-bold mb-4 border-b pb-3" style="color:#1B3A5C">٤. أنواع الأنشطة التجارية</h2>
    <div class="overflow-x-auto">
        <table class="w-full text-sm">
            <thead><tr class="border-b" style="background:#1B3A5C;color:white">
                <th class="p-2 text-right">الميزة</th><th class="p-2 text-center">مطعم/كافيه</th><th class="p-2 text-center">صيدلية</th><th class="p-2 text-center">تجزئة</th><th class="p-2 text-center">سوبر ماركت</th><th class="p-2 text-center">مخبز</th>
            </tr></thead>
            <tbody>
                <tr class="border-b bg-blue-50"><td class="p-2 font-bold" colspan="6">قنوات البيع</td></tr>
                <tr class="border-b"><td class="p-2 font-medium">داخل المحل</td><td class="p-2 text-center text-green-600 font-bold">✅</td><td class="p-2 text-center text-green-600 font-bold">✅</td><td class="p-2 text-center text-red-500">❌</td><td class="p-2 text-center text-red-500">❌</td><td class="p-2 text-center text-green-600 font-bold">✅</td></tr>
                <tr class="border-b bg-gray-50"><td class="p-2 font-medium">توصيل</td><td class="p-2 text-center text-green-600 font-bold">✅</td><td class="p-2 text-center text-green-600 font-bold">✅</td><td class="p-2 text-center text-red-500">❌</td><td class="p-2 text-center text-green-600 font-bold">✅</td><td class="p-2 text-center text-green-600 font-bold">✅</td></tr>
                <tr class="border-b"><td class="p-2 font-medium">تيك أواي</td><td class="p-2 text-center text-green-600 font-bold">✅</td><td class="p-2 text-center text-red-500">❌</td><td class="p-2 text-center text-red-500">❌</td><td class="p-2 text-center text-red-500">❌</td><td class="p-2 text-center text-green-600 font-bold">✅</td></tr>
                <tr class="border-b bg-gray-50"><td class="p-2 font-medium">في المحل</td><td class="p-2 text-center text-green-600 font-bold">✅</td><td class="p-2 text-center text-green-600 font-bold">✅</td><td class="p-2 text-center text-green-600 font-bold">✅</td><td class="p-2 text-center text-green-600 font-bold">✅</td><td class="p-2 text-center text-green-600 font-bold">✅</td></tr>
                <tr class="border-b bg-blue-50"><td class="p-2 font-bold" colspan="6">المميزات الرئيسية</td></tr>
                <tr class="border-b"><td class="p-2 font-medium">الطاولات</td><td class="p-2 text-center text-green-600 font-bold">✅</td><td class="p-2 text-center text-red-500">❌</td><td class="p-2 text-center text-red-500">❌</td><td class="p-2 text-center text-red-500">❌</td><td class="p-2 text-center text-red-500">❌</td></tr>
                <tr class="border-b bg-gray-50"><td class="p-2 font-medium">شاشة المطبخ</td><td class="p-2 text-center text-green-600 font-bold">✅</td><td class="p-2 text-center text-red-500">❌</td><td class="p-2 text-center text-red-500">❌</td><td class="p-2 text-center text-red-500">❌</td><td class="p-2 text-center text-green-600 font-bold">✅</td></tr>
                <tr class="border-b"><td class="p-2 font-medium">المخزون</td><td class="p-2 text-center text-green-600 font-bold">✅</td><td class="p-2 text-center text-green-600 font-bold">✅</td><td class="p-2 text-center text-green-600 font-bold">✅</td><td class="p-2 text-center text-green-600 font-bold">✅</td><td class="p-2 text-center text-green-600 font-bold">✅</td></tr>
                <tr class="border-b bg-gray-50"><td class="p-2 font-medium">الروشتات</td><td class="p-2 text-center text-red-500">❌</td><td class="p-2 text-center text-green-600 font-bold">✅</td><td class="p-2 text-center text-red-500">❌</td><td class="p-2 text-center text-red-500">❌</td><td class="p-2 text-center text-red-500">❌</td></tr>
                <tr class="border-b"><td class="p-2 font-medium">درج الكاش</td><td class="p-2 text-center text-green-600 font-bold">✅</td><td class="p-2 text-center text-green-600 font-bold">✅</td><td class="p-2 text-center text-green-600 font-bold">✅</td><td class="p-2 text-center text-green-600 font-bold">✅</td><td class="p-2 text-center text-green-600 font-bold">✅</td></tr>
                <tr class="border-b bg-gray-50"><td class="p-2 font-medium">المرتجعات</td><td class="p-2 text-center text-green-600 font-bold">✅</td><td class="p-2 text-center text-green-600 font-bold">✅</td><td class="p-2 text-center text-green-600 font-bold">✅</td><td class="p-2 text-center text-green-600 font-bold">✅</td><td class="p-2 text-center text-red-500">❌</td></tr>
            </tbody>
        </table>
    </div>
</div>

<!-- Section 5 -->
<div class="bg-white rounded-2xl shadow p-6">
    <h2 class="text-2xl font-bold mb-4 border-b pb-3" style="color:#1B3A5C">٥. شرح المميزات</h2>
    <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
        <div class="border rounded-xl p-3"><h3 class="font-bold text-green-700 mb-1">نقطة البيع (POS)</h3><p class="text-sm text-gray-600">الشاشة الرئيسية للكاشير - اختيار المنتجات، تحديد الكمية، اختيار طريقة الدفع</p></div>
        <div class="border rounded-xl p-3"><h3 class="font-bold text-green-700 mb-1">إدارة الطلبات</h3><p class="text-sm text-gray-600">متابعة كل الطلبات: جديد ← قيد التحضير ← جاهز ← تم التسليم ← مكتمل</p></div>
        <div class="border rounded-xl p-3"><h3 class="font-bold text-green-700 mb-1">إدارة المنتجات</h3><p class="text-sm text-gray-600">أقسام وأصناف - صورة، سعر، سعر تكلفة، باركود، متغيرات (حجم/إضافات)</p></div>
        <div class="border rounded-xl p-3"><h3 class="font-bold text-green-700 mb-1">إدارة المخزون</h3><p class="text-sm text-gray-600">تتبع الكميات - بدون تتبع، خصم مباشر، وصفة. تنبيهات النقص</p></div>
        <div class="border rounded-xl p-3"><h3 class="font-bold text-green-700 mb-1">الطاولات</h3><p class="text-sm text-gray-600">للمطاعم بس - إنشاء طاولات وربط الطلبات. حالة: فاضية، مشغولة، محجوزة</p></div>
        <div class="border rounded-xl p-3"><h3 class="font-bold text-green-700 mb-1">شاشة المطبخ</h3><p class="text-sm text-gray-600">عرض الطلبات الجديدة تلقائياً - الطبّاخ يحدث حالة كل صنف</p></div>
        <div class="border rounded-xl p-3"><h3 class="font-bold text-green-700 mb-1">درج الكاش</h3><p class="text-sm text-gray-600">فتح/قفل الدرج، حركات إيداع وسحب، ملخص الوردية</p></div>
        <div class="border rounded-xl p-3"><h3 class="font-bold text-green-700 mb-1">التحليلات</h3><p class="text-sm text-gray-600">إجمالي المبيعات، عدد الطلبات، أفضل المنتجات، أفضل كاشير. تصدير التقارير</p></div>
        <div class="border rounded-xl p-3"><h3 class="font-bold text-green-700 mb-1">إدارة العملاء</h3><p class="text-sm text-gray-600">بيانات العملاء: اسم، رقم، عنوان. سجل الطلبات ونقاط الولاء</p></div>
        <div class="border rounded-xl p-3"><h3 class="font-bold text-green-700 mb-1">نظام الولاء</h3><p class="text-sm text-gray-600">كسب نقاط مع كل طلب واستبدالها بخصم</p></div>
        <div class="border rounded-xl p-3"><h3 class="font-bold text-green-700 mb-1">العروض والخصومات</h3><p class="text-sm text-gray-600">خصم نسبة أو مبلغ ثابت، كود خصم، تاريخ انتهاء</p></div>
        <div class="border rounded-xl p-3"><h3 class="font-bold text-green-700 mb-1">المرتجعات والاستبدال</h3><p class="text-sm text-gray-600">إرجاع منتج واسترداد الفلوس أو استبدال بمنتج تاني</p></div>
        <div class="border rounded-xl p-3"><h3 class="font-bold text-green-700 mb-1">الدفع بالتقسيط</h3><p class="text-sm text-gray-600">العميل بيدفع على أقساط والنظام بيتبع المدفوع والمتبقي</p></div>
        <div class="border rounded-xl p-3"><h3 class="font-bold text-green-700 mb-1">ائتمان العملاء</h3><p class="text-sm text-gray-600">الدفع الآجل - العميل بياخد البضاعة ويدفع بعدين</p></div>
        <div class="border rounded-xl p-3"><h3 class="font-bold text-green-700 mb-1">الدفع المقسم</h3><p class="text-sm text-gray-600">تقسيم الفاتورة على أكتر من طريقة دفع</p></div>
        <div class="border rounded-xl p-3"><h3 class="font-bold text-green-700 mb-1">الحضور والانصراف</h3><p class="text-sm text-gray-600">تسجيل دوام الموظفين بـ PIN أو QR Code</p></div>
        <div class="border rounded-xl p-3"><h3 class="font-bold text-green-700 mb-1">المرتبات</h3><p class="text-sm text-gray-600">إدارة مرتبات العمال - شهري أو يومي + الأوفر تايم</p></div>
        <div class="border rounded-xl p-3"><h3 class="font-bold text-green-700 mb-1">المنيو الرقمي</h3><p class="text-sm text-gray-600">منيو أونلاين للعملاء من على الموبايل</p></div>
    </div>
</div>

<!-- Section 6 -->
<div class="bg-white rounded-2xl shadow p-6">
    <h2 class="text-2xl font-bold mb-4 border-b pb-3" style="color:#1B3A5C">٦. الباقات والأسعار</h2>
    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <div class="border-2 border-gray-200 rounded-xl p-4 text-center hover:border-green-500 transition">
            <h3 class="font-bold text-lg mb-1" style="color:#1B3A5C">أساسي</h3>
            <div class="text-3xl font-bold text-green-700 my-3">299 <span class="text-sm">ج.م/شهر</span></div>
            <div class="text-gray-500 text-sm mb-3">مستخدم واحد</div>
            <ul class="text-sm text-right space-y-1"><li>✅ نقطة بيع</li><li>✅ إدارة الطلبات</li><li>✅ إدارة المنيو</li><li>✅ درج الكاش</li><li>✅ فرع واحد</li></ul>
        </div>
        <div class="border-2 border-green-500 rounded-xl p-4 text-center relative">
            <span class="absolute -top-3 right-4 bg-green-600 text-white text-xs px-2 py-0.5 rounded-full">الأكثر طلباً</span>
            <h3 class="font-bold text-lg mb-1" style="color:#1B3A5C">احترافي</h3>
            <div class="text-3xl font-bold text-green-700 my-3">599 <span class="text-sm">ج.م/شهر</span></div>
            <div class="text-gray-500 text-sm mb-3">5 مستخدمين</div>
            <ul class="text-sm text-right space-y-1"><li>✅ كل الأساسي</li><li>✅ تحليلات وتقارير</li><li>✅ إدارة المخزون</li><li>✅ شاشة المطبخ</li><li>✅ إدارة العملاء</li><li>✅ عروض وخصومات</li></ul>
        </div>
        <div class="border-2 border-gray-200 rounded-xl p-4 text-center hover:border-green-500 transition">
            <h3 class="font-bold text-lg mb-1" style="color:#1B3A5C">مؤسسات</h3>
            <div class="text-3xl font-bold text-green-700 my-3">999 <span class="text-sm">ج.م/شهر</span></div>
            <div class="text-gray-500 text-sm mb-3">غير محدود</div>
            <ul class="text-sm text-right space-y-1"><li>✅ كل الاحترافي</li><li>✅ فروع متعددة</li><li>✅ إدارة التوصيل</li><li>✅ الموردين</li><li>✅ تصدير التقارير</li><li>✅ الدفع بالتقسيط</li></ul>
        </div>
        <div class="border-2 border-gray-200 rounded-xl p-4 text-center hover:border-green-500 transition">
            <h3 class="font-bold text-lg mb-1" style="color:#1B3A5C">مخصص</h3>
            <div class="text-3xl font-bold text-green-700 my-3">حسب الاتفاق</div>
            <div class="text-gray-500 text-sm mb-3">حسب الاتفاق</div>
            <ul class="text-sm text-right space-y-1"><li>✅ مميزات مخصصة</li><li>✅ حسب احتياج العميل</li><li>✅ دعم خاص</li><li>✅ تدريب الفريق</li></ul>
        </div>
    </div>
</div>

<!-- Section 7 -->
<div class="bg-white rounded-2xl shadow p-6">
    <h2 class="text-2xl font-bold mb-4 border-b pb-3" style="color:#1B3A5C">٧. أسئلة شائعة</h2>
    <div class="space-y-3">
        <details class="border rounded-lg"><summary class="p-3 font-bold cursor-pointer hover:bg-gray-50">إيه الفرق بين وصلك وأي نظام تاني؟</summary><div class="p-3 pt-0 text-gray-600 text-sm">وصلك بيخدم كل أنواع الأنشطة مش بس المطاعم، وبيشتغل على الموبايل والكمبيوتر، وأسعاره مناسبة جداً.</div></details>
        <details class="border rounded-lg"><summary class="p-3 font-bold cursor-pointer hover:bg-gray-50">ممكن أشتغل من غير نت؟</summary><div class="p-3 pt-0 text-gray-600 text-sm">فيه وضع العمل بدون إنترنت. بتعمل الطلبات عادي ولما النت يرجع بيتزامن تلقائياً.</div></details>
        <details class="border rounded-lg"><summary class="p-3 font-bold cursor-pointer hover:bg-gray-50">بيشتغل على إيه؟</summary><div class="p-3 pt-0 text-gray-600 text-sm">أندرويد (موبايل وتابلت) وديسكتوب (Windows و Mac). مش محتاج أجهزة غالية.</div></details>
        <details class="border rounded-lg"><summary class="p-3 font-bold cursor-pointer hover:bg-gray-50">ممكن أربط أكتر من فرع؟</summary><div class="p-3 pt-0 text-gray-600 text-sm">أيوا، في باقة المؤسسات بتقدر تربط كل الفروع على حساب واحد.</div></details>
        <details class="border rounded-lg"><summary class="p-3 font-bold cursor-pointer hover:bg-gray-50">التجربة المجانية مدتها قد إيه؟</summary><div class="p-3 pt-0 text-gray-600 text-sm">7 أيام تجربة مجانية كاملة بكل المميزات.</div></details>
        <details class="border rounded-lg"><summary class="p-3 font-bold cursor-pointer hover:bg-gray-50">النظام بيدعم الباركود؟</summary><div class="p-3 pt-0 text-gray-600 text-sm">أيوا، تقدر تضيف باركود لكل منتج وتستخدم قارئ الباركود في الكاشير.</div></details>
        <details class="border rounded-lg"><summary class="p-3 font-bold cursor-pointer hover:bg-gray-50">ممكن أطبع فواتير؟</summary><div class="p-3 pt-0 text-gray-600 text-sm">أيوا، طباعة فواتير حرارية وكمان فواتير رقمية على الموبايل.</div></details>
        <details class="border rounded-lg"><summary class="p-3 font-bold cursor-pointer hover:bg-gray-50">إيه طرق الدفع المتاحة؟</summary><div class="p-3 pt-0 text-gray-600 text-sm">كاش، فودافون كاش، إنستا باي، تحويل بنكي، كارت.</div></details>
        <details class="border rounded-lg"><summary class="p-3 font-bold cursor-pointer hover:bg-gray-50">إزاي أضيف موظف جديد؟</summary><div class="p-3 pt-0 text-gray-600 text-sm">من تطبيق المدير ← الموظفين ← إضافة عامل. تحدد اسمه ودوره.</div></details>
        <details class="border rounded-lg"><summary class="p-3 font-bold cursor-pointer hover:bg-gray-50">النظام بيحسب الضرايب؟</summary><div class="p-3 pt-0 text-gray-600 text-sm">أيوا، تفعّل الضرايب وتحدد النسبة (مثلاً 14%). النظام بيحسبها تلقائياً.</div></details>
        <details class="border rounded-lg"><summary class="p-3 font-bold cursor-pointer hover:bg-gray-50">ممكن أصدّر التقارير؟</summary><div class="p-3 pt-0 text-gray-600 text-sm">أيوا، تصدّر تقارير المبيعات والمخزون كملف Excel.</div></details>
        <details class="border rounded-lg"><summary class="p-3 font-bold cursor-pointer hover:bg-gray-50">البيانات آمنة؟</summary><div class="p-3 pt-0 text-gray-600 text-sm">أيوا، كل البيانات مشفرة ومحمية. كل حساب منفصل تماماً.</div></details>
    </div>
</div>

<!-- Section 8 -->
<div class="bg-white rounded-2xl shadow p-6">
    <h2 class="text-2xl font-bold mb-4 border-b pb-3" style="color:#1B3A5C">٨. التواصل والدعم</h2>
    <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div class="bg-blue-50 rounded-xl p-4 text-center"><div class="text-2xl mb-2">📱</div><div class="font-bold">واتساب</div><div class="text-gray-600 text-sm">رقم الدعم الفني</div></div>
        <div class="bg-blue-50 rounded-xl p-4 text-center"><div class="text-2xl mb-2">📞</div><div class="font-bold">تليفون</div><div class="text-gray-600 text-sm">نفس رقم الواتساب</div></div>
        <div class="bg-blue-50 rounded-xl p-4 text-center"><div class="text-2xl mb-2">🕐</div><div class="font-bold">مواعيد الدعم</div><div class="text-gray-600 text-sm">من 9 الصبح لـ 9 بالليل - كل يوم</div></div>
        <div class="bg-blue-50 rounded-xl p-4 text-center"><div class="text-2xl mb-2">🚨</div><div class="font-bold">الدعم الطارئ</div><div class="text-gray-600 text-sm">متاح 24/7 للمشتركين</div></div>
    </div>
    <div class="text-center mt-6 p-4 border-t">
        <p class="text-xl font-bold" style="color:#2E7D32">وصلك - بنوصلك بالنجاح 🚀</p>
    </div>
</div>

</div>
"""
