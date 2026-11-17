package ma.sanai3i.push;

import java.util.Map;

/** Short push texts per notification kind ({name} = person, {v} = value). The app shows the full text. */
final class PushTexts {
    private PushTexts() {
    }

    private static final Map<String, String[]> T = Map.ofEntries(
            Map.entry("NEW_REQUEST", new String[]{"طلب جديد قريب منك", "Nouvelle demande près de vous"}),
            Map.entry("OFFER_RECEIVED", new String[]{"{name} صيفط ليك عرض: {v} درهم", "{name} vous propose {v} DH"}),
            Map.entry("OFFER_ACCEPTED", new String[]{"{name} قبل العرض ديالك 🎉", "{name} a accepté votre offre 🎉"}),
            Map.entry("OFFER_REJECTED", new String[]{"{name} ختار حرفي آخر", "{name} a choisi un autre artisan"}),
            Map.entry("OFFER_COUNTERED", new String[]{"{name} اقترح ثمن جديد: {v} درهم", "{name} propose {v} DH"}),
            Map.entry("OFFER_AGREED", new String[]{"{name} قبل الثمن ديالك 🎉", "{name} accepte votre prix 🎉"}),
            Map.entry("REQUEST_CANCELLED", new String[]{"{name} لغى الطلب", "{name} a annulé la demande"}),
            Map.entry("JOB_DONE", new String[]{"{name} أكد أن الخدمة سالات", "{name} a confirmé la fin du travail"}),
            Map.entry("REVIEW_RECEIVED", new String[]{"توصلتي بتقييم جديد", "Vous avez reçu un avis"}),
            Map.entry("ANNOUNCEMENT", new String[]{"{v}", "{v}"}),
            Map.entry("ADMIN_CANCELLED", new String[]{"الإدارة لغات طلب", "L’administration a annulé une demande"}),
            Map.entry("ARTISAN_APPROVED", new String[]{"الحساب ديالك تفعل 🎉", "Votre compte est activé 🎉"}),
            Map.entry("ARTISAN_REJECTED", new String[]{"الملف ديالك ترجع", "Votre dossier a été renvoyé"}),
            Map.entry("ARTISAN_SUBMITTED", new String[]{"ملف حرفي جديد للمراجعة", "Nouveau dossier artisan à vérifier"}),
            Map.entry("NEW_MESSAGE", new String[]{"{name}: {v}", "{name} : {v}"}),
            Map.entry("APPOINTMENT_PROPOSED", new String[]{"{name} اقترح موعد", "{name} propose un rendez-vous"}),
            Map.entry("APPOINTMENT_CONFIRMED", new String[]{"الموعد تأكد ✓", "Rendez-vous confirmé ✓"}),
            Map.entry("APPOINTMENT_REMINDER", new String[]{"تذكير: الموعد من دابا ساعة", "Rappel : rendez-vous dans une heure"}),
            Map.entry("CLIENT_REVIEWED", new String[]{"الحرفي قيّمك", "L’artisan vous a évalué"}),
            Map.entry("SUBSCRIPTION_EXTENDED", new String[]{"الاشتراك ديالك تجدد ✓", "Votre abonnement est prolongé ✓"}),
            Map.entry("SUBSCRIPTION_ENDING", new String[]{"الاشتراك ديالك غادي يسالي قريب", "Votre abonnement se termine bientôt"}),
            Map.entry("REPORT_CREATED", new String[]{"بلاغ جديد", "Nouveau signalement"}),
            Map.entry("REPORT_HANDLED", new String[]{"البلاغ ديالك تعالج", "Votre signalement a été traité"}),
            Map.entry("ARTISAN_ARRIVED", new String[]{"{name} وصل عندك", "{name} est arrivé"}),
            Map.entry("JOB_FINISHED", new String[]{"{name} قال الخدمة سالات. الباقي: {v} درهم. إلا خلصتيه عطيه الكود: {c}",
                    "{name} a terminé. Reste à payer : {v} DH. Une fois payé, donnez-lui le code {c}"}),
            Map.entry("JOB_DONE_CODE", new String[]{"الخدمة تسالات ✓ قيّم {name}", "Travail terminé ✓ Notez {name}"}),
            Map.entry("JOB_CLOSED", new String[]{"الكود صحيح ✓ الخدمة تسالات", "Code correct ✓ Travail clôturé"}),
            Map.entry("JOB_CLOSED_ADMIN", new String[]{"الفريق سالا هاد الخدمة", "L’équipe a clôturé ce travail"}),
            Map.entry("ARTISAN_WITHDREW", new String[]{"{name} ما يقدرش يجي. ختار عرض آخر", "{name} ne peut pas venir. Choisissez une autre offre"}),
            Map.entry("EXTRA_PROPOSED", new String[]{"{name} طلب زيادة: {v} درهم. وافق ولا رفض فالتطبيق", "{name} demande un supplément de {v} DH"}),
            Map.entry("ADVANCE_DECLARED", new String[]{"أكد التسبيق ديال {v} درهم", "Confirmez l’avance de {v} DH"}),
            Map.entry("ITEM_ACCEPTED", new String[]{"تقبل: {v} درهم ✓", "Accepté : {v} DH ✓"}),
            Map.entry("ITEM_REFUSED", new String[]{"ترفض: {v} درهم", "Refusé : {v} DH"}),
            Map.entry("WARRANTY_OPENED", new String[]{"الضمانة: {name} قال المشكل رجع. خاصك ترجع تصلحو بلا فلوس", "Garantie : {name} signale que le problème est revenu"}),
            Map.entry("WARRANTY_FIXED", new String[]{"{name} قال صلح المشكل. أكد ليه", "{name} dit avoir réparé. Confirmez"}),
            Map.entry("WARRANTY_NOT_FIXED", new String[]{"الكليان قال المشكل باقي", "Le client dit que le problème persiste"}),
            Map.entry("CASE_NEW", new String[]{"مشكل جديد خاصو المعالجة", "Nouveau litige à traiter"}),
            Map.entry("CASE_RESOLVED", new String[]{"الفريق جاوب على المشكل ديالك", "L’équipe a répondu à votre problème"}),
            Map.entry("STRIKE_GIVEN", new String[]{"توصلتي بإنذار", "Vous avez reçu un avertissement"}),
            Map.entry("ACCOUNT_SUSPENDED", new String[]{"الحساب ديالك توقف 7 أيام بسبب الإنذارات", "Votre compte est suspendu 7 jours (avertissements)"}),
            Map.entry("ARTISAN_CHANGES", new String[]{"تعديلات جديدة ديال {name} خاصها مراجعة", "Modifications de {name} à valider"}),
            Map.entry("ARTISAN_CHANGES_APPROVED", new String[]{"التعديلات ديالك تقبلو ✓", "Vos modifications sont validées ✓"}),
            Map.entry("ARTISAN_CHANGES_REJECTED", new String[]{"التعديلات ديالك ترفضو: {v}", "Vos modifications sont refusées : {v}"}),
            Map.entry("ARTISAN_SUSPENDED", new String[]{"الحساب ديالك كحرفي توقف: {v}", "Votre compte artisan est suspendu : {v}"}));

    static String text(String kind, String lang, String name, String value) {
        String[] pair = T.get(kind);
        if (pair == null) {
            return "fr".equals(lang) ? "Nouvelle notification" : "إشعار جديد";
        }
        String s = "fr".equals(lang) ? pair[1] : pair[0];
        String v = value == null ? "" : value;
        String c = "";
        int bar = v.indexOf('|');               // "amount|code"
        if (bar >= 0) {
            c = v.substring(bar + 1);
            v = v.substring(0, bar);
        }
        return s.replace("{name}", name == null ? "" : name).replace("{v}", v).replace("{c}", c).trim();
    }
}
