package com.sohum.bandlog.util

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v2.18 social stream (Areas D + E): the same numbers as the web's scripts/check-v218-social.ts,
 * group by group (freezes, referrals, stamps + reactions, live, pledges, seasonal, packs, leagues,
 * coach view, Wrapped, language + regional foods, offline queue, export + safety).
 */
class V218SocialTest {
    private fun week(ws: String) = (0 until 7).map { Dates.addDays(ws, it.toLong()) }
    private fun ms(iso: String) = java.time.Instant.parse(iso).toEpochMilli()

    // ---------------------------------------------------------------- D5 freezes
    @Test fun freezes() {
        val t = "2026-09-30" // a Wednesday
        assertEquals(listOf("2026-09-21"), Freezes.perfectWeeks(week("2026-09-21"), t))
        assertEquals(listOf("2026-09-21", "2026-09-14"), Freezes.perfectWeeks(week("2026-09-21") + week("2026-09-14"), t))
        assertEquals(emptyList<String>(), Freezes.perfectWeeks(week("2026-09-21").drop(1), t))
        assertEquals(emptyList<String>(), Freezes.perfectWeeks(week("2026-09-28"), t))
        assertEquals(3, Freezes.clampTokens(7))
        assertEquals(0, Freezes.clampTokens(-1))
        assertEquals(0, Freezes.clampTokens("x"))

        val act = listOf("2026-09-25", "2026-09-26", "2026-09-27", "2026-09-28")
        assertEquals(listOf("2026-09-29"), Freezes.planFreezeUse(act, emptyList(), 1, t, "2026-09-01"))
        assertEquals(emptyList<String>(), Freezes.planFreezeUse(act + "2026-09-29", emptyList(), 1, t, "2026-09-01"))
        assertEquals(emptyList<String>(), Freezes.planFreezeUse(act.take(3), emptyList(), 1, t, "2026-09-01"))
        assertEquals(listOf("2026-09-29", "2026-09-28"), Freezes.planFreezeUse(act.take(3), emptyList(), 2, t, "2026-09-01"))
        assertEquals(emptyList<String>(), Freezes.planFreezeUse(listOf("2026-09-24"), emptyList(), 3, t, "2026-09-01"))
        assertEquals(listOf("2026-09-29"), Freezes.planFreezeUse(listOf("2026-09-27"), listOf("2026-09-28"), 1, t, "2026-09-01"))
        assertEquals(emptyList<String>(), Freezes.planFreezeUse(emptyList(), emptyList(), 3, t, null))
        assertEquals(listOf("2026-09-29"), Freezes.planFreezeUse(listOf("2026-09-28"), emptyList(), 3, t, "2026-09-28"))
        assertEquals(emptyList<String>(), Freezes.planFreezeUse(emptyList(), emptyList(), 3, t, "2026-09-29"))
        val lists = Freezes.withFrozen(listOf(act), listOf("2026-09-29"))
        assertEquals(2, lists.size)

        val all = week("2026-09-21") + "2026-09-28"
        val r = Freezes.simulateSync(Freezes.SyncInput(0, emptyList(), emptyList(), all, "2026-09-21"), t)
        assertEquals(listOf("2026-09-21"), r.earnedNow)
        assertEquals(listOf("2026-09-29"), r.usedNow)
        assertEquals(0, r.tokens)
        val r2 = Freezes.simulateSync(Freezes.SyncInput(3, emptyList(), emptyList(), week("2026-09-21") + "2026-09-29", "2026-09-21"), t)
        assertEquals(Freezes.MAX_FREEZES, r2.tokens)
        assertEquals(emptyList<String>(), r2.earnedNow)
        assertEquals(listOf("2026-09-21"), r2.earnedWeeks)

        val now = ms("2026-09-30T10:00:00Z")
        assertNull(Freezes.canGift(1, 0, null, now))
        assertEquals(Freezes.GiftBlock.NONE, Freezes.canGift(0, 0, null, now))
        assertEquals(Freezes.GiftBlock.FULL, Freezes.canGift(2, 3, null, now))
        assertEquals(Freezes.GiftBlock.COOLDOWN, Freezes.canGift(2, 1, "2026-09-26T10:00:00Z", now))
        assertNull(Freezes.canGift(2, 1, "2026-09-22T10:00:00Z", now))
        assertEquals(Freezes.GiftBlock.SELF, Freezes.canGift(2, 1, null, now, self = true))
        assertEquals("No freezes", Freezes.freezeCountText(0))
        assertEquals("1 freeze", Freezes.freezeCountText(1))
        assertEquals("3 freezes", Freezes.freezeCountText(3))
        assertEquals("You're at the max of 3. Use one, then earn it back.", Freezes.earnHint(3, emptyList(), t))
        assertEquals("Log every day to Sunday (5 to go) to earn one.", Freezes.earnHint(0, listOf("2026-09-28", "2026-09-29"), t))
        assertEquals("Log every day to Sunday (4 to go) to earn one.", Freezes.earnHint(0, listOf("2026-09-28", "2026-09-29", t), t))
        assertEquals("Log all 7 days of a week (Mon to Sun) to earn one.", Freezes.earnHint(0, listOf("2026-09-29"), t))
        val ev = Freezes.parseFreezeEvents(
            listOf(
                Freezes.Event("use", "2026-09-20", null, null),
                Freezes.Event("use", "2026-09-29", null, null),
                Freezes.Event("earn", "2026-09-14", null, null),
                Freezes.Event("gift_out", "x:1", "u2", "2026-09-10T00:00:00Z"),
                Freezes.Event("gift_out", "x:2", "u2", "2026-09-25T00:00:00Z"),
                Freezes.Event("use", "garbage", null, null),
            ),
        )
        assertEquals(listOf("2026-09-29", "2026-09-20"), ev.usedDays)
        assertEquals(listOf("2026-09-14"), ev.earnedWeeks)
        assertEquals("2026-09-25T00:00:00Z", ev.lastGiftTo["u2"])
    }

    // ---------------------------------------------------------------- D2 referrals
    @Test fun referrals() {
        assertEquals("ABC23X", Referrals.normalizeCode("abc23x"))
        assertNull(Referrals.normalizeCode("ABC10X"))
        assertNull(Referrals.normalizeCode("ABCDE"))
        assertNull(Referrals.normalizeCode(42))
        assertEquals("ABC23X", Referrals.normalizeCode(" ab-c 23x "))
        assertEquals("https://x.app/r/ABC23X", Referrals.inviteUrl("https://x.app/", "ABC23X"))
        val now = ms("2026-09-30T00:00:00Z")
        assertEquals(Referrals.Grant("banked", "beta", null, 7), Referrals.referralGrant("beta", null, now))
        assertEquals(Referrals.Grant("banked", "beta", null, 14), Referrals.referralGrant("beta", null, now, 7))
        assertEquals("pro", Referrals.referralGrant("free", null, now).plan)
        assertEquals("2026-10-07T00:00:00.000Z", Referrals.referralGrant("free", null, now).proUntil)
        assertEquals("2026-10-17T00:00:00.000Z", Referrals.referralGrant("pro", "2026-10-10T00:00:00Z", now).proUntil)
        assertEquals("2026-10-07T00:00:00.000Z", Referrals.referralGrant("pro", "2026-09-01T00:00:00Z", now).proUntil)
        assertEquals("extended", Referrals.referralGrant("beta", "2026-12-31T00:00:00Z", now).kind)
        assertEquals("You and Ayan both get 1 week of Pro.", Referrals.claimMessage(true, referrerName = "Ayan"))
        assertEquals("That's your own invite.", Referrals.claimMessage(false, "self"))
        assertEquals("Couldn't use that invite right now.", Referrals.claimMessage(false, "weird"))
        assertNull(Referrals.bankedText(0))
        assertEquals("1 week of Pro banked", Referrals.bankedText(7))
        assertEquals("3 weeks of Pro banked", Referrals.bankedText(21))
        assertEquals("10 days of Pro banked", Referrals.bankedText(10))
        assertEquals("ABC23X", Referrals.codeFromPath(listOf("r", "abc23x")))
        assertNull(Referrals.codeFromPath(listOf("join", "ABC23X")))
    }

    // ---------------------------------------------------------------- D6 stamps + reactions
    @Test fun stampsAndReactions() {
        var r = Stamps.toggleStamp(Stamps.EMPTY, "clean")
        assertEquals(Stamps.Change(Stamps.State(1, 0, "clean"), "clean"), r)
        r = Stamps.toggleStamp(r.state, "cheat")
        assertEquals(Stamps.Change(Stamps.State(0, 1, "cheat"), "cheat"), r)
        r = Stamps.toggleStamp(r.state, "cheat")
        assertEquals(Stamps.Change(Stamps.State(0, 0, null), null), r)
        assertEquals("clean", Stamps.stampVerdict(Stamps.State(3, 1)))
        assertEquals("cheat", Stamps.stampVerdict(Stamps.State(1, 2)))
        assertNull(Stamps.stampVerdict(Stamps.State(2, 2)))
        assertNull(Stamps.stampVerdict(Stamps.EMPTY))
        assertTrue(Stamps.stampable("meal", "a.jpg"))
        assertFalse(Stamps.stampable("meal", null))
        assertFalse(Stamps.stampable("message", "a.jpg"))
        assertEquals(mapOf("p" to Stamps.State(2, 0, "clean")), Stamps.parseStampRows(listOf(mapOf("post_id" to "p", "clean" to "2", "cheat" to null, "mine" to "clean"), mapOf("post_id" to 3))))
        assertEquals(13, Reactions.ALL.size)
        assertEquals(listOf("🥗", "🍗", "🏋️", "🙌", "💯", "😤", "🫡"), Reactions.ALL.drop(6))
        assertEquals("❤️", Reactions.ALL[0])
        assertEquals("🏋️", Reactions.normalize("🏋"))
        assertEquals(mapOf("🫡" to 2), Reactions.parseCounts(JSONObject().put("🫡", 2).put("🤡", 5)))
    }

    // ---------------------------------------------------------------- D7 live
    @Test fun live() {
        val now = ms("2026-09-30T10:00:00Z")
        assertTrue(LiveSquad.isLiveFresh("2026-09-30T09:45:00Z", "2026-09-30T09:00:00Z", now))
        assertFalse(LiveSquad.isLiveFresh("2026-09-30T09:30:00Z", "2026-09-30T09:00:00Z", now))
        assertFalse(LiveSquad.isLiveFresh("2026-09-30T09:59:00Z", "2026-09-30T05:00:00Z", now))
        assertEquals("Ayan is training now 🔥", LiveSquad.liveLine("Ayan Kapoor"))
        assertEquals("Ayan is on leg day now 🔥", LiveSquad.liveLine("Ayan", "Leg day"))
        assertEquals("Someone is training now 🔥", LiveSquad.liveLine(""))
        assertEquals("12 min in", LiveSquad.liveElapsed("2026-09-30T09:48:00Z", now))
        assertEquals("1 h 05 min in", LiveSquad.liveElapsed("2026-09-30T08:55:00Z", now))
        assertEquals(3, LiveSquad.parseLiveRows(listOf(mapOf("user_id" to "me"), mapOf("user_id" to "u2", "cheers" to "3")), "me")[0].cheers)
    }

    // ---------------------------------------------------------------- D8 pledges
    @Test fun pledges() {
        val t = "2026-09-30"
        val base = Pledges.Draft("Log every day", "log_days", 6, "chai", 200, t, 7)
        assertNull(Pledges.validate(base, t))
        assertEquals("Say what you're pledging", Pledges.validate(base.copy(goal = "x"), t))
        assertEquals("That's more days than the pledge has (7)", Pledges.validate(base.copy(target = 8), t))
        assertEquals("Start today or later", Pledges.validate(base.copy(startsOn = "2026-09-29"), t))
        assertEquals("Pick 1 to 90 days", Pledges.validate(base.copy(days = 0), t))
        assertNull(Pledges.validate(base.copy(kind = "custom", target = null), t))
        assertEquals("2026-10-06", Pledges.endsOn(t, 7))
        assertEquals("Train on 4 of 7 days", Pledges.goalText("train_days", 4, 7, ""))
        assertEquals("No sugar", Pledges.goalText("custom", null, 7, " No sugar "))
        fun prog(kind: String, status: String, days: List<String>, today: String) = Pledges.progress(kind, 5, "2026-09-28", "2026-10-04", status, days, today)
        assertEquals(Pledges.Progress(2, 5, 5, "active", true), prog("log_days", "active", listOf("2026-09-28", "2026-09-29"), t))
        assertEquals("kept", prog("log_days", "active", listOf("2026-09-28", "2026-09-29", "2026-09-30", "2026-10-01", "2026-10-02"), "2026-10-02").outcome)
        assertEquals("broken", prog("log_days", "active", emptyList(), "2026-10-02").outcome)
        assertEquals("active", prog("custom", "active", emptyList(), t).outcome)
        assertEquals("kept", prog("log_days", "kept", emptyList(), t).outcome)
        assertEquals("Stake: chai for the squad · ₹200 into the squad pot", Pledges.stakeLine("chai for the squad", 200))
        assertEquals("No stake, just pride", Pledges.stakeLine("", 0))
        assertEquals("custom", Pledges.parsePledge(mapOf("id" to 1, "kind" to "zzz", "status" to "odd", "stake_inr" to "-4")).kind)
        assertEquals("active", Pledges.parsePledge(mapOf("status" to "odd")).status)
        assertEquals("₹1,00,000", Money.inr(100000))
        assertEquals("8,000", Money.group(8000))
    }

    // ---------------------------------------------------------------- D9 seasonal
    @Test fun seasonal() {
        assertEquals("2026-11-08", Seasonal.DIWALI[2026])
        val ev26 = Seasonal.eventsForYear(2026)
        assertEquals(listOf("diwali-protein-2026", "monsoon-steps-2026", "new-year-2026"), ev26.map { it.id })
        val diwali = ev26[0]
        assertEquals("2026-10-29", diwali.from)
        assertEquals("2026-11-12", diwali.to)
        val around = Seasonal.eventsAround("2026-09-30")
        assertEquals(listOf("monsoon-steps-2026"), around.live.map { it.id })
        assertEquals(listOf("diwali-protein-2026"), around.soon.map { it.id })
        assertEquals("2027-01-01", Seasonal.eventById("new-year-2027")?.from)
        assertNull(Seasonal.eventById("nope"))
        val pr = Seasonal.eventProgress(diwali, Seasonal.Data(listOf("2026-10-28", "2026-10-29", "2026-10-30"), emptyMap(), emptyList(), emptyList()))
        assertEquals(2, pr.value)
        assertFalse(pr.done)
        val monsoon = ev26[1]
        val steps = (0 until 30).associate { Dates.addDays("2026-07-01", it.toLong()) to 8000L }.toMutableMap()
        steps["2026-06-30"] = 20000L
        assertTrue(Seasonal.eventProgress(monsoon, Seasonal.Data(emptyList(), steps, emptyList(), emptyList())).done)
        steps["2026-07-01"] = 7999L
        assertEquals(29, Seasonal.eventProgress(monsoon, Seasonal.Data(emptyList(), steps, emptyList(), emptyList())).value)
        val ny = ev26[2]
        val logs = (0 until 25).map { Dates.addDays("2026-01-01", it.toLong()) }
        assertFalse(Seasonal.eventProgress(ny, Seasonal.Data(emptyList(), emptyMap(), logs, logs.take(11))).done)
        assertTrue(Seasonal.eventProgress(ny, Seasonal.Data(emptyList(), emptyMap(), logs, logs.take(12))).done)
        assertEquals("Ends in 1 day", Seasonal.eventWhen(monsoon, "2026-09-29"))
        assertEquals("Ends today", Seasonal.eventWhen(monsoon, "2026-09-30"))
        assertEquals("Starts 29 Oct", Seasonal.eventWhen(diwali, "2026-09-30"))
    }

    // ---------------------------------------------------------------- D11 packs
    @Test fun packs() {
        assertEquals(17, Packs.GOLD_COVERS.size)
        assertEquals("plates-gold", Packs.GOLD_COVERS[0].id)
        assertEquals(1, Packs.GOLD_COVERS[0].darkIndex)
        assertTrue(Packs.isGoldCover("summit-gold"))
        assertFalse(Packs.isGoldCover("summit-dark"))
        assertEquals("#0b0906", Packs.goldHex("#000000"))
        assertEquals("#fbe7a8", Packs.goldHex("#ffffff"))
        assertEquals("#fbe7a8", Packs.goldHex("#fff"))
        assertEquals("nope", Packs.goldHex("nope"))
        assertEquals("<rect fill=\"#0b0906\"/><stop stop-color=\"#fbe7a8\"/>", Packs.goldifySvg("<rect fill=\"#000\"/><stop stop-color=\"#FFFFFF\"/>"))
        assertEquals("₹149 · free in the beta", Packs.priceLabel(Packs.PACKS[0], true))
        assertEquals("₹149", Packs.priceLabel(Packs.PACKS[0], false))
        assertEquals("rose", Packs.parseSkin("rose"))
        assertEquals("classic", Packs.parseSkin("x"))
        assertTrue(Packs.skinAllowed("classic", emptyList()))
        assertFalse(Packs.skinAllowed("rose", emptyList()))
        assertTrue(Packs.skinAllowed("rose", listOf("skin-rose")))
    }

    // ---------------------------------------------------------------- D12 leagues (flag OFF)
    @Test fun leagues() {
        assertFalse(Leagues.leaguesOn(true))
        assertEquals("Diamond", Leagues.tierName(1))
        assertEquals("Bronze", Leagues.tierName(9))
        assertEquals(0, Leagues.movers(4))
        assertEquals(1, Leagues.movers(5))
        assertEquals(2, Leagues.movers(10))
        val pts = listOf(10, 50, 30, 50, 5)
        val st = pts.mapIndexed { i, p -> Leagues.Standing("g$i", p) }
        assertEquals(
            listOf("g1" to "up", "g3" to "stay", "g2" to "stay", "g0" to "stay", "g4" to "down"),
            Leagues.closeWeek(st, 3).map { it.groupId to it.movement },
        )
        assertEquals("stay", Leagues.closeWeek(st, 1)[0].movement)
        assertEquals("stay", Leagues.closeWeek(st, 5).last().movement)
    }

    // ---------------------------------------------------------------- D4 coach view
    @Test fun coachView() {
        assertEquals("ayan_k", CoachView.normalizeUsername(" @Ayan_K "))
        assertNull(CoachView.normalizeUsername("ab"))
        assertNull(CoachView.normalizeUsername("a.b.c"))
        val o = CoachView.parseOverview(
            JSONObject("""{"profile":{"name":"Riya","protein_target_g":100},"days":[{"date":"2026-09-30","meals":3,"protein_g":95,"trained":true},{"date":"2026-09-29","meals":2,"protein_g":60,"trained":false},{"date":"2026-09-20","meals":2,"protein_g":120,"trained":true}]}"""),
        )
        assertEquals("Riya", o.name)
        assertEquals("Logged 2 of 7 days · protein hit 1 · trained 1", CoachView.adherenceLine(o, "2026-09-30"))
        assertEquals(0, CoachView.parseOverview(null).days.size)
    }

    // ---------------------------------------------------------------- D1 Wrapped
    @Test fun wrapped() {
        assertEquals(Wrapped.Kind.YEAR, Wrapped.parseKind("year"))
        assertNull(Wrapped.parseKind("weekly"))
        val w = Wrapped.period(Wrapped.Kind.WEEK, "2026-09-30")
        assertEquals("2026-09-21" to "2026-09-27", w.from to w.to)
        assertEquals("2026-08-01", Wrapped.period(Wrapped.Kind.MONTH, "2026-09-30").from)
        val y = Wrapped.period(Wrapped.Kind.YEAR, "2026-09-30")
        assertEquals(listOf("2026-01-01", "2026-09-29", "2026 so far"), listOf(y.from, y.to, y.label))
        val y2 = Wrapped.period(Wrapped.Kind.YEAR, "2027-01-10")
        assertEquals(listOf("2026-01-01", "2026-12-31", "2026"), listOf(y2.from, y2.to, y2.label))
        val empty = Wrapped.Stats(w, 7, 0, 0, 0, 120, 0, null, null, emptyList(), 0, null, "Hit 3 sessions next week")
        val slides = Wrapped.slides(empty, Wrapped.Kind.WEEK, "Ayan Kapoor")
        assertEquals(listOf("cover", "days", "next"), slides.map { it.key })
        assertEquals("Locked in, Ayan.", slides[0].line)
        assertTrue(slides.none { Regex("kcal", RegexOption.IGNORE_CASE).containsMatchIn(it.big + it.line) })
        val full = empty.copy(workouts = 4, minutes = 180, proteinDays = 5, streak = 12, topFoods = listOf(Wrapped.Food("Dal", 6)), weight = Wrapped.Weight(80.0, 79.2, -0.8), squad = Wrapped.Squad("Iron", 2, 6))
        val s2 = Wrapped.slides(full, Wrapped.Kind.WEEK)
        assertEquals(listOf("cover", "days", "training", "protein", "weight", "food", "streak", "squad", "next"), s2.map { it.key })
        assertEquals("−0.8 kg", s2.first { it.key == "weight" }.big)
        assertEquals("August", Wrapped.slides(empty.copy(period = Wrapped.period(Wrapped.Kind.MONTH, "2026-09-30")), Wrapped.Kind.MONTH)[0].big)
    }

    // ---------------------------------------------------------------- E2 language + regional foods
    @Test fun language() {
        assertEquals("hi", I18n.parseLang("hi"))
        assertEquals("en", I18n.parseLang("xx"))
        assertEquals("होम", I18n.t("nav.home", "hi"))
        assertEquals("Khana", I18n.t("dial.food", "hinglish"))
        assertEquals("3 waiting to sync", I18n.t("sync.waiting", "en", mapOf("n" to 3)))
        assertNull(I18n.syncLabel(0))
        assertEquals("1 sync hona baaki", I18n.syncLabel(1, "hinglish"))
        assertEquals("3 सिंक होना बाकी", I18n.syncLabel(3, "hi"))
        for ((k, v) in I18n.STRINGS) assertTrue(k, v.size == 3 && v.all { it.isNotEmpty() })
        assertEquals("fish curry", RegionalFoods.regionalQuery("macher jhol"))
        assertEquals("2 chapati", RegionalFoods.regionalQuery("2 poli"))
        assertEquals("curd rice", RegionalFoods.regionalQuery("Thayir Sadam"))
        assertEquals("mishti doi", RegionalFoods.regionalQuery("mishti doi"))
        assertEquals("pav bhaji", RegionalFoods.regionalQuery("pav bhaji"))
        assertEquals("paneer tikka", RegionalFoods.regionalQuery("paneer tikka"))
        assertEquals("aloo posto", RegionalFoods.regionalMatch("luchi aur alu posto")?.canonical)
    }

    // ---------------------------------------------------------------- E1 offline queue
    @Test fun offlineQueue() {
        assertTrue(OfflineRules.isNetworkError(java.net.UnknownHostException("Unable to resolve host \"x.supabase.co\"")))
        assertTrue(OfflineRules.isNetworkError(java.net.SocketTimeoutException("timeout")))
        assertTrue(OfflineRules.isNetworkError(Exception("Load failed")))
        assertFalse(OfflineRules.isNetworkError(Exception("Pick a valid date")))
        assertTrue(OfflineRules.shouldQueue(false))
        assertTrue(OfflineRules.shouldQueue(true, Exception("NetworkError when attempting to fetch resource.")))
        assertFalse(OfflineRules.shouldQueue(true, Exception("Add at least one exercise")))
        var q = OfflineRules.enqueue(emptyList(), "meal", """{"raw_text":"2 roti"}""", "2026-09-30T10:00:00Z")
        q = OfflineRules.enqueue(q, "water", """{"ml":250}""", "2026-09-30T09:00:00Z")
        assertEquals(listOf("water", "meal"), OfflineRules.ordered(q).map { it.kind })
        val first = OfflineRules.ordered(q)[0]
        var r = OfflineRules.afterAttempt(q, first.id, OfflineRules.Outcome.NETWORK)
        assertTrue(r.stop)
        assertEquals(2, r.queue.size)
        r = OfflineRules.afterAttempt(q, first.id, OfflineRules.Outcome.OK)
        assertEquals(1, r.queue.size)
        var qq = q
        var dropped: OfflineRules.Item? = null
        repeat(OfflineRules.MAX_TRIES) {
            val a = OfflineRules.afterAttempt(qq, first.id, OfflineRules.Outcome.ERROR, "bad")
            qq = a.queue; dropped = a.dropped
        }
        assertEquals(1, qq.size)
        assertEquals(OfflineRules.MAX_TRIES, dropped?.tries)
        assertEquals("2 roti", OfflineRules.label(q[0]))
        assertEquals("250 ml water", OfflineRules.label(q[1]))
    }

    // ---------------------------------------------------------------- E5 export + safety
    @Test fun exportAndSafety() {
        assertEquals("a,b\r\n\"x,y\",\"say \"\"hi\"\"\"\r\n1.23,\r\n", ExportData.toCsv(listOf("a", "b"), listOf(listOf("x,y", "say \"hi\""), listOf(1.234, null)), false))
        assertEquals("f\r\n'=SUM(A1)\r\n-5\r\n", ExportData.toCsv(listOf("f"), listOf(listOf("=SUM(A1)"), listOf("-5")), false))
        assertTrue(ExportData.toCsv(listOf("a"), emptyList()).startsWith("﻿"))
        assertEquals(
            listOf("2026-09-30", "lunch", "Dal", 150.0, 180.0, 9.0, null, null, "dal"),
            ExportData.mealRows(listOf(ExportData.MealIn("2026-09-30", "dal", "lunch", listOf(ExportData.Item("Dal", 150.0, 180.0, 9.0, null, null)))))[0],
        )
        assertEquals(1, ExportData.mealRows(listOf(ExportData.MealIn("2026-09-30", null, null, emptyList()))).size)
        assertTrue(ExportData.combinedCsv(listOf(ExportData.Csv("weights", listOf("date"), listOf(listOf("2026-09-30"))))).contains("# weights\r\ndate"))
        assertTrue(Safety.deleteConfirmed(" delete "))
        assertFalse(Safety.deleteConfirmed("DELET"))
        assertEquals("spam", Safety.parseReason("spam"))
        assertNull(Safety.parseReason("x"))
        assertEquals("At least 6 characters", Safety.passwordProblem("12345", "12345"))
        assertEquals("The two passwords don't match", Safety.passwordProblem("123456", "123457"))
        assertNull(Safety.passwordProblem("123456", "123456"))
        assertEquals(listOf("a"), Safety.withoutBlocked(listOf("a", "b"), listOf("b")) { it })
        assertTrue(Safety.reportSnapshot("message", "hi", "Z", "2026-09-30T10:00:00Z").startsWith("by Z [message]"))
    }
}
