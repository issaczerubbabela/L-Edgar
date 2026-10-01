package com.issaczerubbabel.ledgar.trip

import java.net.URLEncoder

/** The `upi://pay` link any UPI app (GPay, PhonePe, Paytm) opens with the payee and amount filled in. */
object UpiLink {
    fun build(vpa: String, payeeName: String, amountPaise: Long, note: String = "Trip settle-up"): String =
        "upi://pay?pa=${enc(vpa.trim())}&pn=${enc(payeeName)}&am=${TripMath.decimal(amountPaise)}&cu=INR&tn=${enc(note)}"

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
