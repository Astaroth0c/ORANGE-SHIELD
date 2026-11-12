/**
 * ORANGE SHIELD - Phone normalization utilities
 * Brazilian phone numbers (E.164) plus alphanumeric SMS senders.
 */

const COUNTRY_CODE = "55";

/**
 * Normalizes a raw input into an E.164-ish string.
 * Handles: (11) 99999-9999, 0303..., 0800..., +55..., shortcodes.
 * Alphanumeric senders (SMS) are returned uppercased unchanged.
 */
export function normalizePhone(raw: string): string {
  const trimmed = (raw ?? "").trim();
  if (!trimmed) return "";

  // Alphanumeric sender (e.g. "CLARO", "VIVO-OFERTA")
  if (/[A-Za-z]/.test(trimmed)) {
    return trimmed.toUpperCase().replace(/\s+/g, "");
  }

  // Genuine international dialing intent: "+..." or "00..." prefix
  const intlIntent = trimmed.startsWith("+") || trimmed.startsWith("00");

  let digits = trimmed.replace(/\D/g, "");
  if (!digits) return "";

  // Strip the international dial-out prefix (00)
  if (digits.startsWith("00")) digits = digits.slice(2);
  if (!digits) return "";

  // Already carries country code (55 + DDD + number)
  if (digits.startsWith(COUNTRY_CODE) && digits.length >= 12 && digits.length <= 13) {
    return `+${digits}`;
  }

  // Foreign country code — preserve as international
  // (Spoof Detector step 2 relies on genuine international presentation)
  if (intlIntent && !digits.startsWith(COUNTRY_CODE)) {
    return `+${digits}`;
  }

  // 0303 (Anatel telemarketing prefix), 0800/0300 (service numbers)
  if (digits.startsWith("0303") || digits.startsWith("0800") || digits.startsWith("0300")) {
    return `+${COUNTRY_CODE}${digits}`;
  }

  // Shortcodes (SMS premium / operator senders, 3-6 digits)
  if (digits.length <= 6) {
    return `+${COUNTRY_CODE}${digits}`;
  }

  // Local number with DDD
  return `+${COUNTRY_CODE}${digits}`;
}

/** True when the normalized number is international (not +55). */
export function isInternational(e164: string): boolean {
  return e164.startsWith("+") && !e164.startsWith(`+${COUNTRY_CODE}`);
}

/** True when the sender looks like an operator shortcode (3-6 digits after +55). */
export function isShortcode(e164: string): boolean {
  const m = e164.match(/^\+55(\d+)$/);
  return !!m && m[1].length <= 6;
}

/** Formats a Brazilian E.164 for display: +5511999998888 -> +55 (11) 99999-8888 */
export function formatPhone(e164: string): string {
  const m = e164.match(/^\+55(\d{2})(\d{4,5})(\d{4})$/);
  if (!m) return e164;
  return `+55 (${m[1]}) ${m[2]}-${m[3]}`;
}
