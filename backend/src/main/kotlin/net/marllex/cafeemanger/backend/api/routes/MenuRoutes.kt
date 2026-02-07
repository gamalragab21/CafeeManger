package net.marllex.cafeemanger.backend.api.routes

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.marllex.cafeemanger.backend.data.database.CategoriesTable
import net.marllex.cafeemanger.backend.data.database.ItemsTable
import net.marllex.cafeemanger.backend.data.database.VendorsTable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

/**
 * Public route: serves a styled HTML digital menu page for a vendor.
 * No authentication required – anyone with the link can view the menu.
 */
fun Route.digitalMenuRoutes() {
    route("/menu") {
        get("/{vendorId}") {
            val vendorId = call.parameters["vendorId"]
                ?: return@get call.respondText("Vendor ID required", status = HttpStatusCode.BadRequest)

            val uuid = try { UUID.fromString(vendorId) } catch (_: Exception) {
                return@get call.respondText("Invalid vendor ID", status = HttpStatusCode.BadRequest)
            }

            val vendor = transaction {
                VendorsTable.selectAll().where { VendorsTable.id eq uuid }.firstOrNull()
            } ?: return@get call.respondText("Store not found", status = HttpStatusCode.NotFound)

            data class MenuCategory(val id: String, val name: String, val order: Int)
            data class MenuItem(val name: String, val description: String?, val price: Double, val imageUrl: String?, val categoryId: String)

            val categories = transaction {
                CategoriesTable.selectAll()
                    .where { CategoriesTable.vendorId eq uuid }
                    .orderBy(CategoriesTable.displayOrder, SortOrder.ASC)
                    .map { MenuCategory(it[CategoriesTable.id].toString(), it[CategoriesTable.name], it[CategoriesTable.displayOrder]) }
            }

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

            val storeName = escHtml(vendor[VendorsTable.name])
            val storeAddress = escHtml(vendor[VendorsTable.address])
            val storePhone = escHtml(vendor[VendorsTable.contactPhone])
            val logoUrl = vendor[VendorsTable.logoUrl]

            val html = buildString {
                appendLine("<!DOCTYPE html>")
                appendLine("<html lang=\"ar\" dir=\"auto\">")
                appendLine("<head>")
                appendLine("<meta charset=\"UTF-8\">")
                appendLine("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
                appendLine("<title>$storeName - Menu</title>")
                appendLine("<style>")
                appendLine(MENU_CSS)
                appendLine("</style>")
                appendLine("</head>")
                appendLine("<body>")

                // Header
                appendLine("<header class=\"hero\">")
                if (!logoUrl.isNullOrBlank()) {
                    appendLine("<img src=\"${escHtml(logoUrl)}\" alt=\"$storeName\" class=\"logo\">")
                }
                appendLine("<h1>$storeName</h1>")
                appendLine("<p class=\"subtitle\">$storeAddress</p>")
                appendLine("<p class=\"subtitle\"><a href=\"tel:$storePhone\">$storePhone</a></p>")
                appendLine("</header>")

                // Category nav
                if (categories.size > 1) {
                    appendLine("<nav class=\"category-nav\">")
                    categories.forEach { cat ->
                        appendLine("<a href=\"#cat-${cat.id}\" class=\"cat-pill\">${escHtml(cat.name)}</a>")
                    }
                    appendLine("</nav>")
                }

                // Menu sections
                appendLine("<main class=\"menu-container\">")
                categories.forEach { cat ->
                    val catItems = items.filter { it.categoryId == cat.id }
                    if (catItems.isEmpty()) return@forEach
                    appendLine("<section id=\"cat-${cat.id}\" class=\"category-section\">")
                    appendLine("<h2 class=\"category-title\">${escHtml(cat.name)}</h2>")
                    appendLine("<div class=\"items-grid\">")
                    catItems.forEach { item ->
                        appendLine("<div class=\"item-card\">")
                        if (!item.imageUrl.isNullOrBlank()) {
                            appendLine("<img src=\"${escHtml(item.imageUrl)}\" alt=\"${escHtml(item.name)}\" class=\"item-img\" loading=\"lazy\">")
                        }
                        appendLine("<div class=\"item-body\">")
                        appendLine("<h3 class=\"item-name\">${escHtml(item.name)}</h3>")
                        if (!item.description.isNullOrBlank()) {
                            appendLine("<p class=\"item-desc\">${escHtml(item.description)}</p>")
                        }
                        appendLine("<span class=\"item-price\">${"%.2f".format(item.price)} EGP</span>")
                        appendLine("</div>")
                        appendLine("</div>")
                    }
                    appendLine("</div>")
                    appendLine("</section>")
                }
                appendLine("</main>")

                // Footer
                appendLine("<footer class=\"footer\">")
                appendLine("<p>Powered by <strong>Wasel POS</strong></p>")
                appendLine("</footer>")

                appendLine("</body></html>")
            }

            call.respondText(html, ContentType.Text.Html)
        }
    }
}

private fun escHtml(s: String): String = s
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")

private val MENU_CSS = """
:root {
  --primary: #0D9488;
  --primary-light: #CCFBF1;
  --surface: #FAFAF9;
  --card: #FFFFFF;
  --text: #1C1917;
  --text-secondary: #78716C;
  --border: #E7E5E4;
  --shadow: 0 1px 3px rgba(0,0,0,.06), 0 1px 2px rgba(0,0,0,.04);
  --radius: 16px;
}

* { margin: 0; padding: 0; box-sizing: border-box; }

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Noto Sans Arabic', sans-serif;
  background: var(--surface);
  color: var(--text);
  line-height: 1.6;
  min-height: 100vh;
}

/* ── Hero ── */
.hero {
  background: linear-gradient(135deg, var(--primary) 0%, #065F56 100%);
  color: white;
  text-align: center;
  padding: 48px 24px 40px;
}
.hero .logo {
  width: 88px; height: 88px;
  border-radius: 50%;
  object-fit: cover;
  border: 3px solid rgba(255,255,255,.3);
  margin-bottom: 16px;
  box-shadow: 0 4px 12px rgba(0,0,0,.2);
}
.hero h1 { font-size: 28px; font-weight: 700; margin-bottom: 4px; }
.hero .subtitle { font-size: 14px; opacity: .85; margin-top: 2px; }
.hero a { color: white; text-decoration: none; }

/* ── Category Nav ── */
.category-nav {
  display: flex;
  gap: 8px;
  padding: 16px 24px;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  background: var(--card);
  border-bottom: 1px solid var(--border);
  position: sticky;
  top: 0;
  z-index: 10;
}
.cat-pill {
  flex-shrink: 0;
  padding: 8px 20px;
  border-radius: 24px;
  background: var(--primary-light);
  color: var(--primary);
  font-size: 14px;
  font-weight: 600;
  text-decoration: none;
  transition: all .2s ease;
  white-space: nowrap;
}
.cat-pill:hover, .cat-pill:active {
  background: var(--primary);
  color: white;
}

/* ── Menu Container ── */
.menu-container {
  max-width: 960px;
  margin: 0 auto;
  padding: 24px 16px 40px;
}
.category-section { margin-bottom: 32px; }
.category-title {
  font-size: 22px;
  font-weight: 700;
  margin-bottom: 16px;
  padding-bottom: 8px;
  border-bottom: 2px solid var(--primary);
  display: inline-block;
}

/* ── Items Grid ── */
.items-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}
.item-card {
  background: var(--card);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  overflow: hidden;
  transition: transform .15s ease, box-shadow .15s ease;
  border: 1px solid var(--border);
}
.item-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0,0,0,.1);
}
.item-img {
  width: 100%;
  height: 180px;
  object-fit: cover;
}
.item-body { padding: 16px; }
.item-name { font-size: 16px; font-weight: 600; margin-bottom: 4px; }
.item-desc { font-size: 13px; color: var(--text-secondary); margin-bottom: 8px; }
.item-price {
  display: inline-block;
  font-size: 16px;
  font-weight: 700;
  color: var(--primary);
  background: var(--primary-light);
  padding: 4px 12px;
  border-radius: 8px;
}

/* ── Footer ── */
.footer {
  text-align: center;
  padding: 24px;
  color: var(--text-secondary);
  font-size: 13px;
  border-top: 1px solid var(--border);
}

/* ── Mobile tweaks ── */
@media (max-width: 480px) {
  .hero { padding: 32px 16px 28px; }
  .hero h1 { font-size: 24px; }
  .items-grid { grid-template-columns: 1fr; }
  .menu-container { padding: 16px 12px 32px; }
}
""".trimIndent()
