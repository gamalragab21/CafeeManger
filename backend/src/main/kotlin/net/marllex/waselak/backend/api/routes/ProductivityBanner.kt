package net.marllex.waselak.backend.api.routes

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * "Productivity + reminders" banner that appears on every CRM page. Shows a random
 * Arabic message from a curated pool — Islamic duas/ayat/sunnah, productivity tips
 * for sales work, and general motivation. Rotates every 2 hours, can be dismissed
 * by the user (local to browser, 2-hour cooldown), and is positioned as a floating
 * toast at the top of the viewport so it never pushes page content or blocks clicks.
 *
 * The wrapper uses `pointer-events: none` and the inner pill uses `pointer-events:
 * auto` — meaning only the pill itself and its close button catch clicks; everything
 * around and behind stays fully interactive.
 */

/**
 * All 100+ messages — deliberately mixed. The client picks one at random on each
 * load and rotates every 2 hours while the tab is open. No server call — the full
 * list ships inline once so we don't hit the API every rotation.
 */
internal val productivityMessages: List<ProductivityMessage> = listOf(
    // ── Islamic reminders: ayat, sunnah, duas, tasbih (≈45) ────────────────────
    ProductivityMessage("☪️", "سبحان الله وبحمده، سبحان الله العظيم"),
    ProductivityMessage("☪️", "لا إله إلا الله وحده لا شريك له، له الملك وله الحمد وهو على كل شيء قدير"),
    ProductivityMessage("☪️", "اللهم بارك لنا في أعمالنا وأرزاقنا"),
    ProductivityMessage("☪️", "رضيت بالله رباً، وبالإسلام ديناً، وبمحمد ﷺ نبياً"),
    ProductivityMessage("☪️", "أستغفر الله العظيم الذي لا إله إلا هو الحي القيوم وأتوب إليه"),
    ProductivityMessage("☪️", "اللهم صلِّ وسلِّم على نبينا محمد وعلى آله وصحبه أجمعين"),
    ProductivityMessage("☪️", "قال رسول الله ﷺ: «من قال سبحان الله وبحمده في يوم مائة مرة حُطّت خطاياه وإن كانت مثل زبد البحر»"),
    ProductivityMessage("☪️", "﴿وَمَا تَوْفِيقِي إِلَّا بِاللَّهِ﴾"),
    ProductivityMessage("☪️", "اللهم اجعل عملي خالصاً لوجهك الكريم"),
    ProductivityMessage("☪️", "﴿وَأَن لَّيْسَ لِلْإِنسَانِ إِلَّا مَا سَعَىٰ﴾"),
    ProductivityMessage("☪️", "اللهم ارزقني رزقاً حلالاً طيباً واسعاً مباركاً فيه"),
    ProductivityMessage("☪️", "قال ﷺ: «إن الله يحب إذا عمل أحدكم عملاً أن يُتقنه»"),
    ProductivityMessage("☪️", "سبحان الله والحمد لله ولا إله إلا الله والله أكبر ولا حول ولا قوة إلا بالله"),
    ProductivityMessage("☪️", "﴿إِنَّمَا يُوَفَّى الصَّابِرُونَ أَجْرَهُم بِغَيْرِ حِسَابٍ﴾"),
    ProductivityMessage("☪️", "اللهم إني أسألك علماً نافعاً ورزقاً طيباً وعملاً متقبلاً"),
    ProductivityMessage("☪️", "﴿فَإِنَّ مَعَ الْعُسْرِ يُسْرًا﴾"),
    ProductivityMessage("☪️", "لا حول ولا قوة إلا بالله العلي العظيم"),
    ProductivityMessage("☪️", "اللهم يسِّر ولا تعسِّر، وتمِّم بالخير"),
    ProductivityMessage("☪️", "قال ﷺ: «الدعاء هو العبادة»"),
    ProductivityMessage("☪️", "﴿وَبَشِّرِ الصَّابِرِينَ﴾"),
    ProductivityMessage("☪️", "اللهم اجعل هذا اليوم خيراً من أمس، وغداً خيراً من اليوم"),
    ProductivityMessage("☪️", "اللهم أعنّا على ذكرك وشكرك وحسن عبادتك"),
    ProductivityMessage("☪️", "حسبنا الله ونعم الوكيل"),
    ProductivityMessage("☪️", "﴿إِنَّ اللَّهَ مَعَ الصَّابِرِينَ﴾"),
    ProductivityMessage("☪️", "قال ﷺ: «أحب الأعمال إلى الله أدومها وإن قلّ»"),
    ProductivityMessage("☪️", "اللهم بارك لنا في أوقاتنا وأعمالنا"),
    ProductivityMessage("☪️", "﴿وَهُوَ الَّذِي يَقْبَلُ التَّوْبَةَ عَنْ عِبَادِهِ وَيَعْفُو عَنِ السَّيِّئَاتِ﴾"),
    ProductivityMessage("☪️", "اللهم اجعلنا من الذاكرين لك كثيراً، الشاكرين لك على نعمك"),
    ProductivityMessage("☪️", "﴿وَمَن يَتَّقِ اللَّهَ يَجْعَل لَّهُ مَخْرَجًا﴾"),
    ProductivityMessage("☪️", "اللهم لك الحمد كما ينبغي لجلال وجهك وعظيم سلطانك"),
    ProductivityMessage("☪️", "قال ﷺ: «من صلّى عليَّ واحدة صلّى الله عليه بها عشراً»"),
    ProductivityMessage("☪️", "اللهم اجعل خير أيامي يوم ألقاك وأنت راضٍ عنّي"),
    ProductivityMessage("☪️", "﴿وَمَن يَتَوَكَّلْ عَلَى اللَّهِ فَهُوَ حَسْبُهُ﴾"),
    ProductivityMessage("☪️", "اللهم اجعل لي في عملي بركة وفي رزقي سعة"),
    ProductivityMessage("☪️", "سبحان الله عدد خلقه ورضا نفسه وزنة عرشه ومداد كلماته"),
    ProductivityMessage("☪️", "اللهم اجعلنا من عبادك الصالحين المخلصين"),
    ProductivityMessage("☪️", "﴿أَلَا بِذِكْرِ اللَّهِ تَطْمَئِنُّ الْقُلُوبُ﴾"),
    ProductivityMessage("☪️", "اللهم إني أسألك العافية في الدنيا والآخرة"),
    ProductivityMessage("☪️", "قال ﷺ: «اليد العليا خير من اليد السفلى»"),
    ProductivityMessage("☪️", "اللهم اكفِني بحلالك عن حرامك، وأغنني بفضلك عمّن سواك"),
    ProductivityMessage("☪️", "﴿وَعَسَىٰ أَن تَكْرَهُوا شَيْئًا وَهُوَ خَيْرٌ لَّكُمْ﴾"),
    ProductivityMessage("☪️", "قال ﷺ: «تبسُّمك في وجه أخيك صدقة»"),
    ProductivityMessage("☪️", "اللهم بلّغنا رمضان وأعنّا على صيامه وقيامه"),
    ProductivityMessage("☪️", "﴿وَقُل رَّبِّ زِدْنِي عِلْمًا﴾"),
    ProductivityMessage("☪️", "قال ﷺ: «من لا يرحم لا يُرحم»"),

    // ── Productivity & sales tips (≈45) ────────────────────────────────────────
    ProductivityMessage("🎯", "ركّز على مهمة واحدة الآن. إنجاز صغير خيرٌ من ألف تسويف."),
    ProductivityMessage("⚡", "ابدأ بأصعب مهمة في اليوم — الباقي سيصبح أسهل."),
    ProductivityMessage("⏱️", "خصّص 25 دقيقة بدون تشتيت — واحدة منها خيرٌ من ساعة مع الإلهاء."),
    ProductivityMessage("📝", "اكتب قائمة مهامك اليوم، وضع عليها 3 أولويات فقط."),
    ProductivityMessage("🚀", "لا تنتظر الإلهام، ابدأ — الإلهام يأتي مع العمل."),
    ProductivityMessage("🛡️", "احفظ طاقتك لما يهم، وقل لا للمهام التي ليست من أولوياتك."),
    ProductivityMessage("⏳", "أفضل وقت للبدء كان بالأمس، وثاني أفضل وقت هو الآن."),
    ProductivityMessage("📈", "تقدم صغير كل يوم خيرٌ من قفزة كل شهر."),
    ProductivityMessage("✨", "الجودة دائماً أفضل من الكمية."),
    ProductivityMessage("☕", "خذ استراحة كل ساعتين — دماغك يحتاجها ليعمل بكفاءة."),
    ProductivityMessage("🗂️", "رتّب مكتبك، ترتيب البيئة يرتّب الأفكار."),
    ProductivityMessage("🏁", "أنجز قبل أن تكتمل — البداية أهم من الكمال."),
    ProductivityMessage("🪞", "لا تقارن نفسك بالآخرين، قارن نفسك بنفسك بالأمس."),
    ProductivityMessage("🧱", "كل نجاح كبير هو مجموعة قرارات صغيرة صحيحة."),
    ProductivityMessage("🔨", "المعلومة تُنسى، والخبرة تبقى — ابدأ بالتطبيق."),
    ProductivityMessage("💧", "اشرب ماءً وامشِ قليلاً، جسدك يساعد عقلك."),
    ProductivityMessage("🔁", "الاتساق أهم من الحماس."),
    ProductivityMessage("🏆", "سجّل إنجازات اليوم ولو صغيرة — هذه أفضل محفّز."),
    ProductivityMessage("🙂", "تعلّم قول لا بلطف، حماية وقتك احترام لنفسك."),
    ProductivityMessage("📵", "اجعل هاتفك بعيداً عن نظرك عند العمل."),
    ProductivityMessage("🎲", "خذ قراراً واحداً كبيراً اليوم، لا تتردد."),
    ProductivityMessage("🗺️", "الهدف بدون خطة مجرد أمنية."),
    ProductivityMessage("💡", "أفضل استثمار هو في نفسك وفي وقتك."),
    ProductivityMessage("🌅", "صباحك يحدد يومك — ابدأ بشيء إيجابي."),
    ProductivityMessage("🧪", "لا بأس بالمحاولة، حتى الفشل هو درس."),
    ProductivityMessage("🤝", "اجتهد وتوكل — والنتائج بيد الله."),
    ProductivityMessage("👂", "كل عميل قصة جديدة، أعطه انتباهك الكامل."),
    ProductivityMessage("😊", "ابتسامتك أول أداة مبيعات."),
    ProductivityMessage("🎧", "الاستماع الجيد أهم من الكلام الكثير."),
    ProductivityMessage("📦", "اعرف منتجك جيداً قبل أن تُقنع به أحداً."),
    ProductivityMessage("💞", "لا تغلق البيع، افتح علاقة."),
    ProductivityMessage("🔔", "متابعة العميل بعد البيع = ثقة واستمرار."),
    ProductivityMessage("✅", "قل الحقَّ دائماً، الصدق يبني السمعة."),
    ProductivityMessage("⏰", "احترم وقت العميل، هذا نصف الإقناع."),
    ProductivityMessage("💡", "أنت لا تبيع منتجاً، بل تبيع حلاً."),
    ProductivityMessage("🎯", "من يعمل بإتقان يسبق من يعمل بسرعة."),
    ProductivityMessage("📒", "خذ ملاحظات بعد كل مكالمة — الذاكرة تخون."),
    ProductivityMessage("📊", "اجعل كل يوم أفضل من سابقه بنسبة 1٪ فقط."),
    ProductivityMessage("🔥", "الطاقة الإيجابية معدية — انقلها لمن حولك."),
    ProductivityMessage("🛠️", "لا تنتظر الفرصة، اصنعها."),
    ProductivityMessage("🧭", "اسأل: هل هذه المهمة تقرّبني من هدفي؟"),
    ProductivityMessage("⏸️", "دقيقتان من التنفس العميق تعيدان تركيزك."),
    ProductivityMessage("🎯", "في كل مكالمة، حدد الهدف قبل الضغط على الاتصال."),
    ProductivityMessage("📞", "الاتصال الصباحي الأول يحدد إيقاع يومك."),
    ProductivityMessage("🧠", "أفضل الأفكار تأتي بعد راحة قصيرة، لا بعد إجهاد."),

    // ── Happiness, gratitude, motivation (≈20) ────────────────────────────────
    ProductivityMessage("🌸", "ابتسم اليوم، فقط لأنك تستطيع."),
    ProductivityMessage("💪", "التحدي هو ما يجعل الحياة ممتعة، وتجاوزه هو ما يعطيها معنى."),
    ProductivityMessage("🦁", "أنت أقوى مما تظن."),
    ProductivityMessage("🌱", "كل يوم فرصة جديدة، لا تُضيّعها."),
    ProductivityMessage("😊", "السعادة قرار، لا ظرف."),
    ProductivityMessage("🙏", "اشعر بالامتنان للأشياء الصغيرة."),
    ProductivityMessage("🌟", "أفضل نسخة منك قادمة، استمر."),
    ProductivityMessage("🌬️", "تنفّس بعمق، ذكّر نفسك أنك في طريق التقدم."),
    ProductivityMessage("🌅", "الأمل ضوء في نهاية كل نفق."),
    ProductivityMessage("🌿", "كن صبوراً، النتائج تأتي لمن ينتظر ويعمل."),
    ProductivityMessage("❤️", "أحسِن لنفسك كما تحسن للآخرين."),
    ProductivityMessage("🕊️", "الحياة أقصر من أن نحملها هموماً."),
    ProductivityMessage("🌬️", "كل نَفَس نعمة."),
    ProductivityMessage("🌊", "إن لم تستطع أن تفعل أشياء عظيمة، افعل أشياء صغيرة بطريقة عظيمة."),
    ProductivityMessage("⛰️", "القليل من التقدم يومياً يبني جبالاً من الإنجازات."),
    ProductivityMessage("🛤️", "ثق في الطريق، حتى لو لم ترَ النهاية بعد."),
    ProductivityMessage("💝", "أنت تستحق كل خير."),
    ProductivityMessage("🔑", "الصبر مفتاح الفرج."),
    ProductivityMessage("👣", "خذ يومك خطوة بخطوة."),
    ProductivityMessage("✨", "كل لحظة فيها فرصة جديدة للبدء من جديد."),
)

internal data class ProductivityMessage(val icon: String, val text: String)

/**
 * Serialize the messages pool to a JSON array that can be embedded directly into
 * the page script. Using kotlinx.serialization handles escaping quotes/backslashes
 * safely even for the Arabic strings.
 */
private fun messagesJsonArray(): String {
    val arr = JsonArray(productivityMessages.flatMap { m ->
        listOf(JsonArray(listOf(JsonPrimitive(m.icon), JsonPrimitive(m.text))))
    })
    return arr.toString()
}

/**
 * Floating productivity banner. Wrapper is `pointer-events: none` so background
 * clicks pass through to the UI; only the inner pill (and its close button) are
 * clickable. On mobile the banner sits below the fixed mobile header (top-16);
 * on desktop it's at the very top (top-3). Rotation interval is 2 hours. Dismissal
 * persists for 2 hours in `localStorage` so it doesn't immediately reappear.
 */
internal fun productivityBannerHtml(): String {
    val messagesJson = messagesJsonArray()
    return """
    <!-- Productivity + reminders banner: floating, non-blocking, dismissible -->
    <div id="prodBanner" class="fixed top-16 md:top-3 left-0 right-0 z-30 flex justify-center pointer-events-none px-3" style="display:none">
        <div class="pointer-events-auto flex items-center gap-2 bg-gradient-to-l from-emerald-600 via-teal-600 to-cyan-600 text-white px-4 py-2 rounded-full shadow-lg backdrop-blur max-w-[min(92vw,44rem)]"
             style="animation: prodBannerFade .4s ease-out">
            <span id="prodBannerIcon" class="flex-shrink-0 text-base">✨</span>
            <span id="prodBannerText" class="text-xs md:text-sm leading-snug"></span>
            <button onclick="closeProdBanner()"
                    class="flex-shrink-0 ml-1 text-white/75 hover:text-white text-base leading-none px-1"
                    title="إغلاق" aria-label="إغلاق">✕</button>
        </div>
    </div>
    <style>
        @keyframes prodBannerFade {
            from { opacity: 0; transform: translateY(-10px); }
            to { opacity: 1; transform: translateY(0); }
        }
    </style>
    <script>
    (function() {
        // Inlined once per page so rotation is instant and doesn't hit the server.
        var messages = $messagesJson;
        var TWO_HOURS_MS = 2 * 60 * 60 * 1000;
        var STORAGE_KEY = 'prodBannerDismissedUntil';
        var rotationTimer = null;

        function pick() {
            return messages[Math.floor(Math.random() * messages.length)];
        }

        function show() {
            var dismissedUntil = Number(localStorage.getItem(STORAGE_KEY) || 0);
            if (Date.now() < dismissedUntil) return;
            var banner = document.getElementById('prodBanner');
            var textEl = document.getElementById('prodBannerText');
            var iconEl = document.getElementById('prodBannerIcon');
            if (!banner || !textEl || !iconEl) return;
            var m = pick();
            iconEl.textContent = m[0];
            textEl.textContent = m[1];
            banner.style.display = 'flex';
            // Re-trigger the fade animation on rotation.
            var pill = banner.firstElementChild;
            if (pill) { pill.style.animation = 'none'; void pill.offsetWidth; pill.style.animation = ''; }
        }

        window.closeProdBanner = function() {
            var banner = document.getElementById('prodBanner');
            if (banner) banner.style.display = 'none';
            // Stay hidden for 2 hours — same interval as the rotation cadence so the
            // banner reappears with a fresh message after the cool-down, not sooner.
            localStorage.setItem(STORAGE_KEY, String(Date.now() + TWO_HOURS_MS));
        };

        // Initial paint after DOM is ready (layout's script runs at the end of body,
        // but defensively wait in case this is reordered later).
        function init() {
            show();
            if (rotationTimer) clearInterval(rotationTimer);
            rotationTimer = setInterval(show, TWO_HOURS_MS);
        }
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', init);
        } else {
            init();
        }
    })();
    </script>
    """.trimIndent()
}
