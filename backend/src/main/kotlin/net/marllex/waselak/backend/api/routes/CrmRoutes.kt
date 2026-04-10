package net.marllex.waselak.backend.api.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*
import net.marllex.waselak.backend.domain.service.CrmService
import net.marllex.waselak.backend.plugins.CrmPrincipal
import org.koin.java.KoinJavaComponent

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
            val stats = crmService.getStats(principal.agentId, principal.isManager)
            val recentActivities = crmService.listActivities(principal.agentId, principal.isManager, limit = 10)

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
                append("<th class='p-2 text-right'>المندوب</th><th class='p-2 text-right'>العميل</th><th class='p-2 text-right'>الإجراء</th><th class='p-2 text-right'>القناة</th><th class='p-2 text-right'>النتيجة</th>")
                append("</tr></thead><tbody>")
                recentActivities.forEach { act ->
                    append("<tr class='border-b hover:bg-gray-50'>")
                    append("<td class='p-2'>${act.agentName}</td>")
                    append("<td class='p-2'>${act.clientName}</td>")
                    append("<td class='p-2'>${act.actionType ?: "-"}</td>")
                    append("<td class='p-2'>${act.channel ?: "-"}</td>")
                    append("<td class='p-2'>${act.result ?: "-"}</td>")
                    append("</tr>")
                }
                append("</tbody></table></div></div>")
            }

            val content = """
                <div class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-4 mb-8">$kpiCards</div>
                <h3 class="text-lg font-bold mb-4">حالة العملاء</h3>
                $pipelineHtml
                $activitiesHtml
            """.trimIndent()

            call.respondText(
                crmLayout("لوحة التحكم", principal.name, principal.role, principal.isManager, "dashboard", content),
                ContentType.Text.Html
            )
        }

        // ─── Clients Page ───────────────────────────────────────
        get("/crm/clients") {
            val principal = call.principal<CrmPrincipal>()!!
            val clients = crmService.listClients(principal.agentId, principal.isManager)
            val agents = if (principal.isManager) crmService.listAgents() else emptyList()

            val tableRows = buildString {
                clients.forEach { c ->
                    val bgClass = if ((c.daysSinceLastContact ?: 0) >= 7) "bg-red-50" else ""
                    val statusBg = statusColor(c.status)
                    val statusTxt = statusTextColor(c.status)
                    append("""<tr class="border-b hover:bg-gray-50 $bgClass" data-id="${c.id}">""")
                    append("<td class='p-2 font-medium'>${c.clientName}</td>")
                    append("<td class='p-2'>${c.phone}</td>")
                    append("<td class='p-2'>${c.businessName ?: "-"}</td>")
                    append("<td class='p-2'>${c.businessType ?: "-"}</td>")
                    append("<td class='p-2'>${c.governorate ?: "-"}</td>")
                    append("""<td class='p-2'><span class="px-2 py-1 rounded-full text-xs font-medium" style="background:$statusBg;color:$statusTxt">${c.status}</span></td>""")
                    append("<td class='p-2'>${c.plan ?: "-"}</td>")
                    append("<td class='p-2'>${String.format("%,.0f", c.finalAmount)} ج.م</td>")
                    append("<td class='p-2'>${c.assignedName ?: "-"}</td>")
                    append("<td class='p-2'>${c.daysSinceLastContact?.let { "${it} يوم" } ?: "-"}</td>")
                    append("""<td class='p-2'><button onclick="openEditClient('${c.id}')" class="text-blue-600 hover:underline text-xs">تعديل</button></td>""")
                    append("</tr>")
                }
            }

            val agentOptions = agents.joinToString("") { """<option value="${it.id}">${it.name}</option>""" }

            val content = """
                <div class="flex justify-between items-center mb-6">
                    <h2 class="text-xl font-bold">العملاء (${clients.size})</h2>
                    <button onclick="document.getElementById('addClientModal').showModal()" class="bg-green-700 text-white px-4 py-2 rounded-lg hover:bg-green-800">+ إضافة عميل</button>
                </div>
                <div class="bg-white rounded-xl shadow overflow-x-auto">
                    <table class="w-full text-sm">
                        <thead>
                            <tr class="bg-gray-100 border-b">
                                <th class="p-2 text-right">الاسم</th>
                                <th class="p-2 text-right">الهاتف</th>
                                <th class="p-2 text-right">النشاط</th>
                                <th class="p-2 text-right">النوع</th>
                                <th class="p-2 text-right">المحافظة</th>
                                <th class="p-2 text-right">الحالة</th>
                                <th class="p-2 text-right">الباقة</th>
                                <th class="p-2 text-right">المبلغ</th>
                                <th class="p-2 text-right">المندوب</th>
                                <th class="p-2 text-right">آخر تواصل</th>
                                <th class="p-2 text-right"></th>
                            </tr>
                        </thead>
                        <tbody>$tableRows</tbody>
                    </table>
                </div>

                ${addClientModalHtml(agentOptions, principal.isManager)}
                ${editClientModalHtml(agentOptions, principal.isManager)}

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
                    document.getElementById('edit_nextActionDate').value = c.nextActionDate || '';
                    ${if (principal.isManager) "document.getElementById('edit_assignedTo').value = c.assignedTo || '';" else ""}
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
                </script>
            """.trimIndent()

            call.respondText(
                crmLayout("العملاء", principal.name, principal.role, principal.isManager, "clients", content),
                ContentType.Text.Html
            )
        }

        // ─── Activities Page ────────────────────────────────────
        get("/crm/activities") {
            val principal = call.principal<CrmPrincipal>()!!
            val activities = crmService.listActivities(principal.agentId, principal.isManager)
            val clients = crmService.listClients(principal.agentId, principal.isManager)

            val tableRows = buildString {
                activities.forEach { a ->
                    append("<tr class='border-b hover:bg-gray-50'>")
                    append("<td class='p-2'>${a.agentName}</td>")
                    append("<td class='p-2'>${a.clientName}</td>")
                    append("<td class='p-2'>${a.actionType ?: "-"}</td>")
                    append("<td class='p-2'>${a.channel ?: "-"}</td>")
                    if (a.newStatus != null) {
                        val bg = statusColor(a.newStatus)
                        val txt = statusTextColor(a.newStatus)
                        append("""<td class='p-2'><span class="px-2 py-1 rounded-full text-xs" style="background:$bg;color:$txt">${a.newStatus}</span></td>""")
                    } else {
                        append("<td class='p-2'>-</td>")
                    }
                    append("<td class='p-2'>${a.result ?: "-"}</td>")
                    append("<td class='p-2'>${a.notes ?: "-"}</td>")
                    append("<td class='p-2'>${a.nextDate ?: "-"}</td>")
                    append("</tr>")
                }
            }

            val clientOptions = clients.joinToString("") { """<option value="${it.id}">${it.clientName} - ${it.phone}</option>""" }

            val content = """
                <div class="flex justify-between items-center mb-6">
                    <h2 class="text-xl font-bold">الأنشطة (${activities.size})</h2>
                    <button onclick="document.getElementById('addActivityModal').showModal()" class="bg-green-700 text-white px-4 py-2 rounded-lg hover:bg-green-800">+ إضافة نشاط</button>
                </div>
                <div class="bg-white rounded-xl shadow overflow-x-auto">
                    <table class="w-full text-sm">
                        <thead>
                            <tr class="bg-gray-100 border-b">
                                <th class="p-2 text-right">المندوب</th>
                                <th class="p-2 text-right">العميل</th>
                                <th class="p-2 text-right">الإجراء</th>
                                <th class="p-2 text-right">القناة</th>
                                <th class="p-2 text-right">الحالة الجديدة</th>
                                <th class="p-2 text-right">النتيجة</th>
                                <th class="p-2 text-right">ملاحظات</th>
                                <th class="p-2 text-right">الإجراء القادم</th>
                            </tr>
                        </thead>
                        <tbody>$tableRows</tbody>
                    </table>
                </div>

                ${addActivityModalHtml(clientOptions)}

                <script>
                async function submitNewActivity(e) {
                    e.preventDefault();
                    const form = e.target;
                    const data = Object.fromEntries(new FormData(form));
                    data.amount = parseFloat(data.amount) || 0;
                    data.discountPercent = parseInt(data.discountPercent) || 0;
                    const res = await fetch('/crm/api/activities', {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(data)});
                    if (res.ok) { location.reload(); } else { alert('حدث خطأ'); }
                }
                </script>
            """.trimIndent()

            call.respondText(
                crmLayout("الأنشطة", principal.name, principal.role, principal.isManager, "activities", content),
                ContentType.Text.Html
            )
        }

        // ─── Reports Page (Manager Only) ────────────────────────
        get("/crm/reports") {
            val principal = call.principal<CrmPrincipal>()!!
            if (!principal.isManager) {
                call.respondRedirect("/crm/dashboard")
                return@get
            }
            val stats = crmService.getStats(null, true)

            val agentTableRows = buildString {
                stats.agentStats.forEach { a ->
                    val conversion = if (a.clients > 0) ((a.subscribed + a.paid) * 100.0 / a.clients) else 0.0
                    append("<tr class='border-b hover:bg-gray-50'>")
                    append("<td class='p-2 font-medium'>${a.agentName}</td>")
                    append("<td class='p-2'>${a.role}</td>")
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
                    <h3 class="text-lg font-bold mb-4">أداء المندوبين</h3>
                    <div class="overflow-x-auto">
                        <table class="w-full text-sm">
                            <thead>
                                <tr class="bg-gray-100 border-b">
                                    <th class="p-2 text-right">المندوب</th>
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
                crmLayout("التقارير", principal.name, principal.role, principal.isManager, "reports", content),
                ContentType.Text.Html
            )
        }

        // ─── Team Page (Manager Only) ───────────────────────────
        get("/crm/team") {
            val principal = call.principal<CrmPrincipal>()!!
            if (!principal.isManager) {
                call.respondRedirect("/crm/dashboard")
                return@get
            }
            val stats = crmService.getStats(null, true)

            val agentCards = buildString {
                stats.agentStats.forEach { a ->
                    val conversion = if (a.clients > 0) ((a.subscribed + a.paid) * 100.0 / a.clients) else 0.0
                    append("""
                        <div class="bg-white rounded-xl shadow p-6">
                            <div class="flex justify-between items-start mb-4">
                                <div>
                                    <h3 class="font-bold text-lg">${a.agentName}</h3>
                                    <span class="text-sm text-gray-500">${a.role}</span>
                                </div>
                                <span class="text-2xl">👤</span>
                            </div>
                            <div class="grid grid-cols-2 gap-3 text-sm">
                                <div class="bg-gray-50 rounded p-2 text-center"><div class="font-bold text-lg">${a.clients}</div><div class="text-gray-500">عميل</div></div>
                                <div class="bg-gray-50 rounded p-2 text-center"><div class="font-bold text-lg">${a.totalActivities}</div><div class="text-gray-500">نشاط</div></div>
                                <div class="bg-green-50 rounded p-2 text-center"><div class="font-bold text-lg text-green-700">${a.subscribed}</div><div class="text-gray-500">مشترك</div></div>
                                <div class="bg-orange-50 rounded p-2 text-center"><div class="font-bold text-lg text-orange-700">${a.paid}</div><div class="text-gray-500">مدفوع</div></div>
                                <div class="bg-blue-50 rounded p-2 text-center col-span-2"><div class="font-bold text-lg text-blue-700">${String.format("%,.0f", a.revenue)} ج.م</div><div class="text-gray-500">الإيراد</div></div>
                            </div>
                            <div class="mt-3 text-center text-sm text-gray-500">معدل التحويل: <span class="font-bold">${String.format("%.1f", conversion)}%</span></div>
                        </div>
                    """.trimIndent())
                }
            }

            val content = """
                <h2 class="text-xl font-bold mb-6">أداء الفريق</h2>
                <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">$agentCards</div>
            """.trimIndent()

            call.respondText(
                crmLayout("الفريق", principal.name, principal.role, principal.isManager, "team", content),
                ContentType.Text.Html
            )
        }

        // ─── Settings Page (Manager Only) ───────────────────────
        get("/crm/settings") {
            val principal = call.principal<CrmPrincipal>()!!
            if (!principal.isManager) {
                call.respondRedirect("/crm/dashboard")
                return@get
            }
            val agents = crmService.listAgents()

            val agentRows = buildString {
                agents.forEach { a ->
                    val statusBadge = if (a.active)
                        """<span class="bg-green-100 text-green-800 px-2 py-1 rounded-full text-xs">نشط</span>"""
                    else
                        """<span class="bg-red-100 text-red-800 px-2 py-1 rounded-full text-xs">معطل</span>"""
                    append("<tr class='border-b hover:bg-gray-50'>")
                    append("<td class='p-2 font-medium'>${a.name}</td>")
                    append("<td class='p-2'>${a.email}</td>")
                    append("<td class='p-2'>${a.role}</td>")
                    append("<td class='p-2'>$statusBadge</td>")
                    append("""<td class='p-2'>
                        <button onclick="toggleAgent('${a.id}', ${!a.active})" class="text-sm px-2 py-1 rounded ${if (a.active) "bg-red-100 text-red-700 hover:bg-red-200" else "bg-green-100 text-green-700 hover:bg-green-200"}">${if (a.active) "تعطيل" else "تفعيل"}</button>
                    </td>""")
                    append("</tr>")
                }
            }

            val content = """
                <div class="flex justify-between items-center mb-6">
                    <h2 class="text-xl font-bold">الإعدادات - إدارة المندوبين</h2>
                    <button onclick="document.getElementById('addAgentModal').showModal()" class="bg-green-700 text-white px-4 py-2 rounded-lg hover:bg-green-800">+ إضافة مندوب</button>
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

                <script>
                async function submitNewAgent(e) {
                    e.preventDefault();
                    const form = e.target;
                    const data = Object.fromEntries(new FormData(form));
                    const res = await fetch('/crm/api/agents', {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(data)});
                    if (res.ok) { location.reload(); } else { alert('حدث خطأ'); }
                }

                async function toggleAgent(id, active) {
                    const res = await fetch('/crm/api/agents/' + id, {method:'PUT', headers:{'Content-Type':'application/json'}, body:JSON.stringify({active:active})});
                    if (res.ok) { location.reload(); } else { alert('حدث خطأ'); }
                }
                </script>
            """.trimIndent()

            call.respondText(
                crmLayout("الإعدادات", principal.name, principal.role, principal.isManager, "settings", content),
                ContentType.Text.Html
            )
        }

        // ─── JSON API Endpoints ─────────────────────────────────
        route("/crm/api") {

            // GET /crm/api/clients
            get("/clients") {
                val principal = call.principal<CrmPrincipal>()!!
                val clients = crmService.listClients(principal.agentId, principal.isManager)
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
                    assignedTo = if (principal.isManager) obj["assignedTo"]?.jsonPrimitive?.contentOrNull else principal.agentId,
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
                val body = call.receiveText()
                val obj = Json.parseToJsonElement(body).jsonObject
                val ok = crmService.updateClient(
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
                    assignedTo = if (principal.isManager) obj["assignedTo"]?.jsonPrimitive?.contentOrNull else null,
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

            // GET /crm/api/activities
            get("/activities") {
                val principal = call.principal<CrmPrincipal>()!!
                val activities = crmService.listActivities(principal.agentId, principal.isManager)
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

            // GET /crm/api/stats
            get("/stats") {
                val principal = call.principal<CrmPrincipal>()!!
                val stats = crmService.getStats(principal.agentId, principal.isManager)
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

            // GET /crm/api/agents (manager only)
            get("/agents") {
                val principal = call.principal<CrmPrincipal>()!!
                if (!principal.isManager) {
                    call.respondText("""{"error":"forbidden"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                    return@get
                }
                val agents = crmService.listAgents()
                val json = buildJsonArray {
                    agents.forEach { a ->
                        add(buildJsonObject {
                            put("id", a.id)
                            put("name", a.name)
                            put("email", a.email)
                            put("role", a.role)
                            put("active", a.active)
                        })
                    }
                }
                call.respondText(json.toString(), ContentType.Application.Json)
            }

            // POST /crm/api/agents (manager only)
            post("/agents") {
                val principal = call.principal<CrmPrincipal>()!!
                if (!principal.isManager) {
                    call.respondText("""{"error":"forbidden"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                    return@post
                }
                val body = call.receiveText()
                val obj = Json.parseToJsonElement(body).jsonObject
                val agent = crmService.createAgent(
                    name = obj["name"]?.jsonPrimitive?.content ?: "",
                    email = obj["email"]?.jsonPrimitive?.content ?: "",
                    password = obj["password"]?.jsonPrimitive?.content ?: "",
                    role = obj["role"]?.jsonPrimitive?.content ?: "مندوب مبيعات",
                )
                val result = buildJsonObject { put("id", agent.id); put("status", "ok") }
                call.respondText(result.toString(), ContentType.Application.Json, HttpStatusCode.Created)
            }

            // PUT /crm/api/agents/{id} (manager only)
            put("/agents/{id}") {
                val principal = call.principal<CrmPrincipal>()!!
                if (!principal.isManager) {
                    call.respondText("""{"error":"forbidden"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                    return@put
                }
                val id = call.parameters["id"] ?: return@put call.respondText("{\"error\":\"missing id\"}", ContentType.Application.Json, HttpStatusCode.BadRequest)
                val body = call.receiveText()
                val obj = Json.parseToJsonElement(body).jsonObject
                val ok = crmService.updateAgent(
                    id = id,
                    name = obj["name"]?.jsonPrimitive?.contentOrNull,
                    role = obj["role"]?.jsonPrimitive?.contentOrNull,
                    active = obj["active"]?.jsonPrimitive?.booleanOrNull,
                    password = obj["password"]?.jsonPrimitive?.contentOrNull,
                )
                if (ok) {
                    call.respondText("""{"status":"ok"}""", ContentType.Application.Json)
                } else {
                    call.respondText("""{"error":"not found"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
                }
            }

            // GET /crm/api/export (manager only - Excel export placeholder)
            get("/export") {
                val principal = call.principal<CrmPrincipal>()!!
                if (!principal.isManager) {
                    call.respondText("""{"error":"forbidden"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                    return@get
                }
                val clients = crmService.listClients(null, true)
                // CSV export as a simple alternative
                val csv = buildString {
                    append("الاسم,الهاتف,النشاط,النوع,المحافظة,الحالة,الباقة,المبلغ,المندوب\n")
                    clients.forEach { c ->
                        append("${c.clientName},${c.phone},${c.businessName ?: ""},${c.businessType ?: ""},${c.governorate ?: ""},${c.status},${c.plan ?: ""},${c.finalAmount},${c.assignedName ?: ""}\n")
                    }
                }
                call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"crm-clients.csv\"")
                call.respondText(csv, ContentType.Text.CSV)
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════
// HTML Helper Functions
// ════════════════════════════════════════════════════════════════

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

private fun crmLayout(title: String, agentName: String, agentRole: String, isManager: Boolean, activeTab: String, content: String): String {
    fun navLink(tab: String, label: String, icon: String, href: String, managerOnly: Boolean = false): String {
        if (managerOnly && !isManager) return ""
        val active = if (tab == activeTab) "bg-white/10 font-bold" else "hover:bg-white/5"
        return """<a href="$href" class="flex items-center gap-3 px-4 py-3 rounded-lg $active transition">
            <span class="text-lg">$icon</span><span>$label</span>
        </a>"""
    }

    return """<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>$title - وصلك CRM</title>
    <script src="https://cdn.tailwindcss.com"></script>
    <style>
        body { font-family: 'Segoe UI', Tahoma, Arial, sans-serif; }
        dialog::backdrop { background: rgba(0,0,0,0.5); }
        dialog { border: none; border-radius: 1rem; padding: 0; max-width: 600px; width: 90%; }
        .sidebar { background: #1B3A5C; }
        ::-webkit-scrollbar { width: 6px; }
        ::-webkit-scrollbar-thumb { background: #ccc; border-radius: 3px; }
    </style>
</head>
<body class="bg-gray-100 min-h-screen">
    <div class="flex min-h-screen">
        <!-- Sidebar -->
        <aside class="sidebar w-64 min-h-screen text-white flex flex-col flex-shrink-0">
            <div class="p-6 border-b border-white/10">
                <h1 class="text-xl font-bold">وصلك CRM</h1>
                <p class="text-sm text-white/60 mt-1">نظام إدارة المبيعات</p>
            </div>
            <nav class="flex-1 p-4 space-y-1">
                ${navLink("dashboard", "لوحة التحكم", "📊", "/crm/dashboard")}
                ${navLink("clients", "العملاء", "👥", "/crm/clients")}
                ${navLink("activities", "الأنشطة", "📋", "/crm/activities")}
                ${navLink("reports", "التقارير", "📈", "/crm/reports", managerOnly = true)}
                ${navLink("team", "الفريق", "👤", "/crm/team", managerOnly = true)}
                ${navLink("settings", "الإعدادات", "⚙️", "/crm/settings", managerOnly = true)}
            </nav>
            <div class="p-4 border-t border-white/10">
                <div class="text-sm">
                    <p class="font-medium">$agentName</p>
                    <p class="text-white/60">$agentRole</p>
                </div>
                <a href="/crm/logout" class="mt-3 flex items-center gap-2 text-sm text-white/60 hover:text-white transition">
                    <span>🚪</span><span>تسجيل الخروج</span>
                </a>
            </div>
        </aside>

        <!-- Main Content -->
        <main class="flex-1 p-8 overflow-auto">
            <div class="mb-6">
                <h2 class="text-2xl font-bold text-gray-800">$title</h2>
            </div>
            $content
        </main>
    </div>
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
            <div class="w-16 h-16 rounded-full mx-auto mb-4 flex items-center justify-center" style="background:#1B3A5C">
                <span class="text-white text-2xl font-bold">و</span>
            </div>
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

private fun addClientModalHtml(agentOptions: String, isManager: Boolean): String {
    val assignedField = if (isManager) """
        <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">المندوب المسؤول</label>
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
                <div class="grid grid-cols-2 gap-3">
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">اسم العميل *</label>
                        <input type="text" name="clientName" required class="w-full px-3 py-2 border rounded-lg text-sm">
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">رقم الهاتف *</label>
                        <input type="text" name="phone" required class="w-full px-3 py-2 border rounded-lg text-sm">
                    </div>
                    <div class="flex items-center gap-2 col-span-2">
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
                        <label class="block text-sm font-medium text-gray-700 mb-1">الباقة</label>
                        <select name="plan" class="w-full px-3 py-2 border rounded-lg text-sm">
                            <option value="">-- اختر --</option>
                            ${planOptions()}
                        </select>
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">المبلغ الشهري</label>
                        <input type="number" name="monthlyAmount" value="0" class="w-full px-3 py-2 border rounded-lg text-sm">
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">نسبة الخصم %</label>
                        <input type="number" name="discountPercent" value="0" min="0" max="100" class="w-full px-3 py-2 border rounded-lg text-sm">
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">طريقة الدفع</label>
                        <select name="paymentMethod" class="w-full px-3 py-2 border rounded-lg text-sm">
                            <option value="">-- اختر --</option>
                            ${paymentMethodOptions()}
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
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">تاريخ الإجراء القادم</label>
                        <input type="date" name="nextActionDate" class="w-full px-3 py-2 border rounded-lg text-sm">
                    </div>
                    <div class="col-span-2">
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

private fun editClientModalHtml(agentOptions: String, isManager: Boolean): String {
    val assignedField = if (isManager) """
        <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">المندوب المسؤول</label>
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
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">تاريخ الإجراء القادم</label>
                        <input type="date" name="nextActionDate" id="edit_nextActionDate" class="w-full px-3 py-2 border rounded-lg text-sm">
                    </div>
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

private fun addActivityModalHtml(clientOptions: String): String = """
    <dialog id="addActivityModal" class="rounded-2xl">
        <div class="p-6">
            <div class="flex justify-between items-center mb-4">
                <h3 class="text-lg font-bold">إضافة نشاط جديد</h3>
                <button onclick="document.getElementById('addActivityModal').close()" class="text-gray-400 hover:text-gray-600 text-xl">&times;</button>
            </div>
            <form onsubmit="submitNewActivity(event)" class="space-y-3 max-h-[70vh] overflow-y-auto">
                <div class="grid grid-cols-2 gap-3">
                    <div class="col-span-2">
                        <label class="block text-sm font-medium text-gray-700 mb-1">العميل *</label>
                        <select name="clientId" required class="w-full px-3 py-2 border rounded-lg text-sm">
                            <option value="">-- اختر العميل --</option>
                            $clientOptions
                        </select>
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
                        <label class="block text-sm font-medium text-gray-700 mb-1">تاريخ الإجراء القادم</label>
                        <input type="date" name="nextDate" class="w-full px-3 py-2 border rounded-lg text-sm">
                    </div>
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
"""

private fun addAgentModalHtml(): String = """
    <dialog id="addAgentModal" class="rounded-2xl">
        <div class="p-6">
            <div class="flex justify-between items-center mb-4">
                <h3 class="text-lg font-bold">إضافة مندوب جديد</h3>
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
