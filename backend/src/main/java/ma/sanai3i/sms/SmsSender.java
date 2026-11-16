package ma.sanai3i.sms;

/** Sends SMS. enabled() is false when no provider is configured: the SMS features are then turned off. */
public interface SmsSender {
    boolean enabled();

    /** @param phone international format, e.g. +212612345678 */
    void send(String phone, String text);
}
