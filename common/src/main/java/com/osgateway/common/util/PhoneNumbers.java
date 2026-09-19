package com.osgateway.common.util;

/**
 * Normalisation des numéros pour composition USSD (sans indicatif pays).
 * Les opérateurs Mobile Money attendent le numéro national (ex. {@code 70000000}),
 * pas le format international ({@code +22370000000}).
 */
public final class PhoneNumbers {

    /**
     * Indicatifs connus, du plus long au plus court (alignés frontend / apps mobiles).
     */
    private static final String[] DIAL_CODES = {
            "971", "966", "351", "291", "269", "267", "265", "264", "263", "261",
            "260", "258", "257", "256", "255", "254", "253", "252", "251", "250",
            "249", "248", "245", "244", "243", "242", "241", "240", "239", "238",
            "237", "236", "235", "234", "233", "232", "231", "230", "229", "228",
            "227", "226", "225", "224", "223", "222", "221", "220", "218", "216",
            "213", "212", "211", "86", "91", "49", "44", "41", "39", "34", "33",
            "32", "27", "20", "1"
    };

    private PhoneNumbers() {}

    /**
     * Retourne uniquement les chiffres du numéro national (sans indicatif).
     * Si le numéro est déjà national, il est renvoyé tel quel (chiffres seulement).
     */
    public static String toNationalDigits(String phone) {
        if (phone == null || phone.isBlank()) {
            return phone;
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.startsWith("00")) {
            digits = digits.substring(2);
        }
        for (String dial : DIAL_CODES) {
            if (digits.startsWith(dial) && digits.length() > dial.length()) {
                String national = digits.substring(dial.length());
                // Garde-fou : un mobile national a généralement ≥ 6 chiffres
                if (national.length() >= 6) {
                    return national;
                }
            }
        }
        return digits;
    }
}
