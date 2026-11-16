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
            Map.entry("REPORT_HANDLED", new String[]{"البلاغ ديالك تعالج", "Votre signalement a été traité"}));

    static String text(String kind, String lang, String name, String value) {
        String[] pair = T.get(kind);
        if (pair == null) {
            return "fr".equals(lang) ? "Nouvelle notification" : "إشعار جديد";
        }
        String s = "fr".equals(lang) ? pair[1] : pair[0];
        return s.replace("{name}", name == null ? "" : name).replace("{v}", value == null ? "" : value).trim();
    }
}
