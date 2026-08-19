package id.smartbiller.app.data

/** Local review seed. Full 1,328-record master remains the source of truth in Excel/backend. */
object CustomerSeed {
    const val masterRecordCount: Int = 1328
    val all: List<Customer> = listOf(
        Customer("535111194993", "535111194993", "AAN", null, "RBM SAAKKGA • Gardu DJTH • Tiang 407L01", ULP("ULP Sumedang"), -6.823255, 107.921087, "R1", 450, "SAAKKGA", 1, "DJTH", "407L01"),
        Customer("535113379697", "535113379697", "MASJID JAMI AL-FATHONAH", null, "RBM SAAKKGA • Gardu DJHB • Tiang 418L04", ULP("ULP Sumedang"), -6.822226, 107.92581, "S1", 1300, "SAAKKGA", 2, "DJHB", "418L04"),
        Customer("535113133398", "535113133398", "DENNY KURNIAWAN", null, "RBM SAAKKGA • Gardu DJHB • Tiang 418L04", ULP("ULP Sumedang"), -6.822577, 107.921661, "R1", 1300, "SAAKKGA", 3, "DJHB", "418L04")
    )
}
