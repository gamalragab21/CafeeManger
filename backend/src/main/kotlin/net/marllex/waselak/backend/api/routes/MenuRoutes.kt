package net.marllex.waselak.backend.api.routes

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.marllex.waselak.backend.data.database.CategoriesTable
import net.marllex.waselak.backend.data.database.ItemsTable
import net.marllex.waselak.backend.data.database.VendorsTable
import net.marllex.waselak.backend.domain.service.FeatureNotAvailableException
import net.marllex.waselak.backend.domain.service.PlanService
import net.marllex.waselak.backend.plugins.routeTrace
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.java.KoinJavaComponent
import java.util.UUID

/**
 * Public route: serves a beautifully styled HTML digital menu page for a vendor.
 * No authentication required — anyone with the link can view the menu.
 */
fun Route.digitalMenuRoutes() {
    val planService by KoinJavaComponent.inject<PlanService>(clazz = PlanService::class.java)

    route("/menu") {
        get("/{vendorId}") {
            val trace = call.routeTrace()
            trace.step("Digital menu fetch started")
            val vendorId = call.parameters["vendorId"]
                ?: return@get call.respondText("Vendor ID required", status = HttpStatusCode.BadRequest)

            trace.step("Parsing vendor ID", mapOf("vendorId" to vendorId))
            val uuid = try { UUID.fromString(vendorId) } catch (_: Exception) {
                trace.step("Invalid vendor ID format", mapOf("vendorId" to vendorId))
                return@get call.respondText("Invalid vendor ID", status = HttpStatusCode.BadRequest)
            }

            trace.step("Looking up vendor in database")
            val vendor = transaction {
                VendorsTable.selectAll().where { VendorsTable.id eq uuid }.firstOrNull()
            } ?: run {
                trace.step("Vendor not found", mapOf("vendorId" to vendorId))
                return@get call.respondText("Store not found", status = HttpStatusCode.NotFound)
            }

            // ─── Plan feature gate: digital menu ──────────
            trace.step("Checking DIGITAL_MENU feature gate")
            try {
                planService.checkFeature(uuid, "DIGITAL_MENU")
            } catch (_: FeatureNotAvailableException) {
                trace.step("DIGITAL_MENU feature not available", mapOf("vendorId" to vendorId))
                return@get call.respondText(
                    "Digital menu is not available for this store's current plan.",
                    status = HttpStatusCode.Forbidden
                )
            }

            data class MenuCategory(val id: String, val name: String, val order: Int)
            data class MenuItem(
                val name: String, val description: String?,
                val price: Double, val imageUrl: String?, val categoryId: String,
            )

            trace.step("Fetching categories")
            val categories = transaction {
                CategoriesTable.selectAll()
                    .where { CategoriesTable.vendorId eq uuid }
                    .orderBy(CategoriesTable.displayOrder, SortOrder.ASC)
                    .map { MenuCategory(it[CategoriesTable.id].toString(), it[CategoriesTable.name], it[CategoriesTable.displayOrder]) }
            }
            trace.step("Categories fetched", mapOf("categoriesCount" to categories.size.toString()))

            trace.step("Fetching menu items")
            val items = transaction {
                ItemsTable.selectAll()
                    .where { (ItemsTable.vendorId eq uuid) and (ItemsTable.available eq true) }
                    .orderBy(ItemsTable.name, SortOrder.ASC)
                    .map {
                        MenuItem(
                            name = it[ItemsTable.name],
                            description = it[ItemsTable.description],
                            price = it[ItemsTable.price].toDouble(),
                            imageUrl = it[ItemsTable.imageUrl],
                            categoryId = it[ItemsTable.categoryId].toString(),
                        )
                    }
            }
            trace.step("Menu items fetched", mapOf("itemsCount" to items.size.toString()))

            val storeName = esc(vendor[VendorsTable.name])
            val storeAddress = esc(vendor[VendorsTable.address])
            val storePhone = esc(vendor[VendorsTable.contactPhone])
            val logoUrl = vendor[VendorsTable.logoUrl]
            val totalItems = items.size

            val html = buildString {
                appendLine("<!DOCTYPE html>")
                appendLine("<html lang=\"ar\" dir=\"auto\">")
                appendLine("<head>")
                appendLine("<meta charset=\"UTF-8\">")
                appendLine("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1.0\">")
                appendLine("<meta name=\"theme-color\" content=\"#1a1a2e\">")
                appendLine("<title>$storeName</title>")
                appendLine("<link rel=\"preconnect\" href=\"https://fonts.googleapis.com\">")
                appendLine("<link rel=\"preconnect\" href=\"https://fonts.gstatic.com\" crossorigin>")
                appendLine("<link href=\"https://fonts.googleapis.com/css2?family=Cairo:wght@400;600;700;800&family=Inter:wght@400;500;600;700&display=swap\" rel=\"stylesheet\">")
                appendLine("<style>")
                appendLine(MENU_CSS)
                appendLine("</style>")
                appendLine("</head>")
                appendLine("<body>")

                // ── Hero Header ──
                appendLine("<header class=\"hero\">")
                appendLine("<div class=\"hero-bg\"></div>")
                appendLine("<div class=\"hero-content\">")
                if (!logoUrl.isNullOrBlank()) {
                    appendLine("<div class=\"logo-ring\"><img src=\"${esc(logoUrl)}\" alt=\"$storeName\" class=\"logo\"></div>")
                } else {
                    appendLine("<div class=\"logo-ring logo-placeholder\"><span>${ storeName.firstOrNull() ?: "S" }</span></div>")
                }
                appendLine("<h1 class=\"store-name\">$storeName</h1>")
                appendLine("<div class=\"store-meta\">")
                appendLine("<span class=\"meta-item\"><svg width=\"14\" height=\"14\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0118 0z\"/><circle cx=\"12\" cy=\"10\" r=\"3\"/></svg> $storeAddress</span>")
                appendLine("<a href=\"tel:$storePhone\" class=\"meta-item phone-link\"><svg width=\"14\" height=\"14\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M22 16.92v3a2 2 0 01-2.18 2 19.79 19.79 0 01-8.63-3.07 19.5 19.5 0 01-6-6 19.79 19.79 0 01-3.07-8.67A2 2 0 014.11 2h3a2 2 0 012 1.72c.127.96.361 1.903.7 2.81a2 2 0 01-.45 2.11L8.09 9.91a16 16 0 006 6l1.27-1.27a2 2 0 012.11-.45c.907.339 1.85.573 2.81.7A2 2 0 0122 16.92z\"/></svg> $storePhone</a>")
                appendLine("</div>")
                appendLine("<div class=\"items-badge\">$totalItems items</div>")
                appendLine("</div>")
                appendLine("</header>")

                // ── Category Navigation ──
                if (categories.size > 1) {
                    appendLine("<nav class=\"cat-nav\" id=\"catNav\">")
                    appendLine("<div class=\"cat-scroll\">")
                    categories.forEachIndexed { idx, cat ->
                        val activeClass = if (idx == 0) " active" else ""
                        appendLine("<button class=\"cat-chip$activeClass\" data-target=\"cat-${cat.id}\" onclick=\"scrollToCat('cat-${cat.id}', this)\">${esc(cat.name)}</button>")
                    }
                    appendLine("</div>")
                    appendLine("</nav>")
                }

                // ── Menu Body ──
                appendLine("<main class=\"menu\">")

                if (categories.isEmpty() || items.isEmpty()) {
                    // Empty state
                    appendLine("<div class=\"empty-state\">")
                    appendLine("<div class=\"empty-icon\">&#127869;</div>")
                    appendLine("<h2>Menu Coming Soon</h2>")
                    appendLine("<p>We're preparing something delicious for you!</p>")
                    appendLine("</div>")
                } else {
                    categories.forEach { cat ->
                        val catItems = items.filter { it.categoryId == cat.id }
                        if (catItems.isEmpty()) return@forEach

                        appendLine("<section id=\"cat-${cat.id}\" class=\"cat-section\">")
                        appendLine("<div class=\"cat-header\">")
                        appendLine("<h2>${esc(cat.name)}</h2>")
                        appendLine("<span class=\"cat-count\">${catItems.size}</span>")
                        appendLine("</div>")

                        appendLine("<div class=\"items-grid\">")
                        catItems.forEach { item ->
                            appendLine("<article class=\"item-card\">")
                            if (!item.imageUrl.isNullOrBlank()) {
                                appendLine("<div class=\"item-img-wrap\"><img src=\"${esc(item.imageUrl)}\" alt=\"${esc(item.name)}\" class=\"item-img\" loading=\"lazy\"></div>")
                            } else {
                                appendLine("<div class=\"item-img-wrap item-img-placeholder\"><span>&#127860;</span></div>")
                            }
                            appendLine("<div class=\"item-info\">")
                            appendLine("<h3 class=\"item-name\">${esc(item.name)}</h3>")
                            if (!item.description.isNullOrBlank()) {
                                appendLine("<p class=\"item-desc\">${esc(item.description)}</p>")
                            }
                            appendLine("<div class=\"item-footer\">")
                            appendLine("<span class=\"item-price\">${"%.2f".format(item.price)}</span>")
                            appendLine("<span class=\"item-currency\">EGP</span>")
                            appendLine("</div>")
                            appendLine("</div>")
                            appendLine("</article>")
                        }
                        appendLine("</div>")
                        appendLine("</section>")
                    }
                }
                appendLine("</main>")

                // ── Footer ──
                appendLine("<footer class=\"footer\">")
                appendLine("<div class=\"footer-brand\">")
                appendLine("<span class=\"footer-logo\">W</span>")
                appendLine("<span>Powered by <strong>Wasel POS</strong></span>")
                appendLine("</div>")
                appendLine("</footer>")

                // ── JS for smooth scroll & active tracking ──
                appendLine("<script>")
                appendLine(MENU_JS)
                appendLine("</script>")

                appendLine("</body></html>")
            }

            trace.step("Digital menu fetch completed", mapOf(
                "vendorId" to vendorId,
                "categoriesCount" to categories.size.toString(),
                "itemsCount" to items.size.toString()
            ))
            call.respondText(html, ContentType.Text.Html)
        }
    }
}

private fun esc(s: String): String = s
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&#39;")

// ── JavaScript for category scroll & active state ──
private val MENU_JS = """
function scrollToCat(id, btn) {
  var el = document.getElementById(id);
  if (!el) return;
  var nav = document.getElementById('catNav');
  var offset = nav ? nav.offsetHeight + 12 : 80;
  var y = el.getBoundingClientRect().top + window.pageYOffset - offset;
  window.scrollTo({ top: y, behavior: 'smooth' });
  document.querySelectorAll('.cat-chip').forEach(function(c) { c.classList.remove('active'); });
  btn.classList.add('active');
  btn.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'center' });
}

// Intersection observer for auto-highlighting
var observer = new IntersectionObserver(function(entries) {
  entries.forEach(function(entry) {
    if (entry.isIntersecting) {
      var id = entry.target.id;
      document.querySelectorAll('.cat-chip').forEach(function(c) {
        c.classList.toggle('active', c.getAttribute('data-target') === id);
      });
    }
  });
}, { rootMargin: '-30% 0px -60% 0px' });

document.querySelectorAll('.cat-section').forEach(function(s) { observer.observe(s); });
""".trimIndent()

// ── CSS Design ──
private val MENU_CSS = """
:root {
  --bg: #f8f7f4;
  --surface: #ffffff;
  --text: #1a1a2e;
  --text-2: #6b7280;
  --text-3: #9ca3af;
  --accent: #e67e22;
  --accent-light: #fef3e2;
  --accent-dark: #d35400;
  --green: #27ae60;
  --border: #f0eeeb;
  --shadow-sm: 0 1px 2px rgba(0,0,0,.04);
  --shadow-md: 0 4px 16px rgba(0,0,0,.06);
  --shadow-lg: 0 8px 32px rgba(0,0,0,.08);
  --radius: 20px;
  --radius-sm: 12px;
  --radius-xs: 8px;
  --transition: .25s cubic-bezier(.4,0,.2,1);
}

* { margin: 0; padding: 0; box-sizing: border-box; }

body {
  font-family: 'Cairo', 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
  background: var(--bg);
  color: var(--text);
  line-height: 1.6;
  min-height: 100vh;
  -webkit-font-smoothing: antialiased;
}

/* ═══════════════════════════════════
   HERO
   ═══════════════════════════════════ */
.hero {
  position: relative;
  overflow: hidden;
  padding: 56px 24px 44px;
  text-align: center;
  color: #fff;
}

.hero-bg {
  position: absolute;
  inset: 0;
  background: linear-gradient(145deg, #1a1a2e 0%, #16213e 40%, #0f3460 70%, #e94560 140%);
  z-index: 0;
}

.hero-bg::after {
  content: '';
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse at 20% 50%, rgba(233,69,96,.25) 0%, transparent 50%),
    radial-gradient(ellipse at 80% 20%, rgba(15,52,96,.5) 0%, transparent 50%);
}

.hero-content { position: relative; z-index: 1; }

.logo-ring {
  width: 100px;
  height: 100px;
  margin: 0 auto 16px;
  border-radius: 50%;
  padding: 4px;
  background: linear-gradient(135deg, #e94560, #e67e22, #f1c40f);
  box-shadow: 0 4px 24px rgba(233,69,96,.3);
  display: flex;
  align-items: center;
  justify-content: center;
}

.logo {
  width: 92px;
  height: 92px;
  border-radius: 50%;
  object-fit: cover;
  border: 3px solid #1a1a2e;
}

.logo-placeholder {
  background: linear-gradient(135deg, #e94560, #e67e22);
}
.logo-placeholder span {
  font-size: 40px;
  font-weight: 800;
  color: #fff;
  text-shadow: 0 2px 8px rgba(0,0,0,.2);
}

.store-name {
  font-size: 32px;
  font-weight: 800;
  letter-spacing: -.02em;
  margin-bottom: 8px;
  text-shadow: 0 2px 12px rgba(0,0,0,.2);
}

.store-meta {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  margin-bottom: 16px;
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  opacity: .85;
  color: #fff;
  text-decoration: none;
}

.phone-link {
  background: rgba(255,255,255,.12);
  padding: 6px 16px;
  border-radius: 20px;
  backdrop-filter: blur(8px);
  transition: var(--transition);
}
.phone-link:hover { background: rgba(255,255,255,.25); }

.items-badge {
  display: inline-block;
  padding: 6px 20px;
  border-radius: 20px;
  background: rgba(255,255,255,.1);
  backdrop-filter: blur(8px);
  font-size: 13px;
  font-weight: 600;
  letter-spacing: .03em;
  text-transform: uppercase;
  border: 1px solid rgba(255,255,255,.15);
}

/* ═══════════════════════════════════
   CATEGORY NAV
   ═══════════════════════════════════ */
.cat-nav {
  position: sticky;
  top: 0;
  z-index: 50;
  background: var(--surface);
  border-bottom: 1px solid var(--border);
  box-shadow: var(--shadow-sm);
}

.cat-scroll {
  display: flex;
  gap: 8px;
  padding: 14px 20px;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
}
.cat-scroll::-webkit-scrollbar { display: none; }

.cat-chip {
  flex-shrink: 0;
  padding: 8px 22px;
  border-radius: 24px;
  border: 1.5px solid var(--border);
  background: var(--surface);
  color: var(--text-2);
  font-size: 14px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: var(--transition);
  white-space: nowrap;
}

.cat-chip:hover {
  border-color: var(--accent);
  color: var(--accent);
  background: var(--accent-light);
}

.cat-chip.active {
  background: var(--text);
  color: #fff;
  border-color: var(--text);
  box-shadow: 0 2px 8px rgba(26,26,46,.15);
}

/* ═══════════════════════════════════
   MENU BODY
   ═══════════════════════════════════ */
.menu {
  max-width: 1080px;
  margin: 0 auto;
  padding: 28px 16px 60px;
}

.cat-section {
  margin-bottom: 40px;
  scroll-margin-top: 80px;
}

.cat-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 18px;
  padding-bottom: 12px;
  border-bottom: 2px solid var(--border);
}

.cat-header h2 {
  font-size: 24px;
  font-weight: 800;
  color: var(--text);
  letter-spacing: -.01em;
}

.cat-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 28px;
  height: 28px;
  padding: 0 8px;
  border-radius: 14px;
  background: var(--accent-light);
  color: var(--accent-dark);
  font-size: 13px;
  font-weight: 700;
}

/* ═══════════════════════════════════
   ITEMS GRID
   ═══════════════════════════════════ */
.items-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}

.item-card {
  display: flex;
  flex-direction: row;
  background: var(--surface);
  border-radius: var(--radius-sm);
  overflow: hidden;
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--border);
  transition: var(--transition);
  cursor: default;
}

.item-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
  border-color: rgba(230,126,34,.2);
}

.item-img-wrap {
  flex-shrink: 0;
  width: 120px;
  height: 120px;
  overflow: hidden;
  position: relative;
  background: #f5f5f0;
}

.item-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform .4s ease;
}

.item-card:hover .item-img { transform: scale(1.08); }

.item-img-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #fef3e2 0%, #fde8d0 100%);
}
.item-img-placeholder span { font-size: 40px; }

.item-info {
  flex: 1;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-width: 0;
}

.item-name {
  font-size: 16px;
  font-weight: 700;
  color: var(--text);
  margin-bottom: 4px;
  line-height: 1.3;
}

.item-desc {
  font-size: 13px;
  color: var(--text-3);
  margin-bottom: 10px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.5;
}

.item-footer {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.item-price {
  font-size: 20px;
  font-weight: 800;
  color: var(--accent-dark);
  letter-spacing: -.02em;
}

.item-currency {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-3);
  text-transform: uppercase;
  letter-spacing: .03em;
}

/* ═══════════════════════════════════
   EMPTY STATE
   ═══════════════════════════════════ */
.empty-state {
  text-align: center;
  padding: 80px 24px;
}
.empty-icon { font-size: 72px; margin-bottom: 20px; }
.empty-state h2 { font-size: 24px; font-weight: 700; margin-bottom: 8px; }
.empty-state p { font-size: 16px; color: var(--text-2); }

/* ═══════════════════════════════════
   FOOTER
   ═══════════════════════════════════ */
.footer {
  text-align: center;
  padding: 28px 24px;
  border-top: 1px solid var(--border);
  background: var(--surface);
}

.footer-brand {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--text-2);
  font-size: 13px;
}

.footer-logo {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: linear-gradient(135deg, #1a1a2e, #e94560);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 800;
  font-size: 14px;
}

.footer-brand strong { color: var(--text); }

/* ═══════════════════════════════════
   RESPONSIVE
   ═══════════════════════════════════ */
@media (max-width: 640px) {
  .hero { padding: 40px 16px 32px; }
  .store-name { font-size: 26px; }
  .logo-ring { width: 84px; height: 84px; }
  .logo { width: 76px; height: 76px; }
  .logo-placeholder span { font-size: 32px; }

  .items-grid { grid-template-columns: 1fr; }

  .item-card { flex-direction: row; }
  .item-img-wrap { width: 110px; height: 110px; }

  .menu { padding: 20px 12px 48px; }
  .cat-header h2 { font-size: 20px; }
}

@media (min-width: 768px) {
  .items-grid { grid-template-columns: repeat(2, 1fr); }
}

@media (min-width: 1024px) {
  .items-grid { grid-template-columns: repeat(3, 1fr); }
}

/* ═══════════════════════════════════
   DARK MODE
   ═══════════════════════════════════ */
@media (prefers-color-scheme: dark) {
  :root {
    --bg: #0f0f17;
    --surface: #1a1a28;
    --text: #f0f0f5;
    --text-2: #9ca3af;
    --text-3: #6b7280;
    --accent-light: #3d2a1a;
    --border: #2a2a3a;
    --shadow-sm: 0 1px 2px rgba(0,0,0,.15);
    --shadow-md: 0 4px 16px rgba(0,0,0,.25);
    --shadow-lg: 0 8px 32px rgba(0,0,0,.35);
  }

  .item-img-placeholder { background: linear-gradient(135deg, #2a2218 0%, #3d2a1a 100%); }
  .cat-chip.active { background: #e94560; border-color: #e94560; }
  .phone-link:hover { background: rgba(255,255,255,.2); }
}

/* Smooth scroll */
html { scroll-behavior: smooth; }

/* Scrollbar hide for cat-nav */
.cat-scroll { -ms-overflow-style: none; }
""".trimIndent()
