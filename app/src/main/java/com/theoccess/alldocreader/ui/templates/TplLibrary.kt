package com.theoccess.alldocreader.ui.templates

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Built-in templates (all original designs and sample text) in four groups, like the original
 * app's "Select a template": Resume, Letter, Briefing and Poster.
 */
object TplLibrary {

    private const val INK = 0xFF1B1F2A.toInt()
    private const val GREY = 0xFF6B7380.toInt()
    private const val LIGHT = 0xFF9AA1AC.toInt()
    private const val BLUE = 0xFF1E6FE6.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()

    /** Small builder so the templates below read like a layout. */
    private class B(val bg: Int = WHITE) {
        val els = ArrayList<TEl>()
        fun rect(x: Float, y: Float, w: Float, h: Float, color: Int, color2: Int? = null, radius: Float = 0f, horizontal: Boolean = false) {
            els += TShape(TShape.Kind.RECT, x, y, w, h, color, color2, horizontal, radius)
        }
        fun oval(x: Float, y: Float, w: Float, h: Float, color: Int, color2: Int? = null) {
            els += TShape(TShape.Kind.OVAL, x, y, w, h, color, color2)
        }
        fun line(x: Float, y: Float, w: Float, color: Int = 0xFFE3E7ED.toInt(), thick: Float = 0.8f) {
            els += TShape(TShape.Kind.LINE, x, y, w, thick, color)
        }
        fun t(
            text: String, x: Float, y: Float, w: Float, size: Float, color: Int = INK, bold: Boolean = false,
            font: String = TplFonts.DEFAULT, align: Int = 0, list: Int = 0, italic: Boolean = false, line: Float = 1.25f
        ) {
            els += TText(text, x, y, w, size, color, font, bold, italic, list = list, align = align, lineMult = line)
        }
        fun img(res: String, x: Float, y: Float, w: Float, h: Float, circle: Boolean = false) {
            els += TImage(res, null, x, y, w, h, circle = circle)
        }
        fun page() = TPage(bg, els)
    }

    private fun today() = SimpleDateFormat("MMMM d, yyyy", Locale.US).format(Date())

    private const val SUMMARY = "Experienced in leading end-to-end product development across mobile and SaaS platforms. " +
        "Skilled in user research, data-driven decision making, and cross-functional collaboration, " +
        "with a strong focus on driving measurable business impact."
    private const val JOB1 = "Spearheaded the launch of a new mobile productivity app across the APAC region, achieving 1.2 million " +
        "downloads in the first 6 months and a user retention rate of 38% at Day 30.\nDefined the product roadmap and prioritized " +
        "the backlog in collaboration with engineering and UX teams, reducing feature time-to-market by 25%."
    private const val JOB2 = "Managed a SaaS product for SME customers in Southeast Asia. Launched five major features within 18 months, " +
        "growing ARR from US$1.8M to US$4.2M.\nSupported the product team for a consumer-facing e-commerce platform, managing feature " +
        "definition and user-story creation."

    private fun contact(b: B, x: Float, y: Float, color: Int = GREY) {
        val rows = listOf("tpl_ic_pin" to "Singapore", "tpl_ic_mail" to "riley.morgan@email.com", "tpl_ic_phone" to "+65 9123-4567", "tpl_ic_web" to "www.nexora-tech.com")
        rows.forEachIndexed { i, (ic, s) ->
            b.img(ic, x, y + i * 17f, 10f, 10f)
            b.t(s, x + 16f, y + i * 17f, 150f, 8.5f, color)
        }
    }

    // ------------------------------------------------------------------ resumes

    private fun resumeClassic() = B().apply {
        t("Product Manager", 40f, 42f, 300f, 8f, GREY)
        t("Riley K. Morgan", 40f, 54f, 360f, 26f, INK, bold = true)
        line(0f, 100f, PAGE_W, 0xFFE9ECF1.toInt(), 1f)
        t("Summary", 40f, 120f, 200f, 10f, INK, bold = true)
        t(SUMMARY, 40f, 138f, 330f, 8.5f, GREY, line = 1.45f)
        t("Contact", 400f, 120f, 150f, 10f, INK, bold = true)
        contact(this, 400f, 140f)
        t("Education", 40f, 222f, 200f, 10f, INK, bold = true)
        t("2013 – 2018", 40f, 242f, 80f, 8f, LIGHT)
        t("University of Birmingham", 130f, 242f, 300f, 8.5f, INK, bold = true)
        t("Master of Business Administration (MBA), Strategy\nBachelor of Science in Business Administration", 130f, 256f, 400f, 8f, GREY, line = 1.4f)
        t("Experience", 40f, 300f, 200f, 10f, INK, bold = true)
        t("2023 – Present", 40f, 320f, 80f, 8f, LIGHT)
        t("Senior Product Manager", 130f, 320f, 300f, 8.5f, INK, bold = true)
        t(JOB1, 130f, 334f, 420f, 8f, GREY, line = 1.45f)
        t("2021 – 2022", 40f, 420f, 80f, 8f, LIGHT)
        t("Product Manager", 130f, 420f, 300f, 8.5f, INK, bold = true)
        t(JOB2, 130f, 434f, 420f, 8f, GREY, line = 1.45f)
        rect(40f, 530f, 515f, 62f, 0xFFEEF3FB.toInt())
        rect(40f, 530f, 3f, 62f, BLUE)
        t("SOCIAL SKILLS", 56f, 542f, 200f, 7.5f, BLUE, bold = true)
        t("Product Strategy & Roadmapping\nA/B Testing & Experimentation", 56f, 558f, 220f, 8f, GREY, list = 1)
        t("Go-to-Market (GTM) Strategy\nAgile & Scrum Methodology", 300f, 558f, 220f, 8f, GREY, list = 1)
    }.page()

    private fun resumeGradient() = B().apply {
        rect(0f, 0f, PAGE_W, 170f, 0xFFF3E8FF.toInt(), 0xFFFFFFFF.toInt())
        img("tpl_avatar_1", 262f, 30f, 70f, 70f, circle = true)
        t("Riley K. Morgan", 60f, 108f, 475f, 20f, INK, bold = true, align = 1)
        t("Product Manager  ·  Singapore  ·  riley.morgan@email.com", 60f, 136f, 475f, 8.5f, GREY, align = 1)
        t("Summary", 50f, 190f, 100f, 10f, INK, bold = true)
        t(SUMMARY, 150f, 190f, 400f, 8.5f, GREY, line = 1.45f)
        line(50f, 250f, 495f)
        t("Experience", 50f, 266f, 100f, 10f, INK, bold = true)
        t("Senior Product Manager · 2023 – Present", 150f, 266f, 400f, 9f, INK, bold = true)
        t(JOB1, 150f, 282f, 400f, 8f, GREY, line = 1.45f)
        t("Product Manager · 2021 – 2022", 150f, 366f, 400f, 9f, INK, bold = true)
        t(JOB2, 150f, 382f, 400f, 8f, GREY, line = 1.45f)
        line(50f, 470f, 495f)
        t("Education", 50f, 486f, 100f, 10f, INK, bold = true)
        t("University of Birmingham, 2013 – 2018\nMaster of Business Administration (MBA), Strategy", 150f, 486f, 400f, 8.5f, GREY, line = 1.45f)
        line(50f, 540f, 495f)
        t("Skills", 50f, 556f, 100f, 10f, INK, bold = true)
        t("Roadmapping   ·   GTM Strategy   ·   UX Collaboration   ·   Data Analysis", 150f, 556f, 400f, 8.5f, GREY)
    }.page()

    private fun resumeDark() = B(0xFF15161A.toInt()).apply {
        rect(0f, 0f, 200f, PAGE_H, 0xFF1F2127.toInt())
        rect(200f, 520f, 395f, 322f, 0xFF15161A.toInt(), 0xFF6B4A12.toInt())
        t("CONTACTS", 26f, 40f, 150f, 8f, 0xFFB9BDC6.toInt(), bold = true)
        contact(this, 26f, 60f, 0xFFB9BDC6.toInt())
        t("SKILLS", 26f, 150f, 150f, 8f, 0xFFB9BDC6.toInt(), bold = true)
        t("Roadmapping\nGTM Strategy\nUser Research\nData Analysis\nAgile / Scrum", 26f, 168f, 150f, 8.5f, 0xFFDDE0E6.toInt(), list = 1, line = 1.5f)
        t("EDUCATION", 26f, 280f, 150f, 8f, 0xFFB9BDC6.toInt(), bold = true)
        t("University of Birmingham\nMBA, Strategy\n2013 – 2018", 26f, 298f, 160f, 8.5f, 0xFFDDE0E6.toInt(), line = 1.5f)
        t("Riley K. Morgan", 226f, 40f, 340f, 24f, WHITE, bold = true)
        t("Product Manager", 226f, 72f, 340f, 12f, 0xFFF2B233.toInt())
        t("SUMMARY", 226f, 110f, 340f, 8f, 0xFFB9BDC6.toInt(), bold = true)
        t(SUMMARY, 226f, 126f, 340f, 8.5f, 0xFFDDE0E6.toInt(), line = 1.45f)
        t("EXPERIENCE", 226f, 196f, 340f, 8f, 0xFFB9BDC6.toInt(), bold = true)
        t("Senior Product Manager · 2023 – Present", 226f, 214f, 340f, 9f, WHITE, bold = true)
        t(JOB1, 226f, 230f, 340f, 8f, 0xFFDDE0E6.toInt(), line = 1.45f)
        t("Product Manager · 2021 – 2022", 226f, 330f, 340f, 9f, WHITE, bold = true)
        t(JOB2, 226f, 346f, 340f, 8f, 0xFFDDE0E6.toInt(), line = 1.45f)
    }.page()

    private fun resumeBlue() = B().apply {
        rect(0f, 0f, 190f, PAGE_H, 0xFF3D7BEA.toInt())
        t("Riley K. Morgan", 20f, 36f, 160f, 16f, WHITE, bold = true)
        t("Product Manager", 20f, 58f, 160f, 9f, 0xFFDCE7FF.toInt())
        img("tpl_avatar_2", 35f, 90f, 120f, 120f, circle = true)
        t("Summary", 20f, 236f, 160f, 10f, WHITE, bold = true)
        t(SUMMARY, 20f, 254f, 155f, 8f, 0xFFEAF1FF.toInt(), line = 1.45f)
        t("Contact Me", 214f, 40f, 300f, 12f, INK, bold = true)
        contact(this, 214f, 64f)
        t("Experience", 214f, 150f, 300f, 12f, INK, bold = true)
        t("Senior Product Manager", 214f, 172f, 340f, 9.5f, INK, bold = true)
        t("2023 – Present · Singapore", 214f, 186f, 340f, 8f, LIGHT)
        t(JOB1, 214f, 200f, 350f, 8f, GREY, line = 1.45f)
        t("Product Manager", 214f, 290f, 340f, 9.5f, INK, bold = true)
        t("2021 – 2022 · Singapore", 214f, 304f, 340f, 8f, LIGHT)
        t(JOB2, 214f, 318f, 350f, 8f, GREY, line = 1.45f)
        t("Education", 214f, 410f, 300f, 12f, INK, bold = true)
        t("University of Birmingham · 2013 – 2018\nMaster of Business Administration (MBA), Strategy", 214f, 432f, 350f, 8.5f, GREY, line = 1.45f)
    }.page()

    // ------------------------------------------------------------------ letters

    private const val LETTER_BODY = "Dear Mr. Carter,\n\nThank you for taking the time to meet with our team last week. We truly enjoyed learning " +
        "more about your goals for the coming year and how Brandex can support them.\n\nAs discussed, we have prepared a proposal " +
        "that covers the project scope, timeline and budget. We believe this plan will help you reach your targets while keeping " +
        "costs under control. Please review the attached documents and let us know if you have any questions.\n\nWe look forward " +
        "to working with you.\n\nKind regards,"

    private fun letterCompany() = B().apply {
        img("tpl_logo", 50f, 44f, 22f, 22f)
        t("brandex", 78f, 46f, 200f, 15f, BLUE, bold = true)
        t(today(), 380f, 50f, 165f, 8.5f, GREY, align = 2)
        line(50f, 86f, 495f)
        t("Mr. Daniel Carter\nBrightMove Interactive\n12 Harbour Road, Singapore", 50f, 104f, 300f, 9f, INK, line = 1.4f)
        t(LETTER_BODY, 50f, 170f, 495f, 9.5f, INK, line = 1.5f)
        t("Morgan", 50f, 470f, 200f, 22f, INK, font = "Dancing Script")
        t("Riley K. Morgan\nProject Director, Brandex", 50f, 502f, 250f, 9f, GREY, line = 1.4f)
        line(50f, 780f, 495f)
        t("brandex · 88 Market Street, Singapore · hello@brandex.com", 50f, 790f, 495f, 7.5f, LIGHT, align = 1)
    }.page()

    private fun letterFax() = B().apply {
        t("FAX", 50f, 50f, 200f, 36f, INK, bold = true)
        t("BrightMove Interactive", 350f, 60f, 195f, 9f, GREY, align = 2)
        val rows = listOf("To:" to "Daniel Carter", "Phone:" to "+65 6123-4567", "Fax:" to "+65 6123-4568", "From:" to "Riley K. Morgan", "Pages:" to "3", "Date:" to today())
        rows.forEachIndexed { i, (k, v) ->
            t(k, 50f, 120f + i * 20f, 80f, 9f, GREY, bold = true)
            t(v, 130f, 120f + i * 20f, 300f, 9f, INK)
            line(130f, 133f + i * 20f, 300f)
        }
        t("Urgent   ☐     For Review   ☐     Please Reply   ☐", 50f, 260f, 495f, 9f, INK)
        t("Comments", 50f, 300f, 200f, 11f, INK, bold = true)
        t(LETTER_BODY.replace("Dear Mr. Carter,", "Hello Daniel,"), 50f, 322f, 495f, 9f, GREY, line = 1.5f)
    }.page()

    private fun letterHandwritten() = B(0xFFFBF6EE.toInt()).apply {
        img("tpl_art_flowers", 0f, 647f, 300f, 195f)
        t("Dear Friend,", 60f, 70f, 470f, 22f, 0xFF5A4636.toInt(), font = "Dancing Script")
        t("It has been a while since I last wrote, and I have been thinking about the day we walked together at the park — " +
            "the sky was so clear.\n\nI remember you mentioned you were preparing for a new adventure, and I imagine you are " +
            "busy with it now. I hope everything is going well and that you are taking good care of yourself.\n\nWhen you are " +
            "back in town, let's meet for coffee and catch up on everything.",
            60f, 120f, 470f, 15f, 0xFF5A4636.toInt(), font = "Dancing Script", line = 1.35f)
        t("With love,\nMy", 330f, 560f, 200f, 16f, 0xFF5A4636.toInt(), font = "Dancing Script", align = 2)
    }.page()

    private fun letterProposal() = B().apply {
        oval(-80f, 700f, 760f, 260f, 0xFF1E6FE6.toInt(), 0xFF0F3E9C.toInt())
        oval(-60f, 680f, 720f, 200f, 0xFFDDE9FB.toInt())
        oval(-40f, 700f, 680f, 180f, WHITE)
        img("tpl_logo", 50f, 44f, 22f, 22f)
        t("BrightMove Interactive", 78f, 47f, 250f, 11f, BLUE, bold = true)
        t("BUSINESS PROPOSAL", 50f, 110f, 495f, 22f, BLUE, bold = true)
        t(today(), 50f, 146f, 300f, 9f, GREY)
        t("Prepared for: Daniel Carter, Brandex", 50f, 162f, 400f, 9f, GREY)
        t("Overview", 50f, 200f, 300f, 12f, INK, bold = true)
        t("BrightMove Interactive proposes to redesign the Brandex mobile experience to increase engagement and online sales. " +
            "The project will be delivered in three phases over twelve weeks.", 50f, 220f, 495f, 9.5f, GREY, line = 1.5f)
        t("Scope of work", 50f, 290f, 300f, 12f, INK, bold = true)
        t("User research and analytics review\nNew design system and prototypes\nApp development and testing\nLaunch support and reporting",
            50f, 310f, 495f, 9.5f, GREY, list = 2, line = 1.6f)
        t("Investment", 50f, 400f, 300f, 12f, INK, bold = true)
        t("Total project fee: US$48,000, payable in three instalments at the start of each phase.", 50f, 420f, 495f, 9.5f, GREY, line = 1.5f)
        t("Morgan", 50f, 480f, 200f, 20f, INK, font = "Dancing Script")
        t("Riley K. Morgan, Project Director", 50f, 510f, 300f, 9f, GREY)
    }.page()

    // ------------------------------------------------------------------ briefings

    private fun briefingMinutes() = B(0xFFFBF8F1.toInt()).apply {
        t("Meeting\nMinutes\nBriefing", 40f, 40f, 200f, 24f, INK, bold = true, line = 1.0f)
        t("Meeting Time: Monday, 10:00 AM\nMeeting Venue: Room 4B\nRecorder: Riley Morgan", 300f, 48f, 250f, 9f, GREY, line = 1.5f)
        line(40f, 130f, 515f, INK, 1.2f)
        t("1  Meeting Agenda and Discussion Content", 40f, 146f, 515f, 11f, INK, bold = true)
        t("Review of last quarter's results and customer feedback\nPlanning of the new product release\nBudget and resources for Q3",
            40f, 168f, 515f, 9.5f, GREY, list = 1, line = 1.5f)
        t("2  Meeting Resolutions", 40f, 240f, 515f, 11f, INK, bold = true)
        val colors = listOf(0xFFE8453C.toInt(), 0xFF3C8BE8.toInt(), 0xFF1FB35A.toInt(), 0xFFF2B233.toInt())
        for (i in 0 until 4) {
            val x = 40f + (i % 2) * 262f
            val y = 266f + (i / 2) * 96f
            rect(x, y, 253f, 86f, WHITE, radius = 4f)
            rect(x, y, 4f, 86f, colors[i])
            t("Resolution ${i + 1}", x + 14f, y + 10f, 230f, 9.5f, INK, bold = true)
            t("Owner: Team ${'A' + i}\nDeadline: within two weeks\nStatus: in progress", x + 14f, y + 28f, 230f, 8.5f, GREY, line = 1.4f)
        }
        t("3  Next Steps", 40f, 470f, 515f, 11f, INK, bold = true)
        t("Share the release plan with all teams by Friday. The next briefing will review progress on each resolution.",
            40f, 492f, 515f, 9.5f, GREY, line = 1.5f)
    }.page()

    private fun briefingBlueprint() = B().apply {
        rect(40f, 36f, 515f, 4f, 0xFF1FB35A.toInt())
        t("Strategic Work\nArrangement Blueprint", 40f, 56f, 400f, 24f, INK, bold = true, line = 1.05f)
        val steps = listOf(
            "TASK 1" to "Goal & role assignment — define the quarter's goals and who owns each one.",
            "TASK 2" to "Resource preparation — confirm budget, tools and people for every goal.",
            "TASK 3" to "Customer development — interview customers and collect feedback.",
            "TASK 4" to "Product optimisation — turn feedback into improvements and release them."
        )
        steps.forEachIndexed { i, (k, v) ->
            val y = 140f + i * 110f
            t("${i + 1}", 40f, y, 50f, 48f, 0xFFD6DBE3.toInt(), bold = true)
            t(k, 110f, y + 6f, 120f, 9f, 0xFF1FB35A.toInt(), bold = true)
            t(v, 110f, y + 22f, 440f, 10f, INK, line = 1.45f)
            line(110f, y + 90f, 445f)
        }
        t("Work Objectives", 380f, 600f, 175f, 10f, 0xFF1FB35A.toInt(), bold = true, align = 2)
    }.page()

    private fun briefingWeekly() = B().apply {
        rect(40f, 40f, 110f, 18f, 0xFFF2B233.toInt(), radius = 9f)
        t("NOVEMBER 11TH", 40f, 44f, 110f, 7.5f, INK, bold = true, align = 1)
        t("Academic Weekly\nTask Schedule", 40f, 70f, 400f, 26f, INK, bold = true, line = 1.05f)
        val parts = listOf("WORK" to "Finish the project report and send it for review.", "STUDY" to "Read chapters 4–6 and prepare notes for the seminar.",
            "LIFE" to "Plan meals for the week and book a dentist appointment.", "PERSONAL DEVELOPMENT" to "Practise public speaking for 20 minutes every day.")
        parts.forEachIndexed { i, (k, v) ->
            val y = 170f + i * 100f
            t("(${i + 1})  $k", 40f, y, 400f, 11f, INK, bold = true)
            t(v, 40f, y + 20f, 400f, 9.5f, GREY, line = 1.45f)
            rect(470f, y + 14f, 80f, 20f, 0xFFFFF1CC.toInt(), radius = 10f)
            t(listOf("2-4 hours", "1.5 hours", "1 hour", "20 min")[i], 470f, y + 19f, 80f, 8f, 0xFF8A6100.toInt(), bold = true, align = 1)
            line(40f, y + 72f, 515f)
        }
    }.page()

    // ------------------------------------------------------------------ posters

    private fun posterGrads() = B(0xFFFBF1E3.toInt()).apply {
        t("Grads", 60f, 70f, 475f, 64f, NAVY, bold = true, align = 1)
        t("Party", 60f, 140f, 475f, 64f, NAVY, bold = true, align = 1)
        t("Great", 150f, 205f, 300f, 34f, 0xFFF26B1D.toInt(), font = "Dancing Script", align = 1)
        img("tpl_art_cap", 147f, 260f, 300f, 300f)
        t("Class of 2026", 60f, 580f, 475f, 14f, NAVY, align = 1)
        rect(60f, 640f, 475f, 1.5f, NAVY)
        t("NUS Kent Ridge\n36 Heng Mui Keng Terrace, Singapore", 60f, 660f, 300f, 10f, NAVY, line = 1.4f)
        t("06/NOV/26", 380f, 664f, 155f, 16f, 0xFFF26B1D.toInt(), bold = true, align = 2)
    }.page()

    private fun posterAgency() = B().apply {
        rect(0f, 0f, PAGE_W, 330f, 0xFFF7C23C.toInt())
        img("tpl_art_megaphone", 330f, 120f, 240f, 190f)
        t("DIGITAL", 40f, 40f, 300f, 10f, INK, bold = true)
        t("MARKETING\nAGENCY", 40f, 60f, 400f, 48f, INK, bold = true, line = 1.0f)
        t("ABOUT US", 40f, 360f, 250f, 11f, INK, bold = true)
        t("We help brands grow online with strategy, content and campaigns that bring real results.", 40f, 380f, 250f, 9.5f, GREY, line = 1.5f)
        t("WHY CHOOSE US?", 40f, 450f, 250f, 11f, INK, bold = true)
        t("10+ years of experience\n200+ happy clients\nClear monthly reporting", 40f, 470f, 250f, 9.5f, GREY, list = 1, line = 1.6f)
        t("OUR SERVICES", 320f, 360f, 235f, 11f, INK, bold = true)
        t("Strategic Consulting\nCreative Campaigns\nAnalytics & Optimization\nSocial Media Management", 320f, 380f, 235f, 9.5f, GREY, list = 1, line = 1.7f)
        rect(0f, 770f, PAGE_W, 72f, INK)
        t("YOURWEBSITE.COM", 40f, 798f, 515f, 12f, WHITE, bold = true, align = 1)
    }.page()

    private fun posterWebinar() = B(0xFFFBEBDD.toInt()).apply {
        img("tpl_art_sun", 360f, 20f, 220f, 220f)
        rect(40f, 50f, 90f, 20f, 0xFF1FB35A.toInt(), radius = 10f)
        t("Free Webinar", 40f, 55f, 90f, 8.5f, WHITE, bold = true, align = 1)
        t("EXCELLING\nIN ONLINE\nMARKETING", 40f, 90f, 360f, 44f, NAVY, bold = true, line = 1.0f)
        t("Get expert tips on creating effective campaigns, finding your audience and growing your brand.", 40f, 260f, 330f, 11f, GREY, line = 1.5f)
        t("Host:", 40f, 330f, 60f, 10f, GREY)
        rect(40f, 348f, 110f, 22f, 0xFF3C3CE8.toInt())
        t("ALEX REED", 40f, 354f, 110f, 9.5f, WHITE, bold = true, align = 1)
        rect(0f, 520f, PAGE_W, 322f, 0xFF2A2FD6.toInt())
        t("25", 40f, 570f, 120f, 70f, WHITE, bold = true)
        t("AUG\nThursday\n3:00 PM – 4:30 PM", 150f, 580f, 200f, 12f, WHITE, line = 1.5f)
        oval(400f, 600f, 150f, 150f, 0xFFF26B1D.toInt())
        t("Reserve\nyour spot\ntoday!", 400f, 640f, 150f, 14f, WHITE, bold = true, align = 1)
    }.page()

    private fun posterBlackFriday() = B(0xFFD9262E.toInt()).apply {
        img("tpl_art_bags", 150f, 60f, 300f, 270f)
        t("BLACK\nFRIDAY", 40f, 360f, 515f, 70f, WHITE, bold = true, italic = true, align = 1, line = 0.95f)
        rect(170f, 530f, 255f, 60f, INK, radius = 8f)
        t("50% OFF", 170f, 540f, 255f, 34f, 0xFFFFD447.toInt(), bold = true, align = 1)
        t("Black Friday deals up to 50% off on all items.\nThursday – Sunday only.", 40f, 630f, 515f, 12f, WHITE, align = 1, line = 1.5f)
    }.page()

    private fun posterChristmas() = B(0xFFF7EEDD.toInt()).apply {
        img("tpl_art_tree", 172f, 80f, 250f, 300f)
        t("Merry", 40f, 420f, 515f, 54f, 0xFFB3262B.toInt(), font = "Dancing Script", align = 1)
        t("Christmas", 40f, 486f, 515f, 54f, 0xFF1F7A45.toInt(), font = "Dancing Script", align = 1)
        t("May your holidays be filled with joy, warmth and laughter.\nWishing you a happy new year!", 60f, 590f, 475f, 12f, GREY, align = 1, line = 1.5f)
    }.page()

    private const val NAVY = 0xFF24407A.toInt()

    val ALL: List<Template> = listOf(
        Template("resume_classic", Category.RESUME, "Resume", ::resumeClassic),
        Template("resume_gradient", Category.RESUME, "Resume", ::resumeGradient),
        Template("resume_dark", Category.RESUME, "Resume", ::resumeDark),
        Template("resume_blue", Category.RESUME, "Resume", ::resumeBlue),
        Template("letter_company", Category.LETTER, "Letter", ::letterCompany),
        Template("letter_handwritten", Category.LETTER, "Letter", ::letterHandwritten),
        Template("letter_fax", Category.LETTER, "Fax", ::letterFax),
        Template("letter_proposal", Category.LETTER, "Proposal", ::letterProposal),
        Template("briefing_minutes", Category.BRIEFING, "Briefing", ::briefingMinutes),
        Template("briefing_blueprint", Category.BRIEFING, "Briefing", ::briefingBlueprint),
        Template("briefing_weekly", Category.BRIEFING, "Schedule", ::briefingWeekly),
        Template("poster_grads", Category.POSTER, "Poster", ::posterGrads),
        Template("poster_agency", Category.POSTER, "Poster", ::posterAgency),
        Template("poster_webinar", Category.POSTER, "Poster", ::posterWebinar),
        Template("poster_poster_bf", Category.POSTER, "Poster", ::posterBlackFriday),
        Template("poster_christmas", Category.POSTER, "Poster", ::posterChristmas)
    )

    fun byId(id: String) = ALL.firstOrNull { it.id == id }
}
