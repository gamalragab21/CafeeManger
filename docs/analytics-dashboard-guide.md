# Analytics Dashboard - Complete Guide
# لوحة التحليلات - دليل شامل

This document explains every section and every field in the Analytics Dashboard,
so the manager can understand what each number means and how to use it.

---

## 1. Executive Summary (ملخص الأداء العام)

A quick overview of the most important business metrics.

| Field | Arabic | Description |
|-------|--------|-------------|
| **Revenue** | الإيرادات | Total money earned from all orders in the selected period. Shows % change compared to the previous period. |
| **Orders** | الطلبات | Total number of orders placed. Shows % change compared to the previous period. |
| **Avg Order** | متوسط قيمة الطلب | Average amount per order (Revenue / Orders). Shows % change. |
| **Delivery Fees** | مصاريف التوصيل | Total delivery fees collected from delivery orders. |
| **Active Orders** | الطلبات الجارية | Number of orders currently being prepared or in delivery (real-time). |
| **Attendance Today** | حضور اليوم | Number of staff members who checked in today (real-time). |

---

## 2. Revenue & Profit (الإيرادات والأرباح)

Detailed breakdown of where the money comes from.

| Field | Arabic | Description |
|-------|--------|-------------|
| **Gross Revenue** | إجمالي الإيرادات | Total revenue before any deductions. All money received from orders. |
| **Net Revenue** | صافي الإيرادات | Revenue after deducting delivery fees. The actual income from sales. |
| **Delivery Fees** | مصاريف التوصيل | Total delivery fee amounts collected from customers. |
| **Payment Methods** | طرق الدفع | Shows how customers paid (Cash, Card, Wallet, etc.) with the revenue and order count for each method. |
| **Daily Revenue Trend** | حركة الإيرادات اليومية | Bar chart showing revenue for each day in the period. Helps spot trends (growing, declining, or seasonal patterns). |

---

## 3. Orders Intelligence (تحليل الطلبات)

Understanding order volume and patterns.

| Field | Arabic | Description |
|-------|--------|-------------|
| **Total** | الإجمالي | Total number of all orders in the period (all statuses). |
| **Completed** | مكتمل | Orders that were successfully completed and delivered/picked up. |
| **Cancelled** | ملغي | Orders that were cancelled before completion. High numbers here need attention. |
| **Refunded** | مسترد | Orders where money was returned to the customer. |
| **Channel Breakdown** | توزيع الطلبات حسب القناة | Pie chart showing order distribution: Dine-In (محلي), Delivery (توصيل), Takeaway (تيك أواي). Shows count and percentage for each channel. |
| **Daily Order Trend** | حركة الطلبات اليومية | Bar chart showing daily order counts. Each bar breaks down into completed vs cancelled orders. |

---

## 4. Peak Time Analysis (أوقات الذروة)

When is the business busiest? Helps with staffing and preparation.

| Field | Arabic | Description |
|-------|--------|-------------|
| **Busiest Hour** | أكثر ساعة ذروة | The hour of the day with the most orders (e.g., "13:00" means 1 PM). |
| **Busiest Day** | أكثر يوم ذروة | The day of the week with the most orders (e.g., Friday). |
| **Hourly Orders** | عدد الطلبات لكل ساعة | Bar chart showing how many orders came in each hour (6 AM to midnight). Helps decide when to have more staff. |
| **Weekly Heatmap** | خريطة الأسبوع الحرارية | Grid showing order intensity by day and hour. Darker = more orders. Quick visual of busy patterns across the week. |
| **Revenue by Day** | الإيرادات حسب اليوم | Bar chart showing which days of the week bring the most revenue. |

---

## 5. Cashier Performance (أداء الكاشير)

How each cashier is performing.

| Field | Arabic | Description |
|-------|--------|-------------|
| **Name** | الاسم | The cashier's name. |
| **Orders** | الطلبات | Number of orders processed by this cashier. |
| **Revenue** | الإيرادات | Total revenue from orders handled by this cashier. |
| **Cancel %** | نسبة الإلغاء | Percentage of orders this cashier cancelled. Shown in red if above 10% — may indicate a problem. |

Cashiers are ranked by revenue (highest first).

---

## 6. Delivery Performance (أداء فريق التوصيل)

How each delivery driver is performing.

| Field | Arabic | Description |
|-------|--------|-------------|
| **Driver** | السواق | The delivery driver's name. |
| **Completed** | الطلبات المكتملة | Number of orders this driver successfully delivered. |
| **Fees** | المصاريف | Total delivery fees collected by this driver. |
| **Revenue** | الإيرادات | Total revenue from orders delivered by this driver. |

Drivers are ranked by completed orders (highest first).

---

## 7. Product Intelligence (تحليل المنتجات والأصناف)

Which products sell best and which need attention.

| Field | Arabic | Description |
|-------|--------|-------------|
| **Top Selling** | الأكثر مبيعاً | Top 5 best-selling items by quantity. Shows item name, quantity sold, and revenue generated. |
| **Revenue by Category** | الإيرادات حسب القسم | Bar chart showing how much revenue each category (e.g., Hot Drinks, Desserts) generates. Helps decide which categories to expand or promote. |
| **Low Margin Items** | أصناف بهامش ربح منخفض | Items where the profit margin is very low (selling price is close to cost price). Consider raising prices or finding cheaper suppliers for these items. Shows margin percentage in red. |

---

## 8. Customer Intelligence (تحليل العملاء)

Understanding your customer base.

| Field | Arabic | Description |
|-------|--------|-------------|
| **Total Customers** | إجمالي العملاء | Total unique customers who ordered in the period. |
| **New %** | نسبة العملاء الجدد | Percentage of customers who ordered for the first time. |
| **Returning %** | نسبة العملاء المتكررين | Percentage of customers who have ordered before. Higher = stronger loyalty. |
| **Avg Spend** | متوسط الإنفاق | Average total amount each customer spends per visit. |
| **Lifetime Value** | القيمة الإجمالية للعميل | Average total amount a customer has spent across all their orders. |
| **Top Customers** | أفضل العملاء | Top 5 customers ranked by total spending. Shows name, order count, and total spent. |
| **Order Frequency** | معدل تكرار الطلبات | Distribution of how often customers order: 1 order, 2-5 orders, 6-10 orders, 11+ orders. Helps understand customer retention. |

---

## 9. Offers Performance (تحليل العروض والترويج)

How your offers and promotions are performing.

| Field | Arabic | Description |
|-------|--------|-------------|
| **Total Offers** | عدد العروض الإجمالي | Total number of offers created (active + inactive). |
| **Active** | العروض النشطة | Number of offers currently active and available to customers. |
| **Total Uses** | عدد مرات الاستخدام | How many times all offers were used by customers. |
| **Avg Discount/Use** | متوسط الخصم لكل استخدام | Average discount amount given each time an offer is used. |
| **Usage Trend** | حركة استخدام العروض | Bar chart showing daily offer usage counts. Helps track if promotions are gaining or losing traction. |
| **Top Offers** | أفضل العروض أداءً | Top 5 offers ranked by usage count. Shows offer name and how many times it was used. |

---

## 10. Discount Analytics (تحليل الخصومات)

Understanding all discounts given (manual, offers, and loyalty points).

| Field | Arabic | Description |
|-------|--------|-------------|
| **Discounted Orders** | طلبات تم خصمها | Number of orders that received any type of discount. |
| **Total Discount** | إجمالي قيمة الخصومات | Total amount of money given as discounts across all orders. |
| **Avg/Order** | متوسط الخصم لكل طلب | Average discount amount per discounted order. |
| **Discount Rate** | نسبة الطلبات المخصومة | Percentage of all orders that received a discount. High rate may mean too many discounts are being given. |
| **Breakdown** | توزيع أنواع الخصومات | Pie chart showing discount distribution by type: |
| | خصم يدوي | **Manual** — Discounts entered manually by cashier (e.g., manager approval). |
| | خصم عرض | **Offer** — Discounts from active promotions/offers. |
| | خصم نقاط | **Points** — Discounts from loyalty points redemption. |
| **Daily Trend** | حركة الخصومات اليومية | Bar chart showing daily discount amounts broken down by type (manual, offer, points). |

---

## 11. Loyalty Analytics (تحليل نقاط الولاء)

Tracking your loyalty points program.

| Field | Arabic | Description |
|-------|--------|-------------|
| **Total Earned** | إجمالي النقاط المكتسبة | Total loyalty points earned by all customers in the period. Points are earned when customers make purchases. |
| **Total Redeemed** | إجمالي النقاط المستبدلة | Total points customers have used (exchanged for discounts). |
| **Outstanding** | النقاط المتبقية عند العملاء | Points that customers have earned but haven't used yet. This is a "liability" — customers may use these for future discounts. |
| **Active Customers** | العملاء المشاركين في الولاء | Number of customers who have loyalty points (earned or redeemed at least once). |
| **Redemption Rate** | نسبة استبدال النقاط | Percentage of earned points that have been redeemed. Low rate means customers are accumulating points but not using them. |
| **Points Revenue** | الإيراد من استبدال النقاط | The monetary value of redeemed points (how much discount was given through points). |
| **Points Trend** | حركة النقاط | Bar chart showing daily points earned. Helps track loyalty program engagement over time. |

---

## 12. Staff Costs & Salaries (تكاليف الموظفين والمرتبات)

Complete overview of employee compensation costs.

### Row 1 — Compensation Totals:

| Field | Arabic | Description |
|-------|--------|-------------|
| **Base Salaries** | إجمالي المرتبات الأساسية | Total base salary amounts for all workers (without overtime). This is the regular pay each worker earns. |
| **Overtime Pay** | إجمالي الأجر الإضافي | Total overtime pay for all workers. Extra pay for hours worked beyond normal schedule. |
| **Total (Salary + Overtime)** | إجمالي الراتب مع الأجر الإضافي | Combined total: Base Salaries + Overtime Pay = Total Compensation. This is the total cost of all employees. |

### Row 2 — Payment Status:

| Field | Arabic | Description |
|-------|--------|-------------|
| **Amount Paid** | المبالغ المدفوعة | Total amount already paid to workers (both salary and overtime that has been marked as "paid"). |
| **Amount Unpaid** | المبالغ غير المدفوعة | Total amount still owed to workers (salary and overtime not yet paid). This is what you still need to pay. |
| **Workers Count** | عدد الموظفين | Number of workers who have salary records in the selected period. |

### Visual Indicators:

| Field | Arabic | Description |
|-------|--------|-------------|
| **Overtime % of Total** | نسبة الأجر الإضافي من الإجمالي | Progress bar showing what percentage of total compensation is overtime. High overtime percentage may mean you need more staff. |
| **Paid %** | نسبة المبالغ المدفوعة | Progress bar showing what percentage of total compensation has been paid. Green checkmark (100% = all paid). Red warning (< 100% = some unpaid). |
| **Overtime Hours** | إجمالي ساعات الأجر الإضافي | Total overtime hours worked by all employees. Only shows if there are overtime hours. |
| **Top Overtime Workers** | أكثر الموظفين ساعات إضافية | Top 5 workers ranked by overtime pay. Shows worker name, overtime hours, and overtime amount. Helps identify who is working the most extra hours. |

---

## 13. Alerts & Risks (التنبيهات والمخاطر)

Automatic warnings about issues that need attention.

| Alert Type | Arabic | What It Means |
|------------|--------|---------------|
| **Revenue Drop** | انخفاض في الإيرادات | Revenue dropped significantly compared to the previous period. Shows the drop percentage. |
| **High Cancellation** | نسبة إلغاء عالية | Too many orders are being cancelled. Shows the cancellation rate percentage. |
| **High Refund** | نسبة استرداد عالية | Too many refunds are being issued. Shows the refund rate percentage. |
| **Out of Stock** | أصناف نفذت من المخزون | Items have zero stock and cannot be sold. Shows the count of out-of-stock items. |
| **Low Stock** | مخزون قليل | Items are running low and will soon be out of stock. Shows count of items below minimum level. |

**Severity Levels:**
- **Critical (حرج)** — Red background. Needs immediate attention.
- **Warning (تحذير)** — Yellow/secondary background. Should be addressed soon.

---

## 14. Stock Overview (حالة المخزون)

Current inventory status and movement.

| Field | Arabic | Description |
|-------|--------|-------------|
| **Stock Value** | قيمة المخزون | Total cost value of all items currently in stock (quantity x cost price). |
| **Selling Value** | قيمة البيع | Total selling value of all items in stock (quantity x selling price). |
| **Profit Potential** | الربح المتوقع | Selling Value - Stock Value = potential profit if everything is sold. |
| **Total Items** | إجمالي الأصناف | Number of items tracked in inventory. |
| **Low Stock** | مخزون قليل | Number of items where current quantity is below the minimum level. These need reordering soon. |
| **Out of Stock** | المخزون خلص | Number of items with zero quantity. These cannot be sold until restocked. Shows item names in red. |
| **Stock Movement (14 days)** | حركة المخزون (14 يوم) | Bar chart showing daily stock changes: items added (received) vs items deducted (sold/wasted). Net movement shows if stock is growing or shrinking. |

---

## 15. Export Report (تصدير التقرير)

Download the analytics data.

| Option | Arabic | Description |
|--------|--------|-------------|
| **Export PDF** | تصدير PDF | Download a formatted PDF report of the current analytics data. Good for printing or sharing. |
| **Export Excel** | تصدير Excel | Download an Excel spreadsheet with all analytics data. Good for further analysis or record keeping. |

---

## Global Filter Bar (شريط الفلترة)

At the top of the analytics screen, you can filter all sections by time period:

| Filter | Arabic | Description |
|--------|--------|-------------|
| **Today** | النهارده | Data from today only. |
| **Yesterday** | إمبارح | Data from yesterday only. |
| **Last 7 Days** | آخر 7 أيام | Data from the past week. |
| **Last 14 Days** | آخر 14 يوم | Data from the past two weeks. |
| **Last 30 Days** | آخر 30 يوم | Data from the past month. |
| **Last Month** | الشهر اللي فات | Data from the previous calendar month. |
| **Last 3 Months** | آخر 3 شهور | Data from the past 3 months. |
| **All Time** | كل الأوقات | All data since the store was created. |
| **Custom** | مخصص | Pick a custom date range with calendar date picker (From / To). |

All sections update when you change the time filter.

---

## How Paid vs Unpaid Works

The system tracks salary payments for each worker:

1. When a salary period ends (weekly/monthly), a **salary record** is created with:
   - **Base Amount** = regular salary
   - **Overtime Amount** = extra pay for overtime hours
   - **Total = Base + Overtime**
   - **Paid = No** (initially unpaid)

2. When the manager pays a worker, the record is marked as **Paid = Yes** with the payment date.

3. The analytics aggregates:
   - **Amount Paid** = sum of (Base + Overtime) for all records where Paid = Yes
   - **Amount Unpaid** = sum of (Base + Overtime) for all records where Paid = No
   - **Total Compensation** = Amount Paid + Amount Unpaid
