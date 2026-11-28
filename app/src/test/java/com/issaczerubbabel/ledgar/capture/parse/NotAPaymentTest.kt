package com.issaczerubbabel.ledgar.capture.parse

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Not-a-payment alerts (#177): they show an amount but no money moved. Samples quoted in #177 are
 * real alerts with names, digits, references and VPAs blanked; the rest are worded after the
 * banks' real alerts but have not been seen on the phone yet, so swap in a real one when it turns up.
 */
class NotAPaymentTest {

    private fun assertDropped(text: String) = assertTrue(text, NotAPayment.matches(text))

    private fun assertKept(text: String) = assertFalse(text, NotAPayment.matches(text))

    @Test
    fun paymentRequestsAreNotPayments() {
        assertDropped(
            "<NAME> has requested money from you on Google Pay. On approving the request, INR 500.00 will be " +
                "debited from your A/c - Axis Bank"
        )
        assertDropped(
            "Mr <NAME> has requested money through Google-pay. On approval, Rs 200 will be debited from your " +
                "Bank Account-ICICI Bank"
        )
        // Not yet seen on the phone.
        assertDropped("<NAME> requested Rs. 150.00 from you. Pay now on Google Pay")
    }

    @Test
    fun deductionsStillToComeAreNotPayments() {
        // Not yet seen on the phone.
        assertDropped("Mandate Alert! Rs.2000.00 will be deducted from HDFC Bank A/c XX1234 on 05/12/26 towards ACME BROKING LTD")
        assertDropped("Rs.499.00 will be debited from your A/c XX1234 on 05-12-26 for your subscription")
    }

    @Test
    fun mandatesSetUpOrRevokedAreNotPayments() {
        // Not yet seen on the phone.
        assertDropped("Mandate Set! Rs.2000.00 monthly from HDFC Bank A/c XX1234 to ACME BROKING LTD. UMN 111111111111")
        assertDropped("Your UPI Mandate of Rs.2000.00 for ACME BROKING LTD has been successfully created")
        assertDropped("UPI Mandate of Rs.2000.00 towards ACME BROKING LTD from HDFC Bank A/c XX1234 has been revoked")
    }

    @Test
    fun instalmentsDueAreNotPayments() {
        // Not yet seen on the phone.
        assertDropped("RD Installment Due! INR 2000.00 for your HDFC Bank RD XX5678 is due on 12-DEC-26. Keep sufficient balance")
    }

    @Test
    fun declinedFailedAndExpiredPaymentsAreNotPayments() {
        // Not yet seen on the phone.
        assertDropped("TXN DECLINED! Rs.500.00 at SWIGGY on HDFC Bank Card XX9876 due to insufficient balance")
        assertDropped("Your UPI payment of Rs.500.00 to SWIGGY from A/c XX1234 has failed. Any amount debited will be refunded")
        assertDropped("The payment request of Rs.100.00 from <NAME> has expired")
    }

    @Test
    fun refundsOnlyInitiatedAreNotPayments() {
        assertDropped("Refund of Rs 150 has been initiated for Swiggy order 111111111111. It will reach your account in 5-7 days")
        assertDropped("Dear KFC customer, refund for your trxn of Rs 300 has been initiated and will reflect in 5-7 working days")
        // Not yet seen on the phone.
        assertDropped("Rs.250.00 will be refunded to your A/c XX1234 within 7 working days")
    }

    @Test
    fun offersAreNotPayments() {
        assertDropped("Hi!You're credited FREE 2GB TODAY.Also Music Subscription worth Rs 99 @Rs 1! Click to claim")
        assertDropped("Up to ₹2,500 in welcome rewards. Open a savings account with Google Pay today")
        assertDropped("EMI starts at ₹1,000 per month. Get a personal loan on Google Pay")
        assertDropped("Earn flat ₹11 cashback on your next mobile recharge")
        // Not yet seen on the phone.
        assertDropped("Get a welcome bonus of Rs.500 when you open an account")
        assertDropped("Get 10% cashback on mobile recharge of Rs.299 and above")
    }

    @Test
    fun gamingBonusOffersAreNotPayments() {
        assertDropped(
            "Dear , Rs.50 Bonus to be credited on My11Circle. MUM vs DEL Prize Pool - 50000 (1Cr) Entry Fees - Rs.49 " +
                "Click - http://xx.in/xxxx"
        )
        assertDropped(
            "Dear 98XX, Free Rs. 100 welcome bonus can be credited to your Junglee Rummy account. Download Now & Claim: " +
                "https://xx.im/xxxx"
        )
        assertDropped("Congratulation 98XX, Your account has been Credited Bonus with Rs.500 Register For Free xx.in/xxxx")
    }

    @Test
    fun realPaymentsAreKept() {
        assertKept("Credit Alert! Rs.500.00 credited to HDFC Bank A/c XX1234 on 12-11-26 from VPA xxxxxxxxxx@ybl (UPI 111111111111)")
        assertKept("UPI Mandate: Sent Rs.2000.00 from HDFC Bank A/c 1234 To ACME BROKING LTD On 05/11/26 Ref 111111111111")
        assertKept("Sent Rs.70.00 From HDFC Bank A/C *1234 To <NAME> On 26/09/26 Ref 111111111111")
        assertKept("Your a/c no. XXXXXXXX1234 is credited for Rs.35.00 on 14-09-2026 and debited from a/c no. XXXXXXXX5678 (UPI Ref no 111111111111) -CUB")
        assertKept("INR 100.00 was debited from your A/c no. XX1234 on 19-12-24 at 10:19:54 IST via UPI/P2M/111111111111/<MERCHANT>. Avail. Bal: INR 1,000.00.")
        assertKept("Amt Deducted! Rs.5000.00 from your HDFC Bank A/c XX1234 for NEFT txn via HDFC Bank Online Banking Not you?Call 18002586161")
    }

    @Test
    fun aRefundOrCashbackActuallyCreditedIsKept() {
        // Not yet seen on the phone.
        assertKept("Rs.150.00 credited to your A/c XX1234 on 12-11-26 as refund from SWIGGY. Avl bal INR 1,000.00")
        assertKept("Refund of Rs.300.00 credited to HDFC Bank A/c XX1234 on 12-11-26 for your KFC order")
        assertKept("Cashback of ₹11 credited to your bank account XX1234")
        assertKept("₹20 cashback on your mobile recharge has been credited to your bank account")
        assertKept("Welcome reward of ₹50 credited to your Amazon Pay balance")
        assertKept("Refund of Rs.300.00 initiated by AMAZON has been credited to A/c XX1234 on 12-11-26")
        assertKept("Your txn of Rs.500.00 has failed and the amount has been credited back to A/c XX1234")
    }

    @Test
    fun aRealPaymentMentioningAMandateOrAFutureChargeIsKept() {
        // Not yet seen on the phone.
        assertKept("Rs.649.00 debited from A/c XX1234 on 12-11-26 for mandate created on 01-11-26 towards NETFLIX")
        assertKept("Rs.500.00 debited from A/c XX1234 on 12-11-26 to VPA xxxxxxxxxx@ybl. Charges, if any, will be debited separately")
    }
}
