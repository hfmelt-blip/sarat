package com.sarat.app.domain

/** عبارات الترحيب والتحفيز باللهجة العراقية، حسب الوقت والحالة وجنس المستخدم. */
object Motivation {

    data class Context(
        val name: String,
        val sex: Sex,
        val minuteOfDay: Int,
        val consumed: Int,
        val target: Int,
        val streak: Int,
        val mealsToday: Int,
    )

    fun greeting(name: String, minuteOfDay: Int): String {
        val hour = minuteOfDay / 60
        val base = when (hour) {
            in 4..11 -> "صباح الخير"
            in 12..16 -> "هلا والله"
            else -> "مساء الخير"
        }
        return if (name.isBlank()) base else "$base يا $name"
    }

    fun welcomeTitle(name: String): String = if (name.isBlank()) "أهلاً بيك" else "أهلاً $name"

    private fun general(sex: Sex): List<String> {
        val f = sex == Sex.FEMALE
        return listOf(
            "يلا شد الهمة، وخلينا نحسب الأكل ونخلي صحتنا أحسن",
            "كل وجبة تحسبها خطوة لصحة أحسن، يلا نبدي",
            "اليوم فرصة جديدة، خلي همتك عالية",
            if (f) "إنتِ أقوى من أي أكلة، يلا نكمّل الطريق" else "إنت أقوى من أي أكلة، يلا نكمّل الطريق",
            if (f) "صحتچ تستاهل، وإحنا وياچ خطوة بخطوة" else "صحتك تستاهل، وإحنا وياك خطوة بخطوة",
            "شوية التزام اليوم، فرق كبير باچر",
            if (f) "هدفچ قريب، بس لا توگفين" else "هدفك قريب، بس لا توگف",
            "الصحة تاج، وإحنا نبنيها وجبة وجبة",
        )
    }

    /** عبارة التحفيز الرئيسية. [seed] يتغير كل مرة ينفتح التطبيق حتى تتنوع العبارات. */
    fun line(c: Context, seed: Int): String {
        val f = c.sex == Sex.FEMALE
        val remaining = c.target - c.consumed
        val hour = c.minuteOfDay / 60
        val pool = general(c.sex)
        val pick = pool[Math.floorMod(seed, pool.size)]

        return when {
            c.consumed > c.target * 1.05 ->
                "اليوم زدنا شوية، ولا يهمك، باچر نرجع أقوى"
            c.streak >= 3 && c.mealsToday == 0 ->
                if (f) "${c.streak} أيام ملتزمة ورا بعض، عاشت إيدچ! خلّيها ${c.streak + 1} اليوم"
                else "${c.streak} أيام ملتزم ورا بعض، عاشت إيدك! خلّيها ${c.streak + 1} اليوم"
            c.mealsToday > 0 && remaining in 1..350 && hour >= 17 ->
                "باقيلك $remaining سعرة بس، خلّي العشاء خفيف وتختم يومك بطل" + if (f) "ة" else ""
            c.mealsToday > 0 && remaining > 0 ->
                if (f) "ماشية صح! باقيلچ $remaining سعرة لليوم" else "ماشي صح! باقيلك $remaining سعرة لليوم"
            else -> pick
        }
    }
}
