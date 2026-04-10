package net.marllex.waselak.backend.api.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
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
            val stats = crmService.getStats(principal.agentId, principal.canSeeAll)
            val recentActivities = crmService.listActivities(principal.agentId, principal.canSeeAll, limit = 10)

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
            val clients = crmService.listClients(principal.agentId, principal.canSeeAll)
            val agents = if (principal.canSeeAll) crmService.listAgents() else emptyList()

            val tableRows = buildString {
                clients.forEach { c ->
                    val bgClass = if ((c.daysSinceLastContact ?: 0) >= 7) "bg-red-50" else ""
                    val statusBg = statusColor(c.status)
                    val statusTxt = statusTextColor(c.status)
                    val indicator = clientStatusIndicator(c.status)
                    append("""<tr class="border-b hover:bg-gray-50 $bgClass" data-id="${c.id}">""")
                    append("<td class='p-2 font-medium'>${c.clientName}</td>")
                    append("<td class='p-2'>${c.phone}</td>")
                    append("<td class='p-2'>${c.businessName ?: "-"}</td>")
                    append("<td class='p-2'>${c.businessType ?: "-"}</td>")
                    append("<td class='p-2'>${c.governorate ?: "-"}</td>")
                    append("""<td class='p-2'><span class="px-2 py-1 rounded-full text-xs font-medium" style="background:$statusBg;color:$statusTxt">${c.status}</span></td>""")
                    append("""<td class='p-2'>$indicator</td>""")
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
                                <th class="p-2 text-right">التصنيف</th>
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

                ${addClientModalHtml(agentOptions, principal.canSeeAll)}
                ${editClientModalHtml(agentOptions, principal.canSeeAll)}

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
                </script>
            """.trimIndent()

            call.respondText(
                crmLayout("العملاء", principal.name, principal.role, principal, "clients", content),
                ContentType.Text.Html
            )
        }

        // ─── Activities Page ────────────────────────────────────
        get("/crm/activities") {
            val principal = call.principal<CrmPrincipal>()!!
            val activities = crmService.listActivities(principal.agentId, principal.canSeeAll)
            val clients = crmService.listClients(principal.agentId, principal.canSeeAll)

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
            val stats = crmService.getStats(null, true)

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
            val stats = crmService.getStats(null, true)

            val agentCards = buildString {
                stats.agentStats.forEach { a ->
                    val conversion = if (a.clients > 0) ((a.subscribed + a.paid) * 100.0 / a.clients) else 0.0
                    val photoHtml = agentPhotoHtml(a.photoUrl, a.agentName, 48)
                    append("""
                        <div class="bg-white rounded-xl shadow p-6">
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

            val content = """
                <h2 class="text-xl font-bold mb-6">أداء الفريق</h2>
                <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">$agentCards</div>
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
            val agents = crmService.listAgents()

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
                crmLayout("الإعدادات", principal.name, principal.role, principal, "settings", content),
                ContentType.Text.Html
            )
        }

        // ─── Billing Page (Owner Only) ──────────────────────────
        get("/crm/billing") {
            val principal = call.principal<CrmPrincipal>()!!
            if (!principal.canSeeAnalytics) { call.respondRedirect("/crm/dashboard"); return@get }

            val billingStats = crmService.getBillingStats()
            val invoices = crmService.listInvoices(null, null)
            val clients = crmService.listClients(null, true).filter { it.status in setOf("مشترك", "مدفوع") }

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

                <div class="flex flex-wrap gap-2 mb-4">
                    <button onclick="filterInvoices('')" class="px-3 py-1 rounded-full text-sm bg-blue-100 text-blue-800 hover:bg-blue-200 filter-btn active-filter">الكل</button>
                    <button onclick="filterInvoices('غير مدفوع')" class="px-3 py-1 rounded-full text-sm bg-gray-100 text-gray-800 hover:bg-gray-200 filter-btn">غير مدفوع</button>
                    <button onclick="filterInvoices('مدفوع جزئي')" class="px-3 py-1 rounded-full text-sm bg-orange-100 text-orange-800 hover:bg-orange-200 filter-btn">مدفوع جزئي</button>
                    <button onclick="filterInvoices('مدفوع')" class="px-3 py-1 rounded-full text-sm bg-green-100 text-green-800 hover:bg-green-200 filter-btn">مدفوع</button>
                    <button onclick="filterInvoices('متأخر')" class="px-3 py-1 rounded-full text-sm bg-red-100 text-red-800 hover:bg-red-200 filter-btn">متأخر</button>
                </div>

                <div class="flex justify-between items-center mb-4">
                    <h2 class="text-xl font-bold">الفواتير (${invoices.size})</h2>
                    <button onclick="document.getElementById('addInvoiceModal').showModal()" class="bg-green-700 text-white px-4 py-2 rounded-lg hover:bg-green-800">+ إنشاء فاتورة</button>
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
                function filterInvoices(status) {
                    document.querySelectorAll('.filter-btn').forEach(b => b.classList.remove('active-filter'));
                    event.target.classList.add('active-filter');
                    const rows = document.querySelectorAll('#invoicesBody tr');
                    rows.forEach(row => {
                        if (!status) { row.style.display = ''; return; }
                        const badge = row.querySelector('.rounded-full');
                        const text = badge ? badge.textContent.trim() : '';
                        row.style.display = text === status ? '' : 'none';
                    });
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
                crmLayout("الفوترة", principal.name, principal.role, principal, "billing", content),
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
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            val currentMonth = call.request.queryParameters["month"] ?: "${now.year}-${now.monthNumber.toString().padStart(2, '0')}"
            val agents = crmService.listAgents().filter { it.active }
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

                <div class="bg-white rounded-xl shadow overflow-x-auto">
                    <table class="w-full text-sm">
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

            val profile = crmService.getAgentProfile(agentId) ?: run {
                call.respondRedirect("/crm/dashboard")
                return@get
            }

            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            val agent = profile.agent
            val (roleBg, roleTxt) = roleBadgeColor(agent.role)
            val photoHtml = agentPhotoHtml(agent.photoUrl, agent.name, 96)
            val statusDot = if (agent.active) """<span class="inline-flex items-center gap-1 text-sm text-green-700"><span class="w-2 h-2 rounded-full bg-green-500 inline-block"></span>نشط</span>"""
                else """<span class="inline-flex items-center gap-1 text-sm text-red-600"><span class="w-2 h-2 rounded-full bg-red-500 inline-block"></span>غير نشط</span>"""

            val canEditPhoto = principal.isOwner || principal.agentId == agentId

            // Section 1: Agent Card
            val agentCard = """
                <div class="bg-white rounded-2xl shadow p-6 md:p-8 mb-6">
                    <div class="flex flex-col md:flex-row items-center md:items-start gap-6">
                        <div class="relative group">
                            $photoHtml
                            ${if (canEditPhoto) """
                            <button onclick="document.getElementById('photoInput').click()" class="absolute inset-0 flex items-center justify-center bg-black/40 rounded-full opacity-0 group-hover:opacity-100 transition cursor-pointer">
                                <span class="text-white text-sm">📷 تغيير</span>
                            </button>
                            <input type="file" id="photoInput" accept="image/*" class="hidden" onchange="uploadPhoto(this)">
                            """ else ""}
                        </div>
                        <div class="text-center md:text-right flex-1">
                            <h1 class="text-2xl font-bold mb-2">${agent.name}</h1>
                            <span class="inline-block px-3 py-1 rounded-full text-sm font-medium mb-2" style="background:$roleBg;color:$roleTxt">${roleDisplayName(agent.role)}</span>
                            <p class="text-gray-500 text-sm mb-1">${agent.email}</p>
                            <div class="mt-1">$statusDot</div>
                        </div>
                        <div class="flex gap-2">
                            <a href="/crm/logout" class="text-sm px-4 py-2 bg-red-50 text-red-600 rounded-lg hover:bg-red-100 transition">تسجيل الخروج</a>
                        </div>
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

            // Section 2: Monthly Target + Progress
            val currentMonthLabel = arabicMonth(profile.currentMonth)
            val targetSection = if (profile.target != null) {
                val p = profile.progress
                """
                <div class="bg-white rounded-2xl shadow p-6 mb-6">
                    <div class="flex justify-between items-center mb-4">
                        <h2 class="text-lg font-bold">هدف $currentMonthLabel</h2>
                        ${if (principal.isOwner) """<button onclick="document.getElementById('setTargetModal').showModal()" class="text-sm px-3 py-1 bg-blue-100 text-blue-700 rounded-lg hover:bg-blue-200">تعديل الهدف</button>""" else ""}
                    </div>
                    ${progressBar("عملاء جدد", p.actualClients.toString(), p.targetClients.toString(), p.clientsPercent)}
                    ${progressBar("اشتراكات", p.actualSubscriptions.toString(), p.targetSubscriptions.toString(), p.subscriptionsPercent)}
                    ${progressBar("الإيراد", "${String.format("%,.0f", p.actualRevenue)} ج.م", "${String.format("%,.0f", p.targetRevenue)} ج.م", p.revenuePercent)}
                    ${if (!profile.target?.notes.isNullOrBlank()) """
                    <div class="mt-4 p-3 bg-blue-50 rounded-lg border border-blue-200">
                        <p class="text-sm font-bold text-blue-800 mb-1">📝 ملاحظات الهدف:</p>
                        <p class="text-sm text-blue-700">${profile.target?.notes}</p>
                    </div>
                    """ else ""}
                </div>
                """.trimIndent()
            } else {
                """
                <div class="bg-white rounded-2xl shadow p-6 mb-6">
                    <div class="flex justify-between items-center mb-4">
                        <h2 class="text-lg font-bold">هدف $currentMonthLabel</h2>
                        ${if (principal.isOwner) """<button onclick="document.getElementById('setTargetModal').showModal()" class="text-sm px-3 py-1 bg-blue-100 text-blue-700 rounded-lg hover:bg-blue-200">تعديل الهدف</button>""" else ""}
                    </div>
                    <p class="text-gray-400 text-center py-4">لم يتم تحديد هدف لهذا الشهر</p>
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

            // Section 3: All-time Stats
            val statsSection = """
                <div class="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
                    ${kpiCard("إجمالي العملاء", profile.totalClients.toString(), "👥", "#1B3A5C")}
                    ${kpiCard("إجمالي الاشتراكات", profile.totalSubscriptions.toString(), "✅", "#2E7D32")}
                    ${kpiCard("إجمالي الإيراد", "${String.format("%,.0f", profile.totalRevenue)} ج.م", "💰", "#E65100")}
                    ${kpiCard("إجمالي الأنشطة", profile.totalActivities.toString(), "📋", "#00838F")}
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
                <div class="bg-white rounded-2xl shadow p-6 mb-6">
                    <div class="flex justify-between items-center mb-4">
                        <h2 class="text-lg font-bold">سجل التقييمات</h2>
                        ${if (principal.isOwner) """<button onclick="document.getElementById('addReviewModal').showModal()" class="text-sm px-3 py-1 bg-green-100 text-green-700 rounded-lg hover:bg-green-200">+ إضافة تقييم</button>""" else ""}
                    </div>
                    ${if (profile.reviews.isEmpty()) """<p class="text-gray-400 text-center py-4">لا توجد تقييمات</p>""" else """
                    <div class="overflow-x-auto">
                        <table class="w-full text-sm">
                            <thead>
                                <tr class="bg-gray-100 border-b">
                                    <th class="p-2 text-right">الشهر</th>
                                    <th class="p-2 text-right">التقييم</th>
                                    <th class="p-2 text-right">الملاحظات</th>
                                    <th class="p-2 text-center">مثبت</th>
                                    <th class="p-2 text-right">إجراءات</th>
                                </tr>
                            </thead>
                            <tbody>$reviewRows</tbody>
                        </table>
                    </div>
                    """}
                </div>
            """.trimIndent()

            // Section 6: Recent Activities
            val activityRows = profile.recentActivities.joinToString("") { a ->
                """
                <tr class="border-b hover:bg-gray-50">
                    <td class="p-2">${a.createdAt}</td>
                    <td class="p-2">${a.clientName}</td>
                    <td class="p-2">${a.actionType ?: "-"}</td>
                    <td class="p-2">${a.channel ?: "-"}</td>
                    <td class="p-2">${a.result ?: "-"}</td>
                </tr>
                """
            }

            val activitiesSection = """
                <div class="bg-white rounded-2xl shadow p-6 mb-6">
                    <h2 class="text-lg font-bold mb-4">آخر الأنشطة</h2>
                    ${if (profile.recentActivities.isEmpty()) """<p class="text-gray-400 text-center py-4">لا توجد أنشطة</p>""" else """
                    <div class="overflow-x-auto">
                        <table class="w-full text-sm">
                            <thead>
                                <tr class="bg-gray-100 border-b">
                                    <th class="p-2 text-right">التاريخ</th>
                                    <th class="p-2 text-right">العميل</th>
                                    <th class="p-2 text-right">الإجراء</th>
                                    <th class="p-2 text-right">القناة</th>
                                    <th class="p-2 text-right">النتيجة</th>
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

        // ─── JSON API Endpoints ─────────────────────────────────
        route("/crm/api") {

            // Photo upload for CRM agents
            post("/upload") {
                val principal = call.principal<CrmPrincipal>()!!
                val multipart = call.receiveMultipart()
                var uploadedUrl: String? = null
                multipart.forEachPart { part ->
                    if (part is PartData.FileItem) {
                        val ext = (part.originalFileName ?: "image.jpg").substringAfterLast('.', "jpg").lowercase().takeIf { it in listOf("jpg","jpeg","png","webp","gif") } ?: "jpg"
                        val dir = java.io.File("uploads/crm-photos")
                        if (!dir.exists()) dir.mkdirs()
                        val fileName = "${java.util.UUID.randomUUID()}.$ext"
                        val file = java.io.File(dir, fileName)
                        part.streamProvider().use { input -> file.outputStream().buffered().use { output -> input.copyTo(output) } }
                        val host = call.request.header("Host") ?: "localhost:8080"
                        val scheme = call.request.header("X-Forwarded-Proto") ?: "http"
                        uploadedUrl = "$scheme://$host/uploads/crm-photos/$fileName"
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
                val clients = crmService.listClients(principal.agentId, principal.canSeeAll)
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

            // GET /crm/api/activities
            get("/activities") {
                val principal = call.principal<CrmPrincipal>()!!
                val activities = crmService.listActivities(principal.agentId, principal.canSeeAll)
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
                val stats = crmService.getStats(principal.agentId, principal.canSeeAll)
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

            // GET /crm/api/agents (owner only)
            get("/agents") {
                val principal = call.principal<CrmPrincipal>()!!
                if (!principal.canManageAgents) {
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
                val ok = crmService.updateAgent(
                    id = id,
                    name = if (principal.canManageAgents) obj["name"]?.jsonPrimitive?.contentOrNull else null,
                    role = if (principal.canManageAgents) safeRole else null,
                    active = if (principal.canManageAgents) obj["active"]?.jsonPrimitive?.booleanOrNull else null,
                    password = if (principal.canManageAgents || isSelf) obj["password"]?.jsonPrimitive?.contentOrNull else null,
                    photoUrl = obj["photoUrl"]?.jsonPrimitive?.contentOrNull,
                )
                if (ok) {
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
                val invoices = crmService.listInvoices(clientId, status)
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
                val payments = crmService.getPaymentsForInvoice(invoiceId)
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
                val stats = crmService.getBillingStats()
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
                val clients = crmService.listClients(null, true)
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
                val profile = crmService.getAgentProfile(id)
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
                    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
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
                    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
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
                val agents = crmService.listAgents().filter { it.active }
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
                    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
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
    "owner" -> "المالك"
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
        append("<th class='p-2 text-right'>المندوب</th><th class='p-2 text-right'>الأنشطة</th><th class='p-2 text-right'>العملاء</th><th class='p-2 text-right'>الاشتراكات</th><th class='p-2 text-right'>الإيراد</th>")
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

    // Fetch agent photo URL from principal — we use a placeholder approach
    // since CrmPrincipal doesn't carry photoUrl, we show initials
    val sidebarPhotoHtml = agentPhotoHtml(null, agentName, 36)

    fun navLink(tab: String, label: String, icon: String, href: String): String {
        val active = if (tab == activeTab) "bg-white/10 font-bold" else "hover:bg-white/5"
        return """<a href="$href" class="flex items-center gap-3 px-4 py-3 rounded-lg $active transition">
            <span class="text-lg">$icon</span><span>$label</span>
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
            append(navLink("billing", "الفوترة", "\uD83E\uDDFE", "/crm/billing"))
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
        // Profile - everyone can see their own profile
        append(navLink("profile", "ملفي", "👤", "/crm/profile"))
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
        @keyframes pulse { 0%,100%{opacity:1} 50%{opacity:0.5} }
        .overdue { animation: pulse 2s infinite; }
        @media (max-width: 767px) {
            dialog { max-width: 100%; width: 100%; margin: 0; border-radius: 0.5rem; }
        }
    </style>
</head>
<body class="bg-gray-100 min-h-screen">
    <!-- Mobile header -->
    <div class="md:hidden fixed top-0 right-0 left-0 z-50 sidebar flex items-center justify-between p-4">
        <div class="flex items-center gap-2">
            <img src="/landing/waslek_logo_sm.png" class="w-8 h-8 rounded-lg bg-white p-0.5">
            <span class="text-white font-bold">وصلك CRM</span>
        </div>
        <button onclick="document.getElementById('mobileSidebar').classList.toggle('hidden')" class="text-white text-2xl">☰</button>
    </div>
    <!-- Mobile sidebar overlay -->
    <div id="mobileSidebar" class="hidden fixed inset-0 z-40 md:hidden">
        <div class="absolute inset-0 bg-black/50" onclick="this.parentElement.classList.add('hidden')"></div>
        <aside class="sidebar absolute right-0 top-0 bottom-0 w-64 text-white flex flex-col">
            <div class="p-6 border-b border-white/10 flex items-center gap-3">
                <img src="/landing/waslek_logo_sm.png" alt="وصلك" class="w-10 h-10 rounded-lg bg-white p-1">
                <div>
                    <h1 class="text-xl font-bold">وصلك CRM</h1>
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
                <img src="/landing/waslek_logo_sm.png" alt="وصلك" class="w-10 h-10 rounded-lg bg-white p-1">
                <div>
                    <h1 class="text-xl font-bold">وصلك CRM</h1>
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

        <!-- Main Content -->
        <main class="flex-1 p-4 md:p-8 pt-16 md:pt-8 overflow-auto">
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
                        <select name="clientId" required onchange="onClientSelected(this)" class="w-full px-3 py-2 border rounded-lg text-sm">
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

// ════════════════════════════════════════════════════════════════
// Profile Page Helpers
// ════════════════════════════════════════════════════════════════

private fun arabicMonth(month: String): String {
    val parts = month.split("-")
    val monthNames = listOf("", "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")
    return "${monthNames[parts[1].toInt()]} ${parts[0]}"
}

private fun progressBar(label: String, actual: String, target: String, percent: Int): String {
    val color = when { percent >= 75 -> "#2E7D32"; percent >= 50 -> "#F9A825"; else -> "#D32F2F" }
    return """
    <div class="mb-4">
        <div class="flex justify-between text-sm mb-1">
            <span class="font-bold">$label</span>
            <span>$actual / $target (${percent}%)</span>
        </div>
        <div class="w-full bg-gray-200 rounded-full h-4">
            <div class="h-4 rounded-full transition-all duration-500" style="width:${percent.coerceAtMost(100)}%;background:$color"></div>
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
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
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
