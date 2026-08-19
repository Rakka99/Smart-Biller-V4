package id.smartbiller.app.data

/** Master pelanggan hasil impor Data Master Pelanggan.xlsx (1328 record). */
object CustomerSeed {
    val all: List<Customer> = listOf(
        Customer("535111194993", "535111194993", "AAN", null, "RBM SAAKKGA • Gardu DJTH • Tiang 407L01", ULP("ULP Sumedang"), -6.823255, 107.921087, "R1", 450, "SAAKKGA", 1, "DJTH", "407L01"),
        Customer("535113379697", "535113379697", "MASJID JAMI AL-FATHONAH", null, "RBM SAAKKGA • Gardu DJHB • Tiang 418L04", ULP("ULP Sumedang"), -6.822226, 107.92581, "S1", 1300, "SAAKKGA", 2, "DJHB", "418L04")
    )
}
